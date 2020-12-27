package unravel.java;


/**
 * <pre>
 * Location of class. Idea (not everything described here is implemented or designed).
 * Example:
 * 1. If jar file is analyzed
 *      - one locations for the jar itself
 *      - one per each jar referenced from MANIFEST.MF
 * 2. If war is analyzed
 *      - one location for classes from META-INF/classes
 *      - one location per each jar from META-INF/libs
 * 3. If project is analyzed (we can know dependencies)
 *      - one location per each module in project
 *      - one location per each dependency
 * </pre>
 */
public class Location {
    private String id;
    private String type;
    private String name;

    public Location(String id, String type, String name) {
        this.id = id;
        this.type = type;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
