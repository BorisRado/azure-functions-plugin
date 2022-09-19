package com.kumuluz.ee.serverless.azf;

import com.kumuluz.ee.serverless.common.Commons;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import com.kumuluz.ee.serverless.azf.error_handling.ExceptionHandling;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

/**
 * @author Boris Radovic
 * @since 1.0.0
 */

@Mojo(name = "azf-deploy")
public class AzfDeployMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "removeZipFile", defaultValue = "true")
    private boolean removeZipFile;

    private static final String RESOURCE_GROUP_ENV_VAR = "RESOURCE_GROUP";
    private static final String FUNCTION_APP_ENV_VAR = "FUNCTION_APP";
    private static final String AZR_USER_ENV_VAR = "AZF_USER";
    private static final String AZF_USER_PSW_ENV_VAR = "AZF_USER_PSW";
    private static final String ZIP_FILE_NAME_ENV_VAR = "ZIP_FILE_NAME";
    private static final String CONFIG_FOLDER_ENV_VAR = "CONFIG_FOLDER";
    private static final String REMOVE_ZIP_ENV_VAR = "REMOVE_ZIP";
    private static final String INITIAL_INVOKE_ENV_VAR = "INITIAL_INVOKE";
    private static final String DEPLOY_WITH_REST_ENV_VAR = "DEPLOY_WITH_REST";

    private String resourceGroupName;
    private String functionAppName;
    private String azfUser;
    private String azfUserPassword;
    private String zipFileName = "kumuluzEeAzFunction.zip";
    private String configFolder = "azf-config";
    private boolean initialInvoke = true;
    private boolean deployWithRest = true;

    private static final String SERVERLESS_CONFIG_FILE = ".azf";

    public void execute() throws MojoExecutionException {

        try {

            loadConfig();
            if(!checkConfig()) {
                throw new MojoExecutionException("Failed to deploy - invalid configuration");
            }

            zipConfigAndCode();

            // push to azure functions
            deploy();

            if (removeZipFile) {
                getLog().info("Deleting " + zipFileName);
                Files.delete(Paths.get(zipFileName));
            }

            if (initialInvoke) {
                testCallAzf();
            }

        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Failed to deploy", e);
        }

    }

    private boolean checkConfig() {
        boolean isValidConfiguration = true;
        if (functionAppName == null) {
            getLog().error("Function app name was not provided, but it is required! Please set it with " + FUNCTION_APP_ENV_VAR);
            isValidConfiguration = false;
        }

        if (deployWithRest && (azfUser == null || azfUserPassword == null)) {
            getLog().error("Need to provide user and password when deploying with REST. Pass them with" +
                    AZR_USER_ENV_VAR + " and " + AZF_USER_PSW_ENV_VAR + ".");
            isValidConfiguration = false;
        } else if (!deployWithRest && resourceGroupName == null) {
            getLog().error("Need to provide resource group when deploying with `az`. Pass it with " +
                    RESOURCE_GROUP_ENV_VAR + ".");
            isValidConfiguration = false;
        }
        return isValidConfiguration;
    }

    private void loadConfig() throws IOException {
        Properties prop = new Properties();

        if (new File(SERVERLESS_CONFIG_FILE).exists()) {
            getLog().info("Found configuration file. Loading it...");
            prop.load(new FileInputStream(SERVERLESS_CONFIG_FILE));
        }

        resourceGroupName = getEnvString(RESOURCE_GROUP_ENV_VAR, prop, resourceGroupName);
        azfUser = getEnvString(AZR_USER_ENV_VAR, prop, azfUser);
        azfUserPassword = getEnvString(AZF_USER_PSW_ENV_VAR, prop, azfUserPassword);
        functionAppName = getEnvString(FUNCTION_APP_ENV_VAR, prop, functionAppName);
        zipFileName = getEnvString(ZIP_FILE_NAME_ENV_VAR, prop, zipFileName);
        configFolder = getEnvString(CONFIG_FOLDER_ENV_VAR, prop, configFolder);
        initialInvoke = getEnvBool(INITIAL_INVOKE_ENV_VAR, prop, initialInvoke);
        removeZipFile = getEnvBool(REMOVE_ZIP_ENV_VAR, prop, removeZipFile);
        deployWithRest = getEnvBool(DEPLOY_WITH_REST_ENV_VAR, prop, deployWithRest);
    }

    private String getEnvString(String key, Properties prop, String defaultValue) {
        if (System.getenv(key) != null || prop.getProperty(key) != null) {
            return prop.getProperty(key) != null ? prop.getProperty(key) : System.getenv(key);
        } else {
            return defaultValue;
        }
    }

    private boolean getEnvBool(String key, Properties prop, boolean defaultValue) {
        if (System.getenv(key) != null || prop.getProperty(key) != null) {
            return prop.getProperty(key) != null ? Boolean.valueOf(prop.getProperty(key)) : Boolean.valueOf(System.getenv(key));
        } else {
            return defaultValue;
        }
    }

    private void zipConfigAndCode() throws IOException {
        String zipFilePath = Paths.get(project.getBuild().getDirectory(), configFolder, zipFileName).toString();
        getLog().info("Zipping code and configuration to " + zipFilePath);
        Path folder = Paths.get(project.getBuild().getDirectory(), configFolder);
        try (FileOutputStream fos = new FileOutputStream(zipFilePath);
                ZipOutputStream zipOut = new ZipOutputStream(fos);
                Stream<Path> walk = Files.walk(folder, Integer.MAX_VALUE)) {

            walk.filter(file -> !file.getFileName().toString().equals(zipFileName))
                    .forEach(ExceptionHandling.throwingConsumerWrapper(file ->
                        Commons.zipSingleFile(file, folder, zipOut)
            ));
        }
        Commons.chmod777(Paths.get(project.getBasedir().getPath(), zipFileName).toFile());
    }

    private void deploy() throws IOException {
        getLog().info("Deploying with REST methods");

        String encodedCredentials = Base64.getEncoder().encodeToString(
                String.format("%s:%s", azfUser, azfUserPassword).getBytes()
        );

        URL url = new URL(String.format("https://%s.scm.azurewebsites.net/api/zipdeploy", functionAppName));
        HttpURLConnection http = (HttpURLConnection) url.openConnection();
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        http.setRequestProperty("Authorization", String.format("Basic %s", encodedCredentials));

        File binaryFile = Paths.get(project.getBuild().getOutputDirectory(), configFolder, zipFileName).toFile();
        OutputStream output = http.getOutputStream();
        Files.copy(binaryFile.toPath(), output);
        output.flush();
        output.close();

        if (http.getResponseCode() != HttpURLConnection.HTTP_OK) {
            getLog().error(String.format("Response: %d %s", http.getResponseCode(), http.getResponseMessage()));
            getLog().error(http.getResponseMessage());
            throw new IOException("Could not upload zip using REST");
        } else {
            getLog().info("ZIP file uploaded correctly.");
        }
        http.disconnect();

    }


    private void testCallAzf() throws IOException, InterruptedException {
        TimeUnit.SECONDS.sleep(10); // seems that calling too soon the API worsens the startup time
        getLog().info("Making first request to the API");
        URL url = new URL(String.format("https://%s.azurewebsites.net/", functionAppName));
        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        getLog().info("Response status code: " + http.getResponseCode());
        http.disconnect();
    }

}
