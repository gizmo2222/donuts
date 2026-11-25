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

All commands are PowerShell 5.1, run from the repository root. The build scripts dot-source `tools\env.ps1`, which sets
`$env:JAVA_HOME` to the JDK that ships with Android Studio when it is not set, and `$env:ANDROID_HOME` from the
main checkout's `local.properties` when working in a git worktree.

| Do | Command |
|---|---|
| Build the debug APK (`app\build\outputs\apk\debug\app-debug.apk`) | `.\tools\check.ps1 -Quiet` |
| Build and run the unit tests (the full suite) | `.\tools\check.ps1 -Test -Quiet` |
| Run only some test classes | `.\tools\check.ps1 -Quiet -Tests 'com.donuts.game.GameInputTest'` |
| Line and branch coverage per file (`-Lines` lists uncovered lines) | `.\tools\coverage.ps1 -Quiet` |
| Mutation check one file against its tests | `.\tools\mutate.ps1 -File GameBoard.kt -Tests 'com.donuts.game.GameBoardTest'` |
| Start an emulator (`Pixel_8a` default, or `-Avd 10_Tablet`) | `.\tools\emu.ps1 Start` |
| Install the debug build and launch it | `.\tools\emu.ps1 Install` |
| Screenshot into `build\shots` | `.\tools\emu.ps1 Shot -Name board` |
| Tap, drag a chain, or plant a saved board (`-Board 'v1;...'`) on the emulator | `.\tools\emu.ps1 Drag -Path '100,900 200,900 300,900'` |
| Render every screen state at five sizes, no emulator (`build\shots\gallery`) | `.\tools\gallery.ps1 -Quiet` |
| Run the balance simulation, 250 scripted games (`build\balance`) | `.\tools\balance.ps1 -Quiet` |
| Colour-blind check of the donut colours and the gallery shots (`build\shots\colorblind`) | `.\tools\colorblind.ps1 -Quiet` |
| Stop the emulator and the adb server | `.\tools\emu.ps1 Stop` |
| Raise the version for a Play upload | `.\tools\bump-version.ps1 -Name 2.0.5` |
| Build the signed release bundle | `.\release.ps1` |
| Upload the bundle to Play | `.\gradlew.bat publishReleaseBundle` |
| Regenerate the doc map below | `.\tools\code-map.ps1 -UpdateDoc CLAUDE.md` |

Unit tests live in `app\src\test\java\com\donuts\game` and all run on the JVM: the rules tests are plain JUnit,
the view, activity and saved-game tests run under Robolectric 4.16. `BalanceSim` and `ScreenGallery` run only through
`balance.ps1` and `gallery.ps1`. There are no instrumented tests and no CI.

## Code map

All sources are in `app\src\main\java\com\donuts\game`. No main source is over about 450 lines and no test over about 420; split a file when it passes 500.

`GameView` is one class spread over several files. `GameView.kt` holds its state and the frame order; the other files hold `internal fun GameView.name()` extension functions for one area each.

| File | Holds |
|---|---|
| `GameView.kt` | Every field, the surface callbacks, `drawFrame` (the draw order), `onTouchEvent` |
| `GameTypes.kt` | Small value types: `AnimPhase`, `AnimCell`, `PowerUpFx`, `DigitFlip`, `Particle`, `FloatLabel`, `BoxStrip` |
| `GameLayout.kt` | Where the board, HUD and both panels sit |
| `GameAnimation.kt` | Per-frame state updates: pops, drops, counter, hint, celebrations, stickers earned, settings panel slide, box strip |
| `GameInput.kt` | Touch: the touch dispatcher (`handleTouch`), panel hits, dragging and releasing a chain, New game |
| `BoardDraw.kt` | The board: background, cells, chain string, hint ring, flashes, the reduced-motion refused-donut ring |
| `DonutArt.kt` | The six donuts and the ball: vector art and the sprite cache |
| `HudDraw.kt` | Backdrop, icon buttons (star, gear), pretty buttons, the counter, box strip, the sprinkle party meter, text helpers |
| `EffectsDraw.kt` | Float labels, pings, sticker fly, tutorial, no-moves warning, power-up effects |
| `CelebrationDraw.kt` | The milestone celebration: confetti in its look's style, the banner in its colour, golden sparkles |
| `PanelsDraw.kt` | The stickers panel (with the donut box) and the settings panel (three pages: Steven's, the gate, grown-ups) |
| `SettingsIcons.kt` | The settings pictures (speaker, buzzing phone, bulb, donut grid) that show each row's choice, and the icons on New game and Reset stickers |
| `StickerArt.kt` | The twelve sticker pictures, and the wrapped present a sticker shows until it is earned |
| `FriendArt.kt` | The donut friends' faces (`drawFriend`, all twelve), the first four painters and the shared face helpers |
| `FriendArt2.kt` | Friends 4-11 (Choco, Kicky, Berry, Gloop, Zoomy, Puff, Fizz, Captain Glaze), using the face helpers in `FriendArt.kt` |
| `FriendDraw.kt` | Meeting a friend: `checkFriends` starts a reveal, `drawFriendFly` pops it up on the board and flies it into the star; `drawPanelTabs`, `drawFriendsPage` are the stickers panel tabs and the friends page |
| `SavedGame.kt` | Keeping the board and counter across an activity restart (`saveState`, `restoreState`), and session banking (`saveSession`, `lifetimeDonuts`, `rebuildBoard`, prefs save) |
| `RenderThread.kt` | The render thread: lock a canvas, draw a frame, post it, pace to 60 fps |
| `GateDraw.kt` | The settings panel's grown-ups pages: the locked Grown-ups button, the parent gate (question, answer box, keypad), the no-ads promise |
| `ParentGate.kt` | `ParentGate`: the grown-ups gate question (times table in words) and its two-digit keypad answer (pure, unit tested) |
| `TwoTapConfirm.kt` | The tap-twice confirm for New game and Reset stickers (pure, tested) |
| `CelebrationMix.kt` | Celebration looks: confetti style, banner colour, the rare golden shower and the rare tap giggle, from a seedable `Random` (pure, unit tested) |
| `BannerQueue.kt` | Celebration banners wait their turn: a new one starts once the current one has shown its first part (pure, unit tested) |
| `FlashGate.kt` | `FlashGate.allows`: full-screen flashes (New game, big chain) start at least a third of a second apart, three a second at most; `saturatedRed` (pure, unit tested) |
| `PopTiming.kt` | When each cell of a power-up clear starts to pop (pure, unit tested) |
| `ChainRules.kt` | `ChainRules.accepts`: which cell may join a drag chain, golden wild rules (pure, unit tested) |
| `LoopRules.kt` | `LoopRules.closes`: when a drag chain closes into a ring (`MIN_LOOP` cells, back on its first cell) and clears as RING (pure, unit tested) |
| `HitZone.kt` | `HitZone.target`: which cell a drag aims at from the chain's end, nearest centre led along the drag, diagonals weighted (more on 8x8) (pure, unit tested) |
| `ChainPointer.kt` | `ChainPointer`: which finger owns the current chain (pure, unit tested) |
| `ChainBadge.kt` | Where the chain-count badge sits: a chain cell next to the last one (pure, unit tested) |
| `LayoutMath.kt` | Pure layout arithmetic: `spareShare`, sticker tile `gutter` and `fitSide`, `cellIndex`, `boardSide`, `panelLocalY`, `clampCenter` (unit tested) |
| `SavedBoard.kt` | `SavedBoard` and its text `encode`/`decode` form for `Prefs`, no Android types (unit tested) |
| `FriendRules.kt` | `Friend` and `FriendRules`: the twelve donut friends, fixed reveal order on box slot fills (pure, unit tested) |
| `StickerRules.kt` | `StickerStats` and `StickerRules`: which stickers are earned, donut-box slot maths (pure, unit tested) |
| `PartyMeter.kt` | `PartyState` and `PartyMeter`: what fills the sprinkle party meter, when a party starts and ends, its golden chance (pure, unit tested) |
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
| BoardDraw.kt | 392 | `drawResetFlash` 12-25, `drawChainFlash` 26-38, `drawBoardBackground` 39-69, `drawChainLine` 70-98, `drawCells` 99-109, `drawRefusedCue` 110-123, `drawHintRing` 124-133, `nearMissOffset` 134-151, `drawCellsInner` 152-292, `drawGoldenOverlay` 293-325, `drawPoppingCells` 326-392 |
| CelebrationDraw.kt | 112 | `drawCelebration` 9-93, `drawBannerSparkles` 94-112 |
| CelebrationMix.kt | 38 | `ConfettiStyle` 6-11, `CelebrationLook` 12-18, `CelebrationMix` 19-25, `next` 26-36, `giggle` 37-38 |
| ChainBadge.kt | 21 | `ChainBadge` 5-10, `spot` 11-21 |
| ChainPointer.kt | 21 | `ChainPointer` 4-8, `onDown` 9-11, `onPointerUp` 12-17, `reset` 18-21 |
| ChainResult.kt | 19 | `ChainResult` 11-18, `midCell` 19-19 |
| ChainRules.kt | 21 | `ChainRules` 4-9, `accepts` 10-21 |
| DonutArt.kt | 312 | `buildSprites` 9-33, `blit` 34-47, `drawPiece` 48-56, `drawBall` 57-62, `drawPieceVector` 63-77, `ringBase` 78-91, `drawHole` 92-102, `drawDrippyRing` 103-129, `drawStripedRing` 130-147, `drawJellyBun` 148-162, `buildScallop` 163-173, `drawFlowerRing` 174-198, `drawHalfDipRing` 199-228, `drawSquareDonut` 229-258, `drawBallVector` 259-274, `drawPentagon` 275-293, `addSheen` 294-312 |
| DonutType.kt | 18 | `DonutType` 7-18 |
| EffectsDraw.kt | 446 | `fitText` 13-17, `drawBannerLine` 18-24, `drawFloatLabels` 25-68, `drawCenterPing` 69-91, `drawStickerFly` 92-139, `drawTutorial` 140-206, `chainOrder` 207-209, `adj` 210-219, `drawFinger` 220-240, `drawNoMovesWarning` 241-317, `drawPowerUpFx` 318-330, `ring` 331-396, `drawShowSomeone` 397-446 |
| FlashGate.kt | 19 | `FlashGate` 10-14, `allows` 15-17, `saturatedRed` 18-19 |
| FriendArt.kt | 235 | `drawFriend` 22-51, `drawSprinkleFriend` 52-83, `drawJellyFriend` 84-114, `drawBouncyFriend` 115-137, `drawSwirlFriend` 138-173, `friendEye` 174-188, `friendWink` 189-194, `friendCheeks` 195-202, `friendGrin` 203-221, `friendTongueOut` 222-235 |
| FriendArt2.kt | 316 | `glazeTop` 17-26, `drawChocoFriend` 27-62, `drawKickyFriend` 63-100, `drawBerryFriend` 101-156, `drawGloopFriend` 157-185, `drawZoomyFriend` 186-223, `drawPuffFriend` 224-261, `drawFizzFriend` 262-284, `drawCaptainFriend` 285-316 |
| FriendDraw.kt | 128 | `checkFriends` 17-28, `drawFriendFly` 29-86, `drawPanelTabs` 87-94, `drawFriendsPage` 95-128 |
| FriendRules.kt | 39 | `Friend` 4-9, `FriendRules` 10-22, `slotsEver` 23-25, `due` 26-34, `next` 35-39 |
| GameActivity.kt | 57 | `GameActivity` 8-11, `onCreate` 12-34, `handleOnBackPressed` 35-43, `onSaveInstanceState` 44-48, `onPause` 49-57 |
| GameAnimation.kt | 405 | `cascadeWord` 10-15, `updateHint` 16-29, `advancePowerPops` 30-41, `advanceAnimation` 42-165, `addFloatLabelAt` 166-174, `advanceSettingsAnim` 175-185, `slideToward` 186-197, `easeOutQuint` 198-203, `settingsSlideY` 204-204, `stickersSlideY` 205-210, `panelBusy` 211-212, `advanceCounter` 213-246, `celebrate` 247-251, `praise` 252-255, `startCelebration` 256-269, `spawnParticles` 270-310, `easeOutBack` 311-316, `checkStickers` 317-349, `advanceShowSomeone` 350-358, `closeShowSomeone` 359-363, `starPulse` 364-371, `advanceBoxStrip` 372-384, `pourParty` 385-392, `setParty` 393-399, `advancePartyMeter` 400-405 |
| GameBoard.kt | 359 | `GameBoard` 12-53, `refill` 54-72, `randomType` 73-75, `safeRandom` 76-97, `spawnCell` 98-105, `findMatches` 106-146, `resolveOnce` 147-162, `findHint` 163-181, `dragOrder` 182-186, `floodFill` 187-214, `chainTypeOf` 215-234, `peekChainClear` 235-275, `countCleared` 276-280, `collapse` 281-298, `clearChain` 299-306, `hasValidMoves` 307-313, `shuffle` 314-317, `reset` 318-327, `settle` 328-332, `cellCodes` 333-338, `clearedCounts` 339-344, `restore` 345-359 |
| GameCell.kt | 18 | `GameCell` 13-18 |
| GameInput.kt | 395 | `settingsHit` 14-39, `stickersHit` 40-53, `handleStickersTap` 54-66, `panelHitRect` 67-81, `turnSettingsPage` 82-88, `handleSettingsTap` 89-119, `resetStickers` 120-131, `closeSettings` 132-134, `startOver` 135-150, `startHeldDrag` 151-157, `handleDown` 158-170, `handleMove` 171-227, `handleUp` 228-287, `handleTouch` 288-372, `cellCol` 373-374, `cellRow` 375-377, `cellU` 378-379, `cellV` 380-381, `inBounds` 382-383, `adjacent8` 384-387, `onBackPressed` 388-395 |
| GameLayout.kt | 221 | `computeLayout` 10-67, `layoutSettingsPanel` 68-83, `showSettingsPage` 84-91, `placeSettings` 92-116, `row` 117-166, `layoutStickersPanel` 167-175, `sideFor` 176-212, `excludeBackSwipe` 213-221 |
| GameTheme.kt | 23 | `GameTheme` 6-23 |
| GameTypes.kt | 52 | `AnimPhase` 7-13, `AnimCell` 14-20, `PowerUpFx` 21-25, `DigitFlip` 26-27, `Particle` 28-35, `FloatLabel` 36-41, `BoxStrip` 42-52 |
| GameView.kt | 484 | `GameView` 14-405, `surfaceCreated` 406-410, `surfaceChanged` 411-414, `surfaceDestroyed` 415-434, `drawFrame` 435-482, `onTouchEvent` 483-484 |
| GateDraw.kt | 74 | `drawGrownUpsButton` 16-34, `drawGatePage` 35-66, `drawPromise` 67-74 |
| HapticEngine.kt | 34 | `HapticEngine` 10-21, `tick` 22-24, `pop` 25-27, `milestone` 28-31, `shuffle` 32-34 |
| HitZone.kt | 62 | `HitZone` 16-25, `diagWeight` 26-32, `target` 33-56, `aim` 57-62 |
| HudDraw.kt | 288 | `drawHUD` 12-16, `drawBackdrop` 17-30, `drawIconButton` 31-38, `drawStarIcon` 39-55, `drawGearIcon` 56-73, `pressScaleFor` 74-77, `buttonPressScale` 78-84, `drawPrettyButton` 85-126, `drawCounter` 127-194, `drawLabel` 195-210, `drawOutlinedText` 211-221, `drawBoxStrip` 222-228, `drawPartyMeter` 229-284, `blendColor` 285-285, `ch` 286-288 |
| LayoutMath.kt | 36 | `LayoutMath` 7-8, `spareShare` 9-12, `gutter` 13-15, `cellIndex` 16-19, `boardSide` 20-22, `panelLocalY` 23-25, `fitSide` 26-33, `clampCenter` 34-36 |
| LoopRules.kt | 22 | `LoopRules` 9-15, `closes` 16-22 |
| MainActivity.kt | 28 | `MainActivity` 8-12, `onResume` 13-17, `onCreate` 18-28 |
| MainView.kt | 372 | `MainView` 13-66, `onSizeChanged` 67-72, `relayout` 73-94, `onDraw` 95-116, `drawDotGrid` 117-131, `drawLogo` 132-150, `drawDonut` 151-192, `drawSoccerBall` 193-235, `regularPolygon` 236-250, `addSheen` 251-267, `drawTitle` 268-308, `buttonPressScale` 309-315, `drawPlayButton` 316-356, `onTouchEvent` 357-372 |
| PanelsDraw.kt | 420 | `stickerStats` 13-21, `drawStickersPanel` 22-165, `drawDonutBox` 166-233, `popScale` 234-238, `drawClosedBox` 239-258, `drawSettings` 259-339, `drawConfirmButton` 340-367, `drawCheckBadge` 368-377, `drawSettingsBtn` 378-420 |
| ParentGate.kt | 49 | `ParentGate` 11-22, `next` 23-32, `press` 33-49 |
| PartyMeter.kt | 50 | `PartyState` 7-17, `PartyMeter` 18-30, `after` 31-37, `started` 38-40, `goldenChance` 41-43, `fill` 44-47, `restore` 48-50 |
| PopTiming.kt | 49 | `PopTiming` 8-11, `leadMs` 12-22, `origin` 23-31, `delays` 32-46, `totalMs` 47-49 |
| PowerUp.kt | 13 | `PowerUp` 13-13 |
| Praise.kt | 38 | `Praise` 8-21, `chainLabel` 22-32, `cheers` 33-35, `banner` 36-38 |
| Prefs.kt | 90 | `Prefs` 5-52, `highScore` 53-53, `setHighScore` 54-90 |
| RenderThread.kt | 62 | `RenderThread` 9-12, `lockFrame` 13-27, `run` 28-62 |
| SavedBoard.kt | 30 | `SavedBoard` 4-5, `encode` 6-12, `decode` 13-24, `ints` 25-30 |
| SavedGame.kt | 133 | `settleBoard` 19-30, `saveState` 31-45, `restoreState` 46-52, `applySaved` 53-69, `saveToPrefs` 70-78, `restoreFromPrefs` 79-84, `saveSession` 85-92, `lifetimeDonuts` 93-95, `displayedLifetime` 96-97, `rebuildBoard` 98-111, `resetPlayState` 112-126, `clearMotion` 127-133 |
| SettingsIcons.kt | 203 | `drawSettingsIcon` 24-37, `inkLine` 38-44, `outlineAndFill` 45-51, `drawSpeakerIcon` 52-79, `drawBuzzIcon` 80-101, `drawBulbIcon` 102-136, `drawGridIcon` 137-156, `drawRestartIcon` 157-178, `drawConfirmIcon` 179-186, `drawLockIcon` 187-203 |
| SoundEngine.kt | 181 | `SoundEngine` 12-15, `waveSample` 16-22, `playConnectBlip` 23-28, `playPopClear` 29-33, `playBoop` 34-38, `playDropLand` 39-46, `playMilestone` 47-62, `playGiggle` 63-75, `playFriend` 76-87, `playShuffle` 88-95, `playTone` 96-99, `playToneBlocking` 100-110, `playSweep` 111-114, `playSweepBlocking` 115-128, `playPCM` 129-181 |
| StickerArt.kt | 167 | `drawStickerArt` 12-45, `drawStickerGift` 46-99, `drawChainArt` 100-111, `drawBatchArt` 112-121, `drawCrown` 122-140, `drawMiniBoard` 141-153, `drawHeart` 154-167 |
| StickerRules.kt | 75 | `StickerStats` 6-14, `StickerRules` 15-20, `statsLine` 21-27, `isBig` 28-32, `isEarned` 33-52, `earnedMask` 53-59, `toAnnounce` 60-62, `boxesFilled` 63-65, `slotsFilled` 66-68, `slotJustFilled` 69-72, `boxJustFilled` 73-75 |
| TwoTapConfirm.kt | 21 | `TwoTapConfirm` 4-7, `tap` 8-13, `isArmed` 14-16, `progress` 17-19, `cancel` 20-21 |
| UiScale.kt | 26 | `UiScale` 14-21, `update` 22-26 |

### Tests

| File | Lines | Symbols (line ranges) |
|---|---|---|
| ActivityTest.kt | 233 | `ActivityTest` 17-18, `contentChild` 19-21, `gameViewOf` 22-23, `startGame` 24-26, `laidOutMain` 27-36, `dispatch` 37-42, `gameActivityShowsAGameView` 43-49, `coldStartCountsAVisitAndStartsFresh` 50-56, `recreateWithSavedStateKeepsTheBoardAndTheVisitCount` 57-77, `coldStartRestoresTheBoardKeptByOnPause` 78-94, `onPauseBanksDonutsAndWritesTheBoardToPrefs` 95-112, `backPressClosesAnOpenSettingsPanelFirst` 113-122, `backPressClosesAnOpenStickersPanelFirst` 123-132, `backPressWithNoPanelFinishesTheActivity` 133-139, `secondBackPressAfterClosingAPanelFinishes` 140-150, `mainActivityStartsGameActivityWhenPlayIsTapped` 151-157, `send` 158-173, `secondQuickPlayTapStartsOnlyOneGameUntilResume` 174-177, `tap` 178-192, `mainActivityResumeAndPauseDoNotThrow` 193-197, `surfaceDestroyedBanksLifetimeDonutsOnce` 198-211, `surfaceDestroyedStopsAndClearsTheRenderThread` 212-224, `surfaceChangedLaysTheBoardOut` 225-233 |
| BalanceSim.kt | 371 | `BalanceSim` 14-30, `Comp` 31-32, `Analysis` 33-34, `MoveRow` 35-40, `Scenario` 41-59, `Geometry` 60-72, `Dfs` 73-82, `run` 83-95, `go` 96-107, `analyze` 108-113, `ok` 114-138, `randomWalk` 139-155, `choose` 156-178, `verify` 179-200, `play` 201-209, `fail` 210-272, `pct` 273-275, `pctD` 276-278, `f1` 279-279, `f2` 280-281, `summary` 282-340, `runBalanceSimulation` 341-371 |
| BannerQueueTest.kt | 42 | `BannerQueueTest` 7-42 |
| BoardFixtures.kt | 26 | `BoardFixtures` 6-6, `typeOf` 7-12, `boardOf` 13-22, `rowOf` 23-24, `letters` 25-26 |
| CelebrationLookTest.kt | 84 | `CelebrationLookTest` 16-17, `spawn` 18-21, `theSameSeedThrowsTheSameConfetti` 22-29, `theFountainFiresUpFromTheMiddleOfTheBoardBottom` 30-38, `rainStartsAboveTheBoardAndFluttersDownNoFasterThanItsOwnSpeed` 39-51, `theGoldenShowerThrowsOnlyGoldAndWhite` 52-59, `startCelebrationKeepsTheLookAndTheBannerWearsItsColour` 60-75, `celebrationsInARowChangeTheirLook` 76-84 |
| CelebrationMixTest.kt | 69 | `CelebrationMixTest` 11-12, `run` 13-19, `theSameSeedGivesTheSameLooks` 20-25, `theStyleAlwaysChangesAndTheBannerChangesUnlessTheShowerWearsGold` 26-34, `everyStyleAndBannerColourTurnsUp` 35-41, `theGoldenShowerIsRareNeverTwiceInARowAndAlwaysGold` 42-50, `theGiggleIsAboutOneTapInTwelve` 51-59, `aDieThatAlwaysRollsZeroGivesAGoldenShowerFirstThenAPlainOne` 60-69 |
| ChainBadgeTest.kt | 76 | `ChainBadgeTest` 8-76 |
| ChainPointerTest.kt | 31 | `ChainPointerTest` 8-31 |
| ChainRulesTest.kt | 47 | `ChainRulesTest` 9-47 |
| ColorBlindPaletteTest.kt | 70 | `ColorBlindPaletteTest` 18-25, `lin` 26-30, `f` 31-32, `lab` 33-44, `deltaE` 45-70 |
| DrawSmokeTest.kt | 422 | `DrawSmokeTest` 23-24, `Scene` 25-27, `frames` 28-30, `scene` 31-33, `distinctColors` 34-39, `assertPainted` 40-43, `snake` 44-50, `dragSnake` 51-58, `release` 59-100, `powerUpCase` 101-126, `strip` 127-232, `painted` 233-255, `tap` 256-422 |
| EnginesTest.kt | 88 | `EnginesTest` 12-15, `waveSample` 16-22, `waveSampleIsASineAtQuarterPoints` 23-31, `waveSampleStaysInRangeOverManyPhases` 32-36, `everySoundMethodRunsWithoutThrowing` 37-44, `playingWithSoundAndHapticsOffAndOnWorks` 45-61, `hapticMethodsDoNotThrow` 62-69, `renderThreadStartsAndStopsWithoutAValidSurface` 70-80, `renderThreadStoppedBeforeItRunsExitsAtOnce` 81-88 |
| FlashGateTest.kt | 50 | `FlashGateTest` 7-9, `starts` 10-16, `mostInOneSecond` 17-50 |
| FlashTimingTest.kt | 54 | `FlashTimingTest` 10-13, `plantSix` 14-17, `clearSix` 18-54 |
| FriendRevealTest.kt | 148 | `FriendRevealTest` 15-16, `distinctColors` 17-148 |
| FriendRulesTest.kt | 89 | `FriendRulesTest` 9-89 |
| FriendsPageTest.kt | 106 | `FriendsPageTest` 10-11, `openWith` 12-18, `paint` 19-21, `noTabsAndATitleTapDoNothingWithNoFriends` 22-30, `theFriendsTabSwitchesPageAndBack` 31-41, `aTabActsOnReleaseOverTheSameButtonOnly` 42-53, `thePageSurvivesClosingAndReopening` 54-64, `tappingAMetFriendHopsAndAnEmptyTileDoesNothing` 65-78, `aTileTapOnTheStickersPageStartsNoHop` 79-85, `twelveMetFriendsAllPaintAndEveryTileHops` 86-96, `theFriendsPagePaintsMoreThanTheBareBoard` 97-106 |
| GameAnimationPanelsTest.kt | 386 | `GameAnimationPanelsTest` 11-15, `settingsPanelSlidesOpenAndShutToItsEndValues` 16-42, `stickersPanelAnimatesIndependentlyOfSettings` 43-54, `settingsPanelStaysFullyOpenOnceOpen` 55-64, `stickersPanelStaysFullyOpenOnceOpen` 65-74, `aLongFrameGapMovesThePanelByNoMoreThanFiftyMs` 75-86, `firstFrameSeedsAnnouncedMaskFromWhatIsAlreadyEarned` 87-99, `anOpenPanelHoldsTheStickerFlyUntilItCloses` 100-116, `newlyEarnedStickerIsAnnouncedOnceAndNotAgain` 117-137, `twoStickersAtOnceFlyOneAfterTheOther` 138-156, `earningAllTwelveStickersSetsTheParty` 157-171, `fullDonutBoxCelebratesAndPulsesTheStar` 172-190, `starPulseSwellsThenReturnsToOne` 191-204, `boxStripFirstFrameAndResetsDoNotPop` 205-218, `boxStripPopsWhenASlotFillsAndWhenTheBoxFills` 219-243, `boxStripCountsBankedDonutsButNotTheSessionAlreadySaved` 244-255, `plantRow` 256-261, `droppingEndsExactlyAtDropMsAndAQuietBoardRaisesNoWarning` 262-273, `shuffleFlagSurvivesUntilItsFullDurationHasPassed` 274-284, `counterAtItsTargetDoesNotPulse` 285-292, `anEarnedMaskOfZeroIsKeptNotReloadedFromPrefs` 293-302, `confettiSpinStaysWithinFivePerFrameBothWays` 303-310, `powerUpPopsOnlyRecordCellsWhoseDelayHasPassed` 311-329, `powerUpClearDropsOneNewCellForEveryClearedCellIncludingBonusOnes` 330-357, `cascadeAfterALandStartsAComboLabelOverTheMatchedCells` 358-381, `cascadeWordsClimbWithTheCascadePassAndStopAtTheLast` 382-386 |
| GameAnimationTest.kt | 342 | `GameAnimationTest` 14-18, `plainChainWithoutGoldenCellsLeavesGoldenPoppedUnset` 19-29, `chainClearGoesPoppingDroppingIdleAndSettlesTheBoard` 30-62, `poppingHoldsForPopMsBeforeDropping` 63-77, `landedColumnsAreForgottenAfterLandMs` 78-89, `noMovesWarningShufflesAfterTheDelayAndLeavesAPlayableBoard` 90-109, `counterRollsTowardTheRealTotalWithoutOvershooting` 110-127, `counterFirstStepIsAQuarterOfTheGap` 128-137, `counterResetsWhenTheBoardCountDrops` 138-152, `counterRecordsADigitFlipWhenADigitChanges` 153-170, `milestoneStartsABannerWithConfetti` 171-180, `everySecondBigMomentAddsTheCheerLine` 181-195, `hundredDonutMilestoneGetsStarsAroundIt` 196-206, `secondMilestoneQueuesBehindTheFirstThenShowsAfterItsFirstPart` 207-222, `spawnParticlesFillsBothCannonsAtTheBoardBottom` 223-239, `confettiMovesThenExpiresWithTheBanner` 240-258, `hintAppearsOnlyAfterTheIdleDelay` 259-270, `touchingTheBoardClearsTheHintAndRestartsTheWait` 271-288, `hintsAreOffWhenTheDelayIsZero` 289-297, `hintStaysHiddenWhileTheTutorialRuns` 298-306, `hintWaitsForTheAnimationToFinish` 307-317, `easeOutQuintRunsZeroToOneAndOnlyRises` 318-331, `easeOutBackOvershootsThenSettlesAtOne` 332-342 |
| GameBoardMoreTest.kt | 448 | `GameBoardMoreTest` 18-25, `Fixed` 26-26, `nextBits` 27-27, `nextDouble` 28-30, `wholeBoard` 31-34, `tierBoard` 35-39, `defaultConstructorGivesAnEightByEightBoardWithoutMatches` 40-48, `aFiveTypeBoardNeverHoldsTheSixthType` 49-62, `goldenChanceZeroNeverSpawnsGoldenAndOneAlwaysDoes` 63-74, `explicitDefaultsGiveTheSameBoardAsOmittingThem` 75-86, `clearChainDropsSurvivorsAndResetsRows` 87-104, `survivorsKeepTheirGoldenFlagWhenTheyFall` 105-114, `spawnedCellsAreGoldenBelowTheChanceAndPlainAtOrAboveIt` 115-129, `goldenSpawnRateIsAboutTheChanceOverManyRefills` 130-143, `resolveOnceRefillsOnlyTheClearedCells` 144-155, `hintWalksADiagonalFromTheTopLeftCornerToTheBottomRightCorner` 156-161, `hintFindsAnAntiDiagonalThroughTheOtherCorners` 162-167, `hintDoesNotWrapAroundTheBoardEdges` 168-175, `hasValidMovesIsFalseWhenNoTypeHasThreeConnected` 176-180, `hasValidMovesIsTrueWithAnL` 181-186, `tierFor` 187-189, `chainOfThreeAndFourHaveNoPowerUp` 190-199, `chainOfFiveIsABombAroundTheMiddleCell` 200-208, `chainOfSixIsStillABombCentredOnIndexThree` 209-215, `chainOfSevenIsARowBlastOfTheRestOfTheRow` 216-222, `chainOfEightIsStillARowBlast` 223-229, `chainOfNineIsAColorBurstOfEveryOtherCellOfTheType` 230-237, `chainOfTenColorBurstLeavesOnlyTheLooseStrawberries` 238-244, `chainTypeComesFromTheFirstNonGoldenCellEvenWhenGoldenIsFirst` 245-255, `clearedCountsAreSplitPerTypeForChainAndBonusCells` 256-268, `goldenWildCellsCountUnderTheirOwnType` 269-278, `clearedCountsAddUpAcrossSeveralClears` 279-290, `settleClearsPlantedMatchesAndCountsThem` 291-299, `settleLeavesAQuietBoardAlone` 300-308, `settleGivesUpAfterFiftyPassesOnABoardThatAlwaysMatches` 309-319, `shuffleKeepsCountsAndSizeAndRewritesEveryCellPosition` 320-335, `snapshot` 336-337, `goodCleared` 338-340, `restoreRoundTripsTypesGoldenFlagsAndCounts` 341-353, `restoreRejectsWrongSizesAndChangesNothing` 354-365, `restoreRejectsUnknownCodesAndNegativeCountsAndChangesNothing` 366-382, `restoreAcceptsEveryTypeWithAndWithoutTheGoldenBit` 383-397, `repairOfAWholeBoardOfOneTypePicksTheFirstTypeThatDoesNotMakeThree` 398-406, `bombAtTheRightEdgeIsClippedToTheBoard` 407-416, `bombAtTheBottomEdgeIsClippedToTheBoard` 417-425, `hintFollowsAVerticalOnlyGroup` 426-431, `hintListsTheStartCellThenItsRowNeighbourThenTheCellBelow` 432-437, `hintCellsAreAlwaysInDragOrder` 438-448 |
| GameBoardRestoreTest.kt | 87 | `GameBoardRestoreTest` 11-12, `seeded` 13-21, `roundTrip` 22-38, `assertUnchanged` 39-68, `quietBoard` 69-87 |
| GameBoardTest.kt | 404 | `GameBoardTest` 21-29, `cells` 30-36, `sameSeedGivesTheSameBoard` 37-43, `differentSeedsGiveDifferentBoards` 44-53, `newBoardHasNoThreeInARow` 54-61, `newBoardHasNoGoldenCells` 62-67, `shuffledBoardHasNoThreeInARow` 68-76, `shuffleRemovesGoldenCells` 77-83, `resetZeroesCountsAndLeavesNoThreeInARow` 84-96, `peekRejectsChainsUnderThree` 97-104, `peekRejectsMixedTypes` 105-110, `peekAcceptsPlainChainOfThree` 111-120, `peekTreatsGoldenCellAsWild` 121-128, `peekTakesChainTypeFromFirstNonGoldenCell` 129-136, `peekAcceptsAnAllGoldenChain` 137-144, `peekStillRejectsMixedTypesAroundAGoldenCell` 145-154, `powerUpTierFollowsChainLength` 155-170, `bombClearsThreeByThreeAroundTheMiddleCell` 171-187, `bombIsClippedAtTheBoardEdge` 188-202, `rowBlastClearsTheRowOfTheMiddleCell` 203-218, `colorBurstClearsEveryOtherCellOfTheChainType` 219-239, `clearChainDropsSurvivorsToTheBottomInOrderAndKeepsGolden` 240-263, `clearChainCountsEachClearedCellOnce` 264-271, `clearChainCountsBonusCellsOnceEach` 272-290, `resolveOnceClearsAPlantedLine` 291-308, `resolveOnceDoesNothingWithoutAMatch` 309-321, `findHintFindsAConnectedTrioIncludingDiagonals` 322-332, `findHintIsEmptyWhenNoTypeHasThreeConnected` 333-343, `findHintOnSeededBoardsIsThreeConnectedSameTypeCells` 344-365, `findHintSeesAMoveThatNeedsAGoldenCell` 366-372, `adjacent` 373-376, `findHintIsAlwaysAChainPeekAcceptsOnGoldenBoards` 377-393, `everyBoardHasAValidMoveAfterConstructShuffleAndReset` 394-404 |
| GameInputPanelsTest.kt | 272 | `GameInputPanelsTest` 10-13, `gearOpensSettingsAndStarOpensStickers` 14-27, `panelButtonsWorkWhileTheBoardIsAnimating` 28-36, `boardTouchesAreSwallowedWhileAPanelIsOpen` 37-45, `stickersCloseButtonAndOutsideTapBothClose` 46-58, `tapInsideStickersPanelBodyKeepsItOpen` 59-69, `settingsSoundHintBuzzButtonsChangePrefs` 70-82, `settingsGridButtonRebuildsTheBoard` 83-95, `settingsGridSameSizeKeepsTheBoard` 96-104, `buttonActsOnReleaseOverTheSameButtonOnly` 105-123, `settingsDoneAndOutsideTapClose` 124-136, `newGameNeedsTwoTapsAndBanksTheSession` 137-156, `newGameConfirmTimesOut` 157-168, `tappingResetStickersDisarmsNewGame` 169-180, `resetStickersNeedsTwoTapsAndZeroesProgress` 181-199, `closingSettingsDisarmsBothConfirms` 200-209, `grownUpButtonsAreNotOnStevensPage` 210-221, `aWrongAnswerAsksANewQuestionAndStaysOnTheGate` 222-238, `backOnTheGateReturnsToStevensPage` 239-250, `settingsReopensOnStevensPage` 251-261, `theGateGetsAFreshQuestionEachVisit` 262-272 |
| GameInputTest.kt | 404 | `GameInputTest` 10-13, `dragOfThreeSameTypeClearsAndStartsPopping` 14-28, `batchedHistoricalPointsOfOneMoveAllJoinTheChain` 29-44, `diagonalStepsJoinTheChain` 45-51, `chainOfTwoDoesNotClearAndWobbles` 52-64, `singleTapWobblesWithoutALabel` 65-75, `aRareSingleTapGigglesWithAWordButTwoCellsNeverDo` 76-93, `wrongTypeNeighbourIsNotAddedAndShakes` 94-105, `nonAdjacentCellIsNotAdded` 106-112, `cellAlreadyInChainIsNotAddedTwice` 113-121, `draggingBackOverThePreviousCellRemovesTheLast` 122-135, `goldenCellIsWildInAChain` 136-143, `goldenChainGetsTheGoldenWordInGold` 144-153, `plainChainOfThreeGetsNoWord` 154-160, `touchesOutsideTheBoardMakeNoChain` 161-173, `dragLeavingTheBoardKeepsTheChain` 174-185, `cancelDropsTheChainWithoutClearing` 186-197, `aSecondFingerCannotHijackTheChain` 198-223, `liftingTheOwnerFingerReleasesTheChainEvenWithAnotherDown` 224-242, `strayMoveAndUpWithoutADownDoNothing` 243-253, `touchDuringAnimationIsHeldAndStartsTheDragWhenBoardIsIdle` 254-269, `liftingDuringAnimationDropsTheHeldFinger` 270-283, `backClosesAnOpenPanelFirstThenFallsThrough` 284-303, `startOverClearsDragAndAnimationState` 304-328, `aSixCellRingClosedOnItsStartClearsEveryCellOfThatType` 329-344, `aFiveCellClosedPathDoesNotLoop` 345-355, `whileLoopedOtherMovesAreIgnoredAndBackOntoTheLastCellUnloops` 356-371, `cancelLeavesTheChainUnlooped` 372-383, `aSecondFingerOnTheStartDoesNotCloseTheRing` 384-396, `startOverClearsALoopedChain` 397-404 |
| GameInputTestBase.kt | 97 |  |
| GameLayoutTest.kt | 350 | `GameLayoutTest` 14-24, `inside` 25-28, `surfaceRect` 29-29, `boardRect` 30-33, `overlap` 34-36, `forAll` 37-43, `pages` 44-48, `assertNoOverlap` 49-223, `realDensityPhoneStaysInsideAndKeepsTouchTargets` 224-238, `shortLandscapeShrinksTheSettingsPanelToFit` 239-308, `backSwipeIsExcludedBesideTheBoardOnApi29` 309-311, `backSwipeIsExcludedBesideTheBoardOnApi34` 312-314, `backSwipeIsNotExcludedBelowApi29` 315-316, `checkExclusion` 317-339, `snapshot` 340-350 |
| GameViewSmokeTest.kt | 22 | `GameViewSmokeTest` 10-11, `buildsLaysOutDrawsAndTakesATouch` 12-22 |
| HitZoneInputTest.kt | 60 | `HitZoneInputTest` 10-13, `cellAt` 14-17, `cuttingTheCornerOfAWrongDonutJoinsTheDiagonalWithoutAShake` 18-29, `aWobblyStraightDragStillJoinsTheStraightCells` 30-41, `goingBackStillUndoesAndJitterDoesNotRedoIt` 42-53, `aFingerThatSkipsACellStillAddsNothing` 54-60 |
| HitZoneTest.kt | 98 | `HitZoneTest` 7-11, `t` 12-15, `theFingerOnTheLastCellAimsAtItAndACentreAimsAtThatCell` 16-23, `withoutAnAimAStraightStepJoinsAtTheSharedEdgeAsBefore` 24-31, `theAimLeadsTheFingerIntoTheCellItIsHeadingFor` 32-38, `cuttingACornerReachesTheDiagonalNotTheStraightCellBesideIt` 39-48, `aWobblyStraightStepIsNotStolenByTheDiagonal` 49-58, `goingBackTowardThePreviousCellAimsAtIt` 59-65, `beyondTheThreeByThreeBlockAimsAtNothing` 66-72, `cellsOffTheBoardAreNeverAimedAt` 73-79, `eightByEightLeansHarderOnDiagonals` 80-89, `theAimChangesOnlyAfterAStepAndIsAUnitVector` 90-98 |
| LayoutMathTest.kt | 98 | `LayoutMathTest` 7-98 |
| LoopRulesTest.kt | 32 | `LoopRulesTest` 7-32 |
| ParentGateTest.kt | 71 | `ParentGateTest` 9-9, `gate` 10-71 |
| PartyMeterTest.kt | 79 | `PartyMeterTest` 9-79 |
| PartyMeterViewTest.kt | 85 | `PartyMeterViewTest` 12-13, `settle` 14-85 |
| PopTimingTest.kt | 168 | `PopTimingTest` 9-10, `resultOf` 11-13, `checkEveryCellHasAnEntry` 14-18, `plainChainPopsAllAtOnce` 19-29, `bombOriginSwellsFirstThenNeighboursPopTogether` 30-44, `rowBlastGrowsWithColumnDistanceAndMatchesBothSides` 45-61, `colorBurstBonusStartsAfterLeadAndRipplesOutward` 62-78, `ringStartsAtTheCellNearestItsCentreAndRipplesOutwardAfterTheLead` 79-94, `result` 95-168 |
| PraiseTest.kt | 40 | `PraiseTest` 6-7, `powerUpWordsComeFirstEvenWithAGoldenCell` 8-15, `aGoldenCellNamesItselfOnShortChains` 16-21, `plainChainsGetNiceAtFourAndNothingAtThree` 22-27, `theCheerComesOnEverySecondBigMoment` 28-34, `floatWordsStayShortEnoughForAnEightByEightCell` 35-40 |
| PrivacyPromiseTest.kt | 61 | `PrivacyPromiseTest` 16-17, `requestedPermissions` 18-61 |
| RefusedCueTest.kt | 50 | `RefusedCueTest` 17-18, `blank` 19-21, `ringAlpha` 22-50 |
| RingClearTest.kt | 53 | `RingClearTest` 8-10, `board` 11-17, `aRingClearsEveryOtherCellOfItsType` 18-26, `theSameChainUnloopedIsABomb` 27-30, `aRingWinsOverEveryLengthTier` 31-40, `aGoldenFirstCellTakesTheTypeOfTheRestOfTheRing` 41-48, `aMixedRingIsRejected` 49-53 |
| SavedBoardCodecTest.kt | 96 | `SavedBoardCodecTest` 11-12, `seeded` 13-49, `cellsText` 50-96 |
| SavedGameTest.kt | 330 | `SavedGameTest` 10-14, `codes` 15-17, `plant` 18-23, `planted` 24-29, `assertSameBoard` 30-330 |
| ScreenGallery.kt | 206 | `ScreenGallery` 24-27, `Size` 28-37, `save` 38-42, `clearPrefs` 43-46, `game` 47-56, `shoot` 57-66, `drag` 67-77, `home` 78-88, `render` 89-206 |
| SettingsIconsTest.kt | 62 | `SettingsIconsTest` 15-18, `pixels` 19-26, `icon` 27-51, `at` 52-62 |
| ShowSomeoneTest.kt | 117 | `ShowSomeoneTest` 12-13, `boxFull` 14-23, `aFullBoxShowsTheCard` 24-33, `aBigStickerShowsTheCardOnlyAfterTheFlyEnds` 34-50, `aSmallStickerDoesNotShowTheCard` 51-63, `theCardWaitsWhileAPanelIsOpen` 64-76, `aTapClosesTheCardAndStartsNoChain` 77-95, `backClosesTheCardFirst` 96-105, `theCardDrawsForBothKindsAndHoldsTheHintBack` 106-117 |
| StickerGiftTest.kt | 61 | `StickerGiftTest` 16-17, `near` 18-21, `shot` 22-28, `paper` 29-61 |
| StickerRulesTest.kt | 115 | `StickerRulesTest` 8-11, `earned` 12-13, `edge` 14-110, `onlyTheTopOfEachLadderIsBig` 111-115 |
| TestViews.kt | 85 | `TestViews` 17-17, `app` 18-20, `gameView` 21-34, `fixedRandom` 35-35, `nextBits` 36-36, `nextInt` 37-39, `canvas` 40-42, `now` 43-44, `advance` 45-47, `frames` 48-52, `cellCenter` 53-56, `touch` 57-63, `quietView` 64-70, `total` 71-73, `playHintChain` 74-85 |
| TwoTapConfirmTest.kt | 85 | `TwoTapConfirmTest` 8-85 |
| WindowThemeTest.kt | 60 | `WindowThemeTest` 20-21, `themeColor` 22-26, `themeFlag` 27-31, `checkWindow` 32-60 |
<!-- doc-map:end -->

## Sessions and workers

- One task per session. The state is in the README, `docs\overhaul-summary.md`, the commits and the memory files, so the next task starts in a new session.
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
