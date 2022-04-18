package si.fri.maven.plugin.enums;

public enum ConfigTemplateMapping {

    ENDPOINT_REST_METHOD("__ENDPOINT_REST_METHOD__"),
    ENDPOINT_ROUTE("__ENDPOINT_ROUTE__"),
    APP_ROUTE_PREFIX("__APP_ROUTE_PREFIX__");

    private String configString;

    ConfigTemplateMapping(String configString) {
        this.configString = configString;
    }

    @Override
    public String toString() {
        return this.configString;
    }

}
