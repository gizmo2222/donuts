# Overhaul state

One phase per session, on branch `overhaul/phase-N`. Each session reads this file, does the first phase not
marked done or skipped, commits on its branch (no push, no merge, no PR) and updates this file.

| # | Phase | Status |
|---|---|---|
| 1 | Map | done |
| 2 | Bug hunt | done |
| 3 | Tests | todo |
| 4 | Security | todo |
| 5 | Clean-up | todo |
| 6 | UI | todo |
| 7 | Simulation or game balance | todo |
| 8 | Research | todo |
| 9 | Wrap-up | todo |

## Phase log

**1 Map (done, branch `overhaul/phase-1`).** CLAUDE.md now has what the project is, a PowerShell build/run/test
table, a corrected code map table (four files were missing) and a generated doc map with line ranges per symbol.
`code-map.ps1` gained `-Doc` and `-UpdateDoc`. `GameView.kt` is 499 lines, at the split limit: phase 5 should look.

**2 Bug hunt (done, branch `overhaul/phase-2`).** 9 finders (area x bug class) and 9 skeptics: 31 reports, 28
confirmed (13 distinct bugs), 3 refuted (listed in `docs/overhaul-findings.md`). All 13 are fixed; 9 have new JVM
tests (`ChainRules`, `ChainPointer`, `BannerQueue`, `LayoutMath` helpers, board-has-a-move and golden-hint tests).
Decision: the 4 `GameView`/activity-state fixes have no JVM test (adding Robolectric is a big dependency); their
emulator checks are in the findings file for phase 6. Phase 3: the new tests were not mutation-checked yet, and
`Prefs.stickersAnnounced` is a new prefs key (-1 = unset, seeded from the earned mask on first frame).
Phase 5: `GameView.kt` is now 515 lines (new fields), past the 500-line split rule.

## Which later phases apply

- **6 UI: applies.** Native Android UI drawn on a `SurfaceView` canvas, no web front end. "Phone, tablet and
  desktop widths" means the `Pixel_8a` and `10_Tablet` AVDs (`.\tools\emu.ps1 Start -Avd <name>`); there is no
  desktop build, so desktop is not covered. A 2 GB emulator kills the app; use one with more RAM.
- **7 Game balance: applies.** It is a match-3 game. `GameBoard.kt` is pure rules with no Android types, so
  200+ scenarios can run as a JVM unit test or harness under `app\src\test` without the emulator.
- **8 Research: applies, scoped down.** It is on Google Play (closed testing), but it is a gift for one child,
  not a commercial product. Compare kid-friendly match-3 and casual puzzle games for features that suit a
  5-8 year old; pricing matters little.

## Notes for later phases

- The git post-commit hook pushes every commit made on `master`. Never commit on `master` during the
  overhaul; commits on `overhaul/*` branches stay local.
- The check is `.\tools\check.ps1 -Test -Quiet` (build plus unit tests). Phase branches are made from the
  previous phase branch, since nothing is merged to `master` until the user does it.
- Phase 4: the keystores (`*.jks`) and `play-service-account.json` sit in the repo root but are git-ignored
  and untracked. `keys\` is neither tracked nor ignored. Do not move or change any of them.
- The doc map in `CLAUDE.md` is generated: `.\tools\code-map.ps1 -UpdateDoc CLAUDE.md`. Re-run it after any
  change to the sources (phase 9 must).
