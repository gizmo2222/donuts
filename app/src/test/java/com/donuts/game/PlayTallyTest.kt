package com.donuts.game

import org.junit.Assert.*
import org.junit.Test

class PlayTallyTest {
    private fun sample() = PlayTally().apply {
        record(1); record(1); record(2)
        record(3); record(3); record(4)
        record(5, PowerUp.BOMB); record(7, PowerUp.ROW_BLAST, hadGolden = true)
        record(9, PowerUp.COLOR_BURST); record(6, PowerUp.RING)
    }

    @Test
    fun anEmptyTallyCountsNothingAndSaysSo() {
        val t = PlayTally()
        assertTrue(t.isEmpty)
        assertEquals(0L, t.pops); assertEquals(0L, t.tries)
        assertEquals(0.0, t.percentAtLeast(7), 0.0)
        assertEquals(listOf(PlayTally.HEADING, "Nothing yet: it fills in as Steven plays."), t.lines())
    }

    @Test
    fun chainsCountByLengthWithTapsAndTwosAsTries() {
        val t = sample()
        assertFalse(t.isEmpty)
        assertEquals(2, t.chains(1)); assertEquals(1, t.chains(2)); assertEquals(2, t.chains(3))
        assertEquals(3L, t.tries)
        assertEquals(7L, t.pops)
        assertEquals(2L, t.popsAtLeast(7))
        assertEquals(7L, t.popsAtLeast(1))        // below three means every pop
        assertEquals(100.0 * 2 / 7, t.percentAtLeast(7), 1e-9)
        assertEquals(1, t.golden)
    }

    @Test
    fun powerUpsCountByKindAndNoneIsNeverCounted() {
        val t = sample()
        assertEquals(1, t.powerUps(PowerUp.BOMB)); assertEquals(1, t.powerUps(PowerUp.ROW_BLAST))
        assertEquals(1, t.powerUps(PowerUp.COLOR_BURST)); assertEquals(1, t.powerUps(PowerUp.RING))
        assertEquals(0, t.powerUps(PowerUp.NONE))
    }

    @Test
    fun longChainsShareTheLastSlotAndBadLengthsAreIgnored() {
        val t = PlayTally()
        t.record(PlayTally.MAX_LEN); t.record(40); t.record(0); t.record(-3)
        assertEquals(2, t.chains(PlayTally.MAX_LEN))
        assertEquals(0, t.chains(0)); assertEquals(0, t.chains(PlayTally.MAX_LEN + 1))
        assertEquals(2L, t.pops)
    }

    @Test
    fun encodeAndDecodeRoundTrip() {
        val t = sample()
        val back = PlayTally.decode(t.encode())
        assertEquals(t.encode(), back.encode())
        assertEquals(t.lines(), back.lines())
        assertEquals("t1;2,1,2,1,1,1,1,0,1,0,0,0;0,1,1,1,1;1", t.encode())
    }

    @Test
    fun badTextDecodesToAnEmptyTally() {
        val bad = listOf(null, "", "x", "t2;1,1,1,1,1,1,1,1,1,1,1,1;0,0,0,0,0;0",
            "t1;1,1;0,0,0,0,0;0",                          // too few lengths
            "t1;1,1,1,1,1,1,1,1,1,1,1,1;0,0,0;0",          // too few power-ups
            "t1;1,1,1,1,1,1,1,1,1,1,1,-1;0,0,0,0,0;0",     // a negative count
            "t1;1,1,1,1,1,1,1,1,1,1,1,1;0,0,0,0,0;-1",     // negative golden
            "t1;1,1,1,1,1,1,1,1,1,1,1,a;0,0,0,0,0;0",      // not a number
            "t1;1,1,1,1,1,1,1,1,1,1,1,1;0,0,0,0,0")        // a part missing
        for (s in bad) assertTrue("'$s'", PlayTally.decode(s).isEmpty)
    }

    @Test
    fun countsStopAtTheTopInsteadOfWrapping() {
        val max = Int.MAX_VALUE
        val t = PlayTally.decode("t1;$max,0,0,0,0,0,0,0,0,0,0,0;0,$max,0,0,0;$max")
        t.record(1); t.record(5, PowerUp.BOMB, hadGolden = true)
        assertEquals(max, t.chains(1)); assertEquals(max, t.powerUps(PowerUp.BOMB)); assertEquals(max, t.golden)
    }

    @Test
    fun theLinesNameEveryCountAndTheSevenPlusShare() {
        val l = sample().lines()
        assertEquals(4, l.size)
        assertEquals(PlayTally.HEADING, l[0])
        assertEquals("Chains  3: 2  4: 1  5: 1  6: 1  7+: 2", l[1])
        assertEquals("Taps 2  Twos 1  7+ share 28.6%  Golden 1", l[2])
        assertEquals("Bombs 1  Rows 1  Bursts 1  Rings 1", l[3])
    }
}
