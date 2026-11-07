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
| Line and branch coverage per file (`-Lines` lists uncovered lines) | `.\tools\coverage.ps1 -Quiet` |
| Mutation check one file against its tests | `.\tools\mutate.ps1 -File GameBoard.kt -Tests 'com.donuts.game.GameBoardTest'` |
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
| `GameInput.kt` | Touch: the touch dispatcher (`handleTouch`), panel hits, dragging and releasing a chain, New game |
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
| BoardDraw.kt | 367 | `drawResetFlash` 12-24, `drawChainFlash` 25-36, `drawBoardBackground` 37-67, `drawChainLine` 68-95, `drawCells` 96-105, `drawHintRing` 106-115, `nearMissOffset` 116-131, `drawCellsInner` 132-267, `drawGoldenOverlay` 268-300, `drawPoppingCells` 301-367 |
| ChainBadge.kt | 21 | `ChainBadge` 5-10, `spot` 11-21 |
| ChainPointer.kt | 21 | `ChainPointer` 4-8, `onDown` 9-11, `onPointerUp` 12-17, `reset` 18-21 |
| ChainResult.kt | 19 | `ChainResult` 11-18, `midCell` 19-19 |
| ChainRules.kt | 21 | `ChainRules` 4-9, `accepts` 10-21 |
| DonutArt.kt | 312 | `buildSprites` 9-33, `blit` 34-47, `drawPiece` 48-56, `drawBall` 57-62, `drawPieceVector` 63-77, `ringBase` 78-91, `drawHole` 92-102, `drawDrippyRing` 103-129, `drawStripedRing` 130-147, `drawJellyBun` 148-162, `buildScallop` 163-173, `drawFlowerRing` 174-198, `drawHalfDipRing` 199-228, `drawSquareDonut` 229-258, `drawBallVector` 259-274, `drawPentagon` 275-293, `addSheen` 294-312 |
| DonutType.kt | 18 | `DonutType` 7-18 |
| EffectsDraw.kt | 402 | `drawFloatLabels` 12-50, `drawCenterPing` 51-76, `drawCelebration` 77-149, `drawStickerFly` 150-196, `drawTutorial` 197-263, `chainOrder` 264-266, `adj` 267-276, `drawFinger` 277-297, `drawNoMovesWarning` 298-349, `drawPowerUpFx` 350-362, `ring` 363-402 |
| GameActivity.kt | 57 | `GameActivity` 8-11, `onCreate` 12-34, `handleOnBackPressed` 35-43, `onSaveInstanceState` 44-48, `onPause` 49-57 |
| GameAnimation.kt | 323 | `updateHint` 12-25, `advancePowerPops` 26-37, `advanceAnimation` 38-155, `addFloatLabelAt` 156-164, `advanceSettingsAnim` 165-173, `slideToward` 174-185, `easeOutQuint` 186-191, `settingsSlideY` 192-192, `stickersSlideY` 193-197, `advanceCounter` 198-230, `celebrate` 231-234, `startCelebration` 235-243, `spawnParticles` 244-267, `easeOutBack` 268-273, `checkStickers` 274-304, `starPulse` 305-312, `advanceBoxStrip` 313-323 |
| GameBoard.kt | 342 | `GameBoard` 12-47, `refill` 48-66, `randomType` 67-69, `safeRandom` 70-91, `spawnCell` 92-99, `findMatches` 100-140, `resolveOnce` 141-156, `findHint` 157-171, `floodFill` 172-199, `chainTypeOf` 200-217, `peekChainClear` 218-258, `countCleared` 259-263, `collapse` 264-281, `clearChain` 282-289, `hasValidMoves` 290-296, `shuffle` 297-300, `reset` 301-310, `settle` 311-315, `cellCodes` 316-321, `clearedCounts` 322-327, `restore` 328-342 |
| GameCell.kt | 18 | `GameCell` 13-18 |
| GameInput.kt | 306 | `settingsHit` 13-25, `stickersHit` 26-32, `panelHitRect` 33-43, `handleSettingsTap` 44-68, `resetStickers` 69-77, `closeSettings` 78-80, `startOver` 81-96, `startHeldDrag` 97-103, `handleDown` 104-115, `handleMove` 116-153, `handleUp` 154-217, `handleTouch` 218-289, `cellCol` 290-291, `cellRow` 292-293, `inBounds` 294-295, `adjacent8` 296-299, `onBackPressed` 300-306 |
| GameLayout.kt | 152 | `computeLayout` 10-61, `layoutSettingsPanel` 62-72, `placeSettings` 73-85, `row` 86-107, `layoutStickersPanel` 108-143, `excludeBackSwipe` 144-152 |
| GameTheme.kt | 16 | `GameTheme` 6-16 |
| GameTypes.kt | 41 | `AnimPhase` 7-8, `AnimCell` 9-15, `PowerUpFx` 16-20, `DigitFlip` 21-22, `Particle` 23-29, `FloatLabel` 30-35, `BoxStrip` 36-41 |
| GameView.kt | 431 | `GameView` 14-358, `surfaceCreated` 359-363, `surfaceChanged` 364-367, `surfaceDestroyed` 368-387, `drawFrame` 388-429, `onTouchEvent` 430-431 |
| HapticEngine.kt | 34 | `HapticEngine` 10-21, `tick` 22-24, `pop` 25-27, `milestone` 28-31, `shuffle` 32-34 |
| HudDraw.kt | 225 | `drawHUD` 12-16, `drawBackdrop` 17-30, `drawIconButton` 31-38, `drawStarIcon` 39-55, `drawGearIcon` 56-73, `pressScaleFor` 74-77, `buttonPressScale` 78-84, `drawPrettyButton` 85-126, `drawCounter` 127-194, `drawLabel` 195-210, `drawOutlinedText` 211-221, `drawBoxStrip` 222-225 |
| LayoutMath.kt | 32 | `LayoutMath` 7-8, `spareShare` 9-12, `gutter` 13-15, `cellIndex` 16-19, `boardSide` 20-22, `panelLocalY` 23-25, `fitSide` 26-32 |
| MainActivity.kt | 28 | `MainActivity` 8-12, `onResume` 13-17, `onCreate` 18-28 |
| MainView.kt | 366 | `MainView` 13-66, `onSizeChanged` 67-72, `relayout` 73-94, `onDraw` 95-116, `drawDotGrid` 117-131, `drawLogo` 132-150, `drawDonut` 151-190, `drawSoccerBall` 191-229, `regularPolygon` 230-244, `addSheen` 245-261, `drawTitle` 262-302, `buttonPressScale` 303-309, `drawPlayButton` 310-350, `onTouchEvent` 351-366 |
| PanelsDraw.kt | 443 | `stickerStats` 13-21, `drawStickersPanel` 22-183, `drawDonutBox` 184-251, `popScale` 252-256, `drawClosedBox` 257-276, `drawSettings` 277-344, `drawConfirmButton` 345-364, `drawCheckBadge` 365-375, `drawSectionLabel` 376-406, `drawSettingsBtn` 407-443 |
| PopTiming.kt | 40 | `PopTiming` 8-11, `leadMs` 12-19, `origin` 20-22, `delays` 23-37, `totalMs` 38-40 |
| PowerUp.kt | 11 | `PowerUp` 11-11 |
| Prefs.kt | 76 | `Prefs` 5-43, `highScore` 44-44, `setHighScore` 45-76 |
| RenderThread.kt | 60 | `RenderThread` 9-12, `lockFrame` 13-27, `run` 28-60 |
| SavedBoard.kt | 30 | `SavedBoard` 4-5, `encode` 6-12, `decode` 13-24, `ints` 25-30 |
| SavedGame.kt | 133 | `settleBoard` 19-30, `saveState` 31-45, `restoreState` 46-52, `applySaved` 53-69, `saveToPrefs` 70-78, `restoreFromPrefs` 79-84, `saveSession` 85-92, `lifetimeDonuts` 93-95, `displayedLifetime` 96-97, `rebuildBoard` 98-111, `resetPlayState` 112-126, `clearMotion` 127-133 |
| SoundEngine.kt | 125 | `SoundEngine` 12-15, `waveSample` 16-22, `playConnectBlip` 23-28, `playPopClear` 29-33, `playBoop` 34-38, `playDropLand` 39-43, `playMilestone` 44-54, `playShuffle` 55-62, `playTone` 63-66, `playToneBlocking` 67-77, `playSweep` 78-93, `playPCM` 94-125 |
| StickerArt.kt | 106 | `drawStickerArt` 12-38, `drawChainArt` 39-50, `drawBatchArt` 51-60, `drawCrown` 61-79, `drawMiniBoard` 80-92, `drawHeart` 93-106 |
| StickerRules.kt | 65 | `StickerStats` 6-14, `StickerRules` 15-22, `isEarned` 23-42, `earnedMask` 43-49, `toAnnounce` 50-52, `boxesFilled` 53-55, `slotsFilled` 56-58, `slotJustFilled` 59-62, `boxJustFilled` 63-65 |
| TwoTapConfirm.kt | 21 | `TwoTapConfirm` 4-7, `tap` 8-13, `isArmed` 14-16, `progress` 17-19, `cancel` 20-21 |
| UiScale.kt | 26 | `UiScale` 14-21, `update` 22-26 |

### Tests

| File | Lines | Symbols (line ranges) |
|---|---|---|
| ActivityTest.kt | 233 | `ActivityTest` 17-18, `contentChild` 19-21, `gameViewOf` 22-23, `startGame` 24-26, `laidOutMain` 27-36, `dispatch` 37-42, `gameActivityShowsAGameView` 43-49, `coldStartCountsAVisitAndStartsFresh` 50-56, `recreateWithSavedStateKeepsTheBoardAndTheVisitCount` 57-77, `coldStartRestoresTheBoardKeptByOnPause` 78-94, `onPauseBanksDonutsAndWritesTheBoardToPrefs` 95-112, `backPressClosesAnOpenSettingsPanelFirst` 113-122, `backPressClosesAnOpenStickersPanelFirst` 123-132, `backPressWithNoPanelFinishesTheActivity` 133-139, `secondBackPressAfterClosingAPanelFinishes` 140-150, `mainActivityStartsGameActivityWhenPlayIsTapped` 151-157, `send` 158-173, `secondQuickPlayTapStartsOnlyOneGameUntilResume` 174-177, `tap` 178-192, `mainActivityResumeAndPauseDoNotThrow` 193-197, `surfaceDestroyedBanksLifetimeDonutsOnce` 198-211, `surfaceDestroyedStopsAndClearsTheRenderThread` 212-224, `surfaceChangedLaysTheBoardOut` 225-233 |
| BannerQueueTest.kt | 42 | `BannerQueueTest` 7-42 |
| BoardFixtures.kt | 26 | `BoardFixtures` 6-6, `typeOf` 7-12, `boardOf` 13-22, `rowOf` 23-24, `letters` 25-26 |
| ChainBadgeTest.kt | 76 | `ChainBadgeTest` 8-76 |
| ChainPointerTest.kt | 31 | `ChainPointerTest` 8-31 |
| ChainRulesTest.kt | 47 | `ChainRulesTest` 9-47 |
| DrawSmokeTest.kt | 390 | `DrawSmokeTest` 22-23, `Scene` 24-26, `frames` 27-29, `scene` 30-32, `distinctColors` 33-38, `assertPainted` 39-42, `snake` 43-49, `dragSnake` 50-57, `release` 58-99, `powerUpCase` 100-200, `painted` 201-223, `tap` 224-390 |
| EnginesTest.kt | 87 | `EnginesTest` 12-15, `waveSample` 16-22, `waveSampleIsASineAtQuarterPoints` 23-31, `waveSampleStaysInRangeOverManyPhases` 32-36, `everySoundMethodRunsWithoutThrowing` 37-43, `playingWithSoundAndHapticsOffAndOnWorks` 44-60, `hapticMethodsDoNotThrow` 61-68, `renderThreadStartsAndStopsWithoutAValidSurface` 69-79, `renderThreadStoppedBeforeItRunsExitsAtOnce` 80-87 |
| GameAnimationPanelsTest.kt | 363 | `GameAnimationPanelsTest` 11-15, `settingsPanelSlidesOpenAndShutToItsEndValues` 16-42, `stickersPanelAnimatesIndependentlyOfSettings` 43-54, `settingsPanelStaysFullyOpenOnceOpen` 55-64, `stickersPanelStaysFullyOpenOnceOpen` 65-74, `aLongFrameGapMovesThePanelByNoMoreThanFiftyMs` 75-86, `firstFrameSeedsAnnouncedMaskFromWhatIsAlreadyEarned` 87-99, `newlyEarnedStickerIsAnnouncedOnceAndNotAgain` 100-120, `twoStickersAtOnceFlyOneAfterTheOther` 121-139, `earningAllTwelveStickersSetsTheParty` 140-154, `fullDonutBoxCelebratesAndPulsesTheStar` 155-173, `starPulseSwellsThenReturnsToOne` 174-187, `boxStripFirstFrameAndResetsDoNotPop` 188-201, `boxStripPopsWhenASlotFillsAndWhenTheBoxFills` 202-226, `boxStripCountsBankedDonutsButNotTheSessionAlreadySaved` 227-238, `plantRow` 239-244, `droppingEndsExactlyAtDropMsAndAQuietBoardRaisesNoWarning` 245-256, `shuffleFlagSurvivesUntilItsFullDurationHasPassed` 257-267, `counterAtItsTargetDoesNotPulse` 268-275, `anEarnedMaskOfZeroIsKeptNotReloadedFromPrefs` 276-285, `confettiSpinStaysWithinFivePerFrameBothWays` 286-293, `powerUpPopsOnlyRecordCellsWhoseDelayHasPassed` 294-312, `powerUpClearDropsOneNewCellForEveryClearedCellIncludingBonusOnes` 313-340, `cascadeAfterALandStartsAComboLabelOverTheMatchedCells` 341-363 |
| GameAnimationTest.kt | 327 | `GameAnimationTest` 14-18, `plainChainWithoutGoldenCellsLeavesGoldenPoppedUnset` 19-29, `chainClearGoesPoppingDroppingIdleAndSettlesTheBoard` 30-62, `poppingHoldsForPopMsBeforeDropping` 63-77, `landedColumnsAreForgottenAfterLandMs` 78-89, `noMovesWarningShufflesAfterTheDelayAndLeavesAPlayableBoard` 90-109, `counterRollsTowardTheRealTotalWithoutOvershooting` 110-127, `counterFirstStepIsAQuarterOfTheGap` 128-137, `counterResetsWhenTheBoardCountDrops` 138-152, `counterRecordsADigitFlipWhenADigitChanges` 153-170, `milestoneStartsABannerWithConfetti` 171-180, `hundredDonutMilestoneGetsStarsAroundIt` 181-191, `secondMilestoneQueuesBehindTheFirstThenShowsAfterItsFirstPart` 192-207, `spawnParticlesFillsBothCannonsAtTheBoardBottom` 208-224, `confettiMovesThenExpiresWithTheBanner` 225-243, `hintAppearsOnlyAfterTheIdleDelay` 244-255, `touchingTheBoardClearsTheHintAndRestartsTheWait` 256-273, `hintsAreOffWhenTheDelayIsZero` 274-282, `hintStaysHiddenWhileTheTutorialRuns` 283-291, `hintWaitsForTheAnimationToFinish` 292-302, `easeOutQuintRunsZeroToOneAndOnlyRises` 303-316, `easeOutBackOvershootsThenSettlesAtOne` 317-327 |
| GameBoardMoreTest.kt | 400 | `GameBoardMoreTest` 18-25, `Fixed` 26-26, `nextBits` 27-27, `nextDouble` 28-30, `wholeBoard` 31-34, `tierBoard` 35-39, `defaultConstructorGivesAnEightByEightBoardWithoutMatches` 40-50, `clearChainDropsSurvivorsAndResetsRows` 51-68, `survivorsKeepTheirGoldenFlagWhenTheyFall` 69-78, `spawnedCellsAreGoldenBelowTheChanceAndPlainAtOrAboveIt` 79-93, `goldenSpawnRateIsAboutTheChanceOverManyRefills` 94-107, `resolveOnceRefillsOnlyTheClearedCells` 108-119, `hintWalksADiagonalFromTheTopLeftCornerToTheBottomRightCorner` 120-125, `hintFindsAnAntiDiagonalThroughTheOtherCorners` 126-131, `hintDoesNotWrapAroundTheBoardEdges` 132-139, `hasValidMovesIsFalseWhenNoTypeHasThreeConnected` 140-144, `hasValidMovesIsTrueWithAnL` 145-150, `tierFor` 151-153, `chainOfThreeAndFourHaveNoPowerUp` 154-163, `chainOfFiveIsABombAroundTheMiddleCell` 164-172, `chainOfSixIsStillABombCentredOnIndexThree` 173-179, `chainOfSevenIsARowBlastOfTheRestOfTheRow` 180-186, `chainOfEightIsStillARowBlast` 187-193, `chainOfNineIsAColorBurstOfEveryOtherCellOfTheType` 194-201, `chainOfTenColorBurstLeavesOnlyTheLooseStrawberries` 202-208, `chainTypeComesFromTheFirstNonGoldenCellEvenWhenGoldenIsFirst` 209-219, `clearedCountsAreSplitPerTypeForChainAndBonusCells` 220-232, `goldenWildCellsCountUnderTheirOwnType` 233-242, `clearedCountsAddUpAcrossSeveralClears` 243-254, `settleClearsPlantedMatchesAndCountsThem` 255-263, `settleLeavesAQuietBoardAlone` 264-272, `settleGivesUpAfterFiftyPassesOnABoardThatAlwaysMatches` 273-283, `shuffleKeepsCountsAndSizeAndRewritesEveryCellPosition` 284-299, `snapshot` 300-301, `goodCleared` 302-304, `restoreRoundTripsTypesGoldenFlagsAndCounts` 305-317, `restoreRejectsWrongSizesAndChangesNothing` 318-329, `restoreRejectsUnknownCodesAndNegativeCountsAndChangesNothing` 330-346, `restoreAcceptsEveryTypeWithAndWithoutTheGoldenBit` 347-361, `repairOfAWholeBoardOfOneTypePicksTheFirstTypeThatDoesNotMakeThree` 362-370, `bombAtTheRightEdgeIsClippedToTheBoard` 371-380, `bombAtTheBottomEdgeIsClippedToTheBoard` 381-389, `hintFollowsAVerticalOnlyGroup` 390-395, `hintListsTheStartCellThenItsRowNeighbourThenTheCellBelow` 396-400 |
| GameBoardRestoreTest.kt | 87 | `GameBoardRestoreTest` 11-12, `seeded` 13-21, `roundTrip` 22-38, `assertUnchanged` 39-68, `quietBoard` 69-87 |
| GameBoardTest.kt | 404 | `GameBoardTest` 21-29, `cells` 30-36, `sameSeedGivesTheSameBoard` 37-43, `differentSeedsGiveDifferentBoards` 44-53, `newBoardHasNoThreeInARow` 54-61, `newBoardHasNoGoldenCells` 62-67, `shuffledBoardHasNoThreeInARow` 68-76, `shuffleRemovesGoldenCells` 77-83, `resetZeroesCountsAndLeavesNoThreeInARow` 84-96, `peekRejectsChainsUnderThree` 97-104, `peekRejectsMixedTypes` 105-110, `peekAcceptsPlainChainOfThree` 111-120, `peekTreatsGoldenCellAsWild` 121-128, `peekTakesChainTypeFromFirstNonGoldenCell` 129-136, `peekAcceptsAnAllGoldenChain` 137-144, `peekStillRejectsMixedTypesAroundAGoldenCell` 145-154, `powerUpTierFollowsChainLength` 155-170, `bombClearsThreeByThreeAroundTheMiddleCell` 171-187, `bombIsClippedAtTheBoardEdge` 188-202, `rowBlastClearsTheRowOfTheMiddleCell` 203-218, `colorBurstClearsEveryOtherCellOfTheChainType` 219-239, `clearChainDropsSurvivorsToTheBottomInOrderAndKeepsGolden` 240-263, `clearChainCountsEachClearedCellOnce` 264-271, `clearChainCountsBonusCellsOnceEach` 272-290, `resolveOnceClearsAPlantedLine` 291-308, `resolveOnceDoesNothingWithoutAMatch` 309-321, `findHintFindsAConnectedTrioIncludingDiagonals` 322-332, `findHintIsEmptyWhenNoTypeHasThreeConnected` 333-343, `findHintOnSeededBoardsIsThreeConnectedSameTypeCells` 344-365, `findHintSeesAMoveThatNeedsAGoldenCell` 366-372, `adjacent` 373-376, `findHintIsAlwaysAChainPeekAcceptsOnGoldenBoards` 377-393, `everyBoardHasAValidMoveAfterConstructShuffleAndReset` 394-404 |
| GameInputPanelsTest.kt | 208 | `GameInputPanelsTest` 10-13, `gearOpensSettingsAndStarOpensStickers` 14-27, `panelButtonsWorkWhileTheBoardIsAnimating` 28-36, `boardTouchesAreSwallowedWhileAPanelIsOpen` 37-45, `stickersCloseButtonAndOutsideTapBothClose` 46-58, `tapInsideStickersPanelBodyKeepsItOpen` 59-69, `settingsSoundHintBuzzButtonsChangePrefs` 70-82, `settingsGridButtonRebuildsTheBoard` 83-95, `settingsGridSameSizeKeepsTheBoard` 96-104, `buttonActsOnReleaseOverTheSameButtonOnly` 105-123, `settingsDoneAndOutsideTapClose` 124-136, `newGameNeedsTwoTapsAndBanksTheSession` 137-156, `newGameConfirmTimesOut` 157-168, `tappingResetStickersDisarmsNewGame` 169-180, `resetStickersNeedsTwoTapsAndZeroesProgress` 181-199, `closingSettingsDisarmsBothConfirms` 200-208 |
| GameInputTest.kt | 261 | `GameInputTest` 10-13, `dragOfThreeSameTypeClearsAndStartsPopping` 14-28, `diagonalStepsJoinTheChain` 29-35, `chainOfTwoDoesNotClearAndWobbles` 36-48, `singleTapWobblesWithoutALabel` 49-57, `wrongTypeNeighbourIsNotAddedAndShakes` 58-69, `nonAdjacentCellIsNotAdded` 70-76, `cellAlreadyInChainIsNotAddedTwice` 77-85, `draggingBackOverThePreviousCellRemovesTheLast` 86-99, `goldenCellIsWildInAChain` 100-107, `touchesOutsideTheBoardMakeNoChain` 108-120, `dragLeavingTheBoardKeepsTheChain` 121-132, `cancelDropsTheChainWithoutClearing` 133-144, `aSecondFingerCannotHijackTheChain` 145-170, `liftingTheOwnerFingerReleasesTheChainEvenWithAnotherDown` 171-189, `strayMoveAndUpWithoutADownDoNothing` 190-200, `touchDuringAnimationIsHeldAndStartsTheDragWhenBoardIsIdle` 201-216, `liftingDuringAnimationDropsTheHeldFinger` 217-230, `backClosesAnOpenPanelFirstThenFallsThrough` 231-250, `startOverClearsDragAndAnimationState` 251-261 |
| GameInputTestBase.kt | 86 |  |
| GameLayoutTest.kt | 264 | `GameLayoutTest` 14-24, `inside` 25-28, `surfaceRect` 29-29, `boardRect` 30-33, `overlap` 34-36, `forAll` 37-42, `assertNoOverlap` 43-151, `realDensityPhoneStaysInsideAndKeepsTouchTargets` 152-164, `shortLandscapeShrinksTheSettingsPanelToFit` 165-223, `backSwipeIsExcludedBesideTheBoardOnApi29` 224-226, `backSwipeIsExcludedBesideTheBoardOnApi34` 227-229, `backSwipeIsNotExcludedBelowApi29` 230-231, `checkExclusion` 232-254, `snapshot` 255-264 |
| GameViewSmokeTest.kt | 22 | `GameViewSmokeTest` 10-11, `buildsLaysOutDrawsAndTakesATouch` 12-22 |
| LayoutMathTest.kt | 90 | `LayoutMathTest` 7-90 |
| PopTimingTest.kt | 151 | `PopTimingTest` 9-10, `resultOf` 11-13, `checkEveryCellHasAnEntry` 14-18, `plainChainPopsAllAtOnce` 19-29, `bombOriginSwellsFirstThenNeighboursPopTogether` 30-44, `rowBlastGrowsWithColumnDistanceAndMatchesBothSides` 45-61, `colorBurstBonusStartsAfterLeadAndRipplesOutward` 62-77, `result` 78-151 |
| SavedBoardCodecTest.kt | 96 | `SavedBoardCodecTest` 11-12, `seeded` 13-49, `cellsText` 50-96 |
| SavedGameTest.kt | 330 | `SavedGameTest` 10-14, `codes` 15-17, `plant` 18-23, `planted` 24-29, `assertSameBoard` 30-330 |
| StickerRulesTest.kt | 109 | `StickerRulesTest` 8-11, `earned` 12-13, `edge` 14-109 |
| TestViews.kt | 78 | `TestViews` 17-17, `app` 18-20, `gameView` 21-32, `canvas` 33-35, `now` 36-37, `advance` 38-40, `frames` 41-45, `cellCenter` 46-49, `touch` 50-56, `quietView` 57-63, `total` 64-66, `playHintChain` 67-78 |
| TwoTapConfirmTest.kt | 85 | `TwoTapConfirmTest` 8-85 |
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
