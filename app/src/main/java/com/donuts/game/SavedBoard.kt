package com.donuts.game

/** A board and counter as plain data, with a text form for [Prefs]. No Android types. */
data class SavedBoard(val size: Int, val cells: IntArray, val cleared: IntArray, val sessionSaved: Int) {

    fun encode(): String =
        "v1;$size;$sessionSaved;${cells.joinToString(",")};${cleared.joinToString(",")}"

    companion object {
        /** Null for null, garbage, another version, or a cell count that is not size*size. */
        fun decode(text: String?): SavedBoard? {
            if (text.isNullOrEmpty()) return null
            val parts = text.split(';')
            if (parts.size != 5 || parts[0] != "v1") return null
            val size = parts[1].toIntOrNull() ?: return null
            val saved = parts[2].toIntOrNull() ?: return null
            val cells = ints(parts[3]) ?: return null
            val cleared = ints(parts[4]) ?: return null
            if (size <= 0 || cells.size != size * size) return null
            return SavedBoard(size, cells, cleared, saved)
        }

        private fun ints(s: String): IntArray? {
            if (s.isEmpty()) return IntArray(0)
            val out = IntArray(0).toMutableList()
            for (p in s.split(',')) out.add(p.toIntOrNull() ?: return null)
            return out.toIntArray()
        }
    }
}
