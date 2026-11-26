package com.donuts.game

import com.donuts.game.BoardFixtures.boardOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class HintRulesTest {
    private fun assertDraggable(board: GameBoard, path: List<Pair<Int, Int>>) {
        assertTrue("length ${path.size}", path.size in 3..HintRules.LONGEST)
        assertEquals("no cell twice", path.size, path.toSet().size)
        for (i in 1 until path.size) {
            val (a, b) = path[i - 1] to path[i]
            assertTrue("$a next to $b", maxOf(Math.abs(a.first - b.first), Math.abs(a.second - b.second)) == 1)
        }
        assertNotNull("the board takes it as a chain", board.peekChainClear(path))
    }

    @Test
    fun aFiveIsShownOverAThree() {
        val b = boardOf(
            "SSSCV",
            "BVMBM",
            "CCCCC",
            "MVBMV"
        )
        val hint = HintRules.pick(b, null, Random(1))
        assertEquals(5, hint.size)
        assertTrue(hint.all { (r, c) -> b.grid[r][c].type == DonutType.CHOCOLATE })
        assertDraggable(b, hint)
    }

    @Test
    fun theHintStopsAtFiveOnALongerChain() {
        val b = boardOf("SSSSSSS", "BCBCBCB")
        val hint = HintRules.pick(b, null, Random(1))
        assertEquals(HintRules.LONGEST, hint.size)
        assertDraggable(b, hint)
    }

    @Test
    fun thePromptsDonutWinsByOneCellButNotByTwo() {
        val fourVsFive = boardOf(
            "SSSSV",
            "BVMBM",
            "CCCCC"
        )
        val four = HintRules.pick(fourVsFive, DonutType.STRAWBERRY, Random(1))
        assertEquals(4, four.size)
        assertTrue(four.all { (r, c) -> fourVsFive.grid[r][c].type == DonutType.STRAWBERRY })

        val threeVsFive = boardOf(
            "SSSBV",
            "BVMBM",
            "CCCCC"
        )
        val five = HintRules.pick(threeVsFive, DonutType.STRAWBERRY, Random(1))
        assertEquals(5, five.size)
        assertTrue(five.all { (r, c) -> threeVsFive.grid[r][c].type == DonutType.CHOCOLATE })
    }

    @Test
    fun noThreeMeansNoHint() {
        val b = boardOf("SCB", "VMS", "CBV")
        assertTrue(HintRules.pick(b, null, Random(1)).isEmpty())
    }

    @Test
    fun goldenDonutsAreWildInTheHint() {
        val b = boardOf(
            "SsBV",
            "CMSC",
            "VBMB"
        )
        val hint = HintRules.pick(b, null, Random(1))
        assertTrue(0 to 1 in hint)
        assertDraggable(b, hint)
    }

    @Test
    fun equalChainsAreChosenByChanceNotAlwaysTheTopLeft() {
        val b = boardOf(
            "SSSBV",
            "BVMBM",
            "MVCCC"
        )
        val firsts = (1..40).map { HintRules.pick(b, null, Random(it)).toSet() }.toSet()
        assertTrue("both trios turn up: $firsts", firsts.any { s -> s.all { it.first == 0 } } && firsts.any { s -> s.all { it.first == 2 } })
    }

    @Test
    fun seededGameBoardsAlwaysGetADraggableHint() {
        for (seed in 1..60) for (size in listOf(6, 8)) {
            val b = GameBoard.forGrid(size, Random(seed))
            val hint = HintRules.pick(b, DonutType.values()[seed % 5], Random(seed))
            if (b.hasValidMoves()) assertDraggable(b, hint) else assertTrue(hint.isEmpty())
        }
    }

    @Test
    fun theTraceFadesInStepsSlowlyRestsFadesAndLoops() {
        val n = 4
        val start = HintRules.traceAt(0, n, false)
        assertEquals(0f, start.seg, 0f); assertEquals(0f, start.alpha, 0f)
        val shown = HintRules.traceAt(HintRules.FADE_MS, n, false)
        assertEquals(0f, shown.seg, 1e-4f); assertEquals(1f, shown.alpha, 0f)
        val half = HintRules.traceAt(HintRules.FADE_MS + HintRules.STEP_MS / 2, n, false)
        assertEquals(0.5f, half.seg, 1e-4f)
        val one = HintRules.traceAt(HintRules.FADE_MS + HintRules.STEP_MS, n, false)
        assertEquals(1f, one.seg, 1e-4f)
        val end = HintRules.FADE_MS + HintRules.STEP_MS * (n - 1)
        assertEquals(3f, HintRules.traceAt(end + 10, n, false).seg, 0f)
        assertEquals(1f, HintRules.traceAt(end + HintRules.HOLD_MS - 1, n, false).alpha, 0f)
        assertEquals(0f, HintRules.traceAt(end + HintRules.HOLD_MS + HintRules.FADE_MS + 10, n, false).alpha, 0f)
        assertEquals(start, HintRules.traceAt(HintRules.loopMs(n), n, false))
        // Each step takes the slow STEP_MS and the finger only moves forward within a pass
        var last = -1f
        for (t in 0 until end step 50) { val s = HintRules.traceAt(t, n, false).seg; assertTrue(s >= last); last = s }
        assertTrue(HintRules.STEP_MS >= 800)
    }

    @Test
    fun underReducedMotionTheFingerRestsAndThePathShowsWhole() {
        for (t in listOf(0L, 300L, 5_000L)) {
            val p = HintRules.traceAt(t, 5, true)
            assertTrue(p.still); assertEquals(4f, p.seg, 0f); assertEquals(1f, p.alpha, 0f)
        }
    }
}
