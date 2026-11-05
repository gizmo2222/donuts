# Tools

Scripts for the mechanical work. All are PowerShell 5.1 and are run from the repository root.

| Script | Purpose | Example |
|---|---|---|
| `check.ps1` | Builds the app and prints one verdict line; lists compiler errors on failure. Full output in `build\logs\check.log` | `.\tools\check.ps1 -Quiet` |
| `check.ps1 -Test` | Same verdict line, but runs the unit tests (`testDebugUnitTest`); failing tests are listed from the Gradle output | `.\tools\check.ps1 -Test -Quiet` |
| `code-map.ps1` | Lists source files with line counts, one file's functions with line numbers, or finds a function by regex | `.\tools\code-map.ps1 -Match "sticker"` |
| `..\release.ps1` | Builds the signed release bundle (see the README's release checklist) | `.\release.ps1` |
