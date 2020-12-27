package unravel.java;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import javax.json.*;
import javax.json.stream.JsonGenerator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Analyzer {
    private int num = 0;
    private Map<Location, List<ClassNode>> classesByLocation = new LinkedHashMap<>();
    private Map<ClassNode, String> classIds = new LinkedHashMap<>();
    private Location location;
    private ZipFile zipFile;

    public void analyzeFile(Path path) throws IOException {
        zipFile = new ZipFile(path.toFile());
        try {
            location = new Location("l" + num, "jar", zipFile.getName());
            num++;
            classesByLocation.put(location, new ArrayList<>());
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                parseZipEntry(zipEntry);
            }
        } finally {
            zipFile.close();
            zipFile = null;
            location = null;
        }
    }

    public void parseZipEntry(ZipEntry entry) throws IOException {
        if (entry.isDirectory()) {
            // Skipping. It is just a structure in jar file
        } else if (entry.getName().endsWith(".class")) {
            ClassReader classReader = new ClassReader(zipFile.getInputStream(entry));
            ClassNode klazz = new ClassNode();
            classReader.accept(klazz, 0);
            classesByLocation.get(location).add(klazz);
        } else {
            // skipping other files
        }
    }

    private String getClassId(ClassNode node) {
        return classIds.computeIfAbsent(node, n -> "c" + (num++));
    }

    /**
     * <pre>
     * Json should contain
     * - classes
     * - relations (extends, implements)
     * - methods
     * - references (types used as parameter types or method's return value type)
     * - calls to another classes
     * - stereotypes (like request scoped or REST resource)
     * </pre>
     *
     * @param out output stream for json
     */
    public void writeJson(OutputStream out) {
        JsonObject report = Json.createObjectBuilder()
                .add("locations", buildLocationsJson())
                .add("classes", buildClassesJson())
                .add("refs", buildReferences())
                .build();

        JsonWriterFactory factory = Json.createWriterFactory(Map.of(JsonGenerator.PRETTY_PRINTING, true));
        try (JsonWriter writer = factory.createWriter(out, StandardCharsets.UTF_8)) {
            writer.writeObject(report);
        }
    }

    private JsonArray buildLocationsJson() {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Location location : classesByLocation.keySet()) {
            JsonObjectBuilder locationBuilder = Json.createObjectBuilder()
                    .add("id", location.getId())
                    .add("type", location.getType())
                    .add("name", location.getName());
            builder.add(locationBuilder);
        }
        return builder.build();
    }

    private JsonArray buildClassesJson() {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Map.Entry<Location, List<ClassNode>> entry : classesByLocation.entrySet()) {
            for (ClassNode classNode : entry.getValue()) {
                JsonObjectBuilder classBuilder = Json.createObjectBuilder()
                        .add("id", getClassId(classNode))
                        .add("name", classNode.name)
                        .add("location", entry.getKey().getId());
                builder.add(classBuilder);
            }
        }
        return builder.build();
    }

    private JsonArray buildReferences() {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (Map.Entry<Location, List<ClassNode>> entry : classesByLocation.entrySet()) {
            for (ClassNode classNode : entry.getValue()) {

                if (classNode.superName != null && !"java/lang/Object".equals(classNode.superName)) {
                    JsonObjectBuilder extendsBuilder = Json.createObjectBuilder()
                            .add("from", getClassId(classNode))
                            .add("type", "extends")
                            .add("to", "");
                    builder.add(extendsBuilder);
                }

                if (classNode.interfaces != null && classNode.interfaces.size() > 0) {
                    for (String anInterface : classNode.interfaces) {
                        JsonObjectBuilder extendsBuilder = Json.createObjectBuilder()
                                .add("from", getClassId(classNode))
                                .add("type", "implements")
                                .add("to", "");
                        builder.add(extendsBuilder);
                    }
                }

                if (classNode.nestHostClass != null) {
                    JsonObjectBuilder extendsBuilder = Json.createObjectBuilder()
                            .add("from", getClassId(classNode))
                            .add("type", "innerOf")
                            .add("to", "");
                    builder.add(extendsBuilder);
                }
            }
        }
        return builder.build();
    }
}
