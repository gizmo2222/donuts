# Donuts for Steven

A cozy, kid-friendly match-3 game for Android. Drag to connect matching donuts and watch them pop. No timers, no pressure — just fun.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60">](https://play.google.com/store/apps/details?id=com.donuts.game)

---

## Gameplay

Draw a chain through **3 or more** matching pieces to clear them. The board refills from above and cascades automatically. A counter tracks how many pieces you've cleared — there's no score to beat and no moves to run out of.

### Power-ups

Longer chains trigger power-ups that clear extra pieces:

| Chain length | Power-up     | Effect                                        |
|-------------|--------------|-----------------------------------------------|
| 5 – 6       | **Bomb**     | Clears a 3×3 area around the chain mid-point  |
| 7 – 8       | **Row Blast**| Clears the entire row of the chain mid-point  |
| 9+          | **Color Burst** | Clears every remaining piece of that color |

### Golden Donuts

Occasionally a **golden** piece drops in during a refill — it glows and can be included in any chain regardless of its type, acting as a wild card.

### Cascades

When cleared pieces cause new matches to form, they auto-pop in sequence. Each cascade shows a **×N** label so you can track the chain reaction — and every cascade pop counts toward your cleared total.

## Settings

Tap the gear above the board:

- **Sound** — on / off (on by default)
- **Hints** — on / off; when on, a valid chain is highlighted after 5 seconds idle
- **Donuts** — Big (6×6 board) or Small (8×8 board)
- **New game** — fresh board and counter (tap twice to confirm)

Stickers live behind the medal above the board. Each locked sticker says what to do to earn it.

## First run

There is no text tutorial. The first time the board appears, three matching donuts glow while a finger traces the path between them, and the demo repeats until the first chain is made.

## Building

Open in **Android Studio Hedgehog (2023.1)** or later:

```
File → Open → <path-to>/donuts
```

Requires a device or emulator running **Android 8.0 (API 26)** or higher.

### Debug build

```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Release build (Google Play)

Signing credentials live in `local.properties`, which is gitignored and must never be committed:

```properties
KEYSTORE_PATH=F:/dev/donuts/donuts-release.jks
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=donuts
KEY_PASSWORD=your_password
```

Then, from PowerShell in the project root:

```powershell
.\release.ps1
```

The script picks Android Studio's bundled JDK, checks the credentials are present, prints the version it is
building, runs `bundleRelease`, and stops with a red **BUILD FAILED** if anything goes wrong. On success it
prints the path of the signed bundle:

```
app\build\outputs\bundle\release\app-release.aab
```

To check that a password opens the keystore before building, run `check-keystore.cmd` (double-click it, or run
it from Command Prompt or PowerShell) and type the password when asked. A line ending in `PrivateKeyEntry`
means it is correct; `keystore password was incorrect` means it is not.

### Release checklist

1. Bump `versionCode` (must be higher than the last upload) and `versionName` in `app/build.gradle`.
2. Install the debug build on a real phone (`.\gradlew.bat installDebug`) and play through the first-launch
   demo and a milestone. Debug builds log `fps` once a second under the `Donuts` logcat tag.
3. Run `.\release.ps1` and confirm it ends with **BUILD SUCCESSFUL**.
4. Play Console: Donuts for Steven, then Release, then Production (or Internal testing first). Create a new
   release, upload the `.aab`, write the release notes, review, roll out.
5. Commit and push the version bump.

If the upload-key password is lost: Play App Signing holds the real app signing key, so the upload key can be
replaced. Create a new keystore with `keytool -genkeypair`, export its certificate with `keytool -exportcert
-rfc`, and in Play Console go to Setup, then App signing, then **Request upload key reset**, and attach the
certificate. Point `local.properties` at the new keystore once Google confirms.

## Project structure

```
donuts/
├── app/src/main/
│   ├── java/com/donuts/game/
│   │   ├── DonutType.kt     — Piece types, colors, and icon style
│   │   ├── GameTheme.kt     — The single warm-cream palette
│   │   ├── GameCell.kt      — Single grid cell (type, position, golden flag)
│   │   ├── GameBoard.kt     — Match-3 logic (chain detection, fill, cascade, power-ups)
│   │   ├── ChainResult.kt   — Pre-computed chain clear outcome (cells + bonus + power-up)
│   │   ├── PowerUp.kt       — Power-up tier enum (NONE, BOMB, ROW_BLAST, COLOR_BURST)
│   │   ├── GameView.kt      — SurfaceView render loop, animations, settings panel
│   │   ├── MainView.kt      — Home screen (logo, play button)
│   │   ├── MainActivity.kt  — Hosts MainView
│   │   ├── GameActivity.kt  — Hosts GameView
│   │   ├── Prefs.kt         — SharedPreferences wrapper
│   │   └── UiScale.kt       — Density-independent sizing unit
│   ├── res/
│   │   ├── font/            — Fredoka One (rounded kid-friendly typeface)
│   │   ├── values/          — strings, colors, themes
│   │   └── drawable/        — Adaptive launcher icon (vector)
│   └── AndroidManifest.xml
├── .claude/settings.json.disabled — the old auto-push hook, kept for reference
├── build.gradle
└── README.md
```

## Tech notes

- Fully canvas-drawn: no XML layouts for the game or home screen
- `SurfaceView` rendered through a GPU-backed canvas (`lockHardwareCanvas`, API 26+) with a software fallback; the render thread paces itself to a 16 ms budget and stops on `surfaceDestroyed`
- Every size is expressed in design-dp through `UiScale` (density, boosted up to 1.5x on tablets); layout stays inside the window insets
- Pieces are painted once per layout into sprite bitmaps and blitted per frame; the vector painters (six donut silhouettes and the ball) only run when sprites are rebuilt
- Render loop is allocation-free: scratch `Path`/`RectF` objects reused every frame, trig tables precomputed at init time
- All animations are time-based (`SystemClock.elapsedRealtime()`) with `easeOutQuint`; confetti physics is time-based too, so it is correct at any frame rate
- Reduced motion is honoured through the system animator scale
- Thread safety: touch events synchronized on the surface `holder`; float labels on their own lock
- Power-ups computed via `peekChainClear()` (pure/non-mutating) before board mutation so bonus cells animate correctly
- Cascades animate one pass at a time (POPPING then DROPPING, repeated); every cascade pop counts toward the total, milestones, and stickers
- Debug builds log `fps`, canvas path, and lock/draw/post times once a second under the `Donuts` tag

## Requirements

- Android 8.0+ (API 26)
- Targets API 35
