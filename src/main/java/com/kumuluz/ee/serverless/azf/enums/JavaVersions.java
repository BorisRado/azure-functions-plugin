package com.kumuluz.ee.serverless.azf.enums;

public enum JavaVersions {
    // default java ome ->> add docs
    JAVA_DEFAULT("java"),
    JAVA_8("%JAVA_HOME%/bin/java"),
    JAVA_11("%JAVA_HOME%/bin/java");

    private String path;

    JavaVersions(String path) {
        this.path = path;
    }

    public String getPath() {
        return this.path;
    }
}
