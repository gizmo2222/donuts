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
