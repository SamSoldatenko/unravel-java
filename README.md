# unravel-java

`NOR READY YET. WORK IN PROGRESS.`

Tool to analyze structure of your java app.

Creates json file with information about

* packages
* classes, interfaces, enums and inheritance/implementations
* methods, parameters, overrides
* calls

Json file then can be used in https://samsoldatenko.github.io/unravel/
to visualize the structure of your app (works offline).

# Build

```bash
./gradlew build
```

Test report: `build/reports/tests/test/index.html`

# Usage

```bash
java -jar unravel-java.jar path-to-app-to-analize > report.json
# OR
./gradlew run -q --args path-to-app-to-analize > report.json
```

Example
```bash
./gradlew build run -q --args build/libs/unravel-java.jar > report.json
```

