package com.donuts.game

import kotlin.random.Random

/**
 * Pure game-logic class — no Android dependencies.
 *
 * Board coordinate system:
 *   row 0 = top row, row (rows-1) = bottom row
 *   col 0 = left col, col (cols-1) = right col
 */
class GameBoard(
    val rows: Int = 8,
    val cols: Int = 8,
    private val random: Random = Random.Default,
    /** Chance that a refilled cell is golden. The balance sim sets it; the game raises it during a sprinkle party. */
    var goldenChance: Double = GOLDEN_CHANCE,
    /** How many donut types are drawn, the first [typeCount] of [DonutType]. The game picks it with [typesFor]. */
    val typeCount: Int = DonutType.values().size
) {
    companion object {
        /** Chance that a freshly spawned (gravity-fill) cell is golden. */
        const val GOLDEN_CHANCE = 0.04
        /** Marks a golden cell in [cellCodes]. */
        const val GOLDEN_BIT = 0x100
        /** Cap on refill attempts when looking for a board with a valid move. */
        const val REFILL_TRIES = 100
        /** Boards this size or smaller drop the last donut type ([SMALL_GRID_TYPES] in play). */
        const val SMALL_GRID = 6
        /** Donut types on a [SMALL_GRID] board: five, so long chains come about twice as often (BalanceSim, B2/B29). */
        const val SMALL_GRID_TYPES = 5

        /** How many donut types a [size] x [size] game board draws: five on 6x6, all six on 8x8. */
        fun typesFor(size: Int): Int = if (size <= SMALL_GRID) SMALL_GRID_TYPES else DonutType.values().size

        /** The game's board for a [size] x [size] grid, with [typesFor] donut types. */
        fun forGrid(size: Int, random: Random = Random.Default): GameBoard =
            GameBoard(size, size, random, typeCount = typesFor(size))
    }

    private val activeTypes: List<DonutType> = DonutType.values().take(typeCount)

    /** Chance a refilled cell copies a neighbour's type ([DroughtAssist]); 0 keeps the plain random refill. */
    var nudge: Double = 0.0

    val grid: Array<Array<GameCell>> = Array(rows) { r ->
        Array(cols) { c -> GameCell(randomType(), r, c) }
    }

    val donutsCleared: MutableMap<DonutType, Int> =
        DonutType.values().associateWith { 0 }.toMutableMap()

    /** Every donut this board has cleared, all types together. */
    val totalCleared: Int get() = donutsCleared.values.sum()

    // -----------------------------------------------------------------------
    // Initialisation – eliminate any matches that exist in the starting grid
    // -----------------------------------------------------------------------

    init {
        refill(randomFirst = false)
    }

    /**
     * Fills the grid with fresh random cells, repairs 3-in-a-rows, and repeats until
     * a valid chain exists (at most [REFILL_TRIES] tries).
     */
    private fun refill(randomFirst: Boolean = true) {
        var fill = randomFirst
        repeat(REFILL_TRIES) {
            // The first try may keep the grid the constructor filled; every retry starts fresh.
            if (fill) {
                for (r in 0 until rows)
                    for (c in 0 until cols)
                        grid[r][c] = GameCell(safeRandom(r, c), r, c)
            }
            fill = true
            for (pass in 0 until 20) {
                val matches = findMatches()
                if (matches.isEmpty()) break
                matches.forEach { (r, c) -> grid[r][c] = GameCell(safeRandom(r, c), r, c) }
            }
            if (hasValidMoves()) return
        }
    }

    private fun randomType(): DonutType = activeTypes.random(random)

    /** Choose a random type that won't immediately form a 3-in-a-row. */
    private fun safeRandom(row: Int, col: Int): DonutType {
        val forbidden = mutableSetOf<DonutType>()

        // Horizontal: look two to the left
        if (col >= 2 &&
            grid[row][col - 1].type == grid[row][col - 2].type
        ) forbidden += grid[row][col - 1].type

        // Vertical: look two above
        if (row >= 2 &&
            grid[row - 1][col].type == grid[row - 2][col].type
        ) forbidden += grid[row - 1][col].type

        val choices = activeTypes.filterNot { it in forbidden }
        return choices.randomOrNull(random) ?: randomType()
    }

    /**
     * Spawn a fresh cell at [row],[col] with a [goldenChance] chance of being
     * golden.  Used only during gravity fills so the starting board is always
     * golden-free.
     */
    private fun spawnCell(row: Int, col: Int): GameCell =
        GameCell(randomType(), row, col, isGolden = random.nextDouble() < goldenChance)

    // -----------------------------------------------------------------------
    // Match detection
    // -----------------------------------------------------------------------

    /** Returns the set of (row, col) positions that form part of a match. */
    fun findMatches(): Set<Pair<Int, Int>> {
        val matched = mutableSetOf<Pair<Int, Int>>()

        // Horizontal runs ≥ 3
        for (r in 0 until rows) {
            var c = 0
            while (c < cols) {
                val t = grid[r][c].type
                var len = 1
                while (c + len < cols && grid[r][c + len].type == t) len++
                if (len >= 3) for (i in 0 until len) matched += Pair(r, c + i)
                c += len
            }
        }

        // Vertical runs ≥ 3
        for (c in 0 until cols) {
            var r = 0
            while (r < rows) {
                val t = grid[r][c].type
                var len = 1
                while (r + len < rows && grid[r + len][c].type == t) len++
                if (len >= 3) for (i in 0 until len) matched += Pair(r + i, c)
                r += len
            }
        }

        return matched
    }

    // -----------------------------------------------------------------------
    // Remove matches, apply gravity, refill – one cascade pass
    // -----------------------------------------------------------------------

    /**
     * Clears all current matches, drops surviving donuts, fills from the top.
     * Surviving cells keep their [GameCell.isGolden] flag; new cells spawn with
     * a small golden chance via [spawnCell].
     *
     * @return Cells cleared this pass, or 0 when there was no match.
     */
    fun resolveOnce(): Int {
        val matches = findMatches()
        if (matches.isEmpty()) return 0

        // Cascade pops feed the cleared counter, milestones and stickers, like clearChain().
        countCleared(matches)
        collapse(matches)

        return matches.size
    }

    // -----------------------------------------------------------------------
    // Hint – find a valid chain of ≥3 same-type cells for the player
    // -----------------------------------------------------------------------

    /** Returns a list of connected same-type cells (≥3) the player could clear, or empty. */
    fun findHint(): List<Pair<Int, Int>> {
        for (type in DonutType.values()) {
            val visited = mutableSetOf<Pair<Int, Int>>()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (grid[r][c].type != type || Pair(r, c) in visited) continue
                    val group = floodFill(r, c, type)
                    visited.addAll(group)
                    if (group.size >= 3) return dragOrder(group[0], group[1], group[2])
                }
            }
        }
        return emptyList()
    }

    /**
     * The first three flood-fill cells in an order a finger can drag. b is always next to a (found from
     * the start cell); c was found from a or from b, so either a-b-c or b-a-c is a path.
     */
    private fun dragOrder(a: Pair<Int, Int>, b: Pair<Int, Int>, c: Pair<Int, Int>): List<Pair<Int, Int>> {
        val bc = maxOf(Math.abs(b.first - c.first), Math.abs(b.second - c.second)) == 1
        return if (bc) listOf(a, b, c) else listOf(b, a, c)
    }

    private fun floodFill(startR: Int, startC: Int, type: DonutType): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        val queue  = ArrayDeque<Pair<Int, Int>>()
        val seen   = mutableSetOf<Pair<Int, Int>>()
        queue.add(Pair(startR, startC))
        seen.add(Pair(startR, startC))
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            result.add(Pair(r, c))
            for (dr in -1..1) for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val nr = r + dr; val nc = c + dc
                if (nr in 0 until rows && nc in 0 until cols &&
                    Pair(nr, nc) !in seen &&
                    (grid[nr][nc].type == type || grid[nr][nc].isGolden)) {
                    seen.add(Pair(nr, nc))
                    queue.add(Pair(nr, nc))
                }
            }
        }
        return result
    }

    // -----------------------------------------------------------------------
    // Drag-to-connect chain clear
    // -----------------------------------------------------------------------

    /** The type a chain counts as: its first non-golden cell's type, or the first cell's when all are golden; null for no cells. */
    fun chainTypeOf(cells: List<Pair<Int, Int>>): DonutType? =
        cells.firstOrNull { (r, c) -> !grid[r][c].isGolden }?.let { (r, c) -> grid[r][c].type }
            ?: cells.firstOrNull()?.let { (r, c) -> grid[r][c].type }

    /**
     * Pre-computes the full outcome of clearing [cells] without mutating the board.
     *
     * Golden cells ([GameCell.isGolden] == true) are treated as wild — they match
     * whatever type the rest of the chain uses.
     *
     * Power-up bonuses based on chain length:
     *   5–6  → BOMB       : 3×3 area around chain mid-point
     *   7–8  → ROW_BLAST  : entire row of chain mid-point
     *   9+   → COLOR_BURST: every remaining cell of the chain type
     *   [looped] → RING       : every remaining cell of the chain type, at any length (the
     *                           caller guarantees the ring has [LoopRules.MIN_LOOP]+ cells)
     *
     * @return A [ChainResult] describing all cells to clear, or null if the chain
     *         is invalid (< 3 cells, or mixed types without golden wildcards).
     */
    fun peekChainClear(cells: List<Pair<Int, Int>>, looped: Boolean = false): ChainResult? {
        if (cells.size < 3) return null

        // Chain type: first non-golden cell's type, or the first cell's if every cell is golden.
        val chainType = chainTypeOf(cells)!!

        // Validate: every non-golden cell must match chainType.
        if (cells.any { (r, c) -> !grid[r][c].isGolden && grid[r][c].type != chainType }) return null

        val cellSet = cells.toSet()

        val (mr, mc) = midCell(cells)
        val powerUp: PowerUp
        val bonusCells: List<Pair<Int, Int>>
        when {
            looped || cells.size >= 9 -> {
                powerUp = if (looped) PowerUp.RING else PowerUp.COLOR_BURST
                bonusCells = (0 until rows).flatMap { r -> (0 until cols).map { c -> Pair(r, c) } }
                    .filter { (r, c) -> Pair(r, c) !in cellSet && grid[r][c].type == chainType }
            }
            cells.size >= 7 -> {
                powerUp = PowerUp.ROW_BLAST
                bonusCells = (0 until cols).map { c -> Pair(mr, c) }
                    .filter { it !in cellSet }
            }
            cells.size >= 5 -> {
                powerUp = PowerUp.BOMB
                bonusCells = (-1..1).flatMap { dr -> (-1..1).map { dc -> Pair(mr + dr, mc + dc) } }
                    .filter { (r, c) ->
                        r in 0 until rows && c in 0 until cols && Pair(r, c) !in cellSet
                    }
            }
            else -> {
                powerUp = PowerUp.NONE
                bonusCells = emptyList()
            }
        }

        return ChainResult(cells, bonusCells, powerUp, chainType)
    }

    private fun countCleared(cells: Iterable<Pair<Int, Int>>) {
        cells.forEach { (r, c) -> val t = grid[r][c].type; donutsCleared[t] = (donutsCleared[t] ?: 0) + 1 }
    }

    /** Gravity column by column: survivors (golden kept) fall, new cells spawn on top. */
    private fun collapse(cleared: Set<Pair<Int, Int>>) {
        if (nudge > 0.0) { collapseNudged(cleared); return }
        for (c in 0 until cols) {
            val surviving = (0 until rows).filter { r -> Pair(r, c) !in cleared }.map { r -> grid[r][c] }
            val newCount = rows - surviving.size
            val newCells = (0 until newCount).map { spawnCell(0, c) }
            val column = newCells + surviving
            for (r in 0 until rows) grid[r][c] = column[r].copy(row = r)
        }
    }

    /**
     * [collapse] while the drought assist is on: survivors fall as usual, then the new cells are filled from
     * the bottom of each column up. Each one, with chance [nudge], copies the type of a random neighbour
     * already in place (golden ones skipped) that would not make a straight three; otherwise it is random.
     */
    private fun collapseNudged(cleared: Set<Pair<Int, Int>>) {
        val fresh = Array(rows) { BooleanArray(cols) }
        for (c in 0 until cols) {
            val surviving = (0 until rows).filter { r -> Pair(r, c) !in cleared }.map { r -> grid[r][c] }
            val newCount = rows - surviving.size
            for (r in 0 until newCount) fresh[r][c] = true
            for ((i, cell) in surviving.withIndex()) grid[newCount + i][c] = cell.copy(row = newCount + i)
        }
        for (c in 0 until cols) for (r in rows - 1 downTo 0) {
            if (!fresh[r][c]) continue
            var type = randomType()
            if (random.nextDouble() < nudge) {
                val picks = ArrayList<DonutType>()
                for (dr in -1..1) for (dc in -1..1) {
                    val nr = r + dr; val nc = c + dc
                    if ((dr == 0 && dc == 0) || nr !in 0 until rows || nc !in 0 until cols || fresh[nr][nc]) continue
                    val n = grid[nr][nc]
                    if (!n.isGolden && !makesLine(r, c, n.type, fresh)) picks += n.type
                }
                if (picks.isNotEmpty()) type = picks[random.nextInt(picks.size)]
            }
            grid[r][c] = GameCell(type, r, c, isGolden = random.nextDouble() < goldenChance)
            fresh[r][c] = false
        }
    }

    /** True if [type] at [r],[c] would sit in a straight run of three with cells already in place. */
    private fun makesLine(r: Int, c: Int, type: DonutType, fresh: Array<BooleanArray>): Boolean {
        fun same(rr: Int, cc: Int) = rr in 0 until rows && cc in 0 until cols && !fresh[rr][cc] && grid[rr][cc].type == type
        fun run(dr: Int, dc: Int): Int { var k = 0; while (same(r + dr * (k + 1), c + dc * (k + 1))) k++; return k }
        return run(0, -1) + run(0, 1) >= 2 || run(-1, 0) + run(1, 0) >= 2
    }

    /**
     * True if a drag chain of at least [len] cells exists: a path of 8-way neighbours, golden cells wild, as
     * [ChainRules] allows. A depth-limited search that stops at the first hit; the drought assist asks this.
     */
    fun hasChainOf(len: Int): Boolean {
        if (len <= 1) return rows * cols >= len
        val seen = Array(rows) { BooleanArray(cols) }
        fun go(r: Int, c: Int, type: DonutType?, depth: Int): Boolean {
            if (depth >= len) return true
            seen[r][c] = true
            for (dr in -1..1) for (dc in -1..1) {
                val nr = r + dr; val nc = c + dc
                if ((dr == 0 && dc == 0) || nr !in 0 until rows || nc !in 0 until cols || seen[nr][nc]) continue
                val n = grid[nr][nc]
                if (!n.isGolden && type != null && n.type != type) continue
                if (go(nr, nc, if (n.isGolden) type else n.type, depth + 1)) { seen[r][c] = false; return true }
            }
            seen[r][c] = false
            return false
        }
        for (r in 0 until rows) for (c in 0 until cols) {
            val cell = grid[r][c]
            if (go(r, c, if (cell.isGolden) null else cell.type, 1)) return true
        }
        return false
    }

    /**
     * Applies a pre-computed [ChainResult] to the board: clears all affected cells,
     * updates the per-type [donutsCleared] counts, then applies gravity and refills
     * with [spawnCell] (preserving golden on survivors).
     *
     * Call [peekChainClear] first to obtain the result, then pass it here once the
     * pop animation has finished.
     */
    fun clearChain(result: ChainResult) {
        val allSet = (result.chainCells + result.bonusCells).toSet()

        countCleared(result.chainCells + result.bonusCells)
        collapse(allSet)
    }

    /** True if at least one valid chain of ≥3 exists on the current board. */
    fun hasValidMoves(): Boolean = findHint().isNotEmpty()

    // -----------------------------------------------------------------------
    // Reset / Shuffle
    // -----------------------------------------------------------------------

    /** Rerandomizes all cells without touching score or counter — used when no moves remain. */
    fun shuffle() {
        refill()
    }

    fun reset() {
        DonutType.values().forEach { donutsCleared[it] = 0 }
        refill()
    }

    // -----------------------------------------------------------------------
    // Save / restore (the activity can be restarted mid-game)
    // -----------------------------------------------------------------------

    /** Clears matches until none are left, with no animation. Run before saving the board. */
    fun settle() {
        repeat(50) { if (resolveOnce() == 0) return }
    }

    /** One int per cell, row by row: the type's ordinal, plus [GOLDEN_BIT] when the cell is golden. */
    fun cellCodes(): IntArray = IntArray(rows * cols) { i ->
        val cell = grid[i / cols][i % cols]
        cell.type.ordinal or (if (cell.isGolden) GOLDEN_BIT else 0)
    }

    /** Donuts cleared per type, indexed by the type's ordinal. */
    fun clearedCounts(): IntArray = DonutType.values().map { donutsCleared[it] ?: 0 }.toIntArray()

    /**
     * Puts back a board saved with [cellCodes] and [clearedCounts].
     * Returns false and changes nothing when the data does not fit this board.
     */
    fun restore(cells: IntArray, cleared: IntArray): Boolean {
        val types = DonutType.values()
        if (cells.size != rows * cols || cleared.size != types.size) return false
        if (cells.any { (it and GOLDEN_BIT.inv()) !in types.indices } || cleared.any { it < 0 }) return false
        // Counts from edited or corrupt prefs must not overflow the Int total the counter shows
        if (cleared.sumOf { it.toLong() } > Int.MAX_VALUE) return false
        for (i in cells.indices) {
            val r = i / cols; val c = i % cols
            grid[r][c] = GameCell(types[cells[i] and GOLDEN_BIT.inv()], r, c,
                isGolden = (cells[i] and GOLDEN_BIT) != 0)
        }
        types.forEach { donutsCleared[it] = cleared[it.ordinal] }
        return true
    }
}
