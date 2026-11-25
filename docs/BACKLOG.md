# Backlog

The shared ledger for `/backlog`. Every session reads it first and writes it last. Built 2026-11-24 from the
Deferred list in `docs/overhaul-state.md`, the unfixed items in `docs/overhaul-findings.md`, the top ten in
`docs/overhaul-summary.md` and the ranked features in `docs/research.md` section 5 (`#n` is the research rank).
Look up the detail there, by the source id.

Already done and left out: merging the overhaul, releasing 2.0.5, a11y-4 (still cue under reduced motion), the
`.claude/settings.json.disabled` hook, the phase 2 and phase 4 hardening (saved counts, `decode` size bound,
`AudioTrack` release, keystore ignores, `grid_size` clamp). Dropped earlier: C4, silhouettes on locked stickers.

Size: S under an hour, M a session, L several sessions. Value: 3 high, 1 low. Status: todo, done,
blocked, needs-decision, or user-only (nothing in the repo can do it; work sessions skip it).

| Id | Item | Source | Size | Value | Status |
|---|---|---|---|---|---|
| B1 | Praise that names what he did: a golden-donut label, an occasional "Great job, Steven!" on milestones and box fills (wording approved by Nick 2026-11-24; keep labels short enough for an 8x8 float label) | research #2, top ten #6 | S | 3 | done 2026-11-24: `Praise.kt` picks the float word; a chain with a golden cell says GOLDEN! in gold (power-up words still win). Every second milestone or full box adds "Great job, Steven!" as a second banner line (count per game screen, not saved). Choice: the optional name-the-act word for a 3-chain left out; gallery state `19-cheer` |
| B2 | `kidLearner` model in `BalanceSim.kt`, plus a test-only golden-chance and type-count parameter; run 6x6 three ways | state B7, top ten #8, research 4(b) | M | 3 | done 2026-11-24: `GameBoard` takes test-only `goldenChance`/`typeCount`; BalanceSim has `kidLearner56`/`kidLearner78` and variants base/types5/golden08. 6x6 7+ share: kidLearner56 0.3/0.9/0.6%, kidLearner78 2.2/6.3/4.3%; colour bursts stay rare. Five types is the stronger lever; no constant changed, numbers in research.md 4(a)(b) for B4 and B29 |
| B3 | Show a grown-up moment: a "Show someone!" card on a big sticker or a full box, any tap closes it | research #3, top ten #7 | M | 3 | done 2026-11-24: a full box or a big sticker (`StickerRules.isBig`: 500 donuts, chain of 8, best 200, or all twelve) queues the card; it shows after the sticker fly, over a dimmed screen, with the sticker or box, "Show someone!" and "Steven did it!". Any tap after 400 ms or Back closes it and is swallowed. Choices: milestone sound and haptic, no new audio; on a box fill it covers the BOX FULL! banner. Tests in `ShowSomeoneTest`, gallery `20-show-someone` |
| B4 | Loop the ring: a chain that closes on its start clears that type, with a minimum length (after B2) | research #4, top ten #9 | M | 3 | done 2026-11-24: `LoopRules.closes` (`MIN_LOOP` 6, since with 8-way links a 3-cell L or 2x2 block is already a loop); dragging back onto the first cell closes the ring (milestone haptic, string drawn closed), back onto the last cell opens it. Release fires the new `PowerUp.RING`: every cell of that type, beating every length tier, with a glazed-ring effect from the cell nearest the centre and the word RING!. Tests in `LoopRulesTest`, `RingClearTest`, gallery `21-ring`. Choice: a 6-ring clears a colour like a 9-chain on purpose (it is the skill move); BalanceSim does not look for loops, so B16/B21 should not count on it |
| B5 | Voice cheers that read the combo words aloud, offline clips, a settings toggle (needs recorded clips) | research #1, top ten #5 | M | 3 | needs-decision 2026-11-24: there are no clips and a session cannot record a family voice. Question for Nick: record about eight short clips (the combo words, "Shuffle time!", "Steven, you did it!") as WAV or OGG, or use Android's offline `TextToSpeech` voice instead? Recommendation: family clips, dropped into `app\src\main\res\raw` with the names listed in this row once chosen; the engine and toggle are a session's work after that |
| B6a | Donut friends 1/4: pure `FriendRules.kt` (fixed order, which friend a filled slot reveals, saved in `Prefs`), with tests | research #5, top ten #10 | M | 3 | done 2026-11-24: `FriendRules.kt` holds twelve friends in a fixed order (Sprinkle, Jelly, Bouncy, Swirl, Choco, Kicky, Berry, Gloop, Zoomy, Puff, Fizz, Captain Glaze; three are balls). Friend i is met when the box slots ever filled reach `REVEAL_AT` 1,2,4,6,9,12,16,20,25,30,36,42 (fast first, later every few boxes); `next(lifetime, met)` gives one at a time. `Prefs.friendsMet` ("friends_met", 0..12) survives Reset stickers on purpose. For B6b: nothing is wired yet; on the first frame an existing player with lifetime donuts already past several slots should meet them one per reveal, not all at once |
| B6b | Donut friends 2/4: `FriendArt.kt` with the first four friends and the reveal fly in `EffectsDraw.kt` (after B6a) | research #5 | M | 3 | done 2026-11-24: `FriendArt.kt` draws Sprinkle (tongue out), Jelly (jam blob), Bouncy (basketball) and Swirl (wink) with `drawFriend`; `DRAWN` 4 caps `FriendRules.next(.., drawn)` so only friends with art are met. `FriendDraw.kt` (not EffectsDraw, already over 500 lines): `checkFriends` starts one reveal at a time when no fly, card, panel or tutorial is up, saving `friendsMet` at the start; the friend pops in, hops twice, "NEW FRIEND!" and the name, then flies into the star. The sticker fly, Show someone card and hint wait for it. Tests in `FriendRevealTest`, gallery `22-friend` (gallery `game()` sets `friendsMet` 12 so other shots show no fly). For B6d: raise `DRAWN` to 12 |
| B6c | Donut friends 3/4: a friends row or page in the stickers panel that passes the layout tests (after B6b) | research #5 | M | 3 | todo |
| B6d | Donut friends 4/4: the remaining friends and a sound each (after B6c) | research #5 | M | 3 | todo |
| B7 | Gradle wrapper 9.2.1 to 9.3 (two CVEs), checksum from services.gradle.org, then a release build | state B1, build-1 | S | 2 | todo |
| B8 | Icon-first settings: a big picture above each settings word, taller rows still fit short landscape | state C3, a11y-10, research #6 | S | 2 | todo |
| B9 | Colour-blind check of the donut set: `tools\colorblind.ps1` over the gallery shots, a marking only if two collide | research #7 | S | 2 | todo |
| B10 | Forgiving hit zones: join a cell on entering a circle around its centre, biased toward the drag | research #8 | S | 2 | todo |
| B11 | Surprise stickers, not a checklist: hide the to-do feel of locked tiles | research #13 | S | 2 | todo |
| B12 | Flash-safety audit with a timing test: no full-screen flash faster than 3 a second | research #14 | S | 2 | todo |
| B13 | Parent-facing promise and privacy note (also the text a hosted privacy policy needs) | research #17, research section 7 | S | 2 | todo |
| B14 | Drop appcompat and material, the `values-v27` theme, and fix the cold-start splash and black first frame | state B3, rules-24, build-7 | M | 2 | todo |
| B15 | Variety and rare surprises in celebrations, with a seedable `Random` for `spawnParticles` | research #9 | M | 2 | todo |
| B16 | Sprinkle party meter: every clear fills it, a full meter starts a party (after B2) | research #10 | M | 2 | todo |
| B17 | Grown-up corner behind a gate: New game, Reset stickers and grid size behind a parent gate | research #11 | M | 2 | todo |
| B18 | Lift grace: pick a chain back up shortly after lifting the finger | research #15 | M | 2 | todo |
| B19 | Learn from Steven's real play: a local tally of chain lengths and power-ups, never sent anywhere | research #18 | M | 2 | todo |
| B20 | Picture prompts as soft goals ("Pop 3 pink!"), nothing lost if ignored | research #19 | M | 2 | todo |
| B21 | Quiet drought assist: nudge the refill when no long chain has been possible for a while (after B2) | research #20 | M | 2 | todo |
| B22a | Toppings 1/3: pure unlock rules from lifetime donuts and a saved choice, with tests | research #12 | M | 2 | todo |
| B22b | Toppings 2/3: topping art as overlays in `DonutArt.kt` and the sprite cache, and a picker in a panel (after B22a) | research #12 | M | 2 | todo |
| B22c | Toppings 3/3: seasonal looks chosen by date (after B22b) | research #12 | M | 2 | todo |
| B23a | Mascot 1/2: the donut pal art and an idle bob beside the counter, layout tests at the gallery sizes | research #16 | M | 2 | todo |
| B23b | Mascot 2/2: reactions to chain length, power-ups and milestones, and a reduced-motion still pose (after B23a) | research #16 | M | 2 | todo |
| B24a | Shop scene 1/2: pure rules for which shop pieces lifetime donuts unlock, with tests, and the scene on the home screen | research #21 | M | 2 | todo |
| B24b | Shop scene 2/2: more pieces and a small reveal when one is earned (after B24a) | research #21 | M | 2 | todo |
| B25 | Close rules-18 and tests-8 as kept on purpose (findings file only) | state B6 | S | 1 | todo |
| B26 | Calm mode switch: a manual reduced-motion toggle in settings | research #22 | S | 1 | todo |
| B27 | Separate sound levels and a named loudness cap with a test | research #25 | S | 1 | todo |
| B28 | Families-policy release check script (merged manifest: AD_ID, permissions, SDKs) | research #26 | S | 1 | todo |
| B29 | Fewer donut types on 6x6, only if B2's runs favour it | research #27 | S | 1 | todo |
| B30 | Smarter hint and gentle assist (slow finger trace kept, never plays the move) | research #28 | S | 1 | todo |
| B31 | Visual cues for sound-only events | research #29 | S | 1 | todo |
| B32 | AndroidX, Material, AGP and Robolectric 4.17 updates, one at a time (after B14) | state B2, build-8, build-9 | M | 1 | todo |
| B33 | Build tidy-ups: launcher icon XMLs, SDK/JDK lookup via `env.ps1`, proguard rules | state B4, build-10/11/12 | M | 1 | todo |
| B34 | Merge duplicate drawing code and add an `ink()` colour helper, gallery before and after | state B5, draw-10/11/15/22/23/24 | M | 1 | todo |
| B35 | Mutation check of `GameInput` with `tools\mutate.ps1`, kill the survivors | state B8 | M | 1 | todo |
| B36 | Tests for what is still untested: `RenderThread` pacing, the `saveState` race, sprite bitmaps, pixel checks | state B9 | M | 1 | todo |
| B37 | Tappable bubble power-up | research #23 | M | 1 | todo |
| B38 | Parent-set wind-down (opt-in, never an unprompted break) | research #24 | M | 1 | todo |
| B39 | TalkBack labels for buttons and panels | research #30 | M | 1 | todo |
| B40 | The sticker box and stats look small on a tablet; needs a layout decision from the `10_Tablet` shots | state C5, responsive-7 | M | 1 | todo |
| B41 | Play production: 12 testers for 14 days, Data safety, privacy policy, content rating, Contains ads: No | state Deferred, top ten #2, research section 7 | S | 3 | user-only |
| B42 | Real-phone checks: "Amazing!" over a 6x6 edge cell, landscape on an Android 16 large screen | state B10, top ten #4 | S | 2 | user-only |

## Decisions

- 2026-11-24, Nick: strike nothing, keep the order, B1's wording may be built without asking, and the four L
  items are split into M rows now (B6a-d, B22a-c, B23a-b, B24a-b). Work sessions do not ask again.
