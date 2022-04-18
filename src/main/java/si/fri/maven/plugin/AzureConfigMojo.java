package si.fri.maven.plugin;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import si.fri.maven.plugin.enums.ConfigTemplateMapping;
import si.fri.maven.plugin.enums.RestMethodEnum;

import javax.ws.rs.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.jar.JarFile;

/*
 * Load classes from JAR: https://stackoverflow.com/questions/60764/how-to-load-jar-files-dynamically-at-runtime
 * should probably use the EeClassLoader, otherwise problems when using multiple modules (uber jar)... TO-DO
 */

@Mojo(name = "generate-config-files", defaultPhase = LifecyclePhase.PACKAGE)
public class AzureConfigMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String outputDirectory;

    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;

    @Parameter(property = "configFolder", required = false, defaultValue = "azure-config-folder")
    private String outConfigFolder;

    private static final String TEMPLATES_FOLDER = "TEMPLATES";
    private static final String FUNCTIONS_FILE = "function.json";
    private static final String HOST_FILE = "host.json";
    private static final String LOCAL_SETTINGS_FILE = "local.settings.json";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        List<Class> classes = getClasses();
        List<RestEndpoint> endpoints = getEndpoints(classes);
        getLog().info("Found " + endpoints.size() + " endpoints in total");
        endpoints.forEach(endpoint -> getLog().info("\t\t" + endpoint));

        try {
            createConfigFiles(endpoints);
            copyJar();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createConfigFiles(List<RestEndpoint> endpoints) throws IOException {
        // create container folder
        java.nio.file.Path baseDirectory = Paths.get(outputDirectory, outConfigFolder);
        getLog().info("Creating " + baseDirectory);
        Files.createDirectories(baseDirectory);

        for(RestEndpoint endpoint : endpoints) {
            // create folder
            java.nio.file.Path methodFolder = Paths.get(baseDirectory.toString(), endpoint.getFolderName());
            Files.createDirectories(methodFolder);

            // create functions.json file
            String config = getConfigTemplate(FUNCTIONS_FILE)
                    .replace(ConfigTemplateMapping.ENDPOINT_ROUTE.toString(), endpoint.getCompleteURL())
                    .replace(ConfigTemplateMapping.ENDPOINT_REST_METHOD.toString(), endpoint.getRestMethod().name());
            writeConfigFile(config, methodFolder.toString(), FUNCTIONS_FILE);
        }

        // still copy host.json and local.settings.json
        writeConfigFile(getConfigTemplate(HOST_FILE), baseDirectory.toString(), HOST_FILE);
        writeConfigFile(getConfigTemplate(LOCAL_SETTINGS_FILE), baseDirectory.toString(), LOCAL_SETTINGS_FILE);
    }

    private static String getConfigTemplate(String fileName) {
        String filePath = "/" + Paths.get(TEMPLATES_FOLDER, fileName).toString();
        return new Scanner(AzureConfigMojo.class.getResourceAsStream(filePath), StandardCharsets.UTF_8)
                .useDelimiter("\\A").next();
    }

    private static void writeConfigFile(String config, String folder, String fileName) {
        try (PrintWriter out = new PrintWriter(Paths.get(folder, fileName).toString())) {
            out.print(config);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void copyJar() throws IOException {
        java.nio.file.Path targetFile = Paths.get(outputDirectory, outConfigFolder, "handler.jar");
        java.nio.file.Path sourceFile = Paths.get(outputDirectory, finalName + ".jar");
        Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    private List<RestEndpoint> getEndpoints(List<Class> classes) {
        List<RestEndpoint> endpoints = new ArrayList<>();
        for (Class clazz: classes) {
            if (clazz.isAnnotationPresent(ApplicationPath.class)) {
                RestEndpoint.setBaseUrl(getClassPath(clazz));
                continue;
            }

            for (Method method: clazz.getMethods()) {

                // any better way to do this so to avoid all these ifs?
                if (method.isAnnotationPresent(GET.class)) {
                    endpoints.add(createEndpoint(RestMethodEnum.GET, method, clazz));
                } else if (method.isAnnotationPresent(POST.class)) {
                    endpoints.add(createEndpoint(RestMethodEnum.POST, method, clazz));
                } else if (method.isAnnotationPresent(PUT.class)) {
                    endpoints.add(createEndpoint(RestMethodEnum.PUT, method, clazz));
                } else if (method.isAnnotationPresent(PATCH.class)) {
                    endpoints.add(createEndpoint(RestMethodEnum.PATCH, method, clazz));
                } else if (method.isAnnotationPresent(DELETE.class)) {
                    endpoints.add(createEndpoint(RestMethodEnum.DELETE, method, clazz));
                } else if (method.isAnnotationPresent(HEAD.class)) {
                endpoints.add(createEndpoint(RestMethodEnum.HEAD, method, clazz));
                } else if (method.isAnnotationPresent(OPTIONS.class)) {
                    endpoints.add(createEndpoint(RestMethodEnum.OPTIONS, method, clazz));
                } else if (method.getName().equals("doGet")) {
                    // ... continue for servlet
                }
            }
        }
        return endpoints;
    }

    private List<Class> getClasses() {
        if (project.getPackaging().toLowerCase().equals("jar")) {
            String basePackage = String.format("%s.%s", project.getGroupId(), project.getArtifactId());
            String baseJar = String.format("%s/%s.jar", outputDirectory, finalName);
            try {
                return getClassesFromJar(baseJar, basePackage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            getLog().error("Not implemented");
        }
        return null;
    }

    private List<Class> getClassesFromJar(String jarFileName, String basePackage) throws IOException, ClassNotFoundException {
        List<Class> classes = new ArrayList<>();

        try {
            File jarFile = new File(jarFileName);
            URLClassLoader child = new URLClassLoader(
                    new URL[] {jarFile.toURI().toURL()},
                    this.getClass().getClassLoader()
            );
            JarFile jar = new JarFile(jarFile);

            /*List<String> jarFiles = new ArrayList<>();
            jar.stream()
                    .filter(e -> e.getName().endsWith(".jar"))
                    .forEach(e -> jarFiles.add(e.getName()));
            System.out.println("total " + jarFiles.size());
            jarFiles.forEach(System.out::println);*/

            jar.stream()
                    .filter(e -> e.getName().endsWith(".class"))
                    .filter(e -> e.getName().startsWith(project.getGroupId().replace(".", "/")))
                    .forEach(entry -> {
                        try {
                            String className = entry.getName().replace("/", ".")
                                    .substring(0, entry.getName().length() - 6);
                            Class classToLoad = Class.forName(className, true, child);
                            classes.add(classToLoad);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
        return classes;
    }

    private RestEndpoint createEndpoint(RestMethodEnum restMethodEnum, Method method, Class clazz) {
        String methodPath = method.isAnnotationPresent(Path.class) ? method.getAnnotation(Path.class).value() : "";
        String classPath = getClassPath(clazz);
        return new RestEndpoint(classPath, methodPath, method.getName(), restMethodEnum, clazz);
    }

    private static String getClassPath(Class clazz) {
        return clazz.isAnnotationPresent(Path.class) ? ((Path)clazz.getAnnotation(Path.class)).value() : "";
    }
}
