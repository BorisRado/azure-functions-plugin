package com.kumuluz.ee.serverless.common;

import com.kumuluz.ee.serverless.common.enums.RestMethodEnum;
import com.kumuluz.ee.serverless.common.pojo.RestEndpoint;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.List;

public class ProjectParser {

    private final static String JAX_RS_PACKAGE = "javax.ws.rs.";
    private final static String PATH_ANNOTATION = JAX_RS_PACKAGE + "Path";
    private final static String APPLICATION_PATH_ANNOTATION = JAX_RS_PACKAGE + "ApplicationPath";

    /**
     * Scans the project and returns all the endpoints that are present in the project
     * @return
     */
    public static List<RestEndpoint> getEndpoints(MavenProject project) {
        ClassGraph clsGraph = new ClassGraph().overrideClasspath(project.getBuild().getDirectory());

        // get list of all classes in project
        List<RestEndpoint> endpoints = new ArrayList<>();
        try (ScanResult result = clsGraph.enableAllInfo().acceptPackages(project.getGroupId()).scan()) {
            for (RestMethodEnum method :  RestMethodEnum.values()) {
                addEndpointsToList(result, endpoints, method);
                getApplicationBaseUrl(result);
            }
        }
        return endpoints;
    }

    private static void getApplicationBaseUrl(ScanResult result) {
        ClassInfoList classInfos = result.getClassesWithAnnotation(APPLICATION_PATH_ANNOTATION);
        String baseUrl = classInfos.get(0).getAnnotationInfo(APPLICATION_PATH_ANNOTATION)
                .getParameterValues().get("value").getValue().toString();
        RestEndpoint.setBaseUrl(baseUrl);
    }

    private static void addEndpointsToList(ScanResult result, List<RestEndpoint> endpoints, RestMethodEnum method) {
        ClassInfoList classInfos = result.getClassesWithMethodAnnotation(JAX_RS_PACKAGE + method.name());

        classInfos.forEach(classInfo -> {
            String classUrl = classInfo.hasAnnotation(PATH_ANNOTATION) ?
                    classInfo.getAnnotationInfo(PATH_ANNOTATION).getParameterValues().get("value").getValue().toString() : "";
            classInfo.getMethodInfo().forEach(methodInfo -> {
                if (methodInfo.hasAnnotation(JAX_RS_PACKAGE + method.name())) {
                    String methodUrl = methodInfo.hasAnnotation(PATH_ANNOTATION) ?
                            methodInfo.getAnnotationInfo(PATH_ANNOTATION).getParameterValues().get("value").getValue().toString() : "";
                    endpoints.add(new RestEndpoint(classUrl, methodUrl, methodInfo.getName(), method, classInfo.loadClass()));
                }
            });
        });

    }

}
