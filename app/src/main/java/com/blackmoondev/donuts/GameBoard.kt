package com.blackmoondev.donuts

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
    val targetScore: Int = 1000
) {
    val grid: Array<Array<GameCell>> = Array(rows) { r ->
        Array(cols) { c -> GameCell(DonutType.random(), r, c) }
    }

    var score: Int = 0
        private set

    var movesLeft: Int = movesAllowed
        private set

    val isGameOver: Boolean get() = movesLeft <= 0
    val hasWon: Boolean get() = score >= targetScore

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

    // -----------------------------------------------------------------------
    // Player action – swap two adjacent cells
    // -----------------------------------------------------------------------

    /**
     * Attempts to swap the cells at (r1,c1) and (r2,c2).
     *
     * Rules:
     *  - Cells must be orthogonally adjacent.
     *  - The swap must produce at least one match.
     *  - Costs one move even if cascades follow.
     *
     * @return true if the swap was valid and executed.
     */
    fun swap(r1: Int, c1: Int, r2: Int, c2: Int): Boolean {
        if (!adjacent(r1, c1, r2, c2)) return false

        doSwap(r1, c1, r2, c2)
        if (findMatches().isEmpty()) {
            doSwap(r1, c1, r2, c2)   // revert
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
    // Remove matches, apply gravity, refill – one pass
    // -----------------------------------------------------------------------

    /**
     * Clears all current matches, drops surviving donuts, fills from the top.
     *
     * @return Points scored this pass, or 0 if no matches existed.
     */
    fun resolveOnce(): Int {
        val matches = findMatches()
        if (matches.isEmpty()) return 0

        // Score: bonus for larger matches
        val pts = matches.sumOf { pointsFor(matches.size) }
        score += pts

        // Mark matched cells as empty (null type placeholder: we rebuild below)
        val surviving = Array(rows) { r ->
            (0 until cols).map { c -> if (Pair(r, c) !in matches) grid[r][c].type else null }
        }

        // Apply gravity column-by-column: existing donuts sink, new fill from top
        for (c in 0 until cols) {
            val column = (0 until rows).mapNotNull { r -> surviving[r][c] }.toMutableList()
            // column has surviving donuts from top-to-bottom; pad the top with new
            while (column.size < rows) column.add(0, DonutType.random())
            for (r in 0 until rows) {
                grid[r][c] = GameCell(column[r], r, c)
            }
        }

        return pts
    }

    /**
     * Resolves all cascading matches until the board is stable.
     *
     * @return Total points scored across all cascades.
     */
    fun resolveAll(): Int {
        var total = 0
        var pts: Int
        do {
            pts = resolveOnce()
            total += pts
        } while (pts > 0)
        return total
    }

    private fun pointsFor(matchSize: Int): Int = when {
        matchSize >= 5 -> 150
        matchSize >= 4 -> 120
        else           -> 100
    }

    // -----------------------------------------------------------------------
    // Reset
    // -----------------------------------------------------------------------

    fun reset() {
        score = 0
        movesLeft = movesAllowed
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
