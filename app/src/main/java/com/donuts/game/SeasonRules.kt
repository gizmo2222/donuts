package com.donuts.game

/**
 * A seasonal look: a topping-style overlay that every donut can wear between two dates, every year.
 * [key] is the saved form (stable, never the ordinal). The window runs from [fromMonth]/[fromDay] to
 * [toMonth]/[toDay], both days included; [SNOW] runs over New Year. Nature only, no holidays, so it suits
 * any family.
 */
enum class Season(val key: String, val fromMonth: Int, val fromDay: Int, val toMonth: Int, val toDay: Int) {
    SNOW("snow", 12, 1, 1, 6),
    FLOWERS("flowers", 3, 20, 4, 30),
    SUNSHINE("suns", 6, 21, 8, 31),
    LEAVES("leaves", 10, 1, 11, 15)
}

/**
 * Which seasonal look the date gives, and whether Steven is wearing it (pure, no Android types).
 *
 * The first time a season comes round each year it is revealed and worn; [id] tells one year's season from the
 * next. He can take it off in the toppings picker and put it back on while it lasts. When the window ends the
 * donuts go back to his own topping, which the season never overwrites.
 */
object SeasonRules {
    val SEASONS: List<Season> = Season.values().toList()

    private fun md(month: Int, day: Int) = month * 100 + day

    /** The season whose window holds [month] (1-12) and [day], or null between seasons. */
    fun at(month: Int, day: Int): Season? {
        val d = md(month, day)
        return SEASONS.firstOrNull { s ->
            val from = md(s.fromMonth, s.fromDay); val to = md(s.toMonth, s.toDay)
            if (from <= to) d in from..to else d >= from || d <= to
        }
    }

    /** One year's run of [s], e.g. "snow-2026"; the January days of a window that started in December count as its year. */
    fun id(s: Season, year: Int, month: Int): String {
        val wraps = md(s.fromMonth, s.fromDay) > md(s.toMonth, s.toDay)
        val start = if (wraps && month < s.fromMonth) year - 1 else year
        return "${s.key}-$start"
    }

    /** The look being worn: [now] if the saved key says he has it on, else null (his own topping shows). */
    fun worn(saved: String?, now: Season?): Season? = if (now != null && saved == now.key) now else null
}
