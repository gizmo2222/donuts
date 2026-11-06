package com.donuts.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BannerQueueTest {
    @Test fun firstBannerStartsAtOnce() {
        assertEquals("A", BannerQueue(1000).offer(0, "A"))
    }

    @Test fun secondBannerWaitsForTheFirstPart() {
        val q = BannerQueue(1000)
        q.offer(0, "BOX FULL!")
        assertNull(q.offer(100, "50 DONUTS!"))
        assertNull(q.poll(900))
        assertEquals("50 DONUTS!", q.poll(1000))
        assertNull(q.poll(1100))
    }

    @Test fun bannerAfterTheFirstPartStartsAtOnce() {
        val q = BannerQueue(1000)
        q.offer(0, "A")
        assertEquals("B", q.offer(1500, "B"))
    }

    @Test fun waitingBannersKeepTheirOrder() {
        val q = BannerQueue(1000)
        q.offer(0, "A"); q.offer(10, "B"); q.offer(20, "C")
        assertEquals("B", q.poll(1000))
        assertNull(q.poll(1500))
        assertEquals("C", q.poll(2000))
    }

    @Test fun clearForgetsEverything() {
        val q = BannerQueue(1000)
        q.offer(0, "A"); q.offer(10, "B")
        q.clear()
        assertNull(q.poll(5000))
        assertEquals("C", q.offer(5001, "C"))
    }
}
