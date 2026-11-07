# Overhaul summary

The overhaul ran in nine phases, one session each, on the branches `overhaul/phase-1` to `overhaul/phase-9`.
Each branch was made from the one before it, so `overhaul/phase-9` holds all of the work. Nothing was merged
into `master` and nothing was pushed. Up to phase 8 the work adds up to 79 files changed, +5984 / -611 lines.
`master` has not moved since the overhaul started, so the merge is a fast-forward. Run these from the repository root:

```powershell
git checkout master
```

```powershell
git merge --ff-only overhaul/phase-9
```

A warning about the push hook: `.git\hooks\post-commit` pushes every commit made on `master` to GitHub. A
fast-forward merge is not a commit, so the hook does not run and nothing is pushed yet. The next commit you make
on `master`, or a `git push origin master`, sends the whole overhaul at once. Merge only when you are happy to publish it.

## What changed, per phase

Line counts are `git diff --shortstat` against the previous phase branch (phase 1 against `master`).
Details for every phase are in `docs\overhaul-state.md` (phase log) and `docs\overhaul-findings.md`.

**Phase 1: Map** (4 files, +188 / -6)
- `CLAUDE.md` now says what the project is and has a PowerShell table for build, run and test.
- The code map table was corrected (four files were missing), and a generated doc map gives line ranges per symbol.
- `code-map.ps1` gained `-Doc` and `-UpdateDoc`.

**Phase 2: Bug hunt** (23 files, +482 / -115)
- 9 finders and 9 skeptics: 31 reports, 28 confirmed (13 distinct bugs), 3 refuted.
- All 13 bugs fixed; 9 got new JVM tests (`ChainRules`, `ChainPointer`, `BannerQueue`, `LayoutMath` helpers,
  board-has-a-move and golden-hint tests).
- The other 4 fixes (GameView and activity state) were checked on the emulator in phase 6, and passed.
- New prefs key `stickersAnnounced`.

**Phase 3: Tests** (31 files, +3668 / -21)
- Robolectric 4.16 added as a test-only dependency; 15 workers wrote tests, and the suite is now 21 classes.
- Coverage went from 43% to 98% of lines and from 38% to 88% of branches.
- Mutation check killed 156 of 165 mutants; the one real gap found got a test.
- Found and fixed a panel flicker in `advanceSettingsAnim` (an open panel dipped every other frame).

**Phase 4: Security** (11 files, +74 / -8)
- 8 area reviewers, each finding checked by 3 skeptics. No secret was ever committed.
- 5 low findings fixed: a saved-count overflow that would crash every launch, a checksum on the Gradle wrapper,
  `/*.json` and `keys\` git-ignored, and two `mutate.ps1` backup hazards.
- 5 refuted or optional, logged. Gradle 9.2.1 has two CVEs (not exploitable here), left for you.

**Phase 5: Clean-up** (46 files, +554 / -634)
- 6 finders and a skeptic per finding; 43 safe items applied in 5 batches, each built and tested.
- Unused imports and resources and dead fields removed, `onTouchEvent` moved to `GameInput.handleTouch`,
  the `grid_size` pref clamped, `ValidatePattern` added to `check.ps1` and `mutate.ps1`, shared test fixtures.
- `GameView.kt` went from 515 to 431 lines.
- `SavedBoard.decode` bounds `size` (1..MAX_SIZE) and `playPCM` releases its AudioTrack in a `finally` (phase 2
  refuted items save-errors-2 and platform-errors-2).
- 17 risky items logged, not applied (dependency bumps, theme, icons, proguard, art merges that change pixels).

**Phase 6: UI** (21 files, +516 / -136)
- New `gallery.ps1` renders 18 screen states at five sizes on the JVM; the emulator covered phone and tablet.
- 56 audit findings, 32 confirmed, 28 fixed; 4 low ones deferred.
- Fixes: fast swipes no longer skip cells, panels pause the game behind them, an opaque "Shuffle time!"
  banner, reduced-motion caps, opaque rims, and a 6x2 sticker grid on landscape tablets with hints that wrap.
- The phase 2 device checks all passed on the emulator.

**Phase 7: Game balance** (11 files, +476 / -7)
- New `balance.ps1` plays 250 scenarios (5 player models x 2 grids x 25 seeds x 150 moves) and checks the
  chain rules on every move.
- 9 balance findings, all refuted by skeptics, so no thresholds or `GOLDEN_CHANCE` changed.
- Fixed: `findHint` now returns its cells in drag order (it was wrong in 1312 sim moves).
- New cascade words Again!, Wow!, Hooray!, Yippee!, Amazing!, picked by 3 generators and 2 judges, who rejected 14 words.

**Phase 8: Research** (2 files, +344 / -2)
- New `docs\research.md`: 7 researchers, a citation checker per angle (100 URLs opened, 51 items removed),
  two judges, a writer and a critic; 72 references.
- 30 features ranked, rejected ideas listed, and a checklist for going to production on Play. No code changed.

**Phase 9: Wrap-up**
- Doc map checked against the code with `code-map.ps1 -Doc` (no change since phase 7).
- Auditors checked `CLAUDE.md`, both READMEs and the script help against the code. Fixed: rows for `-Tests`,
  `emu.ps1 Drag` and `balance.ps1`, the `env.ps1` note, the Robolectric test note, file-size limits, the
  `LayoutMath` row, `-Task`/`-From`/`-To`/`-Max`/`-Code` in `tools\README.md`, 2.0.3 examples raised to 2.0.5,
  and a PowerShell debug-build and tests section in `README.md` with a shorter project tree.
- This summary.

## Scripts added

All are in `tools\` and listed in `tools\README.md`. Run them from the repository root.

| Script | What it does | Example |
|---|---|---|
| `coverage.ps1` (phase 3) | Line and branch coverage per file; `-Lines` writes the uncovered lines | `.\tools\coverage.ps1 -Quiet -Lines` |
| `mutate.ps1` (phase 3) | Mutation check of one file against its tests; always restores the file | `.\tools\mutate.ps1 -File ChainRules.kt -Tests 'com.donuts.game.ChainRulesTest'` |
| `env.ps1` (phase 3) | Dot-sourced by the build scripts to find the JDK and Android SDK | `. .\tools\env.ps1` |
| `check.ps1 -Tests` (phase 3) | Runs only the named test classes | `.\tools\check.ps1 -Quiet -Tests 'com.donuts.game.GameInputTest'` |
| `code-map.ps1 -Doc`, `-UpdateDoc` (phase 1) | Prints or refreshes the doc map | `.\tools\code-map.ps1 -UpdateDoc CLAUDE.md` |
| `gallery.ps1` (phase 6) | Renders every screen state at five sizes into `build\shots\gallery`, no emulator | `.\tools\gallery.ps1 -Quiet` |
| `balance.ps1` (phase 7) | Runs the 250-scenario balance simulation into `build\balance` | `.\tools\balance.ps1 -Quiet` |
| `emu.ps1 -Memory` (phase 6) | Starts the emulator with 4 GB by default, so the app is not killed | `.\tools\emu.ps1 Start -Avd 10_Tablet -Memory 4096` |

## What was left

Each line points to its section of `docs\overhaul-findings.md` or `docs\research.md`.

**Needs a decision from you**
- The tracked `.claude\settings.json.disabled` holds a dormant add-all, commit and push hook. Keep it or delete it. (Phase 4)
- `NEXT_BUILD.md` lists the build after 2.0.3; all of it went into 2.0.4, and the file says to clear it once 2.0.4
  is in closed testing (uploaded 2026-11-05). It is your checklist, so it was left as it is. (Phase 9)

**Needs a release**
- The overhaul is not in any Play build. Testers have 2.0.4 (versionCode 36). Bump to 2.0.5
  (`.\tools\bump-version.ps1 -Name 2.0.5`), update `app\src\main\play\release-notes\en-US\default.txt`, build with
  `.\release.ps1`, then upload with `.\gradlew.bat publishReleaseBundle` once you are happy. The bump commit on
  `master` triggers the push hook. (README release checklist)

**Needs you in Play Console** (`docs\research.md` section 7)
- Confirm that 12 testers are opted in (7 on 2026-10-09), apply for production, declare the target audience,
  check the merged manifest for AD_ID and SDKs, fill in Data safety as no data collected, host a privacy policy,
  complete IARC, declare Contains ads: No, and decide deliberately before any analytics or cloud save (COPPA).

**Needs more data (balance sim)**
- Row blast and colour burst almost never happen in kid play (0.1 and 0 per 100 moves). Build a `kidLearner`
  model before changing any threshold. (Phase 7; `docs\research.md` section 4)
- The 6x6 board is thinner (12% one-option moves, 1.9 shuffles per 100 moves). Run kidLearner on 6x6 three ways
  (as it is now, five donut types, `GOLDEN_CHANCE` 0.08) through a test-only parameter (`GOLDEN_CHANCE` is a const
  at `GameBoard.kt` 19). Research favours fewer types. (Phase 7; `docs\research.md` section 4(b))

**Needs a device check**
- Does "Amazing!" fit over an edge cell on a 6x6 board? (Phase 7)
- Android 16 ignores the portrait lock on large screens, and the tablet AVD letterboxes. (Phase 6, device notes)

**Risky changes logged, not applied** (Phase 5 table)
- Gradle wrapper 9.2.1 to 9.3 for two CVEs; the new checksum must come from services.gradle.org (build-1, also Phase 4).
- AndroidX, Material and AGP are behind (build-8); Robolectric 4.16 to 4.17 (build-9).
- Dropping appcompat and material, and the repeated `values-v27` theme (rules-24, build-7); a `windowBackground`
  fix for the cold-start splash and black first frame (Phase 6 device notes) belongs in the same change; need an emulator look.
- Ten identical adaptive-icon XMLs (build-10), repeated SDK and JDK discovery (build-11), template proguard rules (build-12).
- Drawing duplicates whose merge would change pixels (draw-10, draw-11, draw-15, draw-22, draw-24) and an `ink(alpha)` helper (draw-23).
- The all-golden chain rule kept separate in `ChainRules.accepts` (rules-18); an overlapping tier test kept on purpose (tests-8).

**Small unfixed findings**
- Four low UI items: a still cue for a refused donut under reduced motion (a11y-4), icons beside settings labels
  (a11y-10, the same as research #6 Icon-first settings), silhouettes on locked stickers (states-4; research #5
  and #13 advise against nagging silhouettes, so decide before building), and the sticker box looking small on a
  tablet (responsive-7). (Phase 6, deferred; `docs\research.md` section 5)
- Keystore backup names (`*.jks.old`, `*.p12`, `*.pem`) are not git-ignored; optional. (Phase 4)
- `GameInput` was never mutation-checked; two survivors in `GameAnimation` touch only sound and haptics. (Phase 3)
- Still untested: real audio, `RenderThread` posting a real frame, `saveState` racing the render thread,
  pixel-level drawing, `buildSprites` bitmaps. (Phase 3)

## Top ten things to do next

1. **Merge the overhaul into `master`.** Nine phases of fixes and tests exist only on local branches. Size S.
   Commands and the push warning are at the top of this file.
2. **Start the Play production steps now.** First check in Play Console how many testers are opted in. Only 7 were
   recorded on 2026-10-09, and production needs 12 for 14 days in a row, so recruit now if fewer than 12 are in;
   the earliest date is 14 days after the 12th joins. Then complete the target audience, Data safety, a hosted
   privacy policy, the content rating and Contains ads: No, check the merged release manifest for AD_ID, and apply
   for production. Size S. `docs\research.md` section 7.
3. **Ship 2.0.5 to closed testing.** It puts the overhaul's bug fixes in testers' hands and on a real phone, and the
   production application should use this build. Size S. README release checklist; `docs\research.md` section 7
   for the merged-manifest check.
4. **Run the device checks on 2.0.5 from Play on a real phone.** No release since 2.0.3 has been tried on one, and
   Steven's device is unknown. Check "Amazing!" on a 6x6 edge cell and the landscape layout on a large Android 16
   screen, which ignores the portrait lock. Size S. Findings, Phase 6 device notes and Phase 7.
5. **Voice cheers that read the words aloud** (research #1). He is an early reader; short offline clips,
   ideally in a family voice, read the combo words for him. Needs recorded clips. Size M. `docs\research.md` section 5.
6. **Praise that names what he did, and his name** (research #2). Mostly strings: a golden-donut label and an
   occasional "Great job, Steven!". Size S. `docs\research.md` section 5.
7. **Show a grown-up moment** (research #3). A "Show someone!" card on a big sticker or a full box gives a
   parent a moment to cheer. No sharing. Size M. `docs\research.md` section 5.
8. **Add a `kidLearner` model to the balance sim,** and the test-only type-count and golden-chance parameter from
   section 4(b). It answers whether a real child ever reaches row blasts and colour bursts, and whether 6x6 needs
   help. Do this before Loop the ring and any threshold or `GOLDEN_CHANCE` change. Size M. Findings, Phase 7;
   `docs\research.md` section 4.
9. **Loop the ring** (research #4). A chain that closes on its start clears that type. It needs a minimum
   length, since any 2x2 block already makes a loop. Size M. `docs\research.md` section 5.
10. **Silly donut friends in the box** (research #5). A fixed, never random friend revealed each time a box
    slot fills, kept forever. A large art job of a dozen or more characters. Size L. `docs\research.md` section 5.
