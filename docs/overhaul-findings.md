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
