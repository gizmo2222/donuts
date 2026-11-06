# Next build

The list for the build after 2.0.3 (versionCode 35). All seven items are built in 2.0.4 (versionCode 36),
not yet uploaded. Delete them once 2.0.4 is in closed testing.
It comes from five notes and the design critique of 2026-11-04 (score 30/40, up from 28).
Decisions marked **Decided** were made by the owner; do not reopen them.

Work through the items in order, one per session. Find code with
`.\tools\code-map.ps1 -Match <regex>` and read only that line range. Check with
`.\tools\check.ps1 -Quiet`. Delete an item from this file when it ships.

## 1. Play resumes the board [x] built (6165ffe)

- **Problem:** Back with no panel open finishes `GameActivity`. The board and counter live only in
  the instance-state bundle (`SavedGame.kt`: `saveState`, `restoreState`), so Play starts a fresh
  board at 0. The edge columns start about 5dp from the screen edge and there is no gesture
  exclusion, so a chain started there may trigger the system back swipe (read from the code, not
  reproduced on a device).
- **Decided:** Play brings the old board and counter back. New game in Settings stays the way to
  start over.
- **Where:** `SavedGame.kt`, `Prefs.kt`, `GameActivity.kt` (the `OnBackPressedCallback`),
  `GameView.kt` (`saveSession`, `rebuildBoard`), `GameInput.kt` (`startOver`).
- **Done when:** leave with Back, press Play, and the same board and counter are there. Changing the
  board size or pressing New game still gives a fresh board. A saved board of the other size is
  not restored.

## 2. Settings: haptics, version, Reset stickers [x] built (6daddf4)

- **Haptics On/Off.** `prefs.hapticEnabled` already exists and every haptic call honours it. Only
  the two buttons are missing. Add a "Buzz" (or similar one-word) section built like Sound.
- **Version.** Show the version name small at the bottom of the panel. There is no `BuildConfig`;
  read it with `PackageManager.getPackageInfo`.
- **Reset stickers. Decided:** it lives in Settings next to New game, with the same tap-twice
  confirm (`resetConfirmMs`, `RESET_CONFIRM_MS`). It clears `lifetimeDonuts`, `highScore6x6`,
  `highScore8x8`, `bestChainLength`, `goldenPopped` and `sessionCount` in `Prefs`, and the
  in-memory `sessionSaved`, `earnedMask`, `boxesSeen` and `allStickersParty`. That also empties the
  donut box.
- **Where:** `GameLayout.kt` (`layoutSettingsPanel`, `placeSettings`; the panel is 540 design-dp
  tall today and shrinks through `settingsSc`), `PanelsDraw.kt` (`drawSettings`), `GameInput.kt`
  (`settingsHit`, `panelHitRect`, `handleSettingsTouch`), `GameView.kt` (new rect fields).
- **Done when:** haptics can be switched off and stay off after a restart; the version matches
  `versionName` in `app\build.gradle`; Reset stickers needs two taps and leaves 0 of 12 stickers
  and an empty box; the panel still fits on a small phone.

## 3. Power-up effects [x] built (4d38c20)

- **Problem:** chains of 5 or more clear extra donuts (`PowerUp.kt`: BOMB 5-6, ROW_BLAST 7-8,
  COLOR_BURST 9+). `handleUp` in `GameInput.kt` puts the chain and the bonus cells into `popCells`
  together, so they all vanish in the same instant with only "BOOM!", "WHOOSH!" or "KABOOM!" to
  explain it.
- **Effects:**
  - BOOM: the middle donut swells, a shockwave ring spreads, and the neighbours are shoved outward
    and pop as the ring reaches them.
  - WHOOSH: a streak shoots both ways along the row and donuts pop as it passes.
  - KABOOM: the matching donuts wiggle, then pop in a ripple outward from the chain.
  - Pops are staggered by distance from the chain's middle donut (`cells[cells.size / 2]`), each
    with a sound and a haptic tick that respects the haptics switch.
- **Where:** `GameAnimation.kt` (pop timing), `BoardDraw.kt` and `EffectsDraw.kt` (drawing),
  `GameTypes.kt` (any new shared type), `GameView.kt` (new fields).
- **Done when:** each of the three power-ups is plainly visible on the emulator, and a plain chain
  of 3 or 4 pops as it does today.

## 4. The donut box as the visible goal [x] built (12cef6e)

- **Problem:** at 12 of 12 stickers nothing new arrives. The donut box (12 slots, one per 10
  lifetime donuts, then a new box) is the never-ending goal, but it sits at the bottom of the
  sticker book with an unexplained "x5", and nothing on the board shows it filling.
- **Decided:** keep Reset stickers (item 2) and also give Steven something that keeps going.
- **Direction:** show the box, or its progress, on the board screen, for example in the empty band
  under the counter. Make the box count read as "boxes filled" without needing words.
- **Where:** `PanelsDraw.kt` (the box in the stickers panel), `GameLayout.kt`, `HudDraw.kt`,
  `GameAnimation.kt` (`checkStickers`, the BOX FULL! party).
- **Done when:** a child who has every sticker can see the next goal without opening a panel.

## 5. Layout polish [x] built (cf54a21)

- Sticker tiles have gutters of about 4px inside a panel with wide margins, and each tick badge
  overlaps the tile next to it (`layoutStickersPanel` in `GameLayout.kt`, `PanelsDraw.kt`).
- On a 20:9 phone about 18% of the screen is empty above the buttons and 20% below the counter;
  the home screen's bottom third is empty too (`GameLayout.kt`, `MainView.kt`). Item 4 may use
  the lower band.

## 6. Launcher icon [x] built (353e9a5)

- **Problem:** the donut's top is cut off by the icon mask. In
  `app\src\main\res\drawable\ic_launcher_foreground.xml` the donut is radius 34 centred at (54,48)
  on a 108 canvas, so its top is at y=14; the safe zone is a radius-33 circle centred at (54,54).
- **Fix:** wrap the paths in a `<group>` with `pivotX=54 pivotY=48`, scale about 0.92 and
  `translateY=6` (radius about 31 centred at 54,54).
- The icon shows a beach ball and the home screen shows a soccer ball. Pick one and use it in both.
- **Done when:** the whole donut shows under circle, squircle and rounded-square masks.

## 7. Final polish [x] built (b139a12, 2642f60)

- The power-up label colour follows chain length (six orange donuts gave a green "BOOM!"). Use the
  chain's flavour colour (`handleUp` in `GameInput.kt`).
- The chain count badge can sit on a neighbouring donut that is not in the chain (`BoardDraw.kt`).
- Then bump the version, add tester release notes and follow the release checklist in the README.

## Not checked in the critique

The tablet AVD (`10_Tablet`) and the 6x6 board were not looked at. The emulator's test data already
has every sticker, so locked stickers, BOX FULL! and the all-12 party were not seen on screen.
