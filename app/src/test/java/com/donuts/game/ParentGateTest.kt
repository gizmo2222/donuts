package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ParentGateTest {
    private fun gate(seed: Int = 1) = ParentGate(Random(seed))

    @Test fun everyQuestionIsATwoDigitTimesTableInWords() {
        val g = gate()
        repeat(500) {
            assertTrue(g.a in 3..9); assertTrue(g.b in 4..9)
            assertTrue(g.answer in 12..81)
            assertEquals("What is ${ParentGate.WORDS[g.a]} times ${ParentGate.WORDS[g.b]}?", g.question)
            assertTrue("no digits in the question", g.question.none { it.isDigit() })
            g.next()
        }
    }

    @Test fun nextNeverRepeatsThePairAndClearsTheEntry() {
        val g = gate(7)
        repeat(500) {
            val a = g.a; val b = g.b
            g.press(1)
            g.next()
            assertTrue(a != g.a || b != g.b)
            assertEquals("", g.entry)
        }
    }

    @Test fun theRightTwoDigitsOpen() {
        val g = gate(3)
        val ans = g.answer
        assertEquals(ParentGate.TYPING, g.press(ans / 10))
        assertEquals((ans / 10).toString(), g.entry)
        assertEquals(ParentGate.OPEN, g.press(ans % 10))
        assertEquals("", g.entry)
    }

    @Test fun aWrongSecondDigitAsksANewQuestion() {
        val g = gate(5)
        val ans = g.answer; val q = g.a to g.b
        assertEquals(ParentGate.TYPING, g.press(ans / 10))
        assertEquals(ParentGate.WRONG, g.press((ans % 10 + 1) % 10))
        assertNotEquals(q, g.a to g.b)
        assertEquals("", g.entry)
    }

    @Test fun aLeadingZeroAndOutOfRangeKeysAreIgnored() {
        val g = gate(9)
        assertEquals(ParentGate.TYPING, g.press(0))
        assertEquals("", g.entry)
        assertEquals(ParentGate.TYPING, g.press(-1))
        assertEquals(ParentGate.TYPING, g.press(10))
        assertEquals("", g.entry)
        val ans = g.answer
        g.press(ans / 10)
        assertEquals(ParentGate.OPEN, g.press(ans % 10))
    }

    @Test fun guessingRarelyGetsIn() {
        val g = gate(11)
        val r = Random(42)
        var opened = 0
        repeat(9000) { if (g.press(1 + r.nextInt(9)) == ParentGate.TYPING && g.press(r.nextInt(10)) == ParentGate.OPEN) opened++ }
        assertTrue("opened $opened of 9000", opened < 200)
    }
}
