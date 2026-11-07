package com.donuts.game

/** A board and counter as plain data, with a text form for [Prefs]. No Android types. */
data class SavedBoard(val size: Int, val cells: IntArray, val cleared: IntArray, val sessionSaved: Int) {

    fun encode(): String =
        "v1;$size;$sessionSaved;${cells.joinToString(",")};${cleared.joinToString(",")}"

    companion object {
        private const val MAX_SIZE = 16

        /** Null for null, garbage, another version, a size outside 1..[MAX_SIZE], or a cell count that is not size*size. */
        fun decode(text: String?): SavedBoard? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split(';')
            if (parts.size != 5 || parts[0] != "v1") return null
            val size = parts[1].toIntOrNull() ?: return null
            val saved = parts[2].toIntOrNull() ?: return null
            val cells = ints(parts[3]) ?: return null
            val cleared = ints(parts[4]) ?: return null
            if (size !in 1..MAX_SIZE || cells.size != size * size) return null
            return SavedBoard(size, cells, cleared, saved)
        }

        private fun ints(s: String): IntArray? {
            if (s.isEmpty()) return IntArray(0)
            return s.split(',').map { it.toIntOrNull() ?: return null }.toIntArray()
        }
    }
}
