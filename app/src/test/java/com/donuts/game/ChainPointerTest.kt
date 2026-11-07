package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainPointerTest {
    @Test fun secondFingerIsIgnored() {
        val p = ChainPointer()
        p.onDown(3)
        assertEquals(3, p.activeId)
        assertFalse(p.onPointerUp(7))
        assertEquals(3, p.activeId)
    }

    @Test fun liftingTheActiveFingerReleasesEvenWhenItIsNotIndexZero() {
        val p = ChainPointer()
        p.onDown(0)
        assertTrue(p.onPointerUp(0))
        assertEquals(ChainPointer.NONE, p.activeId)
        assertFalse(p.onPointerUp(1))
    }

    @Test fun resetClears() {
        val p = ChainPointer()
        p.onDown(2); p.reset()
        assertEquals(ChainPointer.NONE, p.activeId)
        assertFalse(p.onPointerUp(2))
    }
}
