package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayTallyInputTest : GameInputTestBase() {

    @Test
    fun aPoppedChainIsTalliedAndSavedToPrefs() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        assertEquals(1, v.playTally.chains(3))
        assertEquals(1L, v.playTally.pops)
        assertEquals(v.playTally.encode(), v.prefs.playTally)
        assertEquals(v.playTally.lines(), v.tallyLines)
    }

    @Test
    fun aTapAndATwoAreTalliedAsTries() {
        val v = planted()
        drag(v, 0 to 0)
        drag(v, 0 to 0, 0 to 1)
        assertEquals(1, v.playTally.chains(1)); assertEquals(1, v.playTally.chains(2))
        assertEquals(0L, v.playTally.pops)
    }

    @Test
    fun aChainOfFiveCountsItsBomb() {
        val v = planted(listOf("SSSSSV", "CBVMAC", "MAMBVC", "VCAVMB", "BMCABA", "AVBMCV"))
        drag(v, 0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4)
        assertEquals(1, v.playTally.chains(5))
        assertEquals(1, v.playTally.powerUps(PowerUp.BOMB))
    }

    @Test
    fun theTallyOutlivesTheViewAndNewGameAndResetStickers() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        v.startOver(); v.resetStickers()
        val again = GameView(TestViews.app(), GameBoard(6, 6, kotlin.random.Random(2)), v.prefs)
        assertEquals(1, again.playTally.chains(3))
    }

    @Test
    fun theGrownUpsPageDrawsTheTally() {
        val v = planted()
        drag(v, 0 to 0, 0 to 1, 0 to 2)
        openGrownUps(v)
        assertTrue(v.settingsTallyRect.height() > 0f)
        v.drawFrame(TestViews.canvas(v))   // the tally card and its lines paint without throwing
    }
}
