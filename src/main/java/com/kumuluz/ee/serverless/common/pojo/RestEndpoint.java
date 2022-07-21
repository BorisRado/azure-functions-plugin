package com.kumuluz.ee.serverless.common.pojo;

import com.kumuluz.ee.serverless.common.enums.RestMethodEnum;

public class RestEndpoint {

    private String classUrl;
    private String methodUrl;
    private String baseAppUrl;
    private String methodName;
    private RestMethodEnum restMethodEnum;
    private Class clazz;

    public RestEndpoint() {
        super();
    }

    public String getClassUrl() {
        return classUrl;
    }

    public void setClassUrl(String classUrl) {
        this.classUrl = classUrl;
    }

    public String getMethodUrl() {
        return methodUrl;
    }

    public void setMethodUrl(String methodUrl) {
        this.methodUrl = methodUrl;
    }

    public String getBaseAppUrl() {
        return baseAppUrl;
    }

    public void setBaseAppUrl(String baseAppUrl) {
        this.baseAppUrl = baseAppUrl;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public RestMethodEnum getRestMethodEnum() {
        return restMethodEnum;
    }

    public void setRestMethodEnum(RestMethodEnum restMethodEnum) {
        this.restMethodEnum = restMethodEnum;
    }

    public Class getClazz() {
        return clazz;
    }

    public void setClazz(Class clazz) {
        this.clazz = clazz;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %s %s", methodName, clazz.getName(), restMethodEnum.name(), getCompleteURL());
    }

    public String getFolderName() {
        return this.clazz.getSimpleName() + "_" + restMethodEnum.name() + "_" + this.methodName;
    }

    public String getCompleteURL() {
        String url = String.format("%s/%s/%s", this.baseAppUrl, this.classUrl, this.methodUrl);
        while (url.charAt(0) == '/') {
            url = url.substring(1);
        }
        url = url.replace("//", "/");
        return url;
    }

}
