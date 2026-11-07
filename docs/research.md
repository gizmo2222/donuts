# Research: comparable games and features worth building

Date: 2026-11-07. Phase 8 of the repo overhaul.

**Summary**

1. No other chain game in this set has diagonal chains, no move limit, no fail state and no purchases all at once. Two Dots, Tsum Tsum and Pokemon Shuffle depend on lives, move limits or paid boosts [A1][A4][A7][A8].
2. The biggest gap is that nothing is spoken. Steven is an early reader, and every cue in the game is text plus synth sound. Google and Play guidance asks for sound and pictures alongside text [B1][F2].
3. Fine motor skill at ages 6-8 is rated "limited", and precise dragging is hard [C1]. Forgiving hit zones and icon-first settings are cheap ways to help.
4. Competitors keep players through collecting things in a place that grows: characters, a cafe, a gallery [A2][A3][A4]. The donut box and stickers are a good base for that, as long as no lures, streaks or timers come with it [D1][D6].
5. The Play production steps are routine for an app with no ads and no data: 12 testers for 14 days, the target audience form, the Data safety form, a privacy policy and an IARC rating [F1][F2][F3][F5].

**Scope note.** Donuts for Steven is a gift for one child, distributed through Google Play closed testing. It is free, with no ads and no purchases. Competitor pricing is recorded for context only and does not drive any recommendation here.

## 1. Method

- **Seven research angles:** chain games, kids' games, child UX and motor skills, rewards and ethics, accessibility, the Play Store for kids, and feel and difficulty.
- **Citation check:** a citation checker per angle opened the cited URLs, most-cited first. Across the seven angles it opened 100 URLs and removed 51 items, either claims or sources that the page did not support or that could not be opened. The final list has 72 references. Ten ids that pointed to the same page or work were merged into one each (C2, G3, D5, C7, B10, E16, D4, G1, D11, E14). Ids missing from the sequence (A12, B8, C6, E5, E11, E12, F9, G2, G7, G9, G10) belong to sources the checkers removed.

  | Angle | URLs checked | Items removed |
  |---|---|---|
  | Chain games | 13 | 9 |
  | Kids' games | 14 | 8 |
  | Child UX | 12 | 6 |
  | Rewards and ethics | 14 | 8 |
  | Accessibility | 16 | 9 |
  | Play Store | 14 | 5 |
  | Feel and difficulty | 17 | 6 |

- **Sources kept but not fully read:** a few pages blocked the checker or could not be parsed. These are marked *unverified* wherever they are cited. They are D2, D9 and D12 (HTTP 403), C5 (unreadable PDF) and F7 (the dates were confirmed through F8 instead).
- **Judging:** a design judge scored each feature idea for fit with the brief in CLAUDE.md, from 0 to 5. An engineering judge then checked effort, risk and the files each idea would touch against the current code.
- **Ranking:** features are ordered by fit x 2 + evidence. Ties go to the lower-effort feature (S < M < L), then to the lower-risk one (low < medium < high). Evidence was re-judged after duplicate references were merged.
- **No code changed:** this phase made no code changes.

## 2. Comparable games

| Game | Developer | Pricing | What it does better than Donuts | Refs |
|---|---|---|---|---|
| Two Dots | Dots (Playdots) | Free with IAP: moves and power-ups from $0.99, currency $1.99-$9.99, slow lives, some random items | Thousands of levels, with a new goal or obstacle in each world. Also event maps, a hidden-object gallery, loop-made bombs and a free colour-blind mode | [A1][A2][A8] |
| Dots (original) | Playdots / Betaworks | Free, with experience put ahead of monetization; shut down in March 2023 | Pure chain feel and minimal polish. Closing a loop clears a whole colour | [A5] |
| LINE: Disney Tsum Tsum | LINE / Disney | Free-to-play: hearts and random-prize boxes, about $1.5B lifetime by 2019 | Collectible characters, a bubble you pop when you choose, and a Fever meter payoff | [A4] |
| Toon Blast / Toy Blast | Peak Games (Zynga, now Take-Two) | Not confirmed from the sources | Scale: over 12M daily users combined in 2020. Its mechanics were not confirmed from the sources | [A9] |
| Pokemon Shuffle | Genius Sonority / Nintendo | Free-to-play: hearts, coins, paid jewels; no new content after 2018 | Win the stage, then catch the Pokemon. Mega Evolution acts as a bonus state | [A7] |
| Pokemon Cafe ReMix | Genius Sonority / Nintendo | Free-to-start with optional purchases | Link-by-touch matching set in a cafe that grows, with customers who join the staff | [A3][A13] |
| Pok Pok Playroom | Pok Pok | Subscription: $3.99/month or $29.99/year at launch, no ads or IAP | Calm, open-ended toys with no instructions, no success or fail state and restrained sound | [B4][C9][D10] |
| Toca Boca apps | Toca Boca (Spin Master) | Piknik bundle about $14.99/month after a trial; some apps sold alone | Open-ended toys with no instructions, designed by watching children play | [B3][B6][B12][G5][G12] |
| Sago Mini World | Sago Mini (Spin Master) | Subscription, plans from $7.99 to $79.99, free trial | A large library of small toy-like activities under one plan | [B2][C10] |
| Khan Academy Kids | Khan Academy | Free, with no ads and no subscriptions | A cast of animal guides led by a narrator bear, seasonal themes with a parent off switch, and parent settings | [B5][F11] |
| PBS KIDS Games | PBS KIDS | Free, no IAP | A large library that is updated regularly, with known characters and offline games | [B9][F10] |
| Number / Alphabet Match 3 | Mike Hempfling | Free; Remove Ads $0.99 | Speaks each number when it is touched, which suits a child who cannot read yet | [B13][C11] |
| Endless Alphabet | Originator | $8.99 | Letters speak as they move, with no scores, failures or time limits | [D13] |
| Kids Match 3 | Balxar International | Free | Over 120 levels. Matching five makes a colour blast | [G11] |
| Candy Crush Saga (design reference) | King | Not verified | Difficulty tuning with bot simulation: an easier level follows a hard one, and new mechanics arrive in a staircase | [G4] |

## 3. Findings by theme

### Chain games

- **Two Dots is stricter than Donuts.** It allows only straight links, sets a move limit per level, and turns a closed loop into a colour clear with bombs inside [A1][A8]. Donuts allows diagonals and has no limits, which is the gentler choice for a 5-8 year old.
- **Variety keeps people playing.** Competitors keep players with many levels, themed events and collectable galleries [A2][A8]. Donuts has none of these on purpose. Soft goals with no fail state are the only version that fits the brief.
- **What to avoid.** Common Sense Media rates Two Dots 13+ because of in-app purchases, lockouts when lives run out and difficulty spikes [A1]. Wikipedia reports criticism of its near-impossible levels [A8]. Donuts should avoid lives, difficulty spikes and purchase prompts entirely.
- **Agency without pressure.** Tsum Tsum turns long chains into a bubble the player pops when they choose, and fills a meter toward a short boost [A4]. Both add choice without adding pressure. Its hearts and prize boxes are the parts not to copy [A4].
- **Collecting in a place.** Pokemon Cafe ReMix sets collecting inside a visible place that grows [A3][A13]. The donut box is already a small version of that idea.
- **Feel alone can carry a game.** The original Dots won awards on feel and polish while putting monetization second [A5]. "Juice It or Lose It" shows that small effects make a plain game satisfying [A10].

### Kids' games

- **Google's design guidance for kids' apps:**
  - Avoid text-only buttons.
  - Pair text with sound and pictures.
  - Respond to mistakes kindly and let the child get back in quickly.
  - Reward finished tasks with animation and sound [B1].

  Donuts already follows the last two.
- **Calm and open-ended play.** Pok Pok and Toca Boca take a calm, open-ended approach with no instructions [B4][B6][B12]. Donuts is livelier on purpose, but those apps show that celebrations should vary rather than repeat exactly.
- **Characters and themes are normal for this age group.** Khan Academy Kids and PBS KIDS show that characters, seasonal themes and parent settings are standard here, and both are free [B5][B9].
- **Learning apps.** Their research says active, engaged, meaningful and social apps work best, and that unrelated noise pulls children off task [B7]. Donuts is entertainment, not education. Personal touches such as Steven's name can supply the "meaningful" part.
- **Few kid chain games exist.** The kid match-3 games found are low-profile and built on goals or move limits [B11][B13][G11]. Donuts' goal-free chain drag looks like a niche advantage, but the sample is small.

### Child UX and motor skills

- **Fine motor skill.** NN/g rates fine motor skill at 6-8 as "limited". Tapping is easy, but precise dragging is hard. NN/g suggests letting a child finish an action in more than one way [C1].
- **Touch targets.** Young children need touch targets of at least 2 x 2 cm [C1]. At a guess, an 8x8 board on a phone gives cells under 1 cm. A 6x6 board or a tablet is much closer to the target. This is an estimate, not a figure from the source.
- **The chain string helps.** A visible trace while dragging makes children's gestures straighter and more compact, and they prefer having it [C3]. The chain string Donuts draws is the right pattern.
- **No measured rates exist.** Fitts' law models children's pointing well [C4]. No published study gives success rates for long chain drags at ages 5-8. Age-group differences in gestures are reported but not verified [C5].
- **Reading.** Play's Families guidance asks apps for ages 6-8 to rely only lightly on text [F2]. Google's kids page says most under-5s cannot read and may not know how to turn the sound on [B1].
- **Screen time.** Children aged 0-8 average about 2.5 hours of screen time a day, and gaming time has grown sharply [C8]. Endless, pressure-free play suits this, as long as stopping is easy.

### Rewards and ethics

- **Manipulative design is common.** About 80% of 160 apps used by 3-5 year olds used manipulative design: pleading characters, false time pressure, lures and ad pressure [D1].
- **The 5-8 band is targeted too.** In a study of 20 apps for ages 5-8, 15 had at least one deceptive feature. Among them were characters steering children toward paid content and parental controls that children could bypass [D6].
- **Ads are near-universal.** One analysis reports ads in about 95% of apps for under-5s [D2] (*unverified*).
- **Stopping points matter.** Autoplay kept preschoolers watching and frustrated their parents, and the researchers recommend natural stopping points [D3]. The UK Children's Code supports nudges toward breaks, and tools such as pause and save [D9] (*unverified*).
- **Motivation.** Self-determination theory names three needs: autonomy, competence and relatedness. Under its cognitive evaluation theory, rewards that feel controlling reduce intrinsic motivation, while feedback that informs tends to raise it [D7]. The authors maintain that the undermining effect is real [D8]. So surprise rewards and praise that names what he did are safer than a checklist. Relatedness is the need Donuts serves least.
- **"No pressure" is a known quality marker.** Pok Pok and Khan Academy Kids both make it an explicit selling point [D10][B5]. Toca Boca reportedly does too [D12] (*unverified*). The four-pillars framework counts talking with a parent as social interaction [B7].

### Accessibility

- **The basic tier fits Donuts.** The basic tier of the Game Accessibility Guidelines asks for readable text, strong contrast, not relying on colour alone, no flicker and separate sound controls [E1][E2]. Donuts has no speech or music, so most of the sound items are already met.
- **Flash limits.** Xbox guideline 118 sets the failure point at more than about 3 flashes per second over about 20% of the screen, with saturated red the riskiest colour [E3]. The chain and reset flashes, confetti and banners should be checked against these limits when they overlap.
- **Motion.** Xbox guideline 117 asks for a player-facing way to reduce motion [E4]. Donuts already reads the animator duration scale, which the system's Remove animations setting sets to 0 [E7][E8]. It has no toggle of its own.
- **Screen readers.** A canvas view is invisible to TalkBack unless an ExploreByTouchHelper exposes virtual nodes [E6]. This matters little for a sighted child.
- **Colour.** Colour-blind guidance says to pair hue with a second channel such as shape [E9]. Icons alone did not prevent guessing in one match-3 review [E10]. Donuts' six shapes are a strong base, but their round outlines are similar.
- **The category documents little.** Kids' apps document almost no accessibility support [E13][C10][E15], so even modest options would stand out.

### Play Store for kids

- **Closed testing.** New personal accounts need 12 testers opted in for 14 days in a row before applying for production. Review usually takes up to seven days [F3].
- **Families policy.** An app for children must follow the Families policy. Child-appealing art makes an adults-only declaration likely to be rejected [F1][F2].
- **Identifiers and permissions.** Child-only apps must not send the advertising ID, request AD_ID when targeting API 33+, request location, or contain unapproved SDKs [F1].
- **Data safety and privacy policy.** Both are required even when nothing is collected. Data that stays on the device is not counted as collected [F5].
- **COPPA.** It does not apply when no personal information is collected, but the FTC still recommends a privacy policy [F6]. The amended rule was fully in force from April 2026 [F7][F8], and only operators who collect data are bound by it.
- **Ad rules.** Ads to children must come from self-certified SDKs [A11]. An app with no ads avoids all of these rules.
- **Teacher Approved.** It is editorial and needs enough downloads to be reviewed [F4][F12].

### Feel and difficulty

- **Tie big reactions to what he did.** A CHI 2024 study with 1,699 players found that feedback depending on success raised curiosity and competence, while amplified feedback reduced them [G8]. Decorative lushness mostly improved audiovisual appeal [G14]. So each big reaction should follow something Steven did, rather than every reaction being made bigger.
- **Candy Crush tuning.** Its designers put an easier level after a hard one and use bots to estimate difficulty, without replacing human judgment [G4]. BalanceSim plays the bot role here.
- **Adaptive hints and difficulty.** Adaptive hints can help balance difficulty [G6]. Probabilistic dynamic difficulty adjustment quickly found suitable levels for 148 children [G13]. Toca Boca tunes by watching children play [G5].

## 4. The two open questions from phase 7

**(a) Do 5-8 year olds reach 7+ chains (row blasts)? Should the thresholds or a kidLearner model change?**

- **What the evidence supports.** It supports an inference only, not a measurement. Fine motor skill at 6-8 is "limited" and precise dragging is hard [C1]. The visible trace helps accuracy [C3]. No study measures long chain drags at this age [C4]. A 7-chain needs six accurate steps in one touch. Diagonals shorten it, and dense patches of one type can make long chains happen by accident.
- **Best guess:**
  - Chains of 3-4 are routine.
  - Chains of 5-6 happen sometimes.
  - Chains of 7+ are mostly accidental at ages 5-6, and more reachable at 7-8 on 6x6 or a tablet.
- **Confidence.** Low to moderate. The direction is well supported, but the rates are not.
- **Recommendation.** Do not change the thresholds yet. Add a `kidLearner` model to BalanceSim that takes the longest chain it can see but drops each step with an error chance that rises with chain length. Use two settings, about 0.15 per step at age 5-6 and 0.08 at age 7-8. Add it to the player models in `BalanceSim.kt` (`app\src\test\java\com\donuts\game`). `balance.ps1` already runs every model on both grids, so there is nothing to choose. Then run it and read `build\balance\summary.md` for the 7+ chain share:

```powershell
.\tools\balance.ps1 -Quiet
```

If row blasts appear in under about 5% of sessions, consider #10 (party meter) or lowering the row blast threshold. Build #18 (play tally) to replace the guessed error rates with Steven's real chain lengths.

**(b) Should 6x6 get a higher golden chance, or fewer colours?**

- **What the evidence supports.** No source addresses this. The design-judge score for evidence was 0 (#27). The general support is for bot simulation before tuning [G4] and for adjusting difficulty to the child [G13].
- **Reasoning.** Fewer types means bigger groups everywhere on the board. A higher golden chance only adds wild cells, and wild cells need the child to understand they are wild. So fewer types is the stronger lever for long chains, and it keeps the golden cell feeling rare.
- **Confidence.** Low. This is a hypothesis.
- **Recommendation.** Run BalanceSim with the kidLearner model on 6x6 three ways: as it is now, with five donut types instead of six (the golden ball unchanged), and with `GOLDEN_CHANCE` raised from 0.04 to, say, 0.08. Both need a test-only parameter in `GameBoard` or BalanceSim, since `GOLDEN_CHANCE` is a const at `GameBoard.kt` 19. Compare the share of 5+ and 7+ chains. Watch whether colour bursts become trivial. Change no code until the run shows a clear gap.

## 5. Ranked features

Score = fit x 2 + evidence (fit 0-5, evidence 0-5, judged on the number and strength of distinct sources). Ties go to the lower effort (S < M < L), then to the lower risk (low < medium < high). Feature numbers below are ranks (#n), kept separate from citation ids such as [F1].

| Rank | Feature | Fit | Evid. | Score | Effort | Risk | Refs |
|---|---|---|---|---|---|---|---|
| 1 | Voice cheers that read the words aloud | 5 | 4 | 14 | M | medium | [A6][B1][B14][F2][C11][F10][F11] |
| 2 | Praise that names what he did, and his name | 4 | 3 | 11 | S | low | [B7][D7][D8] |
| 3 | Show a grown-up moment | 4 | 3 | 11 | M | low | [B7][D6][D7] |
| 4 | Loop the ring | 4 | 3 | 11 | M | medium | [A5][A8] |
| 5 | Silly donut friends in the box | 4 | 3 | 11 | L | medium | [A4][A7][D1][D6] |
| 6 | Icon-first settings | 4 | 2 | 10 | S | low | [B1] |
| 7 | Colour-blind check of the donut set | 3 | 4 | 10 | S | low | [A2][E2][E9][E10] |
| 8 | Forgiving hit zones for joining cells | 4 | 2 | 10 | S | medium | [C1] |
| 9 | Variety and rare surprises in celebrations | 4 | 2 | 10 | M | low | [B1][B4][G5] |
| 10 | Sprinkle party meter | 4 | 2 | 10 | M | medium | [A4][C1] |
| 11 | Grown-up corner behind a gate | 3 | 4 | 10 | M | medium | [B5][A11][B1][F1][F4] |
| 12 | Unlockable toppings and seasonal looks | 4 | 2 | 10 | L | medium | [B5] |
| 13 | Surprise stickers, not a checklist | 3 | 3 | 9 | S | low | [D7][D8] |
| 14 | Flash-safety audit with a timing test | 3 | 3 | 9 | S | low | [E3] |
| 15 | Lift grace: pick a chain back up | 4 | 1 | 9 | M | high | [C5][C1] |
| 16 | Donut pal mascot | 3 | 3 | 9 | L | medium | [B5][B1][C10][F10][F11] |
| 17 | Parent-facing promise and privacy note | 2 | 4 | 8 | S | low | [D1][D2][F1][D6][E13][C10][F5][F6] |
| 18 | Learn from Steven's real play (local tally) | 3 | 2 | 8 | M | low | [C4][G5] |
| 19 | Picture prompts as soft goals | 3 | 2 | 8 | M | medium | [B6][B12] |
| 20 | Quiet drought assist | 3 | 2 | 8 | M | medium | [G4][G13] |
| 21 | Donut shop scene that keeps growing | 3 | 2 | 8 | L | medium | [A2][A3] |
| 22 | Calm mode switch | 2 | 3 | 7 | S | low | [E4][E7][E8] |
| 23 | Tappable bubble power-up | 3 | 1 | 7 | M | medium | [A4] |
| 24 | Parent-set wind-down | 2 | 3 | 7 | M | medium | [B5][D3][D9] |
| 25 | Separate sound levels and a loudness cap | 2 | 2 | 6 | S | low | [E2][E3] |
| 26 | Families-policy release check script | 1 | 4 | 6 | S | low | [F1][F2][F3][F5][F6][F7][F8] |
| 27 | Fewer donut types on 6x6 | 3 | 0 | 6 | S | medium | (none) |
| 28 | Smarter hint and gentle assist | 2 | 1 | 5 | S | low | [B1][G6] |
| 29 | Visual cues for sound-only events | 1 | 2 | 4 | S | low | [E1][E2] |
| 30 | TalkBack labels for buttons and panels | 1 | 2 | 4 | M | low | [E6] |

### Top ten

**#1 Voice cheers.** Short offline clips play with the combo words, "Shuffle time!" and the chain length, ideally recorded by a family member ("Steven, you did it!"). A settings toggle turns them off. No microphone is used. The risk is that there are no audio assets yet, so the clips must be recorded and trimmed outside the code. *Touches:* new `VoiceEngine.kt` (SoundPool) and `res\raw`; `GameAnimation.kt` `celebrate` 241-244 and `cascadeWord` 10-15; `EffectsDraw.kt` `drawNoMovesWarning` 299-375; `GameInput.kt` `handleUp` 154-215; `Prefs.kt`; `PanelsDraw.kt` `drawSettings` 288-349; `GameLayout.kt` 62-110.

**#2 Praise that names the act.** The game already labels 4-chains and every power-up (`GameInput.kt` 194-201: NICE!, BOOM!, WHOOSH!, KABOOM!) and cascades (`GameAnimation.kt` 10-15). This adds a golden-donut label (a new branch in that `when` block), an occasional "Great job, Steven!" on milestones and box fills, and optional name-the-act wording for 3-chains. Labels describe what happened and never compare or rank him. It is strings only, but longer words must fit a float label on 8x8. Fit is 4 rather than 5 because most of the labelling already exists. *Touches:* `GameInput.kt` `handleUp` 154-215 (label block 194-201, plus a new golden case); `GameAnimation.kt` `cascadeWord` 10-15 and the milestone label near 230; `EffectsDraw.kt` `drawFloatLabels` 12-50; label tests in `GameInputTest` and `GameAnimationTest`.

**#3 Show a grown-up moment.** When a big sticker is earned or a box fills, a "Show someone!" card with his name gives a parent a moment to cheer, with an optional high-five sound. Any tap closes it. There is no sharing. *Touches:* `GameView.kt` state; new `drawShowSomeone` in `EffectsDraw.kt` beside `drawStickerFly` 150-197; `GameAnimation.kt` `checkStickers` 284-313; `GameInput.kt` `handleTouch` 216-294; `BannerQueue.kt`; `SoundEngine.kt`; `GameAnimationPanelsTest`, `DrawSmokeTest`.

**#4 Loop the ring.** A chain that closes on its start makes a donut-ring effect and clears that type. It needs a minimum length, because with 8-way links any 2x2 block already makes a loop. *Touches:* `GameInput.kt` `handleMove` 116-153 (it rejects cells already in the chain at 128) and `handleUp`; `ChainRules.kt` 10-21 or a new pure `LoopRules`; `GameBoard.kt` `peekChainClear` 227-267; `ChainResult.kt`; `PowerUp.kt`; `PopTiming.kt` 23-37; `EffectsDraw.kt` 376-435; `BoardDraw.kt` `drawChainLine` 70-97; BalanceSim.

**#5 Silly donut friends.** A friend is revealed each time a box slot fills (`StickerRules.slotJustFilled`), in a fixed order. Reveals are never random, and there is no "collect them all" prompt or locked silhouette that nags. Each friend is a named silly donut or ball character with a sound, kept forever. There are no streaks, expiry or random purchases. It is a large art job of a dozen or more characters, and the stickers panel is already full. *Touches:* new `FriendArt.kt` and pure `FriendRules.kt`; `StickerRules.kt` 59-65; `GameAnimation.kt` 284-332; `PanelsDraw.kt` 22-172 and 195-262; `EffectsDraw.kt` 150-197; `SoundEngine.kt`; `Prefs.kt`; `GameLayout.kt` 111-119.

**#6 Icon-first settings.** Each control gets a big picture with the word underneath: a speaker, a bulb, a buzzing phone and a grid. The tap-twice confirm pulses as an icon. The taller rows must still pass the short-landscape fit test. *Touches:* `PanelsDraw.kt` `drawSettings` 288-349, `drawConfirmButton` 350-369, `drawSectionLabel` 381-408, `drawSettingsBtn` 409-451; new icon helpers beside `HudDraw.kt` `drawGearIcon` 56-73; `GameLayout.kt` 62-110; `GameLayoutTest` 171-229.

**#7 Colour-blind check.** Run the gallery renders through colour-blindness simulators and greyscale. Add a distinct inner marking only if two types or the golden cell collide. *Touches:* new `tools\colorblind.ps1` over `build\shots\gallery`, registered in `tools\README.md`; `DonutArt.kt` 63-77 and 103-258 only if a collision is found. Run the renders first:

```powershell
.\tools\gallery.ps1 -Quiet
```

**#8 Forgiving hit zones.** A cell joins when the finger enters a circle around its centre, biased toward the drag direction, with a larger radius for diagonal steps and on 8x8. The diagonal zone must not steal a straight step or break the backtrack undo. *Touches:* new pure helper beside `LayoutMath.kt` `cellIndex` 16-19; `GameInput.kt` `handleMove` 116-153 and `cellCol`/`cellRow` 295-298; `LayoutMathTest`; corner cases in `GameInputTest`.

**#9 Variety in celebrations.** Rotate confetti styles, banner flourishes and sound twists, and add rare surprises such as a funny golden pop. Taps already wobble and boop (`GameInput.kt` 203-210), so a rare giggle variant would extend that. `spawnParticles` uses `Math.random()` today. Switch it to a seedable `Random` held on `GameView` so the smoke tests and gallery renders stay repeatable. *Touches:* `GameAnimation.kt` `startCelebration` 245-253 and `spawnParticles` 254-277; `GameView.kt` (the new field); `GameTypes.kt` `Particle`; `EffectsDraw.kt` `drawCelebration` 77-149; `SoundEngine.kt`; `BoardDraw.kt` `drawCellsInner` 134-274; `GameAnimationTest`.

**#10 Sprinkle party meter.** Every clear fills a meter, short chains included. When it is full, a party starts: a free bomb or sprinkle rain, and a higher golden chance for a while. Nothing is lost if it is ignored. It adds HUD chrome and needs a golden-chance parameter and saved state. *Touches:* `GameView.kt`; `GameLayout.kt` `computeLayout` 10-61; `HudDraw.kt` `drawHUD` 12-16; `GameAnimation.kt` 42-161; `GameBoard.kt` `spawnCell` 92-99; `SavedGame.kt` 31-78; `SavedBoard.kt`; `GameLayoutTest`, `GameBoardMoreTest`, BalanceSim.

## 6. Already built, and rejected ideas

### What research confirms the game already does right

- **Diagonal chains, no move limit, no fail state and an automatic shuffle.** This is gentler than Two Dots, which shuffles in the same way but limits moves [A1][A8]. It also matches the guidance on handling mistakes kindly [B1].
- **Free, with no ads, purchases, timers or tracking.** That is rare in a market where manipulative design is common [D1][D6]. It is also the lightest load under the Play Families rules [F1].
- **Juice: squash-and-stretch pops, landing squish and the box-fill bounce.** These are in `BoardDraw.kt` 308-374 and `PanelsDraw.kt` 195-262. They follow "Juice It or Lose It" [A10].
- **Reactions scaled to what the child did.** This covers chain-length labels and power-up tiers (`GameInput.kt` 154-215, `EffectsDraw.kt` 376-388), plus cascade words that climb with each pass. Scaling feedback to success is what the evidence favours [G8].
- **A live chain trace with rising pitch and a count badge.** This is `BoardDraw.kt` 70-97, `SoundEngine.playConnectBlip` 23-28 and `ChainBadge.kt`. It is backed by the visual-feedback study [C3].
- **Automatic reduced motion read from the animator duration scale.** This is in `GameView.kt` 239-241 and `MainView.kt` 28-30 [E7][E8]. Only a manual toggle is missing (#22).
- **Touch target checks.** `GameLayoutTest` 43-170 checks touch targets and overlap across screen sizes [C1].
- **Tap-twice confirms for destructive actions.** These are in `TwoTapConfirm.kt` and `GameInput.kt` 44-68. The gate in #11 would go further [B1].
- **Moderate synth peaks.** Every play call is at 0.50 volume or less (`SoundEngine.kt` 23-93). Only a named cap and a test are missing (#25).
- **Short drags are never silent.** Chains of one or two cells wobble and boop, and two cells show a "3!" label (`GameInput.kt` 203-210) [B1].
- **Board, counter and stickers saved on pause.** This is `SavedGame.kt`, and it fits the UK code's pause-and-save idea [D9] (*unverified*).

### Rejected

- **Teacher Approved as a goal.** It is a passive store review that apps with few downloads cannot get [F4]. It adds nothing to Steven's play.
- **A mirrored left-hand HUD.** Nothing suggests Steven is left-handed, the HUD sits outside the board, and the touch targets are already tested.
- **A hint that plays the move for him.** It would take away the drag and pop that are the whole joy. The slow finger trace is kept in #28.
- **An unprompted "snack break" interruption.** It interrupts the fun and edges toward pressure. It is kept only as the opt-in parent setting in #24 [D3].
- **Lives, hearts, move limits, random-prize boxes, streaks and daily rewards.** These are the competitor levers that the ethics and review sources criticise [A1][A4][A8][D1][D6].

## 7. Before production on Play

The developer must do each of the following in Play Console. No code needs to change.

- [ ] Keep 12 testers opted in, without a break, for 14 days in a row. Do not remove any. If all 12 were in by 2026-11-05, the earliest date is 2026-11-19 [F3].
- [ ] Apply for production from the Dashboard. The form covers how testers were recruited, their engagement and feedback, the target audience, a modest first-year install estimate, and the changes made from testing. Use the overhaul phases as the record of changes. Expect a review of up to about seven days [F3].
- [ ] Declare the target audience accurately, as 6-8 (and 5 & under if that fits). The child-appealing art means an adults-only declaration would likely be rejected [F1][F2].
- [ ] Confirm the merged manifest has no AD_ID or location permission and no unapproved SDKs. Consider `tools:node="remove"` for AD_ID [F1]. Check as follows.

Build the release bundle first:

```powershell
.\release.ps1
```

Then search the merged release manifest:

```powershell
Get-ChildItem app\build\intermediates -Recurse -Filter AndroidManifest.xml | Where-Object { $_.FullName -match 'merged_manifest.*release' } | Select-String -Pattern 'uses-permission'
```

- [ ] Fill in the Data safety form as "no data collected or shared". Prefs stay on the device [F5].
- [ ] Write and host a one-page privacy policy saying that nothing is collected or sent, and link it in the listing. The FTC recommends one even when no data is collected [F5][F6].
- [ ] Complete the IARC content rating honestly: no violence, chat, purchases or ads [F2].
- [ ] Declare "Contains ads: No" [F1].
- [ ] Decide deliberately before adding anything that sends data off the device, such as cloud save, crash reports or analytics. That would bring in the amended COPPA rule's duties [F7][F8]. The guard script in #26 would enforce this.
- [ ] Optional: Teacher Approved review happens automatically once the app is eligible. Do not count on it [F4][F12].

## 8. References

All accessed 2026-11-07. Items marked *unverified* could not be fully read by the citation checker.

- [A1] TwoDots app review. Common Sense Media, undated. https://www.commonsensemedia.org/app-reviews/twodots
- [A2] Two Dots - Connect the Colors (App Store listing). Apple App Store (Dots), live listing 2026-11. https://apps.apple.com/us/app/two-dots-connect-the-colors/id880178264
- [A3] Pokemon Cafe ReMix. Nintendo Wire, undated. https://nintendowire.com/games/mobile/pokemon-cafe-mix/
- [A4] Disney Tsum Tsum. Wikipedia, 2026-11. https://en.wikipedia.org/wiki/Disney_Tsum_Tsum
- [A5] Dots (video game). Wikipedia, 2026-11. https://en.wikipedia.org/wiki/Dots_(video_game)
- [A6] Best Practices: Designing Touch Tablet Experiences for Preschoolers. Joan Ganz Cooney Center, 2012. https://joanganzcooneycenter.org/?p=19607
- [A7] Pokemon Shuffle. Wikipedia, 2026-11. https://en.wikipedia.org/wiki/Pok%C3%A9mon_Shuffle
- [A8] Two Dots (video game). Wikipedia, 2026-11. https://en.wikipedia.org/wiki/Two_Dots_(video_game)
- [A9] Peak Games (Toy Blast, Toon Blast publisher). Wikipedia, 2026-11. https://en.wikipedia.org/wiki/Peak_Games
- [A10] Juice It or Lose It (Jonasson and Purho). GDC Vault, 2012. https://gdcvault.com/play/1016487/Juice-It-or-Lose
- [A11] Families policy and self-certified ads SDK policy. Google Play Console Help, 2026-11. https://support.google.com/googleplay/android-developer/answer/12918983
- [A13] Pokemon Cafe ReMix out now. Nintendo, 2021-10-28. https://nintendo.com/whatsnew/pokemon-cafe-remix-out-now-on-nintendo-switch-and-mobile-devices
- [B1] Designing engaging apps (Building for Kids). Google for Developers, undated. https://developers.google.com/building-for-kids/designing-engaging-apps
- [B2] How much does the subscription cost? How does it work? Sago Mini help center, undated. https://help.sagomini.com/article/382-how-much-does-the-subscription-cost-how-does-it-work
- [B3] Piknik: preschool app subscription. Piknik (Spin Master), undated. https://playpiknik.com/
- [B4] Pok Pok iOS app for kids (review). Engadget, 2021. https://www.engadget.com/pok-pok-ios-app-kids-snowman-subscription-indie-130025594.html
- [B5] Khan Academy Kids (App Store listing). Apple App Store / Khan Academy, undated. https://apps.apple.com/us/app/khan-academy-kids/id1378467217
- [B6] What designers can learn about play from children. Kill Screen, undated. https://www.killscreen.com/what-designers-can-learn-about-play-children/
- [B7] Putting Education in Educational Apps (Hirsh-Pasek et al.). Association for Psychological Science, 2015. https://www.psychologicalscience.org/publications/educational-apps.html
- [B9] PBS Kids Games app is free. 9to5Google, 2016-04-28. https://9to5google.com/2016/04/28/pbs-kids-games-app-free/
- [B11] Kids Puzzles: Match-3 (App Store listing). Apple App Store, undated. https://apps.apple.com/us/app/-/id919655512
- [B12] The design process behind Toca Boca's infectious apps. Motionographer, 2016-04-27. https://motionographer.com/2016/04/27/the-design-process-behind-toca-bocas-infectious-apps/
- [B13] Alphabet Letter Match 3 Fun (App Store listing). Apple App Store, undated. https://apps.apple.com/us/app/id952373301
- [B14] Designing for young audiences. Aten Design Group (practitioner blog), undated. https://atendesigngroup.com/blog/young-audiences
- [C1] Children's UX: Physical Development. Nielsen Norman Group, undated. https://www.nngroup.com/articles/children-ux-physical-development/
- [C3] Children (and Adults) Benefit From Visual Feedback during Gesture Interaction on Mobile Touchscreen Devices (Anthony et al.). University of Florida INIT Lab, Int. J. Child-Computer Interaction 6, 2015-12. https://init.cise.ufl.edu/children-and-adults-benefit-from-visual-feedback-during-gesture-interaction-on-mobile-touchscreen-devices/
- [C4] Examining Fitts' and FFitts' Law Models for Children's Pointing Tasks on Touchscreens (Woodward et al., AVI 2020). University of Florida INIT Lab, 2020. https://init.cise.ufl.edu/?p=3232
- [C5] Shaw et al., gesture characterization for children ages 5-10 (ICMI 2016), talk slides. University of Florida INIT Lab, 2016. *Unverified.* https://init.cise.ufl.edu/wp-content/uploads/sites/775/2017/05/Shaw-et-al-ICMI2016-Conference-talk-slides.pdf
- [C8] The 2025 Common Sense Census: Media Use by Kids Zero to Eight. Common Sense Media, 2025. https://www.commonsensemedia.org/research/the-2025-common-sense-census-media-use-by-kids-zero-to-eight
- [C9] Pok Pok Playroom is a Beautiful New Kids App. MacRumors, 2021-05-20. https://www.macrumors.com/2021/05/20/pok-pok-playroom-now-available/
- [C10] Sago Mini World: Kids Games (App Store listing). Apple App Store, undated. https://apps.apple.com/us/app/id874425722?l=en-us
- [C11] Number Match 3 Learning Game (App Store listing). Apple App Store, undated. https://apps.apple.com/us/app/-/id952373894
- [D1] Prevalence and Characteristics of Manipulative Design in Mobile Applications Used by Children (Radesky et al.). Deceptive Patterns (summary of JAMA Network Open paper), 2022. https://deceptive.design/articles/prevalence-and-characteristics-of-manipulative-design-in-mobile-applications-used-by-children
- [D2] Advertising in Kids' Apps More Prevalent Than Parents May Realize. Michigan Medicine, 2018. *Unverified.* https://www.michiganmedicine.org/health-lab/advertising-kids-apps-more-prevalent-parents-may-realize
- [D3] Apps for children should emphasize parent and child choice (Hiniker et al., CHI 2018). University of Washington, 2018-05-01. https://www.washington.edu/news/?p=57478
- [D6] Popular kids' apps use deceptive tactics to draw them to paid content. Digital Child (QUT), 2023. https://digitalchild.org.au/kids-apps-deceptive-design/
- [D7] Self-determination theory. Wikipedia, 2025. https://en.wikipedia.org/wiki/Self-determination_theory
- [D8] The undermining effect is a reality after all (Deci, Koestner, Ryan). Psychological Bulletin 125(6), via Ewha research portal, 1999. https://pure.ewha.ac.kr/en/publications/the-undermining-effect-is-a-reality-after-all-extrinsic-rewards-t/
- [D9] Age appropriate design code, Standard 13: nudge techniques. UK Information Commissioner's Office, 2020. *Unverified.* https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/childrens-information/childrens-code-guidance-and-resources/age-appropriate-design-a-code-of-practice-for-online-services/13-nudge-techniques/
- [D10] Pok Pok, Montessori Preschool (App Store listing). Apple App Store, 2026. https://apps.apple.com/au/app/id1550204730?l=en-au
- [D12] Toca Boca Celebrates 100 Million Downloads. Business Wire, 2015-09-15. *Unverified.* https://www.businesswire.com/news/home/20150915005582/en/Toca-Boca-Celebrates-100-Million-Downloads
- [D13] Endless Alphabet (App Store listing). Apple App Store, 2026. https://apps.apple.com/app/id591626572
- [E1] Game Accessibility Guidelines. Game Accessibility Guidelines, ongoing since 2012. https://gameaccessibilityguidelines.com/
- [E2] Game Accessibility Guidelines: Basic. Game Accessibility Guidelines, ongoing. https://gameaccessibilityguidelines.com/basic/
- [E3] Xbox Accessibility Guideline 118: Photosensitivity. Microsoft Learn, 2022 (updated 2026-06). https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/118
- [E4] Xbox Accessibility Guideline 117: Visual distractions and motion. Microsoft Learn, 2022 (updated 2026-06). https://learn.microsoft.com/en-us/gaming/accessibility/xbox-accessibility-guidelines/117
- [E6] ExploreByTouchHelper reference. Android Developers, current. https://developer.android.com/reference/androidx/customview/widget/ExploreByTouchHelper
- [E7] Android animations and reduced motion. DEV Community (Eevis Panula), undated. https://dev.to/eevajonnapanula/android-animations-and-reduced-motion-2em2
- [E8] Settings.Global.AnimatorDurationScale. Microsoft Learn (Android binding docs), 2026-10. https://learn.microsoft.com/en-us/dotnet/api/android.provider.settings.global.animatordurationscale?view=net-android-37.0
- [E9] Colorblind friendly game design. Bugnet, undated. https://bugnet.io/blog/colorblind-friendly-game-design
- [E10] Diamond Diaries Saga colour-blind review. We Are Colorblind, undated. https://wearecolorblind.com/examples/diamond-diaries-saga/
- [E13] Sago Mini accessibility. Family Gaming Database, undated. https://www.familygamingdatabase.com/accessibility/Sago+Mini
- [E15] Khan Academy Kids accessibility. Family Gaming Database, undated. https://www.familygamingdatabase.com/accessibility/Khan+Academy+Kids
- [F1] Families policy requirements. Google Play Console Help, 2026. https://support.google.com/googleplay/android-developer/answer/9893335
- [F2] Manage target audience and app content settings. Google Play Console Help, 2026. https://support.google.com/googleplay/android-developer/answer/9867159
- [F3] Closed testing and production access requirements for new personal accounts. Google Play Console Help, 2026. https://support.google.com/googleplay/android-developer/answer/14151465
- [F4] Teacher Approved apps. Google Play Console Help, 2026. https://support.google.com/googleplay/android-developer/answer/10075896
- [F5] Provide information for Google Play's Data safety section. Google Play Console Help, 2026. https://support.google.com/googleplay/android-developer/answer/10787469
- [F6] Complying with COPPA: Frequently Asked Questions. US Federal Trade Commission, 2026. https://www.ftc.gov/business-guidance/resources/complying-coppa-frequently-asked-questions
- [F7] Children's Online Privacy Protection Rule (final amendments), 2025-05904. Federal Register, 2025-04-22. *Not read directly; dates confirmed via F8.* https://www.federalregister.gov/documents/2025/04/22/2025-05904/childrens-online-privacy-protection-rule
- [F8] COPPA's Amended Rule Is Now In Full Effect: What Operators Need To Know. Finnegan, Henderson, Farabow, Garrett & Dunner LLP via Legal 500, 2026. https://www.legal500.com/intelligence/united-states/privacy/coppas-amended-rule-is-now-in-full-effect-what-operators-need-to-know
- [F10] PBS KIDS Games (App Store listing). Apple App Store (PBS KIDS), 2026. https://apps.apple.com/us/app/id1050773989
- [F11] Khan Academy Kids. Khan Academy, 2026. https://learn.khanacademy.org/khan-academy-kids/
- [F12] Google Play streamlines policies around kids apps as regulations tighten. TechCrunch, 2022-11-16. https://techcrunch.com/2022/11/16/google-play-streamlines-policies-around-kids-apps-as-regulations-tighten/
- [G4] Crafting Candy Crush's difficulty: blockers, level design, AI and the complexity staircase. PocketGamer.biz, undated. http://www.pocketgamer.biz/crafting-candy-crushs-difficulty-blockers-level-design-ai-and-the-complexity-staircase/
- [G5] How Toca Boca got 100M mobile downloads by putting kids first. GamesBeat, undated. https://gamesbeat.com/how-toca-boca-got-100m-mobile-downloads-by-putting-kids-first/
- [G6] An Adaptive Hint System for Puzzle Games: A Multimodal-based Approach (Hao et al.). University of Washington, 2022. https://faculty.washington.edu/weicaics/paper/papers/HaoHZC2022.pdf
- [G8] How does juicy game feedback motivate? Testing curiosity, competence, and effectance (Kao et al., CHI 2024). Imperial College London, 2024. https://spiral.imperial.ac.uk/entities/publication/253c32f5-d124-4a23-88b5-9aaaf114608d
- [G11] Kids Match 3. AlternativeTo / Balxar International, undated. https://alternativeto.net/software/kids-match-3/about
- [G12] Toca Boca Hair Salon 4 (App Store listing). Apple App Store / Piknik / Toca Boca, 2026. https://apps.apple.com/app/id1485387513
- [G13] Modeling Skill Progression in Children Through Novel Multidimensional Probabilistic DDA. Hebrew University of Jerusalem (CRIS), 2026. https://cris.huji.ac.il/en/publications/modeling-skill-progression-in-children-through-novel-multidimensi/
- [G14] Meiners, Rich, Hicks et al., lushness study (FDG 2025). University of Staffordshire, 2025. https://eprints.staffs.ac.uk/8847
