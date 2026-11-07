# Tools

Scripts for the mechanical work. All are PowerShell 5.1 and are run from the repository root.

| Script | Purpose | Example |
|---|---|---|
| `check.ps1` | Builds the app and prints one verdict line; lists compiler errors on failure. Full output in `build\logs\check.log`. Fails at once if an interrupted `mutate.ps1` run left a backup | `.\tools\check.ps1 -Quiet` |
| `check.ps1 -Test` | Same verdict line, but runs the unit tests (`testDebugUnitTest`); failing tests are listed from the Gradle output | `.\tools\check.ps1 -Test -Quiet` |
| `check.ps1 -Tests` | Runs only the named test classes (Gradle `--tests` filters); implies `-Test` | `.\tools\check.ps1 -Quiet -Tests 'com.donuts.game.GameInputTest'` |
| `coverage.ps1` | Runs the unit tests with JaCoCo (switched on by the init script `coverage.gradle`, so `app\build.gradle` is unchanged) and prints line and branch coverage per file; `-Lines` also writes the uncovered line numbers to `build\logs\coverage-lines.txt` | `.\tools\coverage.ps1 -Quiet -Lines` |
| `mutate.ps1` | Mutation check: changes one operator at a time in a source file, runs the tests and lists the changes no test caught; the file is always restored; `-WhatIf` lists the mutants only and restores a file an interrupted run left mutated (it refuses if the file was edited since) | `.\tools\mutate.ps1 -File ChainRules.kt -Tests 'com.donuts.game.ChainRulesTest'` |
| `env.ps1` | Dot-sourced by the build scripts: sets `JAVA_HOME` (Android Studio's JDK) and, in a git worktree with no `local.properties`, `ANDROID_HOME` from the main checkout | `. .\tools\env.ps1` |
| `code-map.ps1` | Lists source files with line counts, one file's functions with line numbers, or finds a function by regex; `-Doc` prints a Markdown doc map, `-UpdateDoc <file>` refreshes its doc-map markers | `.\tools\code-map.ps1 -Match "sticker"`; `.\tools\code-map.ps1 -UpdateDoc .\CLAUDE.md` |
| `emu.ps1` | Drives the emulator: `Start` an AVD, `Install` the debug build, take a `Shot` into `build\shots`, `Tap` a point, `Drag` a finger path, `Plant` a saved board (to set up a power-up), `Stop` the emulator and the adb server | `.\tools\emu.ps1 Shot -Name board` |
| `bump-version.ps1` | Sets `versionName` and raises `versionCode` in `app\build.gradle` for the next Play upload; `-WhatIf` shows the change only | `.\tools\bump-version.ps1 -Name 2.0.3` |
| `..\release.ps1` | Builds the signed release bundle (see the README's release checklist) | `.\release.ps1` |
