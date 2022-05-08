# Azure functions plugins
For development, you may want to update your `~/.m2/settings.xml` file by adding the `si.fri` plugin group, e.g.:
```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
	<pluginGroups>
		<pluginGroup>si.fri</pluginGroup>
	</pluginGroups>
</settings>
```

## Plugin for generating azure functions configuration

### Usage
Run `mvn clean install` and then just add the following to the build of the project:
```xlm
<plugin>
    <groupId>si.fri</groupId>
    <artifactId>config-generator-maven-plugin</artifactId>
    <version>1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>generate-config-files</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```
Then:
```maven
mvn clean package
```
This will generate a folder (`azure-config-folder`) inside the `target` folder of the build. Navigate to the folder (`cd target/azure-config-folder`) and run `func start`.

You can change the name of the target folder by specifying `-DconfigFolder=<folder-name>`.

### So-far, to-do
The plugin supports both JAR packaging and copy-dependency packaging. It supports multi-module projects as well.

Next steps...?

## Plugin for deployment on azure functions

### Manual deployment
Documentation can be found [here](https://docs.microsoft.com/en-us/azure/azure-functions/deployment-zip-push).

Under the hood, we use the `.zip` deployment method to push the code to the azure functions. For manual deployment, follow the following steps (tested on Linux for deploying to an azure function with the linux base image):
1. Build the project with `mvn clean package`;
2. Create an azure function app on the azure portal. Use the Linux Operating system and the java runtime stack;
3. Navigate to the folder containing all the configuration and code, which was build by the first plugin, `cd target/azure-config-folder/`;
4. Update the `host.json` by changing the `defaultExecutablePath` to `%JAVA_HOME%/bin/java`;
5. Zip all the contents of the folder, `zip -r app.zip .`;
6. Push the zip to azure functions, `az functionapp deployment source config-zip -g <resource-group-name> -n <function-app-name> --src $(readlink -f app.zip)` - change the resource group name and function name with the values you set during step 2.

### Automatic deployment
Steps 3-6 can be automated with the plugin, by just running the command:
```bash
mvn config-generator:deploy -DresourceGroupName=<resource-group-name> -DfunctionAppName=<function-app-name>
```
Note that the command requires that the `az` binary is installed on your local computer. 

### TO-DO
The deployment with REST still does not work.