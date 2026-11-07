package com.donuts.game

import java.io.File
import kotlin.random.Random
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Balance simulation: five player models x grid 6 and 8 x 25 seeds, 150 moves each, played against the
 * real [GameBoard] rules. Skipped in the normal suite; tools\balance.ps1 runs it with the system property
 * `donuts.balance` set to the output folder (via the init script tools\balance.gradle).
 * Writes scenarios.csv, moves.csv, summary.md and invariants.txt (one line per broken rule; empty when none).
 */
class BalanceSim {

    private val outDir: String? = System.getProperty("donuts.balance")?.takeIf { it.isNotBlank() }

    private val strategies = listOf("kidShort", "kidRandom", "greedyLong", "hint", "goldenHunter")
    private val grids = listOf(6, 8)
    private val seeds = 0..24
    private val movesPerScenario = 150
    private val milestones = intArrayOf(10, 25, 50, 100, 200, 500)
    private val stickerCount = 11 // stickers 0..10
    private val dfsCap = 20000
    private val types = DonutType.values()

    private class Comp(val cells: IntArray, val type: DonutType)

    private class Analysis(val comps: List<Comp>, val bestAny: IntArray, val bestGold: IntArray?)

    private class MoveRow(
        val strategy: String, val grid: Int, val seed: Int, val move: Int,
        val chainLen: Int, val powerUp: PowerUp, val cleared: Int, val passes: Int,
        val options: Int, val bestPossible: Int, val golden: Int, val shuffled: Boolean, val goldenInChain: Boolean
    )

    private class Scenario(val strategy: String, val grid: Int, val seed: Int) {
        var total = 0
        val tiers = IntArray(4)
        var shuffles = 0
        var maxCascade = 0
        var singleOption = 0
        var lowBest = 0
        var longestDry = 0
        var goldenPopped = 0
        var goldenSum = 0L
        var movesPlayed = 0
        var cleared = IntArray(DonutType.values().size)
        var passesSum = 0
        val milestoneMove = IntArray(6) { -1 }
        val stickerMove = IntArray(11) { -1 }
    }

    // ---- board analysis ------------------------------------------------------------------------

    private class Geometry(val n: Int) {
        val nb: Array<IntArray> = Array(n * n) { i ->
            val r = i / n; val c = i % n
            val l = ArrayList<Int>()
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val rr = r + dr; val cc = c + dc
                if (rr in 0 until n && cc in 0 until n) l.add(rr * n + cc)
            }
            l.toIntArray()
        }
    }

    private class Dfs(val geo: Geometry) {
        val eligible = BooleanArray(geo.n * geo.n)
        val vis = BooleanArray(geo.n * geo.n)
        val path = IntArray(geo.n * geo.n)
        var nodes = 0
        var limit = 0
        var bestAny = IntArray(0)
        var bestGold = IntArray(0)
        var golden = BooleanArray(0)

        fun run(comp: IntArray, budget: Int) {
            for (c in comp) eligible[c] = true
            val perStart = maxOf(50, budget / comp.size)
            var compBest = 0
            for (s in comp) {
                nodes = 0; limit = perStart
                go(s, 1, 0)
                compBest = maxOf(compBest, bestAny.size)
                if (bestAny.size == comp.size && comp.none { golden[it] }) break
            }
            for (c in comp) eligible[c] = false
        }

        private fun go(cell: Int, depth: Int, g: Int) {
            if (nodes++ > limit) return
            path[depth - 1] = cell
            vis[cell] = true
            val gg = g + if (golden[cell]) 1 else 0
            if (depth > bestAny.size) bestAny = path.copyOf(depth)
            if (gg > 0 && depth > bestGold.size) bestGold = path.copyOf(depth)
            for (nx in geo.nb[cell]) if (eligible[nx] && !vis[nx]) go(nx, depth + 1, gg)
            vis[cell] = false
        }
    }

    private fun analyze(board: GameBoard, geo: Geometry, dfs: Dfs): Analysis {
        val n = geo.n
        val golden = BooleanArray(n * n) { board.grid[it / n][it % n].isGolden }
        val comps = ArrayList<Comp>()
        for (t in types) {
            val seen = BooleanArray(n * n)
            fun ok(i: Int) = golden[i] || board.grid[i / n][i % n].type == t
            for (i in 0 until n * n) {
                if (seen[i] || !ok(i)) continue
                val cells = ArrayList<Int>()
                val q = ArrayDeque<Int>()
                q.add(i); seen[i] = true
                while (q.isNotEmpty()) {
                    val x = q.removeFirst(); cells.add(x)
                    for (y in geo.nb[x]) if (!seen[y] && ok(y)) { seen[y] = true; q.add(y) }
                }
                if (cells.size < 3) continue
                // A component of golden cells only fits every type; count it once, under the first type.
                if (cells.all { golden[it] } && t != types[0]) continue
                comps.add(Comp(cells.toIntArray(), t))
            }
        }
        dfs.golden = golden
        dfs.bestAny = IntArray(0); dfs.bestGold = IntArray(0)
        val budget = dfsCap / maxOf(1, comps.size)
        for (c in comps) dfs.run(c.cells, budget)
        return Analysis(comps, dfs.bestAny, dfs.bestGold.takeIf { it.isNotEmpty() })
    }

    // ---- strategies ----------------------------------------------------------------------------

    private fun randomWalk(comp: Comp, geo: Geometry, rng: Random, exact3: Boolean): IntArray? {
        val inComp = HashSet<Int>(comp.cells.size * 2).also { s -> comp.cells.forEach { s.add(it) } }
        repeat(40) {
            val path = ArrayList<Int>()
            path.add(comp.cells[rng.nextInt(comp.cells.size)])
            while (true) {
                if (exact3 && path.size == 3) break
                if (!exact3 && path.size >= 3 && rng.nextBoolean()) break
                val cand = geo.nb[path.last()].filter { it in inComp && it !in path }
                if (cand.isEmpty()) break
                path.add(cand[rng.nextInt(cand.size)])
            }
            if (path.size >= 3) return path.toIntArray()
        }
        return null
    }

    private fun choose(strategy: String, board: GameBoard, an: Analysis, geo: Geometry, rng: Random): IntArray {
        val n = geo.n
        return when (strategy) {
            "kidShort" -> randomWalk(an.comps[rng.nextInt(an.comps.size)], geo, rng, true) ?: an.bestAny
            "kidRandom" -> randomWalk(an.comps[rng.nextInt(an.comps.size)], geo, rng, false) ?: an.bestAny
            "greedyLong" -> an.bestAny
            "hint" -> board.findHint().map { (r, c) -> r * n + c }.toIntArray().takeIf { it.size >= 3 } ?: an.bestAny
            "goldenHunter" -> an.bestGold?.takeIf { it.size >= 3 } ?: an.bestAny
            else -> error(strategy)
        }
    }

    /** Set by [verify] when a chain has a step that is not 8-adjacent; the chain is still played (peek accepts it). */
    private var adjacency: String? = null

    /** Returns a description of the first fatal broken chain rule, or null. */
    private fun verify(board: GameBoard, chain: List<Pair<Int, Int>>): String? {
        adjacency = null
        val ts = ArrayList<DonutType>(); val gold = ArrayList<Boolean>()
        for ((i, p) in chain.withIndex()) {
            val (r, c) = p
            if (r !in 0 until board.rows || c !in 0 until board.cols) return "cell $p off board"
            if (p in chain.subList(0, i)) return "repeated cell $p"
            if (i > 0) {
                val (pr, pc) = chain[i - 1]
                if (maxOf(Math.abs(pr - r), Math.abs(pc - c)) != 1) adjacency = "step $i not 8-adjacent"
            }
            val cell = board.grid[r][c]
            if (!ChainRules.accepts(ts, gold, cell.type, cell.isGolden)) return "ChainRules rejects step $i"
            ts.add(cell.type); gold.add(cell.isGolden)
        }
        if (chain.size < 3) return "chain shorter than 3"
        if (board.peekChainClear(chain) == null) return "peekChainClear null"
        return null
    }

    // ---- one scenario --------------------------------------------------------------------------

    private fun play(sIdx: Int, n: Int, seed: Int, moves: MutableList<MoveRow>, bad: MutableList<String>): Scenario {
        val strategy = strategies[sIdx]
        val sc = Scenario(strategy, n, seed)
        val board = GameBoard(n, n, Random(seed * 7919 + sIdx))
        val rng = Random(seed.toLong() * 104729L + sIdx + 1)
        val geo = Geometry(n); val dfs = Dfs(geo)
        var bestChain = 0; var goldenPoppedFlag = false; var dry = 0
        fun fail(move: Int, msg: String) { bad.add("$strategy grid=$n seed=$seed move=$move: $msg") }

        for (m in 1..movesPerScenario) {
            try {
                var an = analyze(board, geo, dfs)
                if (an.comps.isEmpty()) {
                    fail(m, "no chain available on board before the move; shuffling")
                    board.shuffle(); sc.shuffles++
                    an = analyze(board, geo, dfs)
                    if (an.comps.isEmpty()) { fail(m, "still no chain after shuffle; scenario stopped"); break }
                }
                val goldenOnBoard = board.grid.sumOf { row -> row.count { it.isGolden } }
                val chain = choose(strategy, board, an, geo, rng).map { it / n to it % n }
                val err = verify(board, chain)
                if (err != null) { fail(m, err); break }
                adjacency?.let { fail(m, "$it (chain of ${chain.size} played anyway)") }
                val before = board.totalCleared
                val res = board.peekChainClear(chain)!!
                val goldenIn = (res.chainCells + res.bonusCells).any { (r, c) -> board.grid[r][c].isGolden }
                board.clearChain(res)
                var passes = 0; var cascade = 0
                while (passes < 100) { val k = board.resolveOnce(); if (k == 0) break; passes++; cascade += k }
                if (passes >= 100) fail(m, "cascade did not end in 100 passes")
                val cleared = res.chainCells.size + res.bonusCells.size + cascade
                if (board.totalCleared - before != cleared) fail(m, "counter delta ${board.totalCleared - before} != $cleared")
                if (board.findMatches().isNotEmpty()) fail(m, "matches left after cascades")
                var shuffled = false
                if (!board.hasValidMoves()) {
                    board.shuffle(); shuffled = true; sc.shuffles++
                    if (!board.hasValidMoves()) fail(m, "no valid move after shuffle")
                    if (board.findMatches().isNotEmpty()) fail(m, "matches after shuffle")
                }
                sc.movesPlayed++
                sc.total += cleared
                sc.tiers[res.powerUp.ordinal]++
                sc.maxCascade = maxOf(sc.maxCascade, passes)
                sc.passesSum += passes
                if (an.comps.size == 1) sc.singleOption++
                if (an.bestAny.size <= 4) sc.lowBest++
                if (res.powerUp == PowerUp.NONE) { dry++; sc.longestDry = maxOf(sc.longestDry, dry) } else dry = 0
                if (goldenIn) { goldenPoppedFlag = true; sc.goldenPopped++ }
                sc.goldenSum += goldenOnBoard
                bestChain = maxOf(bestChain, chain.size)
                for ((i, ms) in milestones.withIndex()) if (sc.milestoneMove[i] < 0 && sc.total >= ms) sc.milestoneMove[i] = m
                val stats = StickerStats(
                    lifetime = sc.total,
                    highScore6x6 = if (n == 6) sc.total else 0,
                    highScore8x8 = if (n == 8) sc.total else 0,
                    bestChain = bestChain, goldenPopped = goldenPoppedFlag, sessions = 1
                )
                for (i in 0 until stickerCount) if (sc.stickerMove[i] < 0 && StickerRules.isEarned(i, stats)) sc.stickerMove[i] = m
                moves.add(MoveRow(strategy, n, seed, m, chain.size, res.powerUp, cleared, passes,
                    an.comps.size, an.bestAny.size, goldenOnBoard, shuffled, goldenIn))
            } catch (e: Throwable) {
                fail(m, "exception $e"); break
            }
        }
        sc.cleared = board.clearedCounts()
        return sc
    }

    // ---- reporting -----------------------------------------------------------------------------

    private fun pct(v: List<Int>, p: Int): String =
        if (v.isEmpty()) "-" else v.sorted().let { it[Math.round(p / 100.0 * (it.size - 1)).toInt()].toString() }

    private fun pctD(v: List<Double>, p: Int): String =
        if (v.isEmpty()) "-" else v.sorted().let { "%.2f".format(it[Math.round(p / 100.0 * (it.size - 1)).toInt()]) }

    private fun f1(x: Double) = "%.1f".format(x)
    private fun f2(x: Double) = "%.2f".format(x)

    private fun summary(scs: List<Scenario>, moves: List<MoveRow>, seconds: Double, bad: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("# Balance simulation")
        sb.appendLine()
        sb.appendLine("${scs.size} scenarios, ${moves.size} moves, ${f1(seconds)} s. Invariant failures: ${bad.size}.")
        sb.appendLine("Rows are strategy x grid over seeds 0..24; p10/p50/p90 are across scenarios.")
        sb.appendLine()
        for (n in grids) for (st in strategies) {
            val g = scs.filter { it.strategy == st && it.grid == n }
            if (g.isEmpty()) continue
            val mv = moves.filter { it.strategy == st && it.grid == n }
            val played = g.sumOf { it.movesPlayed }.coerceAtLeast(1)
            val dpm = g.map { it.total.toDouble() / it.movesPlayed.coerceAtLeast(1) }
            sb.appendLine("## $st, ${n}x$n")
            sb.appendLine()
            sb.appendLine("- donuts per move: mean ${f2(dpm.average())}, p10 ${pctD(dpm, 10)}, p50 ${pctD(dpm, 50)}, p90 ${pctD(dpm, 90)}")
            sb.appendLine("- power-ups per 100 moves: bomb ${f1(g.sumOf { it.tiers[1] } * 100.0 / played)}, " +
                "row blast ${f1(g.sumOf { it.tiers[2] } * 100.0 / played)}, " +
                "colour burst ${f1(g.sumOf { it.tiers[3] } * 100.0 / played)}")
            sb.appendLine("- shuffles per 100 moves: ${f1(g.sumOf { it.shuffles } * 100.0 / played)}")
            sb.appendLine("- cascade passes per move: ${f2(g.sumOf { it.passesSum }.toDouble() / played)}, max depth seen ${g.maxOf { it.maxCascade }}")
            sb.appendLine("- moves with one option: ${f1(g.sumOf { it.singleOption } * 100.0 / played)}%, moves with best possible chain <= 4: ${f1(g.sumOf { it.lowBest } * 100.0 / played)}%")
            sb.appendLine("- longest run without a power-up: median ${pct(g.map { it.longestDry }, 50)}, max ${g.maxOf { it.longestDry }}")
            sb.appendLine("- mean chain length ${f2(mv.map { it.chainLen }.average())}, golden cells on board (mean) ${f2(g.sumOf { it.goldenSum }.toDouble() / played)}, golden-touching moves ${f1(g.sumOf { it.goldenPopped } * 100.0 / played)}%")
            val tot = g.sumOf { it.cleared.sum() }.coerceAtLeast(1)
            sb.appendLine("- cleared share per type: " + types.joinToString(", ") { t ->
                "${t.name.lowercase()} ${f1(g.sumOf { it.cleared[t.ordinal] } * 100.0 / tot)}%" })
            sb.appendLine("- milestone move (p50/p90, never): " + milestones.indices.joinToString("; ") { i ->
                val v = g.map { it.milestoneMove[i] }.filter { it >= 0 }
                "${milestones[i]}: ${pct(v, 50)}/${pct(v, 90)}, never ${g.size - v.size}" })
            sb.appendLine("- sticker first-earned move (p50/p90, never): " + (0 until stickerCount).joinToString("; ") { i ->
                val v = g.map { it.stickerMove[i] }.filter { it >= 0 }
                "#$i: ${pct(v, 50)}/${pct(v, 90)}, never ${g.size - v.size}" })
            sb.appendLine()
        }
        if (bad.isNotEmpty()) {
            sb.appendLine("## Invariant failures (first 30)")
            bad.take(30).forEach { sb.appendLine("- $it") }
        }
        return sb.toString()
    }

    @Test
    fun runBalanceSimulation() {
        assumeTrue(outDir != null)
        val dir = File(outDir!!).also { it.mkdirs() }
        val t0 = System.nanoTime()
        val moves = ArrayList<MoveRow>(40000)
        val scs = ArrayList<Scenario>()
        val bad = ArrayList<String>()
        for (n in grids) for (sIdx in strategies.indices) for (seed in seeds) scs.add(play(sIdx, n, seed, moves, bad))
        val seconds = (System.nanoTime() - t0) / 1e9

        val sc = StringBuilder("strategy,grid,seed,moves,total,none,bomb,rowBlast,colorBurst,shuffles,maxCascade,singleOptionMoves,lowBestMoves,longestDry,goldenMoves,")
        sc.append(types.joinToString(",") { "cleared_" + it.name.lowercase() }).append(',')
        sc.append(milestones.joinToString(",") { "ms$it" }).append(',')
        sc.appendLine((0 until stickerCount).joinToString(",") { "sticker$it" })
        for (s in scs) {
            sc.append("${s.strategy},${s.grid},${s.seed},${s.movesPlayed},${s.total},${s.tiers.joinToString(",")},${s.shuffles},${s.maxCascade},")
            sc.append("${s.singleOption},${s.lowBest},${s.longestDry},${s.goldenPopped},${s.cleared.joinToString(",")},")
            sc.appendLine("${s.milestoneMove.joinToString(",")},${s.stickerMove.joinToString(",")}")
        }
        File(dir, "scenarios.csv").writeText(sc.toString())

        val mv = StringBuilder("strategy,grid,seed,move,chainLen,powerUp,cleared,cascadePasses,options,bestPossible,goldenOnBoard,shuffled,goldenInChain\n")
        for (r in moves) mv.append("${r.strategy},${r.grid},${r.seed},${r.move},${r.chainLen},${r.powerUp},${r.cleared},${r.passes},${r.options},${r.bestPossible},${r.golden},${r.shuffled},${r.goldenInChain}\n")
        File(dir, "moves.csv").writeText(mv.toString())
        File(dir, "invariants.txt").writeText(bad.joinToString("\n"))
        File(dir, "summary.md").writeText(summary(scs, moves, seconds, bad))
    }
}
