# Overhaul findings

Things found during the overhaul that were not fixed: unconfirmed reports, risky changes and anything the
rules kept off limits. Each phase adds its own section.

## Phase 2: bug hunt

Nine finders (one per area and bug class) sent in 31 reports. A second worker tried to disprove each one:
28 were confirmed (13 distinct bugs, fixed on `overhaul/phase-2`) and these 3 were refuted. They are real
code smells, but the app never creates the input that triggers them. Worth a look in phase 4 (security) or
phase 5 (clean-up).

| Id | Report | Why it was refuted |
|---|---|---|
| save-errors-1 | `SavedGame.applySaved`: saved cleared counts that sum past `Int.MAX_VALUE` wrap negative, and `coerceIn` then throws on every launch | Only `saveToPrefs` writes `saved_board`, from real play counts; no one clears 2 billion donuts. A hand-edited prefs file is needed. Cheap hardening: sum as `Long`, or have `GameBoard.restore` reject huge counts. |
| save-errors-2 | `SavedBoard.decode`: `size * size` overflows `Int` (65536² = 0), so an empty cell list passes the count check | `decode`'s only caller, `applySaved`, rejects `size != prefs.gridSize` before building a board. Cheap hardening: bound `size` (for example 3..16) in `decode`. |
| platform-errors-2 | `SoundEngine.playPCM`: if `write()` fails, `play()` throws and `release()` is skipped, leaking the `AudioTrack` | A `MODE_STATIC` write of exactly the buffer size does not fail in normal use, and `build()` throws before anything is held. Cheap hardening: `release()` in a `finally`. |

### Fixed in phase 2, needing an on-device check

These have no JVM test because the code is `GameView` or activity state (there is no Robolectric and no
instrumented tests). Check them on the emulator in phase 6.

- **New game / grid-size change mid-animation** (`startOver`, `rebuildBoard`): now `settleBoard()`, then
  `saveSession()`, then the shared `resetPlayState()`. Check: make a 7+ chain, open the gear at once and switch
  grid size or tap New game twice. Every column of the new board should draw, the counter should be 0 and the
  first milestone should fire again.
- **Auto-shuffle mid-drag** (`advanceAnimation` IDLE): waits while a chain is held.
- **Double tap on PLAY** (`MainActivity`): a `launching` flag, cleared in `onResume`, starts one GameActivity only.
- **Multi-touch wiring** in `onTouchEvent`: the `ChainPointer` helper is unit tested, the `MotionEvent` mapping is not.

## Phase 3: tests

Robolectric 4.16 (test-only dependency) now runs `GameView`, the activities and the drawing code on the JVM.
Coverage (lines 43% → 98%, branches 38% → 88%) was measured with `.\tools\coverage.ps1 -Quiet -Lines` and mutation-checked with `.\tools\mutate.ps1`.

**Bug found and fixed.** `advanceSettingsAnim`: when a panel was fully open (`settingsAnim == 1`,
`settingsTarget == 1`) the `target > anim` test failed, so the close step was applied and the panel dipped
every other frame (a one-frame flicker of about 3%). Same for the stickers panel. Both now hold still at the
target; `GameAnimationPanelsTest` and `GameInputPanelsTest` cover it.

**Phase-2 fixes now covered on the JVM:** double tap on PLAY, the multi-touch `MotionEvent` mapping,
`startOver` mid-animation and back-press order (`ActivityTest`, `GameInputTest`). The grid-size change
mid-animation and auto-shuffle mid-drag still want a look on the emulator in phase 6.

**Mutation results** (killed / total). Survivors were each read; only GameAnimation line 51 was a real gap, and it now has a test.

| Module | Killed | Survivors |
|---|---|---|
| GameAnimation | 26 / 29 (1 did not compile) | lines 36 and 42 change only sound or haptics; line 51 `&&`→`\|\|` is a real gap (a cascade with no golden cell would set `goldenPopped`; no test checks `goldenPopped` stays false after a cascade) |
| SavedGame, Prefs | 17 / 19 | lines 92 and 94 `>`→`>=` (equivalent: equal values give the same result) |
| GameLayout | 28 / 29 | line 74 (equivalent) |
| Draw helpers | 17 / 17 | none |
| GameBoard | 30 / 30 | none |
| Pure helpers | 30 / 32 | StickerRules line 42 and LayoutMath line 18 (equivalent); a ChainRules line-18 survivor got a killing test afterwards |
| Platform (activities, sound, haptics) | 8 / 9 | MainActivity line 11 (equivalent) |
| GameInput | not run | each run of `GameInputTest` takes minutes; mutating it is a job for a quieter phase |

**Still untested:** real audio output (`SoundEngine` is only run, not listened to), `RenderThread` posting a
real frame, `saveState` racing the render thread, pixel-level checks of the drawing (smoke tests only draw
and check nothing throws), `buildSprites` bitmaps. A worker also noted that `GameBoard.shuffle` does not keep
the multiset of types (it re-rolls them); that is by design, not a bug.

## Phase 4: security

Eight area reviewers (secrets, git history, platform, saved data, build and dependencies, scripts, release,
input). Each finding was checked by three skeptics told to refute it. No secret was ever committed.

**Confirmed and fixed (all low):**

| Finding | Fix |
|---|---|
| Saved cleared counts that sum past `Int.MAX_VALUE` wrap negative; `applySaved` then calls `coerceIn(0, negative)` and the game screen crashes on every launch | `GameBoard.restore` rejects a total over `Int.MAX_VALUE`; tests in `GameBoardMoreTest` and `SavedGameTest` |
| `gradle-wrapper.properties` had no `distributionSha256Sum`, so a tampered Gradle zip would run on the signing machine | Checksum added (same value from services.gradle.org and downloads.gradle.org) |
| The Play service-account key is ignored only as `play-service-account*.json`; under its downloaded name it could be committed | `.gitignore` ignores `/*.json` and `/keys/`; README step 2 says so |
| `mutate.ps1` restored a stale backup over newer edits after an interrupted run | Restores only when at most one line differs; otherwise keeps both, stamps the backup and exits 1 |
| A killed mutation run left the source mutated and nothing noticed | `check.ps1` fails at once while a backup exists |

**Refuted or optional (not changed):**

- Keystore backup names (`*.jks.old`, `*.p12`, `*.pem`) are not ignored. The keystores are PKCS12, so keytool makes no `.old` backup; optional hardening.
- `.claude/settings.json.disabled` is tracked and holds an add-all-commit-push hook. It is dormant. It is publishing automation, so it was left alone: **the user decides** whether to delete it.
- The `grid_size` pref is not clamped on read. Only root or adb can write a bad value; clamp it in phase 5.
- Gradle 9.2.1 is affected by CVE-2026-22816 and CVE-2026-22865 (fixed in 9.3.0). Not exploitable here, since only Maven Central and the Plugin Portal follow `google()`; upgrade or add a content filter in phase 5.
- `check.ps1` and `mutate.ps1` build a `cmd /c` string from `-Tests` and `-Task`. No outside input reaches them; fixed in phase 5 with a `ValidatePattern` on both parameters.

**Not verified:** a duplicate of the wrapper checksum finding (verification was capped at three per area).
Checked by hand and fine: only the launcher activity is exported, the only permission is `VIBRATE`, release
builds minify, signing credentials come from `local.properties`, `allowBackup=true` is wanted (keeps progress).

## Phase 5: clean-up

Six area finders (GameView state, drawing, rules, tests, build and scripts, cross-cutting), and each finding
was checked by a skeptic told to refute it. The safe items were applied in five batches, each built and tested.
The risky ones below were **not applied**. Each needs a pixel check, a dependency or release change, or a judgement call.

**Risky, logged only:**

| Id | Finding | Why it was not applied |
|---|---|---|
| draw-4 | `Color.argb(100/200/240, ...)` alpha is overwritten by `alpha = 255` (HudDraw 121-122, PanelsDraw 50-51, 280, 305) | The dead alpha may record a translucent border someone meant to draw. Phase 6 decides between translucent and opaque |
| draw-10 | `MainView` has its own copies of the DonutArt sheen and pentagon helpers | The copies differ in spec alpha, outline width and shadow, so merging them would change pixels |
| draw-11 | `MainView` play button duplicates `drawPrettyButton` and `buttonPressScale` (same formula as `easeOutQuint`) | Many constants differ, and tests call both names |
| draw-15 | The stickers and settings panels repeat the shell and title drawing | Sheen band, title outline, offsets and sizes all differ, and no test checks pixels |
| draw-22 | Outline-then-fill paths, tray backgrounds and outlined text are repeated | The cases are not equivalent: the NEW! baseline and the gear's extra circle |
| draw-23 | Ink colour `argb(a, 28, 12, 0)` is written about 45 times in 8 files | An `ink(alpha)` helper is fine, but it should be its own commit, made by a regex script |
| draw-24 | Home-screen copies of game art, two check badges, per-cell allocations, implicit `textAlign` | Pixel or hot-path changes; adding an explicit `CENTER` would only harden the code |
| rules-18 | "Chain type = first non-golden cell" is written three times | `ChainRules.accepts` handles an all-golden chain differently. Only the GameBoard/GameView copies were merged (`GameBoard.chainTypeOf`) |
| rules-24 | `appcompat` and `material` serve only as a base class and a theme parent | Changes the window theme and the release dependencies; needs an emulator check on API 26 and 34+ |
| build-1 | Gradle wrapper 9.2.1 has CVE-2026-22816 and CVE-2026-22865 (fixed in 9.3.x) | Build toolchain on the signing machine; the new `distributionSha256Sum` must come from services.gradle.org |
| build-7 | Material is only the theme parent; `values-v27` repeats 9 of the base theme's 10 lines | Changes the window theme of both activities; needs an emulator look |
| build-8 | AndroidX (core-ktx 1.12, appcompat 1.6.1), Material 1.11 and AGP are behind | Can change insets, back handling and R8 output; needs a release build and a device check |
| build-9 | Robolectric 4.16 → 4.17 | Changes the runtime of the whole suite, and `mutate.ps1 --offline` would fail until the new jars are cached |
| build-10 | Ten identical adaptive-icon XMLs in five `mipmap-*dpi` folders (minSdk 26 needs only `mipmap-anydpi`) | Changes launcher-icon packaging; needs a launcher check |
| build-11 | SDK and JDK discovery is repeated in `emu.ps1` and `release.ps1` instead of using `env.ps1` | `release.ps1` is release tooling, and `emu.ps1` needs an emulator to verify |
| build-12 | `proguard-rules.pro` holds template rules; the JavascriptInterface keep duplicates AGP's default | R8 input for the signed bundle |
| tests-8 | `GameBoardTest.powerUpTierFollowsChainLength` overlaps `GameBoardMoreTest`'s tier tests | It uses a different fixture (a 1x9 row) and covers an edge the other tests do not, so it was kept |

**Refuted:** about 30 reports. Most duplicated a safe item from another finder (unused imports, `grid_size`
clamp, AudioTrack release, count-and-gravity). The rest were not dead code (`core-ktx` is used, the
`gradle.properties` flags are needed) or would change behaviour.

**Still the user's call:** whether to delete the tracked `.claude/settings.json.disabled` add-commit-push hook.

## Phase 6: UI

**Method.** New `tools\gallery.ps1` renders 18 screen states on the JVM (Robolectric native graphics) at phone,
phone-landscape, small, tablet and tablet-portrait sizes. The emulator (`Pixel_8a`, `10_Tablet`, both at 4 GB)
covered insets and the phase-2 device checks. Auditors covered accessibility, performance, responsiveness,
states, copy and polish. A skeptic checked each report: 32 confirmed, 24 refuted.

**Fixed (six workers plus one fix by hand, each built and tested):**

| Area | Fix |
|---|---|
| Input | `ACTION_MOVE` replays the batched historical points, so a fast swipe no longer skips cells. A chain of 1 or 2 now gives a boop and a tick |
| Animation | While a panel is open, the counter, the sticker fly and the auto-shuffle wait. The cascade label is "Again!" then "Wow!". Sticker hints are easier to read: "Pop a ball", "Pop 20 in a game" |
| Effects | The no-moves banner is opaque and says "Shuffle time!" with a spinning arrow icon. Power-up rings are clipped to the board. The sticker name shows while it flies in. Reduced motion stops the tutorial pulse and the label scale |
| Board | No `Pair` or `Set` is allocated per cell per frame. Reduced motion caps the reset and chain flashes |
| Panels | **draw-4 decided:** rims and rings are opaque; only the pretty-button inner ring stays translucent. Titles are outlined. The confirm buttons say "New game?" / "Clear all?". Section labels are mixed case, 17 dp |
| Home, layout | The ball outline is opaque and its sheen is clipped. Render-thread pacing uses a nanosecond deadline. The chrome keeps its margin at the top and bottom edges |
| copy-1 (high) | The stickers panel uses a 6x2 grid on wide, short screens (landscape tablets) when that gives bigger tiles. Locked hints wrap to two lines instead of spilling out of the tile, and the "?" moves up and shrinks above a two-line hint |

**Deferred (low, not done):**

- a11y-4: a still cue for a refused donut when reduced motion is on.
- a11y-10: icons beside the settings section labels.
- states-4: silhouettes on locked stickers.
- responsive-7: the sticker box and stats look small on a tablet. The suggested fix was circular, since the panel size depends on them.

**Device notes.**

- Both activities are locked to portrait. With targetSdk 36, Android 16 ignores that lock on large screens, so the landscape tablet layout is real. The tablet AVD (API 35) letterboxes the app instead.
- A short splash and a black first frame show on a cold start.
- The phase-2 checks all passed on the emulator: grid change mid-animation, New game mid-animation, auto-shuffle mid-drag, double tap on PLAY, multi-touch.

## Phase 7: game balance

`.\tools\balance.ps1 -Quiet` plays 250 scenarios by script: 5 player models (kidShort, kidRandom, greedyLong,
hint, goldenHunter) x grid 6 and 8 x 25 seeds x 150 moves, and checks the chain rules on every move.
Each finding was put to two skeptics who tried to refute it.

**Fixed.**

- hint-order: `findHint` returned its three cells in flood-fill order, which was not always a path a finger
  can drag (1312 sim moves). The hint ring did not care and the tutorial sorted them, but any new caller
  would have been caught out. `findHint` now returns a drag order (`dragOrder`), with a test.

**Refuted (0 of 9 confirmed), logged for the record.**

- The hint never leads to power-ups or chain stickers: the hint is help when stuck, not a strategy.
- Session milestones stop at 500: "BOX FULL!" repeats every 120 lifetime donuts.
- Golden cells are too rare (0.3-0.5 on the board): they are meant to be a treat.
- The 6x6 board is thinner (12% one-option moves vs 1% on 8x8, shuffles 1.9 vs 0.2 per 100 moves): harmless,
  the shuffle is a celebrated event. `DEFAULT_GRID` stays 6.
- Row blast and colour burst are dead content for kid play (0.1 and 0 per 100 moves for kidRandom): the kid
  models are short-sighted on purpose; a real child learns long chains. A `kidLearner` model would settle it
  before any threshold change.

**Numbers worth keeping.** Kid models clear 4.5-5.7 donuts a move, greedy 14 on 8x8. Cascades do not run away
(0.94 passes per move at most, deepest 9). Cascade depth for kid play: pass 1 in 24% of moves, pass 2 in 7%,
pass 3 in 2.1%, pass 4 in 0.7%, pass 5+ in 0.3%.

**New content: cascade words.** Cascade passes said "Again!" then "Wow!" for every deeper pass, so a rare deep
cascade looked like an ordinary one. Three Sonnet generators each proposed a 5-word ladder (keep-and-extend,
cartoon sound effects, sight words) and two judges (an early-reading lens and a game-design lens) rejected
14 of the 15 words between them: Pop, Zing, Whoa, Zoom and Kapow (unreadable, or too close to the power-up
labels); Yes, Yay and Super (flat, or a step down); and Super!! (two "!"). Both judges kept the same five, so
`cascadeWord` now gives Again!, Wow!, Hooray!, Yippee!, Amazing! for passes 1 to 5+. Not yet seen on a device:
check that "Amazing!" fits over a 6x6 board edge cell.
