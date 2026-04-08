# Donuts for Steven

A cozy, kid-friendly match-3 game for Android. Drag to connect matching pieces and watch them pop. No timers, no pressure — just fun.

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

When cleared pieces cause new matches to form, they auto-pop in sequence. Each cascade shows a **Combo ×N** label so you can track the chain reaction.

## Themes

Pick your favorite world from the settings panel:

| Theme  | Pieces        | Palette      |
|--------|---------------|--------------|
| Donuts | Glazed donuts | Warm cream   |
| Stars  | Stars         | Dark navy    |
| Dinos  | Dinosaurs     | Jungle green |
| Trucks | Trucks        | Steel blue   |

Each theme recolors the entire UI — buttons, board, background, and all.

## Settings

Tap the gear icon to open the settings panel:

- **Theme** — Donuts / Stars / Dinos / Trucks
- **Hint delay** — how long before a valid chain is highlighted (1 s / 3 s / 5 s / off)
- **Grid size** — 6×6 or 8×8

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

### Release build

Add signing credentials to `local.properties` (this file is gitignored — never commit it):

```properties
KEYSTORE_PATH=/path/to/donuts-release.jks
KEYSTORE_PASSWORD=your_password
KEY_ALIAS=donuts
KEY_PASSWORD=your_password
```

Then build:

```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

## Project structure

```
donuts/
├── app/src/main/
│   ├── java/com/donuts/game/
│   │   ├── DonutType.kt     — Piece types, colors, and icon style
│   │   ├── GameTheme.kt     — Theme definitions (colors, icon type)
│   │   ├── GameCell.kt      — Single grid cell (type, position, golden flag)
│   │   ├── GameBoard.kt     — Match-3 logic (chain detection, fill, cascade, power-ups)
│   │   ├── ChainResult.kt   — Pre-computed chain clear outcome (cells + bonus + power-up)
│   │   ├── PowerUp.kt       — Power-up tier enum (NONE, BOMB, ROW_BLAST, COLOR_BURST)
│   │   ├── GameView.kt      — SurfaceView render loop, animations, settings panel
│   │   ├── MainView.kt      — Home screen (logo, play button)
│   │   ├── MainActivity.kt  — Hosts MainView
│   │   ├── GameActivity.kt  — Hosts GameView
│   │   └── Prefs.kt         — SharedPreferences wrapper
│   ├── res/
│   │   ├── font/            — Fredoka One (rounded kid-friendly typeface)
│   │   ├── values/          — strings, colors, themes
│   │   └── drawable/        — Adaptive launcher icon (vector)
│   └── AndroidManifest.xml
├── .claude/settings.json    — Auto git-push + AAB build hooks
├── build.gradle
└── README.md
```

## Tech notes

- Fully canvas-drawn — no XML layouts for the game or home screen
- `SurfaceView` with a dedicated render thread at ~60 fps
- All animations are time-based (`SystemClock.elapsedRealtime()`) with `easeOutQuint`
- Thread safety: touch events synchronized on the surface `holder`; float labels on their own lock
- Icons drawn with a two-pass cartoon technique (dark stroke outline + color fill)
- 3D sheen via `canvas.clipPath()` with an upper-left oval highlight
- Render loop is allocation-free: scratch `Path`/`RectF` objects reused each frame, trig tables precomputed at init time
- Power-ups computed via `peekChainClear()` (pure/non-mutating) before board mutation so bonus cells animate correctly
- Cascades animate one pass at a time (POPPING → DROPPING → repeat) rather than resolving all at once

## Requirements

- Android 8.0+ (API 26)
- Targets API 35
