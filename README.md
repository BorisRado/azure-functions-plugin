# Plugin for generating azure functions configuration

### Usage
Run `mvn clean install` and then just add the following to the bluild of the project:
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
This will generate a folder (`azure-config-folder`) inside the `target` folder of the build. Navigate to the folder and run `func start`.

You can change the name of the target folder by specifying `-DconfigFolder=<folder-name>`.

### So-far, to-do
The plugin supports both JAR packaging and copy-dependency packaging. It supports multi-module projects as well.

Next steps...?