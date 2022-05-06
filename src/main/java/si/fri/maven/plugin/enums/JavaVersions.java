package si.fri.maven.plugin.enums;

public enum JavaVersions {
    JAVA_DEFAULT("java"),
    JAVA_8("/usr/lib/jvm/adoptium-8-x64/bin/java"),
    JAVA_11("/usr/lib/jvm/zre-11-azure-amd64/bin/java");

    private String path;

    JavaVersions(String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }
}
