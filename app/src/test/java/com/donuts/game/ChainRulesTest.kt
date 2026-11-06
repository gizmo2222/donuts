package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.random.Random

class ChainRulesTest {
    private val S = DonutType.STRAWBERRY
    private val C = DonutType.CHOCOLATE

    @Test fun allGoldenChainAcceptsAnyType() {
        assertTrue(ChainRules.accepts(listOf(S), listOf(true), C, false))
        assertTrue(ChainRules.accepts(listOf(S, C), listOf(true, true), DonutType.MATCHA, false))
    }

    @Test fun mixedChainLocksToFirstNonGolden() {
        assertTrue(ChainRules.accepts(listOf(C, S), listOf(true, false), S, false))
        assertFalse(ChainRules.accepts(listOf(C, S), listOf(true, false), C, false))
        assertTrue(ChainRules.accepts(listOf(S, S), listOf(false, false), C, true))
        assertFalse(ChainRules.accepts(listOf(S, S), listOf(false, false), C, false))
    }

    @Test fun agreesWithPeekChainClear() {
        for (seed in 0..300) {
            val rnd = Random(seed)
            val board = GameBoard(1, 4, rnd)
            for (c in 0 until 4)
                board.grid[0][c] = GameCell(DonutType.values().random(rnd), 0, c, isGolden = rnd.nextDouble() < 0.5)
            val chain = (0 until 3).map { Pair(0, it) }
            val prefix = chain.take(2)
            val g = board.grid[0]
            val accepted = ChainRules.accepts(prefix.map { g[it.second].type }, prefix.map { g[it.second].isGolden },
                g[2].type, g[2].isGolden)
            // a prefix that is itself legal is extended exactly when peekChainClear accepts three
            val prefixLegal = prefix.map { g[it.second] }.filterNot { it.isGolden }.map { it.type }.distinct().size <= 1
            if (prefixLegal) assertEquals("seed $seed", board.peekChainClear(chain) != null, accepted)
        }
    }
}
