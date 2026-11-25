package com.donuts.game

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/** Unit tests for [PicturePrompts] (no Android needed). */
class PicturePromptsTest {
    private val types = DonutType.values().size

    @Test fun theFirstPromptAsksForThreeAndLaterOnesForThreeToSix() {
        val r = Random(7)
        assertEquals(PicturePrompts.FIRST_GOAL, PicturePrompts.next(r, -1, types, 0).goal)
        val goals = (1..400).map { PicturePrompts.next(r, -1, types, it).goal }.toSet()
        assertEquals((3..6).toSet(), goals)
    }

    @Test fun aNewPromptNeverRepeatsTheLastTypeAndEveryOtherTypeTurnsUp() {
        val r = Random(11)
        for (last in 0 until types) {
            val seen = (1..300).map { PicturePrompts.next(r, last, types, 1).type }.toSet()
            assertFalse(last in seen)
            assertEquals(types - 1, seen.size)
        }
    }

    @Test fun theSameSeedGivesTheSamePrompts() {
        val a = Random(3); val b = Random(3)
        repeat(20) { assertEquals(PicturePrompts.next(a, it % types, types, it), PicturePrompts.next(b, it % types, types, it)) }
    }

    @Test fun popsCountUpToTheGoalAndNoFurther() {
        var p = Prompt(type = 2, goal = 5)
        assertEquals(5, p.left); assertFalse(p.complete)
        p = PicturePrompts.after(p, 3)
        assertEquals(2, p.left)
        p = PicturePrompts.after(p, 0)
        assertEquals(2, p.left)
        p = PicturePrompts.after(p, -4)
        assertEquals(3, p.done)
        p = PicturePrompts.after(p, 9)
        assertEquals(5, p.done); assertEquals(0, p.left); assertTrue(p.complete)
        assertSame(p, PicturePrompts.after(p, 2))
    }

    @Test fun encodeAndDecodeRoundTrip() {
        val p = Prompt(type = 4, goal = 6, done = 2)
        assertEquals(Pair(p, 9), PicturePrompts.decode(PicturePrompts.encode(p, 9), types))
    }

    @Test fun badTextDecodesToNull() {
        for (bad in listOf(null, "", "p1", "p2;1;3;0;0", "p1;x;3;0;0", "p1;6;3;0;0", "p1;-1;3;0;0",
                           "p1;1;2;0;0", "p1;1;7;0;0", "p1;1;3;4;0", "p1;1;3;-1;0", "p1;1;3;0;-1", "p1;1;3;0;0;0")) {
            assertNull(bad, PicturePrompts.decode(bad, types))
        }
    }
}
