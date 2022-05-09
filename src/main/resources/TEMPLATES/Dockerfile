ARG JAVA_VERSION=__JAVA_VERSION__

FROM mcr.microsoft.com/azure-functions/java:4-java__JAVA_VERSION__-slim

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

WORKDIR /home/site/wwwroot
COPY ["./", "/home/site/wwwroot"]
RUN sed -i 's/"defaultExecutablePath": "java"/"defaultExecutablePath": "%JAVA_HOME%\/bin\/java"/' host.json