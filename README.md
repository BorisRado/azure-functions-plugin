# Azure functions plugins
For development, you may want to update your `~/.m2/settings.xml` file by adding the `com.kumuluz.ee` plugin group, e.g.:
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
	<pluginGroups>
		<pluginGroup>com.kumuluz.ee</pluginGroup>
	</pluginGroups>
</settings>
```

## Generating azure functions configuration

### Usage
Run `mvn clean install` to install locally the Kumuluz serverless framework, and then add the following plugin to the `pom.xml` of the project you want to deploy to the serverless environment:
```xlm
<plugin>
    <groupId>com.kumuluz.ee</groupId>
    <artifactId>serverless-maven-plugin</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>azf-generate-config</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
Then:
```maven
mvn clean package
```
This will generate a folder (`azf-config`) inside the `target` folder. Navigate to the folder (`cd target/azf-config`) and run `func start`.

You can change the name of the folder that will contain the required files by specifying `-DconfigFolder=<folder-name>`.

### So-far, to-do
Still not support [these use cases](https://stackoverflow.com/questions/25755130/nested-resources-in-jersey-jax-rs-how-to-implement-restangular-example/25775371#25775371). See if you can use the [jersley implementation](https://github.com/eclipse-ee4j/jersey)

## Deployment on Azure functions

### Manual deployment
Documentation can be found [here](https://docs.microsoft.com/en-us/azure/azure-functions/deployment-zip-push).

Under the hood, we use the `.zip` deployment method to push the code to the Azure functions. For manual deployment, follow the following steps (tested on Linux for deploying to an Azure function with the linux base image):
1. Build the project with `mvn clean package`;
2. Create an Azure function app on the Azure portal. Use the Linux Operating system and the java runtime stack;
3. Navigate to the folder containing all the configuration and code, which was build by the first plugin, `cd target/azure-config-folder/`;
4. Update the `host.json` by changing the `defaultExecutablePath` to `%JAVA_HOME%/bin/java`;
5. Zip all the contents of the folder, `zip -r app.zip .`;
6. Push the zip to azure functions, `az functionapp deployment source config-zip -g <resource-group-name> -n <function-app-name> --src $(readlink -f app.zip)` - change the resource group name and function name with the values you set during step 2.

### Automatic deployment
Steps 3-6 can be automated with the plugin, by just running the command:
```bash
mvn config-generator:deploy -DresourceGroupName=<resource-group-name> -DfunctionAppName=<function-app-name>
```
This command does not use the `az` binary; instead, it packages all the files into a zip file and sends the zip file by means of REST methods to the Azure cloud. Note, that this deployment method fails when using Linux as the base image for the Function app, so we recommend using Windows as the base image until we manage to sort out this problem.

### TO-DO
List of top-priorities:
* correct the zip deployment for Linux. Currently, it does not work if using the consumption serverless plan, but it works when using other, premium tiers;
* When deploying on Windows this message often pops up:
    ```bash
    [WARNING] WARNING: Getting scm site credentials for zip deployment
    [WARNING] WARNING: Starting zip deployment. This operation can take a while to complete ...
    [WARNING] WARNING: Deployment endpoint responded with status code 202
    ```
    Not sure why it happens, online some people report the same issue but no solution on how to fix it, e.g. [this stackoverflow](https://stackoverflow.com/questions/60284151/azure-zip-push-deployment-for-a-function-app-doesnt-work)
* Slow first response after several hours of inactivity;

### Notes
* If getting the `Unable to open a connection to your app. This may be due to any network security groups or IP restriction rules that you have placed on your app. To use log streaming, please make sure you are able to access your app directly from your current network.` message when trying to connect to logs, make sure to clear the cache.
* If deploying on linux with consumption plan, the logs are not shown in the streaming log tab. Either deploy on premium plan (then go to app service logs and configure to use file system logs) or use live metric stream, see [the docs](https://github.com/MicrosoftDocs/azure-docs/blob/main/articles/azure-functions/streaming-logs.md).
