package si.fri.maven.plugin;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.maven.project.MavenProject;
import si.fri.maven.plugin.enums.JavaVersions;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class Commons {

    private final static String EXECUTABLE_PATH_KEY = "defaultExecutablePath";

    public static JavaVersions getJavaVersion(MavenProject project) {
        String javaVersion = (String) project.getProperties().get("maven.compiler.target");
        if (javaVersion.equals("11"))
            return JavaVersions.JAVA_11;
        else if (javaVersion.equals("8") || javaVersion.equals("1.8"))
            return JavaVersions.JAVA_8;
        else
            throw new NotImplementedException("Only java 8 and 11 are supported");
    }

    public static void setJavaPathInHost(Path hostFile, JavaVersions javaVersion) throws IOException {
        String config = Files.readString(hostFile);
        int idx = config.indexOf(EXECUTABLE_PATH_KEY);
        int begin = config.indexOf('"', idx + EXECUTABLE_PATH_KEY.length() + 2);
        int end = config.indexOf('"', begin + 1);
        String newConfig = config.substring(0, begin + 1) + javaVersion.getPath() + config.substring(end);
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
}
