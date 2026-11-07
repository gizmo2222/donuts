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
    private val random: Random = Random.Default
) {
    companion object {
        /** Chance that a freshly spawned (gravity-fill) cell is golden. */
        const val GOLDEN_CHANCE = 0.04
        /** Marks a golden cell in [cellCodes]. */
        const val GOLDEN_BIT = 0x100
        /** Cap on refill attempts when looking for a board with a valid move. */
        const val REFILL_TRIES = 100
    }

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

    private fun randomType(): DonutType = DonutType.values().random(random)

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

        val choices = DonutType.values().filterNot { it in forbidden }
        return choices.randomOrNull(random) ?: randomType()
    }

    /**
     * Spawn a fresh cell at [row],[col] with a [GOLDEN_CHANCE] chance of being
     * golden.  Used only during gravity fills so the starting board is always
     * golden-free.
     */
    private fun spawnCell(row: Int, col: Int): GameCell =
        GameCell(randomType(), row, col, isGolden = random.nextDouble() < GOLDEN_CHANCE)

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
                    if (group.size >= 3) return group.take(3)
                }
            }
        }
        return emptyList()
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
     *
     * @return A [ChainResult] describing all cells to clear, or null if the chain
     *         is invalid (< 3 cells, or mixed types without golden wildcards).
     */
    fun peekChainClear(cells: List<Pair<Int, Int>>): ChainResult? {
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
            cells.size >= 9 -> {
                powerUp = PowerUp.COLOR_BURST
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
        for (c in 0 until cols) {
            val surviving = (0 until rows).filter { r -> Pair(r, c) !in cleared }.map { r -> grid[r][c] }
            val newCount = rows - surviving.size
            val newCells = (0 until newCount).map { spawnCell(0, c) }
            val column = newCells + surviving
            for (r in 0 until rows) grid[r][c] = column[r].copy(row = r)
        }
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
