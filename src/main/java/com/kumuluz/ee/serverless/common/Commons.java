package com.kumuluz.ee.serverless.common;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Paths;
import java.util.List;

public class Commons {

    public static String getJavaVersion(MavenProject project) {
        String javaVersion = (String) project.getProperties().get("maven.compiler.target");
        if (javaVersion.equals("1.8"))
            javaVersion = "8";
        return javaVersion;
    }

    @SuppressWarnings("unchecked")
    public static boolean getIsJarPackaging(MavenProject project) {
        List<Plugin> buildPlugins = (List<Plugin>) project.getBuildPlugins();
        return buildPlugins.stream().anyMatch(plugin -> plugin.getArtifactId().equals("kumuluzee-maven-plugin"));
    }

    public static void writeConfigFile(String config, String folder, String fileName) {
        writeConfigFile(config, Paths.get(folder, fileName).toFile());
    }

    private static void writeConfigFile(String config, File file) {
        try (BufferedWriter out = new BufferedWriter(new FileWriter(file))) {
            out.write(config);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String getJavaPath() {
        return Paths.get("%JAVA_HOME%", "bin", "java").toString();
    }

    public static boolean isWindowsOs() {
        return System.getProperty("os.name").equalsIgnoreCase("windows");
    }

}
