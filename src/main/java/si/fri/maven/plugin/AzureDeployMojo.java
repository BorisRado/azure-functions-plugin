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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Mojo(name = "deploy")
public class AzureDeployMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(property = "resourceGroupName", required = true)
    private String resourceGroupName;

    @Parameter(property = "functionAppName", required = true)
    private String functionAppName;

    @Parameter(property = "zipFileName", required = false, defaultValue = "app.zip")
    private String zipFileName;

    @Parameter(property = "removeZipFile", required = false, defaultValue = "true")
    private boolean removeZipFile;

    @Parameter(property = "configFolder", required = false, defaultValue = "azure-config-folder")
    private String outConfigFolder;

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
        try {
            Process process = Runtime.getRuntime().exec("az --version");
            deployWithAz();
        } catch (IOException | InterruptedException e) {
            getLog().warn("Deployment with `az` failed. Trying with rest...");
            deployWithCurl();
        }
    }

    private void deployWithAz() throws IOException, InterruptedException {
        getLog().info("Deploying with `az`");
        // String zipFilePath = Paths.get(project.getBuild().getDirectory(), zipFileName).toString();
        String deployString  = String.format("az functionapp deployment source config-zip -g %s -n %s --src %s",
                resourceGroupName, functionAppName, zipFileName);
        runCommandInShell(deployString);
    }

    private void deployWithCurl() throws IOException, InterruptedException {
        /*String deployString  = String.format("curl -X POST -u %s --data-binary @"%s" https://%s.scm.azurewebsites.net/api/zipdeploy",
                azureUsername, zipFilePath, functionAppName);*/
        getLog().error("Not implemented yet...");
        throw new IOException("");
    }

    private void runCommandInShell(String command) throws IOException, InterruptedException {
        getLog().info(String.format("Executing command `%s`", command));
        Process proc = Runtime.getRuntime().exec(command);

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        BufferedReader stdError = new BufferedReader(new
                InputStreamReader(proc.getErrorStream()));

        // Read the output from the command
        getLog().info("Output of command:");
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

}
