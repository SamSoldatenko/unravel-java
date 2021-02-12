package unravel.java;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class Analyzer {
    private LinkedList<Unit> units = new LinkedList<>();

    private Unit currentUnit;

    public void analyzeFile(Path path) throws IOException {
        if (isWarFile(path)) {
            analyzeWarFile(path, path.getFileName().toString());
        } else if (isJarFile(path)) {
            analyzeJarFile(path, path.getFileName().toString());
        }
    }

    private boolean isWarFile(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry webInf = zipFile.getEntry("WEB-INF");
            return webInf != null;
        } catch (ZipException | FileNotFoundException e) {
            return false;
        }
    }

    private boolean isJarFile(Path path) throws IOException {
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            ZipEntry webInf = zipFile.getEntry("WEB-INF");
            ZipEntry metaInf = zipFile.getEntry("META-INF");
            return webInf == null && metaInf != null;
        } catch (ZipException | FileNotFoundException e) {
            return false;
        }
    }

    private void analyzeWarFile(Path path, String unitName) throws IOException {
        Unit prevUnit = currentUnit;
        try (ZipFile zipFile = new ZipFile(path.toFile())) {
            Unit war = new Unit(UnitType.WAR, unitName);
            units.add(war);
            currentUnit = war;
            analyzeWebInfClasses(zipFile);
            analyzeWebInfLibs(zipFile);
        } finally {
            currentUnit = prevUnit;
        }
    }

    private void analyzeWebInfClasses(ZipFile zipFile) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            if (zipEntry.getName().startsWith("WEB-INF/classes/")) {
                parseZipEntry(zipEntry, zipFile);
            }
        }
    }

    private void analyzeWebInfLibs(ZipFile zipFile) throws IOException {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry zipEntry = entries.nextElement();
            if (zipEntry.getName().matches("WEB-INF/lib/.*\\.jar")) {
                Path jar = Files.createTempFile("unravel-java-tmp", ".jar");
                Unit unit = currentUnit;
                try {
                    Files.copy(zipFile.getInputStream(zipEntry), jar, StandardCopyOption.REPLACE_EXISTING);
                    String jarName = Path.of(zipEntry.getName()).getFileName().toString();
                    analyzeJarFile(jar, jarName);
                } finally {
                    currentUnit = unit;
                    Files.deleteIfExists(jar);
                }
            }
        }
    }

    private void analyzeJarFile(Path path, String unitName) throws IOException {
        Unit unit = currentUnit;
        try {
            Unit jar = new Unit(UnitType.JAR, unitName);
            units.add(jar);
            currentUnit = jar;

            ZipFile zipFile = new ZipFile(path.toFile());
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                parseZipEntry(zipEntry, zipFile);
            }
        } finally {
            currentUnit = unit;
        }
    }

    public void parseZipEntry(ZipEntry entry, ZipFile zipFile) throws IOException {
        if (entry.isDirectory()) {
            // Skipping. It is just a structure in jar file
        } else if (entry.getName().endsWith(".class")) {
            ClassReader classReader = new ClassReader(zipFile.getInputStream(entry));
            ClassNode klazz = new ClassNode();
            classReader.accept(klazz, 0);
            currentUnit.addClass(klazz);
        } else {
            // skipping other files
        }
    }

    public void writeJson(OutputStream out) {
        JsonObject report = Json.createObjectBuilder()
                .add("units", buildUnitsJson())
                .build();

        JsonWriterFactory factory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
        try (JsonWriter writer = factory.createWriter(out, StandardCharsets.UTF_8)) {
            writer.writeObject(report);
        }
    }

    private JsonArray buildUnitsJson() {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Unit unit : units) {
            JsonObjectBuilder unitBuilder = Json.createObjectBuilder()
                    .add("type", unit.getType().toString())
                    .add("name", unit.getName())
                    .add("classes", buildClassesJson(unit));

            builder.add(unitBuilder);
        }
        return builder.build();
    }

    private JsonArray buildClassesJson(Unit unit) {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (ClassNode classNode : unit.getKlazzes()) {
            JsonObjectBuilder classBuilder = Json.createObjectBuilder()
                    .add("name", classNode.name);
            if (classNode.superName != null && !"java/lang/Object".equals(classNode.superName)) {
                classBuilder.add("extends", classNode.superName);
            }
            if (classNode.outerClass != null) {
                classBuilder.add("innerOf", classNode.outerClass);
            }
            if (classNode.interfaces.size() > 0) {
                classBuilder.add("implements", Json.createArrayBuilder(classNode.interfaces));
            }

            if (classNode.visibleAnnotations != null || classNode.invisibleAnnotations != null) {
                JsonArrayBuilder annotationsBuilder = Json.createArrayBuilder();
                if (classNode.visibleAnnotations != null) {
                    for (AnnotationNode annotation : classNode.visibleAnnotations) {
                        annotationsBuilder.add(buildAnnotationJson(annotation));
                    }
                }
                if (classNode.invisibleAnnotations != null) {
                    for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                        annotationsBuilder.add(buildAnnotationJson(annotation));
                    }
                }
                classBuilder.add("annotations", annotationsBuilder);
            }

            if (classNode.fields.size() > 0) {
                JsonObjectBuilder fieldsBuilder = Json.createObjectBuilder();
                for (FieldNode field : classNode.fields) {
                    fieldsBuilder.add(field.name, Type.getType(field.desc).getInternalName());
                }
                classBuilder.add("fields", fieldsBuilder);
            }

            if (classNode.methods.size() > 0) {
                JsonArrayBuilder methodsBuilder = Json.createArrayBuilder();

                for (MethodNode method : classNode.methods) {
                    JsonObjectBuilder methodBuilder = Json.createObjectBuilder();
                    methodBuilder.add("name", method.name);

                    Type returnType = Type.getReturnType(method.desc);
                    if (returnType.getSort() == Type.OBJECT) {
                        methodBuilder.add("return", returnType.getInternalName());
                    } else if (returnType.getSort() == Type.ARRAY) {
                        methodBuilder.add("return", returnType.getElementType().getInternalName());
                    }

                    Type[] argumentTypes = Type.getArgumentTypes(method.desc);
                    if (argumentTypes.length > 0) {
                        JsonArrayBuilder argumentsBuilder = Json.createArrayBuilder();
                        for (Type argumentType : argumentTypes) {
                            argumentsBuilder.add(argumentType.getInternalName());
                        }
                        methodBuilder.add("arguments", argumentsBuilder);
                    }

                    JsonArrayBuilder callsBuilder = Json.createArrayBuilder();
                    for (AbstractInsnNode i : method.instructions) {
                        if (i instanceof MethodInsnNode) {
                            MethodInsnNode m = (MethodInsnNode) i;
                            if (!m.owner.equals(classNode.name)) {
                                callsBuilder.add(Json.createObjectBuilder()
                                        .add("class", m.owner)
                                        .add("method", m.name));
                            }
                        }
                    }
                    JsonArray callsJson = callsBuilder.build();
                    if (callsJson.size() > 0) {
                        methodBuilder.add("calls", callsJson);
                    }
                    methodsBuilder.add(methodBuilder);
                }
                classBuilder.add("methods", methodsBuilder);
            }
            builder.add(classBuilder);
        }
        return builder.build();
    }

    private JsonObjectBuilder buildAnnotationJson(AnnotationNode annotation) {
        Type a = Type.getType(annotation.desc);
        JsonObjectBuilder annotationBuilder = Json.createObjectBuilder();
        annotationBuilder.add("type", a.getInternalName());
        if (annotation.values != null && annotation.values.size() > 0) {
            annotationBuilder.add("values", buildAnnotationValuesJson(annotation.values));
        }
        return annotationBuilder;
    }

    private JsonObjectBuilder buildAnnotationValuesJson(List<Object> values) {
        JsonObjectBuilder valuesBuilder = Json.createObjectBuilder();

        for (int i = 0; i < values.size(); i += 2) {
            String name = (String) values.get(i);
            Object value = values.get(i + 1);

            if (value instanceof Byte) {
                valuesBuilder.add(name, (Byte) value);
            } else if (value instanceof Boolean) {
                valuesBuilder.add(name, (Boolean) value);
            } else if (value instanceof Character) {
                valuesBuilder.add(name, (Character) value);
            } else if (value instanceof Short) {
                valuesBuilder.add(name, (Short) value);
            } else if (value instanceof Integer) {
                valuesBuilder.add(name, (Integer) value);
            } else if (value instanceof Long) {
                valuesBuilder.add(name, (Long) value);
            } else if (value instanceof Float) {
                valuesBuilder.add(name, (Float) value);
            } else if (value instanceof Double) {
                valuesBuilder.add(name, (Double) value);
            } else if (value instanceof String) {
                valuesBuilder.add(name, (String) value);
            } else
                if (value instanceof Type) {
                    valuesBuilder.add(name, ((Type) value).getInternalName()); // class
                } else if (value instanceof String[] && ((String[]) value).length == 2) {
                    String enumType = ((String[]) value)[0];
                    String enumValue = ((String[]) value)[1];
                    valuesBuilder.add(name, enumValue);
                } else if (value instanceof AnnotationNode) {
                    valuesBuilder.add(name, buildAnnotationJson((AnnotationNode) value));
                } else if (value instanceof List) {
                    JsonArrayBuilder arrayBuilder = Json.createArrayBuilder();
                    for (Object v : (List) value) {
                        JsonValue valueElement = buildAnnotationValuesJson(Arrays.asList("fake-name", v))
                                .build()
                                .get("fake-name");
                        arrayBuilder.add(valueElement);
                    }
                    valuesBuilder.add(name, arrayBuilder);
                } else {
                    throw new IllegalStateException("Unknown Annotation parameter");
                }
        }
        return valuesBuilder;
    }
}
