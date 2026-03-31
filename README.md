# Donuts — Android Match-3 Game

A native Android match-three puzzle game inspired by the browser game at
the Donuts game.

## Gameplay

Swap adjacent donuts to make rows or columns of **3 or more** matching flavours.
Matched donuts disappear, the remaining donuts fall, and new ones fill in from the top.
Cascade matches score bonus points. Reach the target score before your moves run out!

### Donut flavours

| Flavour     | Colour     |
|-------------|------------|
| Strawberry  | Pink       |
| Chocolate   | Brown      |
| Blueberry   | Blue/Purple|
| Vanilla     | Yellow     |
| Matcha      | Green      |
| Caramel     | Amber      |

### Levels

| Level  | Moves | Target score |
|--------|-------|-------------|
| Easy   | 30    | 1 000        |
| Medium | 25    | 2 000        |
| Hard   | 20    | 3 500        |

## Controls

- **Tap** a donut to select it.
- **Swipe** in any direction to swap it with its neighbour.
- A swap that produces no match is rejected automatically.
- When the game ends, **tap anywhere** to play again.

## Building

Open the project in **Android Studio Hedgehog (2023.1)** or later.

```
File → Open → <path-to>/donuts
```

Run on a device or emulator with **API 26+** (Android 8.0 Oreo and above).

Alternatively from the command line (after running `gradle wrapper` once):

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

```
donuts/
├── app/src/main/
│   ├── java/com/donuts/game/
│   │   ├── DonutType.kt          – Enum of flavours + colours
│   │   ├── GameCell.kt           – Single grid cell data class
│   │   ├── GameBoard.kt          – Match-3 logic (swap, detect, resolve, score)
│   │   ├── GameView.kt           – SurfaceView render loop + touch input
│   │   ├── MainActivity.kt       – Title / main menu screen
│   │   ├── LevelSelectActivity.kt– Level picker screen
│   │   └── GameActivity.kt       – Hosts the GameView
│   ├── res/
│   │   ├── layout/               – XML layouts for menu screens
│   │   ├── values/               – strings, colors, themes
│   │   └── drawable/             – Adaptive launcher icon (vector)
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── README.md
```
