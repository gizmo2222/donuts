package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

class SeasonRulesTest {

    @Test fun eachWindowIncludesBothEndDaysAndNothingOutside() {
        val cases = listOf(
            Triple(11, 30, null), Triple(12, 1, Season.SNOW), Triple(12, 31, Season.SNOW), Triple(1, 1, Season.SNOW),
            Triple(1, 6, Season.SNOW), Triple(1, 7, null), Triple(3, 19, null), Triple(3, 20, Season.FLOWERS),
            Triple(4, 30, Season.FLOWERS), Triple(5, 1, null), Triple(6, 20, null), Triple(6, 21, Season.SUNSHINE),
            Triple(8, 31, Season.SUNSHINE), Triple(9, 1, null), Triple(9, 30, null), Triple(10, 1, Season.LEAVES),
            Triple(11, 15, Season.LEAVES), Triple(11, 16, null), Triple(11, 25, null)
        )
        for ((m, d, s) in cases) assertEquals("$m/$d", s, SeasonRules.at(m, d))
    }

    @Test fun aYearHasTheExpectedNumberOfDaysInEachSeason() {
        val days = HashMap<Season?, Int>()
        var d = java.time.LocalDate.of(2028, 1, 1)
        while (d.year == 2028) {
            val s = SeasonRules.at(d.monthValue, d.dayOfMonth)
            days[s] = (days[s] ?: 0) + 1
            d = d.plusDays(1)
        }
        assertEquals(37, days[Season.SNOW]); assertEquals(42, days[Season.FLOWERS])
        assertEquals(72, days[Season.SUNSHINE]); assertEquals(46, days[Season.LEAVES])
        assertEquals(366 - 37 - 42 - 72 - 46, days[null])
    }

    @Test fun januarySnowBelongsToTheWinterThatStartedInDecember() {
        assertEquals("snow-2026", SeasonRules.id(Season.SNOW, 2026, 12))
        assertEquals("snow-2026", SeasonRules.id(Season.SNOW, 2027, 1))
        assertEquals("snow-2027", SeasonRules.id(Season.SNOW, 2027, 12))
        assertEquals("leaves-2026", SeasonRules.id(Season.LEAVES, 2026, 10))
        assertEquals("flowers-2027", SeasonRules.id(Season.FLOWERS, 2027, 4))
    }

    @Test fun theLookIsWornOnlyInItsOwnSeasonWhenSavedOn() {
        assertEquals(Season.SNOW, SeasonRules.worn("snow", Season.SNOW))
        assertNull(SeasonRules.worn("", Season.SNOW))
        assertNull(SeasonRules.worn(null, Season.SNOW))
        assertNull(SeasonRules.worn("leaves", Season.SNOW))
        assertNull(SeasonRules.worn("snow", null))
    }

    @Test fun keysAreDistinctAndNamesAreShort() {
        assertEquals(SeasonRules.SEASONS.size, SeasonRules.SEASONS.map { it.key }.toSet().size)
        for (s in SeasonRules.SEASONS) {
            assertTrue(seasonName(s).length <= 10)
            assertTrue(seasonCheer(s).endsWith("!"))
        }
    }
}
