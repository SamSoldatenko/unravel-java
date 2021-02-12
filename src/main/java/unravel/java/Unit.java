package unravel.java;


import org.objectweb.asm.tree.ClassNode;

import java.util.LinkedList;
import java.util.List;

/**
 * <pre>
 * Part of analyzed application
 * Example:
 * 1. If jar file is analyzed
 *      - one locations for the jar itself
 *      - one per each jar referenced from MANIFEST.MF
 * 2. If war is analyzed
 *      - one location for classes from META-INF/classes
 *      - one location per each jar from META-INF/libs
 * </pre>
 */
public class Unit {
    private UnitType type;
    private String name;
    private List<ClassNode> klazzes = new LinkedList<>();

    public Unit(UnitType type, String name) {
        this.type = type;
        this.name = name;
    }

    public UnitType getType() {
        return type;
    }

    public void setType(UnitType type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addClass(ClassNode klazz) {
        klazzes.add(klazz);
    }

    public List<ClassNode> getKlazzes() {
        return klazzes;
    }
}
