package com.kumuluz.ee.serverless.common.pojo;

import com.kumuluz.ee.serverless.common.enums.RestMethodEnum;

public class RestEndpoint {

    private String classPath;
    private String methodPath;
    private String methodName;
    private RestMethodEnum restMethodEnum;
    private static String baseUrl;
    private Class clazz;

    public RestEndpoint(String classPath, String methodPath, String methodName, RestMethodEnum restMethodEnum, Class clazz) {
        this.classPath = classPath;
        this.methodPath = methodPath;
        this.methodName = methodName;
        this.restMethodEnum = restMethodEnum;
        this.clazz = clazz;
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static void setBaseUrl(String baseUrl) {
        RestEndpoint.baseUrl = baseUrl;
    }

    public String getClassPath() {
        return classPath;
    }

    public void setClassPath(String classPath) {
        this.classPath = classPath;
    }

    public String getMethodPath() {
        return methodPath;
    }

    public void setMethodPath(String methodPath) {
        this.methodPath = methodPath;
    }

    public String getMathodName() {
        return methodName;
    }

    public void setMathodName(String mathodName) {
        this.methodName = mathodName;
    }

    public RestMethodEnum getRestMethod() {
        return restMethodEnum;
    }

    public void setRestMethod(RestMethodEnum restMethodEnum) {
        this.restMethodEnum = restMethodEnum;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %s %s", methodName, clazz.getName(), restMethodEnum.name(), getCompleteURL());
    }

    public String getFolderName() {
        return this.clazz.getSimpleName() + "_" + restMethodEnum.name() + "_" + methodName;
    }

    public String getCompleteURL() {
        String url = String.format("%s/%s/%s", baseUrl, this.classPath, this.methodPath);
        while (url.charAt(0) == '/')
            url = url.substring(1);
        url = url.replace("//", "/");
        return url;
    }

}
