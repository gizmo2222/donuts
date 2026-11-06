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

## Code map

All sources are in `app\src\main\java\com\donuts\game`. No file is over about 600 lines; keep it that way and split a file when it passes 500.

`GameView` is one class spread over several files. `GameView.kt` holds its state and the frame order; the other files hold `internal fun GameView.name()` extension functions for one area each.

| File | Holds |
|---|---|
| `GameView.kt` | Every field, the surface callbacks, `drawFrame` (the draw order), `onTouchEvent` |
| `GameTypes.kt` | Small value types: `AnimPhase`, `AnimCell`, `DigitFlip`, `Particle`, `FloatLabel` |
| `GameLayout.kt` | Where the board, HUD and both panels sit |
| `GameAnimation.kt` | Per-frame state updates: pops, drops, counter, hint, celebrations, stickers earned |
| `GameInput.kt` | Touch: panel hits, dragging and releasing a chain, New game |
| `BoardDraw.kt` | The board: background, cells, chain string, hint ring, flashes |
| `DonutArt.kt` | The six donuts and the ball: vector art and the sprite cache |
| `HudDraw.kt` | Backdrop, icon buttons, pretty buttons, the counter, text helpers |
| `EffectsDraw.kt` | Float labels, pings, confetti and banners, sticker fly, tutorial, no-moves warning |
| `PanelsDraw.kt` | The stickers panel (with the donut box) and the settings panel |
| `StickerArt.kt` | The twelve sticker pictures |
| `SavedGame.kt` | Keeping the board and counter across an activity restart (`saveState`, `restoreState`), and session banking (`saveSession`, `lifetimeDonuts`, `rebuildBoard`, prefs save) |
| `RenderThread.kt` | The render thread: lock a canvas, draw a frame, post it, pace to 60 fps |
| `TwoTapConfirm.kt` | The tap-twice confirm for New game and Reset stickers (pure, tested) |
| `PopTiming.kt` | When each cell of a power-up clear starts to pop (pure, unit tested) |
| `GameBoard.kt`, `GameCell.kt`, `ChainResult.kt`, `PowerUp.kt`, `DonutType.kt` | Game rules, no drawing |
| `MainView.kt`, `MainActivity.kt`, `GameActivity.kt` | Home screen and the two activities |
| `Prefs.kt`, `UiScale.kt`, `GameTheme.kt`, `SoundEngine.kt`, `HapticEngine.kt` | Saved data, dp sizing, palette, sound, haptics |

Rules for this layout:

1. Find code with `.\tools\code-map.ps1 -Match <regex>` (or `-File <name>`), then read only that line range. Never read a source file whole.
2. New state is an `internal` field in `GameView.kt`. A new function goes in the file for its area as `internal fun GameView.name()`. Do not add functions to `GameView.kt` itself unless they are overrides.
3. A type used by more than one file goes in `GameTypes.kt`, not nested in the class (extensions cannot see nested types by their short name).

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
