package com.donuts.game

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** TalkBack on the canvas: the virtual nodes GameView and MainView list, their words, boxes and presses. */
@RunWith(RobolectricTestRunner::class)
class AccessNodesTest : GameInputTestBase() {

    private fun labels(v: GameView) = v.accessNodes().map { it.label }
    private fun node(v: GameView, label: String) = v.accessNodes().first { it.label == label }
    private fun click(v: GameView, label: String) =
        v.access.performAction(node(v, label).id, AccessibilityNodeInfo.ACTION_CLICK, null)

    @Test
    fun theHudNamesTheButtonsTheCounterAndTheBoardInsideTheirBoxes() {
        val v = planted()
        TestViews.frames(v, TestViews.canvas(v), 1)   // the counter's box is set as it is drawn
        val l = labels(v)
        assertEquals(listOf("Stickers", "Settings"), l.take(2))
        assertTrue(l.toString(), l.contains("0 donuts"))
        assertTrue(l.any { it.startsWith("Donut board, 6 by 6.") })
        assertEquals(v.stickersBtnRect, node(v, "Stickers").bounds)
        assertTrue(node(v, "Settings").clickable)
        assertFalse(node(v, "0 donuts").clickable)
        val ids = v.accessNodes().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun theHostNodeListsEveryVirtualNodeAndEachOneHasItsWords() {
        val v = planted()
        val host = v.access.createAccessibilityNodeInfo(View.NO_ID)!!
        assertEquals(v.accessNodes().size, host.childCount)
        val star = v.access.createAccessibilityNodeInfo(ACCESS_STICKERS)!!
        assertEquals("Stickers", star.contentDescription)
        assertTrue(star.isClickable)
        assertEquals("android.widget.Button", star.className)
        assertNull(v.access.createAccessibilityNodeInfo(424242))
    }

    @Test
    fun pressingTheStarAndTheGearOpensTheirPanels() {
        val v = planted()
        assertTrue(click(v, "Stickers"))
        assertTrue(v.stickersOpen)
        val w = planted()
        assertTrue(click(w, "Settings"))
        assertTrue(w.settingsOpen)
        assertEquals(SETTINGS_KID, w.settingsPage)
    }

    @Test
    fun focusMovesFromNodeToNodeAndClears() {
        val v = planted()
        val a = v.access
        assertTrue(a.performAction(ACCESS_STICKERS, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
        assertFalse(a.performAction(ACCESS_STICKERS, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
        assertEquals(ACCESS_STICKERS, a.focused)
        assertTrue(a.createAccessibilityNodeInfo(ACCESS_STICKERS)!!.isAccessibilityFocused)
        assertTrue(a.performAction(ACCESS_SETTINGS, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null))
        assertEquals(ACCESS_SETTINGS, a.focused)
        assertNotNull(a.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
        assertTrue(a.performAction(ACCESS_SETTINGS, AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS, null))
        assertEquals(View.NO_ID, a.focused)
        assertNull(a.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY))
        assertFalse(a.performAction(ACCESS_BOARD, AccessibilityNodeInfo.ACTION_CLICK, null))
    }

    @Test
    fun stevensSettingsPageReadsEachChoiceAndAPressChangesIt() {
        val v = planted()
        openSettings(v)
        val l = labels(v)
        assertFalse(l.contains("Stickers"))
        for (w in listOf("Sound on", "Sound off", "Buzz on", "Buzz off", "Hints on", "Hints off", "Grown-ups only", "Done"))
            assertTrue(w, l.contains(w))
        assertTrue(node(v, "Sound on").selected)
        assertTrue(click(v, "Sound off"))
        assertFalse(v.prefs.soundEnabled)
        assertTrue(node(v, "Sound off").selected)
        val off = node(v, "Sound off").bounds
        assertEquals(v.soundRects[1].centerX(), off.centerX(), 0.5f)
        assertEquals(v.soundRects[1].centerY() + v.settingsSlideY(), off.centerY(), 0.5f)
    }

    @Test
    fun theGateReadsItsQuestionAndTheKeysAnswerIt() {
        val v = planted()
        openSettings(v)
        assertTrue(click(v, "Grown-ups only"))
        assertEquals(SETTINGS_GATE, v.settingsPage)
        val q = v.accessNodes().first { it.label.startsWith("Grown-ups only.") }
        assertTrue(q.label.contains(v.parentGate.question))
        assertTrue(q.label.endsWith("Typed: nothing yet"))
        val ans = v.parentGate.answer
        click(v, "${ans / 10}")
        click(v, "${ans % 10}")
        assertEquals(SETTINGS_GROWN, v.settingsPage)
        val l = labels(v)
        assertTrue(l.toString(), l.contains("Size: Big, 6 by 6"))
        assertTrue(node(v, "Size: Big, 6 by 6").selected)
        assertTrue(l.contains("Motion: Bouncy"))
        assertTrue(l.contains("New game, tap twice"))
        assertTrue(l.contains("Reset stickers, tap twice"))
        assertTrue(l.any { it.startsWith("Rest reminder: ") })
        assertTrue(click(v, "New game, tap twice"))
        assertTrue(labels(v).contains("New game? Tap again to start over"))
    }

    @Test
    fun theStickersPanelReadsStickersTabsAndFriends() {
        val v = planted()
        v.prefs.friendsMet = 2
        openStickers(v)
        val l = labels(v)
        assertTrue(l.toString(), l.contains("Stickers page"))
        assertTrue(l.contains("Friends page"))
        assertTrue(node(v, "Stickers page").selected)
        assertTrue(l.any { it.endsWith("not earned yet") })
        assertTrue(l.any { it.startsWith("Donut box. ") })
        assertTrue(click(v, "Friends page"))
        assertEquals(1, v.stickersPage)
        val f = labels(v)
        assertTrue(f.contains(FriendRules.FRIENDS[0].name))
        assertTrue(f.contains(FriendRules.FRIENDS[1].name))
        assertFalse(f.contains(FriendRules.FRIENDS[2].name))
        assertTrue(click(v, "Done"))
        assertFalse(v.stickersOpen)
    }

    @Test
    fun theCardIsTheOnlyNodeAndClosesOnlyAfterItsGuard() {
        val v = TestViews.quietView()
        v.showSomeoneMs = SystemClock.elapsedRealtime(); v.showSomeoneKind = SHOW_BOX
        val nodes = v.accessNodes()
        assertEquals(1, nodes.size)
        assertTrue(nodes[0].label.startsWith("Show someone!"))
        v.access.performAction(ACCESS_CARD, AccessibilityNodeInfo.ACTION_CLICK, null)
        assertTrue(v.showSomeoneMs >= 0)
        TestViews.advance(SHOW_CARD_GUARD_MS)
        v.access.performAction(ACCESS_CARD, AccessibilityNodeInfo.ACTION_CLICK, null)
        assertEquals(-1L, v.showSomeoneMs)
        assertTrue(labels(v).contains("Stickers"))
    }

    @Test
    fun exploreByTouchFindsTheNodeUnderTheFingerOnlyWhileTouchExplorationIsOn() {
        val v = planted()
        val r = v.settingsBtnRect
        val t = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, r.centerX(), r.centerY(), 0)
        assertFalse(v.access.onHover(e))
        val am = TestViews.app().getSystemService(AccessibilityManager::class.java)
        shadowOf(am).setEnabled(true)
        shadowOf(am).setTouchExplorationEnabled(true)
        assertTrue(v.access.onHover(e))
        assertEquals(ACCESS_SETTINGS, v.access.hovered)
        e.recycle()
    }

    @Test
    fun refreshForgetsAFocusThatIsNoLongerOnScreen() {
        val v = planted()
        v.access.refresh()
        v.access.performAction(ACCESS_STICKERS, AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        v.access.performAction(ACCESS_STICKERS, AccessibilityNodeInfo.ACTION_CLICK, null)
        assertEquals(View.NO_ID, v.access.focused)
    }

    @Test
    fun theHomeScreenReadsTheTitleAndPlayStartsTheGame() {
        var plays = 0
        val mv = MainView(TestViews.app()) { plays++ }
        mv.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(2400, View.MeasureSpec.EXACTLY))
        mv.layout(0, 0, 1080, 2400)
        val nodes = mv.accessNodes()
        assertEquals(listOf("Donuts for Steven", "Play"), nodes.map { it.label })
        assertEquals(mv.playRect, nodes[1].bounds)
        assertEquals(2, mv.accessibilityNodeProvider.createAccessibilityNodeInfo(View.NO_ID)!!.childCount)
        assertTrue(mv.access.performAction(2, AccessibilityNodeInfo.ACTION_CLICK, null))
        assertEquals(1, plays)
        assertFalse(mv.access.performAction(1, AccessibilityNodeInfo.ACTION_CLICK, null))
    }

    @Test
    fun wordsForSizesAndPrompts() {
        assertEquals("Big, 6 by 6", sizeWords("Big  6×6"))
        assertEquals("1 donut", donutsWord(1))
        assertEquals("2 donuts", donutsWord(2))
    }
}
