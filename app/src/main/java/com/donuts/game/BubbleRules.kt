package com.donuts.game

/**
 * The bubble donut (B37): a long chain leaves a donut wrapped in a shiny bubble where the finger lifted, and a
 * tap pops it with the 3×3 around it, whenever Steven likes. Pure rules, no Android types (unit tested).
 *
 * A bubble never runs out and never pops by itself: automatic cascades skip it ([GameBoard.findMatches] treats it
 * as a gap), so it waits for a tap. It is still a donut of its type, so a drag chain can take it in; then it bursts
 * as well, and so does any bubble a burst or a power-up reaches. A shuffle keeps bubbles where they are and New
 * game clears them. At most [MAX_ON_BOARD] at once, so the board never fills with them.
 */
object BubbleRules {
    /** A chain this long (or any ring) leaves a bubble. */
    const val MIN_CHAIN = 6
    /** No new bubble while this many are on the board. */
    const val MAX_ON_BOARD = 2

    /** Whether a released chain of [length] cells, closed into a ring when [looped], earns a bubble. */
    fun earns(length: Int, looped: Boolean): Boolean = looped || length >= MIN_CHAIN
}

/** Every bubble cell on the board, row by row. */
fun GameBoard.bubbles(): List<Pair<Int, Int>> =
    (0 until rows).flatMap { r -> (0 until cols).filter { c -> grid[r][c].isBubble }.map { c -> Pair(r, c) } }

/** Wraps the donut at [r],[c] in a bubble. */
fun GameBoard.markBubble(r: Int, c: Int) {
    grid[r][c] = grid[r][c].copy(isBubble = true)
}

/** The 3×3 block around [r],[c], clipped to the board, centre included. */
internal fun GameBoard.around(r: Int, c: Int): List<Pair<Int, Int>> =
    (-1..1).flatMap { dr -> (-1..1).map { dc -> Pair(r + dr, c + dc) } }
        .filter { (rr, cc) -> rr in 0 until rows && cc in 0 until cols }

/** Adds to [cleared] the 3×3 of every bubble in it, and of every bubble those reach; returns the bubbles burst. */
private fun GameBoard.burst(cleared: MutableSet<Pair<Int, Int>>): List<Pair<Int, Int>> {
    val burst = ArrayList<Pair<Int, Int>>()
    var wave = cleared.filter { (r, c) -> grid[r][c].isBubble }
    while (wave.isNotEmpty()) {
        burst += wave
        val next = ArrayList<Pair<Int, Int>>()
        for ((r, c) in wave) for (p in around(r, c)) if (cleared.add(p) && grid[p.first][p.second].isBubble) next += p
        wave = next
    }
    return burst
}

/** A tap on [r],[c]: when that donut is in a bubble, the clear that pops it (a [PowerUp.BUBBLE]); otherwise null. */
fun GameBoard.peekBubblePop(r: Int, c: Int): ChainResult? {
    if (r !in 0 until rows || c !in 0 until cols || !grid[r][c].isBubble) return null
    val cleared = linkedSetOf(Pair(r, c))
    val burst = burst(cleared)
    return ChainResult(listOf(Pair(r, c)), cleared.drop(1), PowerUp.BUBBLE, grid[r][c].type, bursting = burst)
}

/**
 * A drawn chain's [result] with its bubbles: any bubble in the clear bursts its 3×3 (a plain chain then counts
 * as a [PowerUp.BUBBLE]), and a chain that [BubbleRules.earns] one leaves a new bubble on its last cell, unless
 * [BubbleRules.MAX_ON_BOARD] would still be on the board after the clear.
 */
fun GameBoard.withBubbles(result: ChainResult, looped: Boolean = false): ChainResult {
    val cleared = LinkedHashSet(result.chainCells + result.bonusCells)
    val burst = burst(cleared)
    val left = bubbles().count { it !in cleared }
    val at = if (BubbleRules.earns(result.chainCells.size, looped) && left < BubbleRules.MAX_ON_BOARD)
        result.chainCells.last() else null
    val chainSet = result.chainCells.toSet()
    val power = if (burst.isNotEmpty() && result.powerUp == PowerUp.NONE) PowerUp.BUBBLE else result.powerUp
    return result.copy(bonusCells = cleared.filter { it !in chainSet }, powerUp = power, bursting = burst, bubbleAt = at)
}
