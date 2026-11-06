## Design Context

### Users
Steven — a young kid (5–8 years old). He's the sole player. This is a gift made specifically for him, so the experience should feel personal and joyful, not generic. The "job to be done" is pure fun: drag donuts, watch them pop, feel like a champion. No score pressure, no timers, no failure states.

### Brand Personality
**Playful · Bouncy · Silly**

The app should feel like it's having as much fun as Steven is. Big reactions to small actions. Pieces that feel alive. Celebrations that feel genuinely exciting, not perfunctory. The name "for Steven" on the title screen is the whole vibe — this was made *for him*.

### Aesthetic Direction
- **Saturated, warm, high-energy** — not pastel, not muted. Colors should pop.
- **Cartoon with weight** — thick outlines, bold shapes, chunky buttons. Nothing thin or wispy.
- **Bouncy motion** — pieces should feel springy and alive. Scale pops, juicy squash-and-stretch on interactions.
- **NOT**: corporate flat design, dark gamer aesthetic, neon-on-black, toddler-oversimplified, cluttered/noisy UI.
- **Reference feel**: the joy of a Saturday morning cartoon — vibrant, warm, full of personality.

### Design Principles
1. **Every interaction should feel rewarding** — tapping, dragging, connecting, clearing should all have tactile, satisfying feedback. Nothing should feel dead or flat.
2. **Big and readable** — Steven is 5–8. Text must be generous. Touch targets must be large. Never sacrifice legibility for aesthetics.
3. **Warmth over precision** — slightly wobbly, hand-drawn energy beats pixel-perfect sterility. Imperfect curves and playful shapes feel more alive.
4. **Celebration is a feature** — milestones, clears, and connections deserve real fanfare. Err on the side of more confetti, not less.
5. **The board is the hero** — every UI chrome element (HUD, buttons, counter) should recede and let the colorful pieces command attention.

## What this is

Donuts for Steven: an Android match-3 game in Kotlin (min API 26). One module, `app`. The views draw on a
`SurfaceView` canvas from a render thread; there are no layouts, Compose or web views. Game rules (`GameBoard`
and the small pure helpers) have no Android types and are unit tested on the JVM. Released through Google Play
closed testing; see the README for the release checklist.

## Build, run and test

All commands are PowerShell 5.1, run from the repository root. `check.ps1` finds the JDK that ships with
Android Studio when `$env:JAVA_HOME` is not set.

| Do | Command |
|---|---|
| Build the debug APK (`app\build\outputs\apk\debug\app-debug.apk`) | `.\tools\check.ps1 -Quiet` |
| Build and run the unit tests (the full suite) | `.\tools\check.ps1 -Test -Quiet` |
| Start an emulator (`Pixel_8a` default, or `-Avd 10_Tablet`) | `.\tools\emu.ps1 Start` |
| Install the debug build and launch it | `.\tools\emu.ps1 Install` |
| Screenshot into `build\shots` | `.\tools\emu.ps1 Shot -Name board` |
| Stop the emulator and the adb server | `.\tools\emu.ps1 Stop` |
| Raise the version for a Play upload | `.\tools\bump-version.ps1 -Name 2.0.5` |
| Build the signed release bundle | `.\release.ps1` |
| Upload the bundle to Play | `.\gradlew.bat publishReleaseBundle` |
| Regenerate the doc map below | `.\tools\code-map.ps1 -UpdateDoc CLAUDE.md` |

Unit tests live in `app\src\test\java\com\donuts\game`. There are no instrumented tests and no CI.

## Code map

All sources are in `app\src\main\java\com\donuts\game`. No file is over about 600 lines; keep it that way and split a file when it passes 500.

`GameView` is one class spread over several files. `GameView.kt` holds its state and the frame order; the other files hold `internal fun GameView.name()` extension functions for one area each.

| File | Holds |
|---|---|
| `GameView.kt` | Every field, the surface callbacks, `drawFrame` (the draw order), `onTouchEvent` |
| `GameTypes.kt` | Small value types: `AnimPhase`, `AnimCell`, `PowerUpFx`, `DigitFlip`, `Particle`, `FloatLabel`, `BoxStrip` |
| `GameLayout.kt` | Where the board, HUD and both panels sit |
| `GameAnimation.kt` | Per-frame state updates: pops, drops, counter, hint, celebrations, stickers earned, settings panel slide, box strip |
| `GameInput.kt` | Touch: panel hits, dragging and releasing a chain, New game |
| `BoardDraw.kt` | The board: background, cells, chain string, hint ring, flashes |
| `DonutArt.kt` | The six donuts and the ball: vector art and the sprite cache |
| `HudDraw.kt` | Backdrop, icon buttons (star, gear), pretty buttons, the counter, box strip, text helpers |
| `EffectsDraw.kt` | Float labels, pings, confetti and banners, sticker fly, tutorial, no-moves warning, power-up effects |
| `PanelsDraw.kt` | The stickers panel (with the donut box) and the settings panel |
| `StickerArt.kt` | The twelve sticker pictures |
| `SavedGame.kt` | Keeping the board and counter across an activity restart (`saveState`, `restoreState`), and session banking (`saveSession`, `lifetimeDonuts`, `rebuildBoard`, prefs save) |
| `RenderThread.kt` | The render thread: lock a canvas, draw a frame, post it, pace to 60 fps |
| `TwoTapConfirm.kt` | The tap-twice confirm for New game and Reset stickers (pure, tested) |
| `BannerQueue.kt` | Celebration banners wait their turn: a new one starts once the current one has shown its first part (pure, unit tested) |
| `PopTiming.kt` | When each cell of a power-up clear starts to pop (pure, unit tested) |
| `ChainRules.kt` | `ChainRules.accepts`: which cell may join a drag chain, golden wild rules (pure, unit tested) |
| `ChainPointer.kt` | `ChainPointer`: which finger owns the current chain (pure, unit tested) |
| `ChainBadge.kt` | Where the chain-count badge sits: a chain cell next to the last one (pure, unit tested) |
| `LayoutMath.kt` | Pure layout arithmetic: `spareShare`, sticker tile `gutter` and `fitSide` (unit tested) |
| `SavedBoard.kt` | `SavedBoard` and its text `encode`/`decode` form for `Prefs`, no Android types (unit tested) |
| `StickerRules.kt` | `StickerStats` and `StickerRules`: which stickers are earned, donut-box slot maths (pure, unit tested) |
| `GameBoard.kt`, `GameCell.kt`, `ChainResult.kt`, `PowerUp.kt`, `DonutType.kt` | Game rules, no drawing |
| `MainView.kt`, `MainActivity.kt`, `GameActivity.kt` | Home screen and the two activities |
| `Prefs.kt`, `UiScale.kt`, `GameTheme.kt`, `SoundEngine.kt`, `HapticEngine.kt` | Saved data, dp sizing, palette, sound, haptics |

Rules for this layout:

1. Find code with `.\tools\code-map.ps1 -Match <regex>` (or `-File <name>`), then read only that line range. Never read a source file whole.
2. New state is an `internal` field in `GameView.kt`. A new function goes in the file for its area as `internal fun GameView.name()`. Do not add functions to `GameView.kt` itself unless they are overrides.
3. A type used by more than one file goes in `GameTypes.kt`, not nested in the class (extensions cannot see nested types by their short name).

## Doc map

Generated by `.\tools\code-map.ps1 -UpdateDoc CLAUDE.md`; do not edit by hand. Each symbol is `name start-end`,
so read just that range.

<!-- doc-map:start -->
### Main sources

| File | Lines | Symbols (line ranges) |
|---|---|---|
| BannerQueue.kt | 31 | `BannerQueue` 10-14, `offer` 15-21, `poll` 22-27, `clear` 28-29, `canStart` 30-31 |
| BoardDraw.kt | 381 | `drawResetFlash` 20-32, `drawChainFlash` 33-44, `drawBoardBackground` 45-75, `drawChainLine` 76-103, `drawCells` 104-113, `drawHintRing` 114-123, `nearMissOffset` 124-139, `drawCellsInner` 140-281, `drawGoldenOverlay` 282-314, `drawPoppingCells` 315-381 |
| ChainBadge.kt | 21 | `ChainBadge` 5-10, `spot` 11-21 |
| ChainPointer.kt | 25 | `ChainPointer` 4-8, `onDown` 9-12, `onPointerDown` 13-15, `onPointerUp` 16-21, `reset` 22-25 |
| ChainResult.kt | 16 | `ChainResult` 11-16 |
| ChainRules.kt | 21 | `ChainRules` 4-9, `accepts` 10-21 |
| DonutArt.kt | 330 | `buildSprites` 17-41, `blit` 42-55, `drawPiece` 56-64, `drawBall` 65-70, `drawPieceVector` 71-85, `ringBase` 86-99, `drawHole` 100-110, `drawDrippyRing` 111-137, `drawStripedRing` 138-155, `drawJellyBun` 156-170, `buildScallop` 171-181, `drawFlowerRing` 182-206, `drawHalfDipRing` 207-236, `drawSquareDonut` 237-266, `drawBallVector` 267-292, `drawPentagon` 293-311, `addSheen` 312-330 |
| DonutType.kt | 23 | `DonutType` 7-20, `random` 21-23 |
| EffectsDraw.kt | 411 | `drawFloatLabels` 20-59, `drawCenterPing` 60-85, `drawCelebration` 86-158, `drawStickerFly` 159-205, `drawTutorial` 206-272, `chainOrder` 273-275, `adj` 276-285, `drawFinger` 286-306, `drawNoMovesWarning` 307-358, `drawPowerUpFx` 359-371, `ring` 372-411 |
| GameActivity.kt | 57 | `GameActivity` 8-11, `onCreate` 12-34, `handleOnBackPressed` 35-43, `onSaveInstanceState` 44-48, `onPause` 49-57 |
| GameAnimation.kt | 327 | `updateHint` 20-33, `advancePowerPops` 34-45, `advanceAnimation` 46-170, `advanceSettingsAnim` 171-192, `easeOutQuint` 193-200, `advanceCounter` 201-234, `celebrate` 235-238, `startCelebration` 239-247, `spawnParticles` 248-271, `easeOutBack` 272-277, `checkStickers` 278-308, `starPulse` 309-316, `advanceBoxStrip` 317-327 |
| GameBoard.kt | 352 | `GameBoard` 12-44, `refill` 45-63, `randomType` 64-66, `safeRandom` 67-88, `spawnCell` 89-96, `findMatches` 97-137, `resolveOnce` 138-166, `findHint` 167-181, `floodFill` 182-222, `peekChainClear` 223-275, `clearChain` 276-301, `hasValidMoves` 302-308, `shuffle` 309-312, `reset` 313-322, `settle` 323-327, `cellCodes` 328-333, `clearedCounts` 334-339, `restore` 340-352 |
| GameCell.kt | 20 | `GameCell` 14-20 |
| GameInput.kt | 252 | `settingsHit` 19-31, `stickersHit` 32-38, `panelHitRect` 39-49, `handleSettingsTouch` 50-77, `handleStickersTouch` 78-82, `resetStickers` 83-92, `startOver` 93-108, `startHeldDrag` 109-115, `handleDown` 116-127, `handleMove` 128-165, `handleUp` 166-235, `cellCol` 236-237, `cellRow` 238-239, `inBounds` 240-241, `adjacent8` 242-245, `onBackPressed` 246-252 |
| GameLayout.kt | 159 | `computeLayout` 17-68, `layoutSettingsPanel` 69-79, `placeSettings` 80-92, `row` 93-114, `layoutStickersPanel` 115-150, `excludeBackSwipe` 151-159 |
| GameTheme.kt | 16 | `GameTheme` 6-16 |
| GameTypes.kt | 41 | `AnimPhase` 7-8, `AnimCell` 9-15, `PowerUpFx` 16-20, `DigitFlip` 21-22, `Particle` 23-29, `FloatLabel` 30-35, `BoxStrip` 36-41 |
| GameView.kt | 515 | `GameView` 14-365, `surfaceCreated` 366-370, `surfaceChanged` 371-374, `surfaceDestroyed` 375-394, `drawFrame` 395-442, `onTouchEvent` 443-515 |
| HapticEngine.kt | 34 | `HapticEngine` 10-21, `tick` 22-24, `pop` 25-27, `milestone` 28-31, `shuffle` 32-34 |
| HudDraw.kt | 234 | `drawHUD` 20-24, `drawBackdrop` 25-38, `drawIconButton` 39-46, `drawStarIcon` 47-63, `drawGearIcon` 64-81, `pressScaleFor` 82-85, `buttonPressScale` 86-92, `drawPrettyButton` 93-134, `drawCounter` 135-203, `drawLabel` 204-219, `drawOutlinedText` 220-230, `drawBoxStrip` 231-234 |
| LayoutMath.kt | 32 | `LayoutMath` 7-8, `spareShare` 9-12, `gutter` 13-15, `cellIndex` 16-19, `boardSide` 20-22, `panelLocalY` 23-25, `fitSide` 26-32 |
| MainActivity.kt | 28 | `MainActivity` 8-12, `onResume` 13-17, `onCreate` 18-28 |
| MainView.kt | 366 | `MainView` 13-65, `onSizeChanged` 66-71, `relayout` 72-93, `onDraw` 94-115, `drawDotGrid` 116-130, `drawLogo` 131-149, `drawDonut` 150-189, `drawSoccerBall` 190-228, `regularPolygon` 229-243, `addSheen` 244-260, `drawTitle` 261-301, `buttonPressScale` 302-308, `drawPlayButton` 309-350, `onTouchEvent` 351-366 |
| PanelsDraw.kt | 454 | `stickerStats` 21-30, `isStickerEarned` 31-32, `drawStickersPanel` 33-194, `drawDonutBox` 195-262, `popScale` 263-267, `drawClosedBox` 268-287, `drawSettings` 288-355, `drawConfirmButton` 356-375, `drawCheckBadge` 376-386, `drawSectionLabel` 387-417, `drawSettingsBtn` 418-454 |
| PopTiming.kt | 40 | `PopTiming` 8-11, `leadMs` 12-19, `origin` 20-22, `delays` 23-37, `totalMs` 38-40 |
| PowerUp.kt | 11 | `PowerUp` 11-11 |
| Prefs.kt | 66 | `Prefs` 5-66 |
| RenderThread.kt | 60 | `RenderThread` 9-12, `lockFrame` 13-27, `run` 28-60 |
| SavedBoard.kt | 30 | `SavedBoard` 4-5, `encode` 6-10, `decode` 11-22, `ints` 23-30 |
| SavedGame.kt | 130 | `settleBoard` 19-34, `saveState` 35-49, `restoreState` 50-56, `applySaved` 57-73, `saveToPrefs` 74-82, `restoreFromPrefs` 83-88, `saveSession` 89-100, `lifetimeDonuts` 101-102, `rebuildBoard` 103-116, `resetPlayState` 117-130 |
| SoundEngine.kt | 122 | `SoundEngine` 12-15, `waveSample` 16-22, `playConnectBlip` 23-28, `playPopClear` 29-33, `playBoop` 34-38, `playDropLand` 39-43, `playMilestone` 44-54, `playShuffle` 55-62, `playTone` 63-66, `playToneBlocking` 67-77, `playSweep` 78-93, `playPCM` 94-122 |
| StickerArt.kt | 114 | `drawStickerArt` 20-46, `drawChainArt` 47-58, `drawBatchArt` 59-68, `drawCrown` 69-87, `drawMiniBoard` 88-100, `drawHeart` 101-114 |
| StickerRules.kt | 62 | `StickerStats` 6-14, `StickerRules` 15-19, `isEarned` 20-39, `earnedMask` 40-46, `toAnnounce` 47-49, `boxesFilled` 50-52, `slotsFilled` 53-55, `slotJustFilled` 56-59, `boxJustFilled` 60-62 |
| TwoTapConfirm.kt | 21 | `TwoTapConfirm` 4-7, `tap` 8-13, `isArmed` 14-16, `progress` 17-19, `cancel` 20-21 |
| UiScale.kt | 26 | `UiScale` 14-21, `update` 22-26 |

### Tests

| File | Lines | Symbols (line ranges) |
|---|---|---|
| BannerQueueTest.kt | 42 | `BannerQueueTest` 7-42 |
| ChainBadgeTest.kt | 49 | `ChainBadgeTest` 8-49 |
| ChainPointerTest.kt | 33 | `ChainPointerTest` 8-33 |
| ChainRulesTest.kt | 41 | `ChainRulesTest` 9-41 |
| GameBoardTest.kt | 423 | `GameBoardTest` 19-27, `typeOf` 28-32, `boardOf` 33-42, `GameBoard` 43-45, `cells` 46-47, `rowOf` 48-54, `sameSeedGivesTheSameBoard` 55-61, `differentSeedsGiveDifferentBoards` 62-71, `newBoardHasNoThreeInARow` 72-79, `newBoardHasNoGoldenCells` 80-85, `shuffledBoardHasNoThreeInARow` 86-94, `shuffleRemovesGoldenCells` 95-101, `resetZeroesCountsAndLeavesNoThreeInARow` 102-114, `peekRejectsChainsUnderThree` 115-122, `peekRejectsMixedTypes` 123-128, `peekAcceptsPlainChainOfThree` 129-138, `peekTreatsGoldenCellAsWild` 139-146, `peekTakesChainTypeFromFirstNonGoldenCell` 147-154, `peekAcceptsAnAllGoldenChain` 155-162, `peekStillRejectsMixedTypesAroundAGoldenCell` 163-172, `powerUpTierFollowsChainLength` 173-188, `bombClearsThreeByThreeAroundTheMiddleCell` 189-205, `bombIsClippedAtTheBoardEdge` 206-220, `rowBlastClearsTheRowOfTheMiddleCell` 221-236, `colorBurstClearsEveryOtherCellOfTheChainType` 237-257, `clearChainDropsSurvivorsToTheBottomInOrderAndKeepsGolden` 258-282, `clearChainCountsEachClearedCellOnce` 283-290, `clearChainCountsBonusCellsOnceEach` 291-309, `resolveOnceClearsAPlantedLine` 310-327, `resolveOnceDoesNothingWithoutAMatch` 328-340, `findHintFindsAConnectedTrioIncludingDiagonals` 341-351, `findHintIsEmptyWhenNoTypeHasThreeConnected` 352-362, `findHintOnSeededBoardsIsThreeConnectedSameTypeCells` 363-384, `findHintSeesAMoveThatNeedsAGoldenCell` 385-391, `adjacent` 392-395, `findHintIsAlwaysAChainPeekAcceptsOnGoldenBoards` 396-412, `everyBoardHasAValidMoveAfterConstructShuffleAndReset` 413-423 |
| LayoutMathTest.kt | 52 | `LayoutMathTest` 6-52 |
| PopTimingTest.kt | 88 | `PopTimingTest` 9-10, `boardOf` 11-21, `resultOf` 22-24, `checkEveryCellHasAnEntry` 25-29, `plainChainPopsAllAtOnce` 30-40, `bombOriginSwellsFirstThenNeighboursPopTogether` 41-55, `rowBlastGrowsWithColumnDistanceAndMatchesBothSides` 56-72, `colorBurstBonusStartsAfterLeadAndRipplesOutward` 73-88 |
| SavedBoardCodecTest.kt | 49 | `SavedBoardCodecTest` 11-12, `seeded` 13-49 |
| SavedBoardTest.kt | 86 | `SavedBoardTest` 10-11, `seeded` 12-20, `roundTrip` 21-37, `assertUnchanged` 38-67, `quietBoard` 68-86 |
| StickerRulesTest.kt | 109 | `StickerRulesTest` 8-11, `earned` 12-13, `edge` 14-109 |
| TwoTapConfirmTest.kt | 49 | `TwoTapConfirmTest` 8-49 |
<!-- doc-map:end -->

## Sessions and workers

- One task per session. The state is in the README, the commits and the memory files, so the next task starts in a new session.
- A worker gets one deliverable. Its brief names the files and the functions by their `code-map.ps1` line numbers. Workers that build something already decided run on Sonnet; design and diagnosis stay on the larger model. A worker stops and reports at about 80 tool calls.
- `.\tools\check.ps1 -Quiet` is the check: one verdict line, compiler errors only when it fails. Open `build\logs\check.log` only on a failure.
- Use the emulator only when a change affects what is on screen. Take one screenshot per thing to verify, and shut the emulator and the adb server down afterwards.

## Script the repetitive work

Treat any task with fixed, mechanical steps, or any task I have asked for before, as something to script rather than do by hand.

1. Before doing a task, check `tools\` and the root (`release.ps1`, `.\gradlew.bat publishReleaseBundle`) for a script that already does it. If it almost fits, add a parameter instead of working around it.
2. If you notice you are repeating a sequence of edits or commands, stop, write a script in `tools\`, then use it.
3. Scripts are deterministic: no LLM calls. They exit non-zero on failure, are safe to re-run, and have `-WhatIf` for anything that publishes or deletes.
4. Scripts are PowerShell 5.1 and start with comment-based help: `.SYNOPSIS`, a `.PARAMETER` per parameter and an `.EXAMPLE`.
5. When a script's output is wrong, fix the script, not the output.
6. Register every new script in `tools\README.md` with one line: name, purpose, example.
7. When you finish a task, list which steps are now scripted and the exact command for each.
