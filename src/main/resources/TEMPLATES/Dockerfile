ARG JAVA_VERSION=__JAVA_VERSION__

FROM mcr.microsoft.com/azure-functions/java:4-java__JAVA_VERSION__-slim

ENV AzureWebJobsScriptRoot=/home/site/wwwroot \
    AzureFunctionsJobHost__Logging__Console__IsEnabled=true

WORKDIR /home/site/wwwroot
COPY ["./", "/home/site/wwwroot"]
RUN if [ "__JAVA_VERSION__"  = "11" ]; then \
      sed -i 's/"defaultExecutablePath": "java"/"defaultExecutablePath": "\/usr\/lib\/jvm\/zre-11-azure-amd64\/bin\/java"/' host.json; \
    else \
      sed -i 's/"defaultExecutablePath": "java"/"defaultExecutablePath": "\/usr\/lib\/jvm\/adoptium-8-x64\/bin\/java"/' host.json; \
    fi;