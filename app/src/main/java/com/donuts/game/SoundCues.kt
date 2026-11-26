package com.donuts.game

/**
 * What Steven sees for every sound, so no sound carries something the screen does not (pure, unit tested).
 *
 * Each [SoundLevels.Level] names the picture that plays with it and the picture that still plays under reduced
 * motion (Calm, or the phone's animations off), when the bouncy one is switched off. A new sound level fails
 * `SoundCuesTest` until it has both. The game draws these; this table is the audit that keeps them honest.
 */
object SoundCues {

    /** The visual cue for a sound: [bouncy] with full motion, [calm] under reduced motion. */
    class Cue(val bouncy: String, val calm: String)

    val cues: Map<SoundLevels.Level, Cue> = mapOf(
        SoundLevels.Level.BLIP to Cue("the donut joins the chain string and pings", "the chain string and the ping"),
        SoundLevels.Level.BOOP to Cue("the tapped donuts wobble; a wrong gate answer shakes and flashes pink", "a white ring round the tapped donuts (drawTapCue); the gate answer flashes pink"),
        SoundLevels.Level.GIGGLE to Cue("a big wiggle and a giggle word", "a white ring and the giggle word"),
        SoundLevels.Level.LAND to Cue("new donuts drop in and squash", "new donuts drop into place"),
        SoundLevels.Level.SHUFFLE to Cue("the no-moves warning, then every donut shrinks and pops back", "the warning, then the donuts shrink and pop back"),
        SoundLevels.Level.POP to Cue("the chain pops with a word and a flash", "the chain pops, the word, a softer flash"),
        // Also the topping tile tap (the ring and check move to it) and a shop piece popping in on the home screen
        SoundLevels.Level.PROMPT to Cue(
            "the prompt chip turns gold with a star and sparkles; a tapped topping hops; a shop piece springs in",
            "the chip turns gold with a star; the worn ring and check move; a shop piece glows in"),
        SoundLevels.Level.SPARKLE to Cue("golden sparkles round the banner", "still golden sparkles round the banner"),
        SoundLevels.Level.FRIEND_SLIDE to Cue("the friend pops up and flies to the star", "the friend shows and flies to the star"),
        SoundLevels.Level.FRIEND to Cue("the friend pops up and flies to the star", "the friend shows and flies to the star"),
        SoundLevels.Level.MILESTONE_SLIDE to Cue("the banner and confetti", "the banner and a little confetti"),
        SoundLevels.Level.MILESTONE to Cue("the banner and confetti", "the banner and a little confetti"),
    )
}
