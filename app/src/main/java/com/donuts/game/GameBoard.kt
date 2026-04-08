package com.donuts.game

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
    val movesAllowed: Int = 30,
    val targetScore: Int = 1000,
    val sandbox: Boolean = false
) {
    companion object {
        /** Chance that a freshly spawned (gravity-fill) cell is golden. */
        const val GOLDEN_CHANCE = 0.04
    }

    val grid: Array<Array<GameCell>> = Array(rows) { r ->
        Array(cols) { c -> GameCell(DonutType.random(), r, c) }
    }

    var score: Int = 0
        private set

    var movesLeft: Int = movesAllowed
        private set

    val donutsCleared: MutableMap<DonutType, Int> =
        DonutType.values().associateWith { 0 }.toMutableMap()

    val isGameOver: Boolean get() = !sandbox && movesLeft <= 0
    val hasWon: Boolean get() = !sandbox && score >= targetScore

    // -----------------------------------------------------------------------
    // Initialisation – eliminate any matches that exist in the starting grid
    // -----------------------------------------------------------------------

    init {
        repeat(20) {
            val matches = findMatches()
            if (matches.isEmpty()) return@repeat
            matches.forEach { (r, c) ->
                // Replace with a random type that doesn't match neighbours
                grid[r][c] = GameCell(safeRandom(r, c), r, c)
            }
        }
    }

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
        return choices.randomOrNull() ?: DonutType.random()
    }

    /**
     * Spawn a fresh cell at [row],[col] with a [GOLDEN_CHANCE] chance of being
     * golden.  Used only during gravity fills so the starting board is always
     * golden-free.
     */
    private fun spawnCell(row: Int, col: Int): GameCell =
        GameCell(DonutType.random(), row, col, isGolden = Math.random() < GOLDEN_CHANCE)

    // -----------------------------------------------------------------------
    // Player action – swap two adjacent cells
    // -----------------------------------------------------------------------

    fun swap(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!adjacent(r1, c1, r2, c2)) return false
        doSwap(r1, c1, r2, c2)
        if (findMatches().isEmpty()) {
            doSwap(r1, c1, r2, c2)
            return false
        }
        movesLeft--
        return true
    }

    private fun adjacent(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        val dr = kotlin.math.abs(r1 - r2)
        val dc = kotlin.math.abs(c1 - c2)
        return dr + dc == 1
    }

    private fun doSwap(r1: Int, c1: Int, r2: Int, c2: Int) {
        val tmp = grid[r1][c1].type
        grid[r1][c1] = grid[r1][c1].copy(type = grid[r2][c2].type)
        grid[r2][c2] = grid[r2][c2].copy(type = tmp)
    }

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
     * @return Points scored this pass, or 0 if no matches existed.
     */
    fun resolveOnce(): Int {
        val matches = findMatches()
        if (matches.isEmpty()) return 0

        // One call to pointsFor() for the whole match set, then multiply by cell count —
        // identical in value to the previous sumOf but clearly expresses the intent.
        val pts = matches.size * pointsFor(matches.size)
        score += pts

        // Apply gravity column-by-column, preserving golden status of survivors.
        for (c in 0 until cols) {
            val surviving = (0 until rows)
                .filter { r -> Pair(r, c) !in matches }
                .map { r -> grid[r][c] }
            val newCount = rows - surviving.size
            val newCells = (0 until newCount).map { spawnCell(0, c) }
            val column = newCells + surviving
            for (r in 0 until rows) grid[r][c] = column[r].copy(row = r, drawOffY = 0f)
        }

        return pts
    }

    /**
     * Resolves all cascading matches until the board is stable.
     * (Kept for utility; GameView drives the animated cascade loop itself.)
     */
    fun resolveAll(): Int {
        var total = 0; var pts: Int
        do { pts = resolveOnce(); total += pts } while (pts > 0)
        return total
    }

    private fun pointsFor(matchSize: Int): Int = when {
        matchSize >= 5 -> 150
        matchSize >= 4 -> 120
        else           -> 100
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
                    Pair(nr, nc) !in seen && grid[nr][nc].type == type) {
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

        // Determine chain type from the first non-golden cell; fall back to first cell's type
        // if every cell happens to be golden.
        val chainType = cells.firstOrNull { (r, c) -> !grid[r][c].isGolden }
            ?.let { (r, c) -> grid[r][c].type }
            ?: cells.first().let { (r, c) -> grid[r][c].type }

        // Validate: every non-golden cell must match chainType.
        if (cells.any { (r, c) -> !grid[r][c].isGolden && grid[r][c].type != chainType }) return null

        val cellSet = cells.toSet()

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
                val (mr, _) = cells[cells.size / 2]
                bonusCells = (0 until cols).map { c -> Pair(mr, c) }
                    .filter { it !in cellSet }
            }
            cells.size >= 5 -> {
                powerUp = PowerUp.BOMB
                val (mr, mc) = cells[cells.size / 2]
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

    /**
     * Applies a pre-computed [ChainResult] to the board: clears all affected cells,
     * updates score and [donutsCleared] counts, decrements moves, then applies
     * gravity and refills with [spawnCell] (preserving golden on survivors).
     *
     * Call [peekChainClear] first to obtain the result, then pass it here once the
     * pop animation has finished.
     */
    fun clearChain(result: ChainResult): Boolean {
        val allSet = (result.chainCells + result.bonusCells).toSet()

        // Update per-type cleared counts for all cells (chain + bonus).
        result.chainCells.forEach { (r, c) ->
            donutsCleared[grid[r][c].type] = (donutsCleared[grid[r][c].type] ?: 0) + 1
        }
        result.bonusCells.forEach { (r, c) ->
            donutsCleared[grid[r][c].type] = (donutsCleared[grid[r][c].type] ?: 0) + 1
        }

        if (!sandbox) {
            score += result.chainCells.size * pointsFor(result.chainCells.size)
            if (result.bonusCells.isNotEmpty()) score += result.bonusCells.size * 50
            movesLeft--
        }

        // Apply gravity column-by-column, preserving golden status of survivors.
        for (c in 0 until cols) {
            val surviving = (0 until rows)
                .filter { r -> Pair(r, c) !in allSet }
                .map { r -> grid[r][c] }
            val newCount = rows - surviving.size
            val newCells = (0 until newCount).map { spawnCell(0, c) }
            val column = newCells + surviving
            for (r in 0 until rows) grid[r][c] = column[r].copy(row = r, drawOffY = 0f)
        }

        return true
    }

    /** True if at least one valid chain of ≥3 exists on the current board. */
    fun hasValidMoves(): Boolean = findHint().isNotEmpty()

    // -----------------------------------------------------------------------
    // Reset / Shuffle
    // -----------------------------------------------------------------------

    /** Rerandomizes all cells without touching score or counter — used when no moves remain. */
    fun shuffle() {
        for (r in 0 until rows)
            for (c in 0 until cols)
                grid[r][c] = GameCell(safeRandom(r, c), r, c)
        repeat(20) {
            val matches = findMatches()
            if (matches.isEmpty()) return@repeat
            matches.forEach { (r, c) -> grid[r][c] = GameCell(safeRandom(r, c), r, c) }
        }
    }

    fun reset() {
        score = 0
        movesLeft = movesAllowed
        DonutType.values().forEach { donutsCleared[it] = 0 }
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                grid[r][c] = GameCell(safeRandom(r, c), r, c)
            }
        }
        // Clear any initial matches after reset
        repeat(20) {
            val matches = findMatches()
            if (matches.isEmpty()) return@repeat
            matches.forEach { (r, c) ->
                grid[r][c] = GameCell(safeRandom(r, c), r, c)
            }
        }
    }
}
