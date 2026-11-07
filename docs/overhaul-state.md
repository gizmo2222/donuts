# Overhaul state

One phase per session, on branch `overhaul/phase-N`. Each session reads this file, does the first phase not
marked done or skipped, commits on its branch (no push, no merge, no PR) and updates this file.

| # | Phase | Status |
|---|---|---|
| 1 | Map | done |
| 2 | Bug hunt | done |
| 3 | Tests | done |
| 4 | Security | done |
| 5 | Clean-up | done |
| 6 | UI | done |
| 7 | Simulation or game balance | done |
| 8 | Research | done |
| 9 | Wrap-up | done |

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

**3 Tests (done, branch `overhaul/phase-3`).** Robolectric 4.16 added as a test-only dependency; 15 workers wrote
tests per module (suite now 21 classes). Coverage went from lines 43% / branches 38% to lines 98% / branches 88%; mutation
check killed 156 of 165 mutants, the survivors are logged in `docs/overhaul-findings.md` (one real gap, now tested).
New scripts: `coverage.ps1`, `mutate.ps1`, `env.ps1`, `check.ps1 -Tests`. Found and fixed a panel flicker in
`advanceSettingsAnim`. Phase 5: `GameInput` is still not mutation-checked; `GameView.kt` is still 515 lines.

**4 Security (done, branch `overhaul/phase-4`).** 8 area reviewers and 3-skeptic verification: 5 low findings
confirmed and fixed (saved-board count overflow crash, wrapper checksum, root JSON and `keys\` ignored, two
`mutate.ps1` backup hazards); 5 refuted or optional, logged in `docs/overhaul-findings.md`. No secrets in git history.
Phase 5: Gradle 9.2.1 has two CVEs fixed in 9.3.0 (not exploitable here), clamp the `grid_size` pref, add
`ValidatePattern` to `-Tests`/`-Task`. The user must decide on the tracked `.claude/settings.json.disabled` push hook.

**5 Clean-up (done, branch `overhaul/phase-5`).** 6 finders and a skeptic per finding: 43 safe items applied in 5 built-and-tested batches (unused imports and resources, dead fields, `onTouchEvent` moved to `GameInput.handleTouch`, `GameBoard.chainTypeOf`/`totalCleared`, `grid_size` clamp, `ValidatePattern` in `check.ps1`/`mutate.ps1`, shared test fixtures, `EnginesTest`); 45 files, net -89 lines, `GameView.kt` now 431 lines.
17 risky items are logged, not applied, in `docs/overhaul-findings.md`: the Gradle 9.3 CVE upgrade, AndroidX/Material/AGP/Robolectric bumps, dropping appcompat/material, the icon XMLs, proguard, and pixel-changing art merges.
Phase 6: check by screenshot the settings and stickers taps (dispatch is now `handleSettingsTap`), the panel drawing (shared scratch `RectF`) and the home ball (`ringBase`), and decide the draw-4 border alpha.

**6 UI (done, branch `overhaul/phase-6`).** New `tools\gallery.ps1` renders 18 states at five sizes on the JVM, and the emulator checks passed. 32 of 56 audit findings were confirmed, and all but 4 low ones are fixed (`docs/overhaul-findings.md`).
Fixes include: historical touch points in a drag, panels pausing the game behind them, an opaque "Shuffle time!" banner, reduced-motion caps, and opaque rims (draw-4 decided). The sticker panel is 6x2 on landscape tablets, with hints that wrap.
Phase 7: the `GameBoard` rules did not change. The gallery is a handy way to look at a balance change without the emulator.

**7 Game balance (done, branch `overhaul/phase-7`).** New `tools\balance.ps1` plays 250 scripted scenarios (5 player models x 2 grids x 25 seeds x 150 moves) and checks the chain rules every move; 9 balance findings, all refuted by skeptics, so no thresholds or `GOLDEN_CHANCE` changed (`docs/overhaul-findings.md`).
Fixed: `findHint` now returns its cells in drag order (was flood-fill order, 1312 sim moves). New content: cascade words Again!/Wow!/Hooray!/Yippee!/Amazing!, picked by 3 generators and 2 judges who rejected 14 words. 18 agents used.
Phase 8: open questions worth research are a `kidLearner` model (do real kids reach row blasts?) and whether 6x6 should get a higher golden chance. Check on a device that "Amazing!" fits over an edge cell.

**8 Research (done, branch `overhaul/phase-8`).** New `docs/research.md`: 7 Sonnet researchers, a citation checker per angle (100 URLs opened, 51 items removed), a design and an engineering judge, a writer and a critic (18 agents); the critic's 9 fixes were applied (duplicate ids merged, 72 references, scores re-sorted).
Top ranked: voice cheers that read the words aloud, praise that names the act and Steven, a grown-up moment, loop-the-ring, deterministic donut friends in the box. 30 features ranked, rejected ideas and a pre-production Play checklist are in the doc. No code changed.
Phase 9: the two phase-7 questions are answered with low confidence; the next step is a `kidLearner` model in `BalanceSim.kt` and a test-only `GOLDEN_CHANCE`/type-count parameter. List the research top ten in the summary's next steps.

**9 Wrap-up (done, branch `overhaul/phase-9`).** Doc map regenerated (no change since phase 7). Auditors checked `CLAUDE.md`, both READMEs and the script help against the code; 8 stale items fixed (missing command rows, test note, size limit, 2.0.3 examples, a bash block in the README).
New `docs/overhaul-summary.md`: per-phase changes, scripts added, what was left (the `.claude/settings.json.disabled` and `NEXT_BUILD.md` decisions are yours), and the top ten next steps, with merge commands and the push-hook warning.
Overhaul complete; the merge decision is the user's (`git merge --ff-only overhaul/phase-9` on `master`; the post-commit hook pushes the next commit there).

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
  and untracked. `keys\` is now git-ignored (phase 4) and untracked. Do not move or change any of them.
- The doc map in `CLAUDE.md` is generated: `.\tools\code-map.ps1 -UpdateDoc CLAUDE.md`. Re-run it after any
  change to the sources (phase 9 must).
- Workflow worktrees are cut from `master`, not from the phase branch: a worker that edits code must
  `git reset --hard overhaul/phase-N` first, and its commits are cherry-picked or checked out file by file.
- The full suite takes several minutes (Robolectric drawing tests). Run `.\tools\check.ps1 -Test -Quiet` in
  the background and use `-Tests '<class>'` while iterating.
- Phase 5 choices: anything that changes pixels, dependencies, the theme or release config was logged as risky
  instead of applied. `ChainRules.accepts` keeps its own all-golden rule (rules-18). The tracked
  `.claude/settings.json.disabled` push hook is still the user's decision.
