## Impact estimation template repository

### How to use

Register a new diagnostic in `FirDiagnosticsList.kt` with the name `IE_DIAGNOSTIC` and only one string parameter (a group does not matter):

```kotlin
val IE_DIAGNOSTIC by warning<KtElement> {
    parameter<String>("info")
}
```

Write your checker and report any data associated with your diagnostic using the reporter in `report/reporter.kt` from this repository.

Get two logs from external and internal projects. (`compilation-diagnostics-log.yaml.txt` in artifacts) and put them as separate files in `Logs` folder.

Run `main` function from `Main.kt` from module `app` to generate data classes and check if everything is ok.

Operate with your data as a list of data classes in pure Kotlin.

### What is going on

Reporter serializes your data into a simple string using format: `KLEKLE key: value; key: value KLEKLE`.

Gradle plugin from `codegen-plugin` reads this data, collects all possible keys and values for them and generate data classes to represent this data (trying to use `Int`, `Boolean` or generate enums where possible).

### Notes

The format of the logs might change at any time, so do not be surprised if everything goes wrong. Also, there is a risk of incorrect parsing if your values contains `:` or `;`. If anything of this happened, inspect it, ask Junie to fix it and make a PR.

If you think about any other improvements or new standard functions for analysis, feel free to make a PR.
