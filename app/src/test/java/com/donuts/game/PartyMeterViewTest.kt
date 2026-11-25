package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The sprinkle party meter in a running GameView: clears pour in, a full meter starts a party, prefs keep it. */
@RunWith(RobolectricTestRunner::class)
class PartyMeterViewTest {

    private fun settle(v: GameView, c: android.graphics.Canvas) {
        var n = 0
        do { TestViews.frames(v, c, 1) } while (v.animPhase != AnimPhase.IDLE && n++ < 800)
    }

    @Test fun aClearPoursIntoTheMeterAndPrefsKeepIt() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        val before = TestViews.total(v)
        TestViews.playHintChain(v)
        settle(v, c)
        val cleared = TestViews.total(v) - before
        assertEquals(cleared, v.party.meter)
        assertEquals(cleared, v.prefs.partyMeter)
        assertFalse(v.party.partyOn)
    }

    @Test fun aFullMeterStartsAPartyThatRaisesGoldenAndEndsAfterFourChains() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        TestViews.frames(v, c, 3)
        v.setParty(PartyState(PartyMeter.FILL - 1, 0))
        TestViews.playHintChain(v)
        settle(v, c)
        assertTrue(v.party.partyOn)
        assertEquals(PartyMeter.CHAINS, v.party.left)
        assertEquals(PartyMeter.CHAINS, v.prefs.partyLeft)
        assertTrue("party banner shown or queued",
            v.celebrateLabel == PartyMeter.LABEL || v.celebrateLook == PartyMeter.LOOK)

        TestViews.frames(v, c, 200)          // let the banner pass
        TestViews.playHintChain(v)
        settle(v, c)
        assertEquals(PartyMeter.GOLDEN, v.board.goldenChance, 0.0)
        assertEquals(PartyMeter.CHAINS - 1, v.party.left)
        repeat(PartyMeter.CHAINS - 1) { TestViews.playHintChain(v); settle(v, c) }
        assertFalse("the party ends after its four chains", v.party.partyOn)
        assertEquals(0, v.prefs.partyLeft)
        TestViews.playHintChain(v); settle(v, c)
        assertEquals(GameBoard.GOLDEN_CHANCE, v.board.goldenChance, 0.0)
    }

    @Test fun savedPartyStateComesBackInANewView() {
        val p = Prefs(TestViews.app())
        p.partyMeter = 17; p.partyLeft = 2
        val v = TestViews.gameView()
        assertEquals(PartyState(17, 2), v.party)
    }

    @Test fun resetStickersEmptiesTheMeterButNewGameKeepsIt() {
        val v = TestViews.quietView()
        v.setParty(PartyState(20, 0))
        v.startOver()
        assertEquals(20, v.party.meter)
        v.resetStickers()
        assertEquals(PartyState(), v.party)
        assertEquals(0, v.prefs.partyMeter)
        assertEquals(0, v.prefs.partyLeft)
    }

    @Test fun theMeterDrawsHalfFullAndDuringAParty() {
        val v = TestViews.quietView()
        val c = TestViews.canvas(v)
        v.setParty(PartyState(PartyMeter.FILL / 2, 0))
        TestViews.frames(v, c, 2)
        assertEquals(0.5f, v.partyShownFill, 0.01f)
        v.setParty(PartyState(0, PartyMeter.CHAINS))
        TestViews.frames(v, c, 120)
        assertEquals(1f, v.partyShownFill, 0.01f)
    }
}
