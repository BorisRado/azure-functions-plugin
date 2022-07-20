package com.kumuluz.ee.serverless.common;

import org.apache.maven.model.Plugin;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Commons {

    private static Logger log = Logger.getLogger(Commons.class.getName());

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

    public static void writeConfigFile(String config, String folder, String fileName) throws IOException {
        writeConfigFile(config, Paths.get(folder, fileName).toFile());
    }

    private static void writeConfigFile(String config, File file) throws IOException {
        Files.writeString(file.toPath(), config);
    }

    public static String getJavaPath() {
        return Paths.get("%JAVA_HOME%", "bin", "java").toString();
    }

    public static boolean isWindowsOs() {
        return System.getProperty("os.name").equalsIgnoreCase("windows");
    }

    public static void chmod777 (File file) {
        file.setReadable(true, false);
        file.setWritable(true, false);
        file.setExecutable(true, false);
    }

    public static void zipSingleFile(Path file, Path folder, ZipOutputStream zipOut) throws IOException {
        if (file.toString().equals(folder.toString()) || file.toString().contains("Dockerfile"))
            return;

        String fileName = folder.relativize(file).toString();
        if (file.toFile().isDirectory()) {
            zipOut.putNextEntry(new ZipEntry(fileName + "/"));
            zipOut.closeEntry();
        } else {
            try (FileInputStream fis = new FileInputStream(file.toString())) {

                ZipEntry zipEntry = new ZipEntry(fileName);
                zipOut.putNextEntry(zipEntry);
                byte[] bytes = new byte[1_024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 1, length);
                }
            }
        }
    }

}
