# Tools

Scripts for the mechanical work. All are PowerShell 5.1 and are run from the repository root.

| Script | Purpose | Example |
|---|---|---|
| `check.ps1` | Builds the app and prints one verdict line; lists compiler errors on failure. Full output in `build\logs\check.log` | `.\tools\check.ps1 -Quiet` |
| `check.ps1 -Test` | Same verdict line, but runs the unit tests (`testDebugUnitTest`); failing tests are listed from the Gradle output | `.\tools\check.ps1 -Test -Quiet` |
| `code-map.ps1` | Lists source files with line counts, one file's functions with line numbers, or finds a function by regex | `.\tools\code-map.ps1 -Match "sticker"` |
| `emu.ps1` | Drives the emulator: `Start` an AVD, `Install` the debug build, take a `Shot` into `build\shots`, `Tap` a point, `Drag` a finger path, `Plant` a saved board (to set up a power-up), `Stop` the emulator and the adb server | `.\tools\emu.ps1 Shot -Name board` |
| `bump-version.ps1` | Sets `versionName` and raises `versionCode` in `app\build.gradle` for the next Play upload; `-WhatIf` shows the change only | `.\tools\bump-version.ps1 -Name 2.0.3` |
| `..\release.ps1` | Builds the signed release bundle (see the README's release checklist) | `.\release.ps1` |
