package com.kumuluz.ee.serverless.azf;

import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Commons {

    private final static String EXECUTABLE_PATH_KEY = "defaultExecutablePath";
    private final static String LINUX_JAVA_PATH = "%JAVA_PATH%/bin/java";
    private final static String WINDOWS_JAVA_PATH = "%JAVA_PATH%\\bin\\java";

    public static String getJavaVersion(MavenProject project) {
        String javaVersion = (String) project.getProperties().get("maven.compiler.target");
        if (javaVersion.equals("1.8"))
            javaVersion = "8";
        return javaVersion;
    }

    public static void setJavaPathInHost(Path hostFile, String javaVersion) throws IOException {
        String config = Files.readString(hostFile);
        int idx = config.indexOf(EXECUTABLE_PATH_KEY);
        int begin = config.indexOf('"', idx + EXECUTABLE_PATH_KEY.length() + 2);
        int end = config.indexOf('"', begin + 1);
        String newConfig = config.substring(0, begin + 1) + javaVersion + config.substring(end);
        writeConfigFile(newConfig, hostFile.toFile());
    }

    protected static void writeConfigFile(String config, String folder, String fileName) {
        writeConfigFile(config, Paths.get(folder, fileName).toFile());
    }

    private static void writeConfigFile(String config, File file) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected static String getJavaPath() {
        return Paths.get("%JAVA_HOME%", "bin", "java").toString();
    }

    protected static String getJavaPathOS(String os) throws IOException {
        if (os.equals("linux"))
            return LINUX_JAVA_PATH;
        else if (os.equals("windows"))
            return WINDOWS_JAVA_PATH;
        else
            throw new IOException(String.format("Invalid operating system selected! Valid values are [%s, %s]", "linux", "windows"));
    }
}
