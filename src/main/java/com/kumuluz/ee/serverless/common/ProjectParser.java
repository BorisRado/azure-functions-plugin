package com.kumuluz.ee.serverless.common;

import com.kumuluz.ee.serverless.common.enums.RestMethodEnum;
import com.kumuluz.ee.serverless.common.pojo.RestEndpoint;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import org.apache.maven.project.MavenProject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Boris Radovic
 * @since 1.0.0
 */

public class ProjectParser {

    private final static String JAX_RS_PACKAGE = "javax.ws.rs.";
    private final static String PATH_ANNOTATION = JAX_RS_PACKAGE + "Path";
    private final static String APPLICATION_PATH_ANNOTATION = JAX_RS_PACKAGE + "ApplicationPath";

    public static List<RestEndpoint> getEndpoints(MavenProject project) {
        // This method scans the project and returns all the endpoints that are present in the project

        ClassGraph clsGraph = new ClassGraph().overrideClasspath(project.getBuild().getDirectory());

        // get list of all classes in project
        List<RestEndpoint> endpoints = new ArrayList<>();
        try (ScanResult result = clsGraph.enableAllInfo().acceptPackages(project.getGroupId()).scan()) {
            String baseUrl = getApplicationBaseUrl(result);
            for (RestMethodEnum method :  RestMethodEnum.values()) {
                addEndpointsToList(result, endpoints, method, baseUrl);
            }
        }
        endpoints.sort(Comparator.comparing(RestEndpoint::toString));
        return endpoints;
    }

    private static String getApplicationBaseUrl(ScanResult result) {
        ClassInfoList classInfos = result.getClassesWithAnnotation(APPLICATION_PATH_ANNOTATION);
        String baseUrl = classInfos.get(0).getAnnotationInfo(APPLICATION_PATH_ANNOTATION)
                .getParameterValues().get("value").getValue().toString();
        return baseUrl;
    }

    private static void addEndpointsToList(ScanResult result, List<RestEndpoint> endpoints,
                                           RestMethodEnum method, String baseAppUrl) {
        ClassInfoList classInfos = result.getClassesWithMethodAnnotation(JAX_RS_PACKAGE + method.name());

        classInfos.forEach(classInfo -> {
            String classUrl = classInfo.hasAnnotation(PATH_ANNOTATION) ?
                    classInfo.getAnnotationInfo(PATH_ANNOTATION).getParameterValues().get("value").getValue().toString() : "";
            classInfo.getMethodInfo().forEach(methodInfo -> {
                if (methodInfo.hasAnnotation(JAX_RS_PACKAGE + method.name())) {
                    String methodUrl = methodInfo.hasAnnotation(PATH_ANNOTATION) ?
                            methodInfo.getAnnotationInfo(PATH_ANNOTATION).getParameterValues().get("value").getValue().toString() : "";
                    RestEndpoint restEndpoint = new RestEndpoint();
                    restEndpoint.setRestMethodEnum(method);
                    restEndpoint.setMethodName(methodInfo.getName());
                    restEndpoint.setBaseAppUrl(baseAppUrl);
                    restEndpoint.setClazz(classInfo.loadClass());
                    restEndpoint.setMethodUrl(methodUrl);
                    restEndpoint.setClassUrl(classUrl);
                    endpoints.add(restEndpoint);
                }
            });
        });

    }

}
