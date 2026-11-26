package com.donuts.game

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * How loud each sound is, by name, and the cap no sound may pass (pure, unit tested).
 *
 * There are three kinds, each with its own levels: the small, frequent sounds ([Kind.FEEDBACK]: blips, boops,
 * landings), the clears ([Kind.CLEAR]) and the rewards ([Kind.CHEER]). Every feedback level is softer than the
 * loudest clear and the loudest cheer, so a pop or a celebration always stands out from the taps around it. Levels are a fraction of full scale; [LOUDNESS_CAP] is the hard ceiling and the
 * synth below clamps to it, so no level, however it is changed later, can make a sample louder.
 */
object SoundLevels {

    /** The loudest any sample may be, as a fraction of full 16-bit scale (about -6 dBFS). */
    const val LOUDNESS_CAP = 0.50f

    enum class Kind { FEEDBACK, CLEAR, CHEER }

    /** A named level of one [kind]. */
    enum class Level(val kind: Kind, val volume: Float) {
        BLIP(Kind.FEEDBACK, 0.35f),       // a donut joins the chain
        BOOP(Kind.FEEDBACK, 0.28f),       // a chain let go too short
        GIGGLE(Kind.FEEDBACK, 0.30f),     // the rare tap giggle
        LAND(Kind.FEEDBACK, 0.30f),       // new donuts land
        SHUFFLE(Kind.CLEAR, 0.38f),       // the board shuffles
        POP(Kind.CLEAR, 0.45f),           // a chain pops
        PROMPT(Kind.CHEER, 0.34f),        // a picture prompt is finished
        SPARKLE(Kind.CHEER, 0.30f),       // the golden trill after a milestone
        FRIEND_SLIDE(Kind.CHEER, 0.38f),  // a sliding note in a friend's jingle
        FRIEND(Kind.CHEER, 0.40f),        // a held note in a friend's jingle
        MILESTONE_SLIDE(Kind.CHEER, 0.40f),
        MILESTONE(Kind.CHEER, 0.42f),
    }

    /** [volume] clamped into 0..[LOUDNESS_CAP]; NaN is silence. */
    fun capped(volume: Float): Float = if (volume.isNaN()) 0f else volume.coerceIn(0f, LOUDNESS_CAP)

    /** The loudest level of [kind]. */
    fun loudest(kind: Kind): Float = Level.values().filter { it.kind == kind }.maxOf { it.volume }

    /** The softest level of [kind]. */
    fun softest(kind: Kind): Float = Level.values().filter { it.kind == kind }.minOf { it.volume }

    fun wave(phase: Double): Double = sin(phase)

    /** A fading sine of [freq] Hz for [seconds], at [volume] (capped). */
    fun tone(freq: Float, seconds: Float, volume: Float, rate: Int = 44100): ShortArray {
        val n = (rate * seconds).toInt().coerceAtLeast(0)
        val v = capped(volume)
        return ShortArray(n) { i ->
            val t = i.toFloat() / rate
            val env = (1f - t / seconds).coerceAtLeast(0f).pow(0.6f)
            (wave(2.0 * PI * freq * t) * env * v * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** A sine sliding from [from] to [to] Hz over [seconds], fading to 30 %, at [volume] (capped). */
    fun sweep(from: Float, to: Float, seconds: Float, volume: Float, rate: Int = 44100): ShortArray {
        val n = (rate * seconds).toInt().coerceAtLeast(0)
        val v = capped(volume)
        val buf = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toFloat() / n
            phase += 2.0 * PI * (from + (to - from) * t) / rate
            buf[i] = (wave(phase) * (1f - t * 0.7f) * v * Short.MAX_VALUE).toInt().toShort()
        }
        return buf
    }
}
