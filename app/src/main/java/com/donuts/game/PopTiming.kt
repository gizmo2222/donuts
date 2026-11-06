package com.donuts.game

/**
 * When each cell of a player clear starts to pop. Pure maths, no drawing, so it is unit tested.
 * A plain chain pops all at once; a power-up pops the chain first and the bonus cells in a ripple
 * outward from the chain's middle donut, after a short lead-in that shows the power-up building.
 */
object PopTiming {
    const val STEP_MS = 60L

    /** How long the power-up builds (swell, wiggle) before its first bonus cell pops. */
    fun leadMs(p: PowerUp): Long = when (p) {
        PowerUp.NONE        -> 0L
        PowerUp.BOMB        -> 160L
        PowerUp.ROW_BLAST   -> 60L
        PowerUp.COLOR_BURST -> 320L
    }

    /** The middle donut of the chain: where the power-up starts. */
    fun origin(r: ChainResult): Pair<Int, Int> = r.chainCells[r.chainCells.size / 2]

    /** Pop delay in ms for every chain and bonus cell. */
    fun delays(r: ChainResult): Map<Pair<Int, Int>, Long> {
        val lead = leadMs(r.powerUp)
        val (or, oc) = origin(r)
        val out = HashMap<Pair<Int, Int>, Long>()
        for (cell in r.chainCells) out[cell] = 0L
        if (r.powerUp == PowerUp.BOMB) out[origin(r)] = lead
        for ((row, col) in r.bonusCells) {
            val dist = if (r.powerUp == PowerUp.ROW_BLAST) Math.abs(col - oc)
                       else maxOf(Math.abs(row - or), Math.abs(col - oc))
            out[Pair(row, col)] = lead + dist * STEP_MS
        }
        return out
    }

    /** The whole pop lasts until the last cell has finished its own [popMs]. */
    fun totalMs(delays: Map<Pair<Int, Int>, Long>, popMs: Long): Long =
        (delays.values.maxOrNull() ?: 0L) + popMs
}
