package com.kumuluz.ee.serverless.azf;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.kumuluz.ee.serverless.azf.error_handling.ExceptionHandling;
import com.kumuluz.ee.serverless.common.Commons;
import com.kumuluz.ee.serverless.common.ProjectParser;
import com.kumuluz.ee.serverless.common.pojo.RestEndpoint;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Mojo(name = "azf-generate-config", defaultPhase = LifecyclePhase.PACKAGE)
public class AzfGenerateConfigMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String targetDirectory;

    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;

    @Parameter(property = "configFolder", required = false, defaultValue = "azf-config")
    private String outConfigFolder;

    @Parameter(property = "generateDockerfile", required = false, defaultValue = "true")
    private boolean generateDockerfile;

    @Parameter(property = "javaVersion", required = false)
    private String javaVersion; // if not set, use the one from project

    private static final String TEMPLATES_FOLDER = "TEMPLATES";
    private static final String FUNCTIONS_FILE = "function.json";
    private static final String HOST_FILE = "host.json";
    private static final String HOST_FILE_EXPLODED = "host_exploded.json";
    private static final String HOST_FILE_JAR = "host_jar.json";
    private static final String LOCAL_SETTINGS_FILE = "local.settings.json";
    private static final String DOCKERFILE = "Dockerfile";

    private static final String EE_CLS_LOADER_FOLDER = Paths.get("tmp", "EeClassLoader").toString();

    private boolean jarPackaging; // true when jar, false when "copy-dependencies"

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        jarPackaging = Commons.getIsJarPackaging(project);
        try {

            createDirectoryStructure();
            List<RestEndpoint> endpoints = ProjectParser.getEndpoints(project);
            getLog().info("Found " + endpoints.size() + " endpoints in total");
            endpoints.sort(Comparator.comparing(o -> o.toString()));
            endpoints.forEach(endpoint -> getLog().info("\t\t" + endpoint));

            createConfigFiles(endpoints);

            if (generateDockerfile)
                generateDockerfile();
            clean();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createConfigFiles(List<RestEndpoint> endpoints) throws IOException {
        Path baseDirectory = Paths.get(targetDirectory, outConfigFolder);

        endpoints.stream().parallel().forEach(ExceptionHandling.throwingConsumerWrapper(endpoint -> {
            // create folder
            Path methodFolder = Paths.get(baseDirectory.toString(), endpoint.getFolderName());
            Files.createDirectories(methodFolder);

            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache m = mf.compile(Paths.get(TEMPLATES_FOLDER, FUNCTIONS_FILE).toString());
            StringWriter writer = new StringWriter();
            m.execute(writer, endpoint).flush();

            Commons.writeConfigFile(writer.toString(), methodFolder.toString(), FUNCTIONS_FILE);

        }));

        // still copy host.json and local.settings.json
        writeHostJson(baseDirectory);
        Commons.writeConfigFile(getConfigTemplate(LOCAL_SETTINGS_FILE), baseDirectory.toString(), LOCAL_SETTINGS_FILE);
    }

    private void writeHostJson(Path baseDirectory) throws IOException {
        String baseHostConfigFile = jarPackaging ? HOST_FILE_JAR : HOST_FILE_EXPLODED;
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache m = mf.compile(Paths.get(TEMPLATES_FOLDER, baseHostConfigFile).toString());
        Map<String, String> javaVersionMap = new HashMap();
        javaVersionMap.put("javaPath", Commons.getJavaPath());
        StringWriter writer = new StringWriter();
        m.execute(writer, javaVersionMap).flush();
        Commons.writeConfigFile(writer.toString(), baseDirectory.toString(), HOST_FILE);
    }

    private void generateDockerfile() throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache m = mf.compile(Paths.get(TEMPLATES_FOLDER, DOCKERFILE).toString());
        Map<String, String> javaVersionMap = new HashMap();
        javaVersionMap.put("javaVersion", javaVersion != null ? javaVersion : Commons.getJavaVersion(project));
        StringWriter writer = new StringWriter();
        m.execute(writer, javaVersionMap).flush();
        Commons.writeConfigFile(writer.toString(), Paths.get(targetDirectory, outConfigFolder).toString(), DOCKERFILE);
    }

    private static String getConfigTemplate(String fileName) {
        String filePath = "/" + Paths.get(TEMPLATES_FOLDER, fileName).toString();
        return new Scanner(AzfGenerateConfigMojo.class.getResourceAsStream(filePath), StandardCharsets.UTF_8)
                .useDelimiter("\\A").next();
    }

    private void clean() throws IOException {
        if (jarPackaging) {
            File tmpFolder = Paths.get(targetDirectory, outConfigFolder, EE_CLS_LOADER_FOLDER).toFile();
            for (File file: tmpFolder.listFiles())
                if (file.getName().endsWith(".jar"))
                    file.delete();
        }

    }

    private void createDirectoryStructure() throws IOException {
        Path containerFolder = Paths.get(targetDirectory, outConfigFolder);
        Files.createDirectories(containerFolder);
        getLog().info("Configuration will be created in " + containerFolder.toString());

        if (jarPackaging) {
            Path eeClsLoaderFolder = Paths.get(targetDirectory, outConfigFolder, EE_CLS_LOADER_FOLDER);
            Files.createDirectories(eeClsLoaderFolder);
            chmod777(eeClsLoaderFolder.toFile());
            getLog().info("Created container folder " + containerFolder.toString());
        }

    }

    private static void chmod777 (File file) {
        file.setReadable(true, false);
        file.setWritable(true, false);
        file.setExecutable(true, false);
    }

}
