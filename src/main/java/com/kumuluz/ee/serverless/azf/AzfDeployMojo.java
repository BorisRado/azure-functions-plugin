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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mojo(name = "azf-deploy")
public class AzfDeployMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "removeZipFile", defaultValue = "true")
    private boolean removeZipFile;

    private final String RESOURCE_GROUP_ENV_VAR = "RESOURCE_GROUP";
    private final String FUNCTION_APP_ENV_VAR = "FUNCTION_APP";
    private final String AZR_USER_ENV_VAR = "AZF_USER";
    private final String AZF_USER_PSW_ENV_VAR = "AZF_USER_PSW";
    private final String ZIP_FILE_NAME_ENV_VAR = "ZIP_FILE_NAME";
    private final String CONFIG_FOLDER_ENV_VAR = "CONFIG_FOLDER";
    private final String REMOVE_ZIP_ENV_VAR = "REMOVE_ZIP";
    private final String INITIAL_INVOKE_ENV_VAR = "INITIAL_INVOKE";
    private final String DEPLOY_WITH_REST_ENV_VAR = "DEPLOY_WITH_REST";

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
            if(!checkConfig())
                throw new MojoExecutionException("Failed to deploy - invalid configuration");

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
        if (System.getenv(key) != null || prop.getProperty(key) != null)
            return prop.getProperty(key) != null ? prop.getProperty(key) : System.getenv(key);
        else
            return defaultValue;
    }

    private boolean getEnvBool(String key, Properties prop, boolean defaultValue) {
        if (System.getenv(key) != null || prop.getProperty(key) != null)
            return prop.getProperty(key) != null ? Boolean.valueOf(prop.getProperty(key)) : Boolean.valueOf(System.getenv(key));
        else
            return defaultValue;
    }

    private void zipConfigAndCode() throws IOException {
        getLog().info("Zipping code and configuration to " + zipFileName);
        Path folder = Paths.get(project.getBuild().getDirectory(), configFolder);
        FileOutputStream fos = new FileOutputStream(zipFileName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        Files.walk(folder, Integer.MAX_VALUE).forEach(ExceptionHandling.throwingConsumerWrapper(file -> {

            if (file.toString().equals(folder.toString()) || file.toString().contains("Dockerfile"))
                return;

            String fileName = getRelativePath(file, folder);
            if (file.toFile().isDirectory()) {
                zipOut.putNextEntry(new ZipEntry(fileName + "/"));
                zipOut.closeEntry();
            } else {
                FileInputStream fis = new FileInputStream(file.toString());
                ZipEntry zipEntry = new ZipEntry(fileName);
                zipOut.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
                fis.close();
            }
        }));
        Commons.chmod777(Paths.get(project.getBasedir().getPath(), zipFileName).toFile());

        zipOut.close();
        fos.close();
    }

    private static String getRelativePath(Path fileName, Path baseFolder) {
        return fileName.toString()
                .replaceAll(baseFolder.toString(), "")
                .substring(1);
    }

    private void deploy() throws IOException, InterruptedException {
        getLog().info("Deploying app...");

        if (deployWithRest)
            deployWithCurl();
        else
            deployWithAz();
    }

    private void deployWithAz() throws IOException, InterruptedException {
        getLog().info("Deploying with `az`");
        String deployString  = String.format("az functionapp deployment source config-zip -g %s -n %s --src %s",
                resourceGroupName, functionAppName, zipFileName);
        runCommandInShell(deployString);
    }

    private void deployWithCurl() throws IOException {
        getLog().info("Deploying with rest methods");

        String encodedCredentials = Base64.getEncoder().encodeToString(
                String.format("%s:%s", azfUser, azfUserPassword).getBytes()
        );

        URL url = new URL(String.format("https://%s.scm.azurewebsites.net/api/zipdeploy", functionAppName));
        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        http.setRequestMethod("POST");
        http.setDoOutput(true);
        http.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        http.setRequestProperty("Authorization", String.format("Basic %s", encodedCredentials));

        File binaryFile = new File(zipFileName);
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

    private void runCommandInShell(String command) throws IOException, InterruptedException {
        getLog().info(String.format("Executing command `%s`", command));
        Process proc = Runtime.getRuntime().exec(command);

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        // Read the output from the command
        String s;
        while ((s = stdInput.readLine()) != null) {
            getLog().info(s);
        }

        while ((s = stdError.readLine()) != null) {
            getLog().warn(s);
        }

        proc.waitFor();
        int exitStatus = proc.exitValue();
        if (exitStatus != 0) {
            getLog().error("Command exited with exit status: " + exitStatus);
            throw new IOException();
        } else {
            getLog().info("Command completed successfully");
        }
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
