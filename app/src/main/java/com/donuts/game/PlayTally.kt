package com.donuts.game

/**
 * A tally of how Steven really plays, kept in [Prefs] on this phone and never sent anywhere (the app has no
 * network permission at all). It counts every released chain by length (taps and twos that only wobble, and
 * chains that pop), the power-ups those chains fired and the chains a golden donut helped. A grown-up reads it
 * on the Grown-ups page; its real chain lengths replace the guessed slip rates in BalanceSim. No Android types.
 *
 * Counts stop at [Int.MAX_VALUE] rather than wrap. Lengths of [MAX_LEN] or more share the last slot.
 */
class PlayTally private constructor(private val len: IntArray, private val pow: IntArray, golden: Int) {

    /** Chains a golden (wild) donut was part of. */
    var golden: Int = golden
        private set

    constructor() : this(IntArray(MAX_LEN + 1), IntArray(PowerUp.values().size), 0)

    /** Counts one released chain of [length] cells (1 or 2 for a wobble), the [powerUp] it fired and whether a golden donut was in it. */
    fun record(length: Int, powerUp: PowerUp = PowerUp.NONE, hadGolden: Boolean = false) {
        if (length < 1) return
        bump(len, length.coerceAtMost(MAX_LEN))
        if (powerUp != PowerUp.NONE) bump(pow, powerUp.ordinal)
        if (hadGolden && golden < Int.MAX_VALUE) golden++
    }

    /** Chains of exactly [length] cells; [MAX_LEN] means that many or more. */
    fun chains(length: Int): Int = if (length in 1..MAX_LEN) len[length] else 0

    /** Power-ups of kind [p] fired so far. */
    fun powerUps(p: PowerUp): Int = if (p == PowerUp.NONE) 0 else pow[p.ordinal]

    /** Single taps and chains of two: they wobble and teach "3!", nothing pops. */
    val tries: Long get() = len[1].toLong() + len[2]

    /** Chains of three or more, the ones that popped. */
    val pops: Long get() = (3..MAX_LEN).sumOf { len[it].toLong() }

    /** Popped chains of [n] cells or more ([n] at least 3). */
    fun popsAtLeast(n: Int): Long = (n.coerceIn(3, MAX_LEN)..MAX_LEN).sumOf { len[it].toLong() }

    /** Percent of popped chains that had [n] or more cells, 0 before the first pop. */
    fun percentAtLeast(n: Int): Double = if (pops == 0L) 0.0 else 100.0 * popsAtLeast(n) / pops

    val isEmpty: Boolean get() = len.all { it == 0 }

    /** The text form for [Prefs]: `t1;<count per length 1..MAX_LEN>;<count per power-up>;<golden>`. */
    fun encode(): String =
        "t1;${(1..MAX_LEN).joinToString(",") { len[it].toString() }};${pow.joinToString(",")};$golden"

    /** What the Grown-ups page shows: a heading and three short lines. */
    fun lines(): List<String> {
        if (isEmpty) return listOf(HEADING, "Nothing yet: it fills in as Steven plays.")
        val sizes = (3..7).joinToString("  ") { n ->
            if (n < 7) "$n: ${len[n]}" else "7+: ${popsAtLeast(7)}"
        }
        val share = "%.1f".format(java.util.Locale.ROOT, percentAtLeast(7))
        return listOf(
            HEADING,
            "Chains  $sizes",
            "Taps ${len[1]}  Twos ${len[2]}  7+ share $share%  Golden $golden",
            "Bombs ${powerUps(PowerUp.BOMB)}  Rows ${powerUps(PowerUp.ROW_BLAST)}  " +
                "Bursts ${powerUps(PowerUp.COLOR_BURST)}  Rings ${powerUps(PowerUp.RING)}",
        )
    }

    private fun bump(a: IntArray, i: Int) { if (a[i] < Int.MAX_VALUE) a[i]++ }

    companion object {
        /** Chains this long or longer share one slot. 12 is longer than any chain seen in BalanceSim runs. */
        const val MAX_LEN = 12
        const val HEADING = "Steven's play so far (stays on this phone)"

        /** An empty tally for null, garbage, another version, wrong slot counts or a negative count. */
        fun decode(text: String?): PlayTally {
            if (text.isNullOrEmpty()) return PlayTally()
            val parts = text.split(';')
            if (parts.size != 4 || parts[0] != "t1") return PlayTally()
            val lens = ints(parts[1]) ?: return PlayTally()
            val pows = ints(parts[2]) ?: return PlayTally()
            val gold = parts[3].toIntOrNull() ?: return PlayTally()
            if (lens.size != MAX_LEN || pows.size != PowerUp.values().size) return PlayTally()
            if (gold < 0 || lens.any { it < 0 } || pows.any { it < 0 }) return PlayTally()
            return PlayTally(IntArray(MAX_LEN + 1) { if (it == 0) 0 else lens[it - 1] }, pows, gold)
        }

        private fun ints(s: String): IntArray? =
            s.split(',').map { it.toIntOrNull() ?: return null }.toIntArray()
    }
}
