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
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;

import java.io.*;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

@Mojo(name = "azf-generate-config", defaultPhase = LifecyclePhase.PACKAGE)
public class AzfGenerateConfigMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String targetFolder;

    @Parameter(property = "configFolder", required = false, defaultValue = "azf-config")
    private String configFolder;

    @Parameter(property = "generateDockerfile", required = false, defaultValue = "false")
    private boolean generateDockerfile;

    @Parameter(property = "javaVersion", required = false)
    private String javaVersion; // if not set, use the one from project; relevant only for dockerfile

    @Parameter(property = "os", required = false)
    private String os; // relevant only when exploded packaging

    protected static final String TEMPLATES_FOLDER = "TEMPLATES";
    protected static final String FUNCTIONS_FILE = "function.json";
    protected static final String HOST_FILE = "host.json";
    protected static final String HOST_FILE_EXPLODED = "host_exploded.json";
    protected static final String HOST_FILE_JAR = "host_jar.json";
    protected static final String LOCAL_SETTINGS_FILE = "local.settings.json";
    protected static final String DOCKERFILE = "Dockerfile";

    private static final String EE_CLS_LOADER_FOLDER = Paths.get("tmp", "EeClassLoader").toString();

    private boolean jarPackaging; // true when jar, false when "copy-dependencies"

    @Override
    public void execute() throws MojoExecutionException {
        jarPackaging = Commons.getIsJarPackaging(project);
        String jarMsg = jarPackaging ? "Detected jar packaging" : "Detected `copy-dependencies` packaging";
        getLog().info(jarMsg);

        try {

            createDirectoryStructure();
            List<RestEndpoint> endpoints = ProjectParser.getEndpoints(project);

            getLog().info("Found " + endpoints.size() + " endpoints in total");
            endpoints.forEach(endpoint -> getLog().info("\t\t" + endpoint));

            createConfigFiles(endpoints);
            copyCode();

            if (generateDockerfile) {
                generateDockerfile();
            }

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to generate config", e);
        }
    }

    private void createConfigFiles(List<RestEndpoint> endpoints) throws IOException {
        Path baseDirectory = Paths.get(targetFolder, configFolder);

        endpoints.stream().parallel().forEach(ExceptionHandling.throwingConsumerWrapper(endpoint -> {
            // create folder
            Path methodFolder = Paths.get(baseDirectory.toString(), endpoint.getFolderName());
            Files.createDirectories(methodFolder);

            // set values in configuration
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache m = mf.compile(Paths.get(TEMPLATES_FOLDER, FUNCTIONS_FILE).toString());
            StringWriter writer = new StringWriter();
            m.execute(writer, endpoint).flush();

            // write configuration to file
            Commons.writeConfigFile(writer.toString(), methodFolder.toString(), FUNCTIONS_FILE);

        }));

        // still copy host.json and local.settings.json
        writeHostJson(baseDirectory);
        Commons.writeConfigFile(getConfigTemplate(LOCAL_SETTINGS_FILE), baseDirectory.toString(), LOCAL_SETTINGS_FILE);
    }

    private void writeHostJson(Path baseDirectory) throws IOException {
        // creates a `host.json` file with the appropriate configuration
        String baseHostConfigFile = jarPackaging ? HOST_FILE_JAR : HOST_FILE_EXPLODED;
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache m = mf.compile(Paths.get(TEMPLATES_FOLDER, baseHostConfigFile).toString());
        Map<String, String> javaPathMap = new HashMap<>();
        javaPathMap.put("javaPath", Commons.getJavaPath());
        boolean useWindowsSeparator = Commons.isWindowsOs();
        if (os != null) {
            if (!os.equals("windows") && !os.equals("linux")) {
                getLog().warn("Invalid os " + os + ". Valid values are `windows` and `linux`. Will keep the current os");
            }
            useWindowsSeparator = os.equals("windows");
        }
        javaPathMap.put("osSeparator", useWindowsSeparator ? ";" : ":");
        StringWriter writer = new StringWriter();
        m.execute(writer, javaPathMap).flush();
        Commons.writeConfigFile(writer.toString(), baseDirectory.toString(), HOST_FILE);
    }

    private void copyCode() throws IOException {
        if (jarPackaging) {
            Path targetFile = Paths.get(targetFolder, configFolder, "handler.jar");
            Path sourceFile = Paths.get(targetFolder, project.getBuild().getFinalName() + ".jar");
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            FileUtils.copyDirectoryStructure(Paths.get(targetFolder, "classes").toFile(), Paths.get(targetFolder, configFolder, "classes").toFile());
            FileUtils.copyDirectoryStructure(Paths.get(targetFolder, "dependency").toFile(), Paths.get(targetFolder, configFolder, "dependency").toFile());
        }
    }


    private void generateDockerfile() throws IOException {
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache m = mf.compile(Paths.get(TEMPLATES_FOLDER, DOCKERFILE).toString());
        Map<String, String> javaVersionMap = new HashMap<>();
        javaVersionMap.put("javaVersion", javaVersion != null ? javaVersion : Commons.getJavaVersion(project));
        StringWriter writer = new StringWriter();
        m.execute(writer, javaVersionMap).flush();
        Commons.writeConfigFile(writer.toString(), Paths.get(targetFolder, configFolder).toString(), DOCKERFILE);
    }

    private static String getConfigTemplate(String fileName) throws IOException {
        // reads the file `fileName` in the templates folder and returns it as a string

        String filePath =  "/" + Paths.get(TEMPLATES_FOLDER, fileName); // can we do this more elegantly?
        try (InputStream in = AzfGenerateConfigMojo.class.getResourceAsStream(filePath)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

    }

    private void createDirectoryStructure() throws IOException {
        Path containerFolder = Paths.get(targetFolder, configFolder);
        Files.createDirectories(containerFolder);
        getLog().info("Configuration will be created in " + containerFolder);

        if (jarPackaging) {
            Path eeClsLoaderFolder = Paths.get(containerFolder.toString(), EE_CLS_LOADER_FOLDER);
            Files.createDirectories(eeClsLoaderFolder);
            Commons.chmod777(eeClsLoaderFolder.toFile());
        }
    }

}
