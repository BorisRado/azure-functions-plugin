package si.fri.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import si.fri.maven.plugin.enums.JavaVersions;
import si.fri.maven.plugin.error_handling.ExceptionHandling;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Mojo(name = "deploy")
public class AzureDeployMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "resourceGroupName", required = false)
    private String resourceGroupName;

    @Parameter(property = "functionAppName", required = true)
    private String functionAppName;

    @Parameter(property = "zipFileName", required = false, defaultValue = "app.zip")
    private String zipFileName;

    @Parameter(property = "removeZipFile", required = false, defaultValue = "true")
    private boolean removeZipFile;

    @Parameter(property = "configFolder", required = false, defaultValue = "azure-config-folder")
    private String outConfigFolder;

    @Parameter(property = "testApi", required = false, defaultValue = "true")
    private boolean testApi;

    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            Commons.setJavaPathInHost(
                    Paths.get(project.getBuild().getDirectory(), outConfigFolder, "host.json"),
                    Commons.getJavaVersion(project)
            );

            zipConfigAndCode();

            // restore java version
            Commons.setJavaPathInHost(
                    Paths.get(project.getBuild().getDirectory(), outConfigFolder, "host.json"),
                    JavaVersions.JAVA_DEFAULT
            );

            // push to azure functions
            deploy();

            if (testApi)
                testCallApi();

            if (removeZipFile) {
                getLog().info("Deleting " + zipFileName);
                Files.delete(Paths.get(zipFileName));
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new MojoExecutionException("");
        }

    }

    private void zipConfigAndCode() throws IOException {
        getLog().info("Zipping code and configuration to " + zipFileName);
        Path folder = Paths.get(project.getBuild().getDirectory(), outConfigFolder);
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

        if (resourceGroupName == null) {

            getLog().info("Resource group was not provided. Deploying with curl");
            deployWithCurl();

        } else {

            try {
                Runtime.getRuntime().exec("az --version");
                deployWithAz();
            } catch (IOException | InterruptedException e) {
                getLog().warn("Deployment with `az` failed. Trying with rest...");
                deployWithCurl();
            }

        }
    }

    private void deployWithAz() throws IOException, InterruptedException {
        getLog().info("Deploying with `az`");
        String deployString  = String.format("az functionapp deployment source config-zip -g %s -n %s --src %s",
                resourceGroupName, functionAppName, zipFileName);
        runCommandInShell(deployString);
    }

    private void deployWithCurl() throws IOException, InterruptedException {
        System.out.print("Username: ");
        String username = new Scanner(System.in).next();
        String password = new String(System.console().readPassword("Enter host password for user '" + username + "': "));

        String encodedCredentials = Base64.getEncoder().encodeToString(
                String.format("%s:%s", username, password).getBytes()
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
        String s = null;
        while ((s = stdInput.readLine()) != null) {
            getLog().info(s);
        }

        while ((s = stdError.readLine()) != null) {
            getLog().error(s);
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

    private void testCallApi() throws IOException, InterruptedException {
        TimeUnit.SECONDS.sleep(10); // seems that calling too soon the API worsens the startup time
        getLog().info("Making first request to the API");
        URL url = new URL(String.format("https://%s.azurewebsites.net/", functionAppName));
        HttpURLConnection http = (HttpURLConnection)url.openConnection();
        getLog().info("Response status code: " + http.getResponseCode());
        http.disconnect();
    }

}
