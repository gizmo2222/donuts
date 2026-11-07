package com.donuts.game

import kotlin.random.Random

/** Letter-board fixtures shared by the board tests: upper case = plain, lower case = golden; S C B V M A. */
internal object BoardFixtures {
    fun typeOf(ch: Char): DonutType = when (ch.uppercaseChar()) {
        'S' -> DonutType.STRAWBERRY; 'C' -> DonutType.CHOCOLATE; 'B' -> DonutType.BLUEBERRY
        'V' -> DonutType.VANILLA; 'M' -> DonutType.MATCHA; 'A' -> DonutType.CARAMEL
        else -> error("unknown donut letter $ch")
    }

    fun boardOf(vararg rows: String, random: Random = Random(7)): GameBoard {
        val board = GameBoard(rows.size, rows[0].length, random)
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch ->
                board.grid[r][c] = GameCell(typeOf(ch), r, c, isGolden = ch.isLowerCase())
            }
        }
        return board
    }

    fun rowOf(row: Int, from: Int, to: Int): List<Pair<Int, Int>> = (from..to).map { Pair(row, it) }

    fun letters(b: GameBoard): List<String> = b.grid.map { r -> r.joinToString("") { it.type.name.take(1) } }
}
