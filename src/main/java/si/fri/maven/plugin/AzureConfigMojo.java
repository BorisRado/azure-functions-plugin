package si.fri.maven.plugin;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import si.fri.maven.plugin.enums.ConfigTemplateMapping;
import si.fri.maven.plugin.enums.JavaVersions;
import si.fri.maven.plugin.enums.RestMethodEnum;
import si.fri.maven.plugin.error_handling.ExceptionHandling;

import javax.ws.rs.*;
import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;


@Mojo(name = "generate-config-files", defaultPhase = LifecyclePhase.PACKAGE)
public class AzureConfigMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}")
    private String targetDirectory;

    @Parameter(defaultValue = "${project.build.finalName}")
    private String finalName;

    @Parameter(property = "configFolder", required = false, defaultValue = "azure-config-folder")
    private String outConfigFolder;

    @Parameter(property = "extractAllJars", required = false, defaultValue = "false")
    private boolean extractAllJars;

    @Parameter(property = "generateDockerfile", required = false, defaultValue = "true")
    private boolean generateDockerfile;

    private static final String TEMPLATES_FOLDER = "TEMPLATES";
    private static final String FUNCTIONS_FILE = "function.json";
    private static final String HOST_FILE = "host.json";
    private static final String HOST_FILE_EXPLODED = "host_exploded.json";
    private static final String HOST_FILE_JAR = "host_jar.json";
    private static final String LOCAL_SETTINGS_FILE = "local.settings.json";
    private static final String DOCKERFILE = "Dockerfile";

    private static final String CLASSES_FOLDER = "classes";
    private static final String DEPENDENCY_FOLDER = "dependency";

    private static final String EE_CLS_LOADER_FOLDER = Paths.get("tmp", "EeClassLoader").toString();

    private boolean jarPackaging; // true when jar, false when "copy-dependencies"


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        jarPackaging = project.getBuildPlugins().stream().anyMatch(e -> e.toString().contains("kumuluzee-maven-plugin"));
        try {

            createDirectoryStructure();
            URL[] urls = setupBuildFiles();

            List<Class> classes = getClasses(urls);
            List<RestEndpoint> endpoints = getEndpoints(classes);
            getLog().info("Found " + endpoints.size() + " endpoints in total");
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
        java.nio.file.Path baseDirectory = Paths.get(targetDirectory, outConfigFolder);

        endpoints.stream().parallel().forEach(ExceptionHandling.throwingConsumerWrapper(endpoint -> {
            // create folder
            java.nio.file.Path methodFolder = Paths.get(baseDirectory.toString(), endpoint.getFolderName());
            Files.createDirectories(methodFolder);

            // create functions.json file
            String config = getConfigTemplate(FUNCTIONS_FILE)
                    .replace(ConfigTemplateMapping.ENDPOINT_ROUTE.toString(), endpoint.getCompleteURL())
                    .replace(ConfigTemplateMapping.ENDPOINT_REST_METHOD.toString(), endpoint.getRestMethod().name());
            Commons.writeConfigFile(config, methodFolder.toString(), FUNCTIONS_FILE);

        }));

        // still copy host.json and local.settings.json
        String baseHostConfig = jarPackaging ? getConfigTemplate(HOST_FILE_JAR) : getConfigTemplate(HOST_FILE_EXPLODED);
        Commons.writeConfigFile(baseHostConfig, baseDirectory.toString(), HOST_FILE);
        Commons.writeConfigFile(getConfigTemplate(LOCAL_SETTINGS_FILE), baseDirectory.toString(), LOCAL_SETTINGS_FILE);
    }

    private void generateDockerfile() {
        JavaVersions javaVersion = Commons.getJavaVersion(project);
        String dockerTemplate = getConfigTemplate(DOCKERFILE);
        if (javaVersion == JavaVersions.JAVA_11)
            dockerTemplate = dockerTemplate.replaceAll(ConfigTemplateMapping.JAVA_VERSION.toString(), "11");
        else if (javaVersion == JavaVersions.JAVA_8)
            dockerTemplate = dockerTemplate.replaceAll(ConfigTemplateMapping.JAVA_VERSION.toString(), "8");

        Commons.writeConfigFile(dockerTemplate, Paths.get(targetDirectory, outConfigFolder).toString(), DOCKERFILE);
    }

    private static String getConfigTemplate(String fileName) {
        String filePath = "/" + Paths.get(TEMPLATES_FOLDER, fileName).toString();
        return new Scanner(AzureConfigMojo.class.getResourceAsStream(filePath), StandardCharsets.UTF_8)
                .useDelimiter("\\A").next();
    }


    private void copyJar() throws IOException {
        java.nio.file.Path targetFile = Paths.get(targetDirectory, outConfigFolder, "handler.jar");
        java.nio.file.Path sourceFile = Paths.get(targetDirectory, finalName + ".jar");
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

    private void clean() throws IOException {
        if (jarPackaging) {
            File tmpFolder = Paths.get(targetDirectory, outConfigFolder, EE_CLS_LOADER_FOLDER).toFile();
            for (File file: tmpFolder.listFiles())
                if (file.getName().endsWith(".jar"))
                    file.delete();
        }

    }

    /**
     * moves all the files reuired for the build process and for the kumuluzee start-up to the configuration folder,
     * and returns an array of all the JARs, that may be required during the build process
     * @return
     * @throws IOException
     */
    private URL[] setupBuildFiles() throws IOException {
        URL[] urls = null;
        if (jarPackaging) {

            copyJar();
            File baseJarFile = Paths.get(targetDirectory, finalName + ".jar").toFile();
            urls = extractJarFiles(baseJarFile);

        } else {

            // copy `classes` and `dependency` folders
            java.nio.file.Path targetClassPath = Paths.get(targetDirectory, outConfigFolder, CLASSES_FOLDER);
            java.nio.file.Path targetDependencyPath = Paths.get(targetDirectory, outConfigFolder, DEPENDENCY_FOLDER);
            FileUtils.copyDirectoryStructure(
                    Paths.get(targetDirectory, CLASSES_FOLDER).toFile(),
                    targetClassPath.toFile()
            );
            FileUtils.copyDirectoryStructure(
                    Paths.get(targetDirectory, DEPENDENCY_FOLDER).toFile(),
                    targetDependencyPath.toFile()
            );

            List<URL> urlsList = Files.walk(targetDependencyPath, 1)
                    .filter(e -> e.toString().endsWith(".jar"))
                    .map(e -> {
                        try {
                            return e.toUri().toURL();
                        } catch (MalformedURLException ex) {
                            ex.printStackTrace();
                        }
                        return null;
                    }).filter(e -> e != null).collect(Collectors.toList());
            urlsList.add(targetClassPath.toUri().toURL());
            urls = urlsList.toArray(new URL[0]);

        }

        return urls;
    }

    private List<Class> getClasses(URL[] urls) throws IOException {
        List<Class> classes = null;

        URLClassLoader clsLoader = getClassLoader(urls);

        if (jarPackaging) {
            String baseJar = String.format("%s/%s.jar", targetDirectory, finalName);
            classes = getClassesFromJar(baseJar, clsLoader);
        } else {
            classes = getClassesFromFileSystem(clsLoader);
        }
        getLog().info("Discovered " + classes.size() + " classes that might contain endpoints.");
        return classes;
    }

    private List<Class> getClassesFromFileSystem(URLClassLoader clsLoader) throws IOException {
        List<Class> classes = new ArrayList<>();

        int tmp = Paths.get(targetDirectory, outConfigFolder, CLASSES_FOLDER).toString().length() + 1;
        Files.walk(Paths.get(targetDirectory, outConfigFolder, CLASSES_FOLDER), Integer.MAX_VALUE)
                .filter(e -> e.toString().endsWith(".class"))
                .forEach(ExceptionHandling.throwingConsumerWrapper(classFileName -> {
                    String classFileNameString = classFileName.toString();
                    String className = classFileName.toString().substring(tmp, classFileNameString.length() - 6)
                            .replace(File.separator, ".");
                    Class classToLoad = Class.forName(className, true, clsLoader);
                    classes.add(classToLoad);
                }));
        return classes;
    }

    private List<Class> getClassesFromJar(String jarFileName, URLClassLoader clsLoader) throws IOException {
        List<Class> classes = new ArrayList<>();

        File jarFile = new File(jarFileName);
        JarFile jar = new JarFile(jarFile);

        jar.stream()
                .filter(e -> e.getName().endsWith(".class"))
                .filter(e -> e.getName().startsWith(project.getGroupId().replace(".", "/")))
                .forEach(ExceptionHandling.throwingConsumerWrapper(entry -> {
                    String className = entry.getName().replace("/", ".")
                            .substring(0, entry.getName().length() - 6);
                    Class classToLoad = Class.forName(className, true, clsLoader);
                    classes.add(classToLoad);
                }));

        return classes;
    }

    private URLClassLoader getClassLoader(URL[] urls) throws IOException {

        return new URLClassLoader(
                urls,
                this.getClass().getClassLoader()
        );
    }

    private URL[] extractJarFiles (File jarFile) throws IOException {

        final String LIB_FOLDER = "lib/";

        Queue<File> jarFiles = new ConcurrentLinkedQueue<>();
        JarFile jar = new JarFile(jarFile);

        String clsLoaderFolderAbsPath = Paths.get(targetDirectory, outConfigFolder, EE_CLS_LOADER_FOLDER).toString();
        jar.stream().parallel().forEach(ExceptionHandling.throwingConsumerWrapper(jarEntry -> {
            handleJarEntry(jarEntry, LIB_FOLDER, clsLoaderFolderAbsPath, jar, jarFiles);
        }));

        jarFiles.add(jarFile);

        URL[] jarFilesArray = jarFiles.stream().map(entry -> {
            try {
                return entry.toURI().toURL();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            return null;
        }).filter(e -> e != null).toArray(URL[]::new);

        return jarFilesArray;
    }

    private void handleJarEntry(JarEntry jarEntry, String libFolder, String containerFolder,
                                JarFile sourceJar, Queue<File> jarFiles) throws IOException {

        if (!jarEntry.getName().startsWith(libFolder) && !jarEntry.isDirectory()) {
            String targetFileName = Paths.get(containerFolder, jarEntry.getName()).toString();
            copyJarContent(sourceJar, jarEntry, targetFileName);

        } else if (jarEntry.getName().startsWith(libFolder) && jarEntry.getName().endsWith(".jar")) {
            java.nio.file.Path targetFile = Paths.get(containerFolder, new File(jarEntry.getName()).getName());
            copyJarContent(sourceJar, jarEntry, targetFile.toString());
            jarFiles.add(targetFile.toFile());
        }

    }

    private static void copyJarContent (JarFile sourceJar, JarEntry sourceEntry, String targetFileName) throws IOException {
        InputStream is = sourceJar.getInputStream(sourceEntry);

        // create directory if it does not exist already
        File parentFolder = new File(targetFileName).getParentFile();
        optCreateDirectory(Paths.get(parentFolder.getAbsolutePath()));

        BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(targetFileName));

        // copy content
        while (is.available() > 0)
            fos.write(is.read());

        fos.close();
        is.close();
    }

    private void createDirectoryStructure() throws IOException {
        java.nio.file.Path containerFolder = Paths.get(targetDirectory, outConfigFolder);
        Files.createDirectories(containerFolder);
        getLog().info("Configuration will be created in " + containerFolder.toString());

        if (jarPackaging) {
            java.nio.file.Path eeClsLoaderFolder = Paths.get(targetDirectory, outConfigFolder, EE_CLS_LOADER_FOLDER);
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

    private RestEndpoint createEndpoint(RestMethodEnum restMethodEnum, Method method, Class clazz) {
        String methodPath = method.isAnnotationPresent(Path.class) ? method.getAnnotation(Path.class).value() : "";
        String classPath = getClassPath(clazz);
        return new RestEndpoint(classPath, methodPath, method.getName(), restMethodEnum, clazz);
    }

    private static String getClassPath(Class clazz) {
        return clazz.isAnnotationPresent(Path.class) ? ((Path)clazz.getAnnotation(Path.class)).value() : "";
    }

    private static void optCreateDirectory(java.nio.file.Path path) throws IOException {
        if (!path.toFile().exists())
            Files.createDirectories(path);
    }
}
