package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every sound has a picture with it, with full motion and under reduced motion. */
class SoundCuesTest {

    @Test fun everySoundLevelHasABouncyAndACalmCue() {
        for (level in SoundLevels.Level.values()) {
            val cue = SoundCues.cues[level]
            assertTrue("$level has no visual cue", cue != null)
            assertTrue("$level has a blank bouncy cue", cue!!.bouncy.isNotBlank())
            assertTrue("$level has a blank calm cue", cue.calm.isNotBlank())
        }
        assertEquals(SoundLevels.Level.values().size, SoundCues.cues.size)
    }
}
