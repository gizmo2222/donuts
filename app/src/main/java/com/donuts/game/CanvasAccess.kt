package com.donuts.game

import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider

/**
 * TalkBack for a view that draws everything on a canvas: each [AccessNode] the view lists becomes a
 * virtual node with its label, bounds and click. Uses the framework provider, so no extra library.
 * Call [refresh] after anything that may change the nodes; it tells TalkBack only when they did.
 */
internal class CanvasAccess(private val host: View, private val nodes: () -> List<AccessNode>) :
    AccessibilityNodeProvider() {

    internal var focused = View.NO_ID; private set
    internal var hovered = View.NO_ID; private set
    private var lastSig = ""
    private val loc = IntArray(2)
    private val box = Rect()

    private fun node(id: Int): AccessNode? = nodes().firstOrNull { it.id == id }

    @Suppress("DEPRECATION")
    override fun createAccessibilityNodeInfo(virtualViewId: Int): AccessibilityNodeInfo? {
        if (virtualViewId == View.NO_ID) {
            val info = AccessibilityNodeInfo.obtain(host)
            host.onInitializeAccessibilityNodeInfo(info)
            for (n in nodes()) info.addChild(host, n.id)
            return info
        }
        val n = node(virtualViewId) ?: return null
        val info = AccessibilityNodeInfo.obtain(host, n.id)
        info.packageName = host.context.packageName
        info.className = if (n.clickable) "android.widget.Button" else "android.view.View"
        info.contentDescription = n.label
        info.setParent(host)
        n.bounds.roundOut(box)
        info.setBoundsInParent(box)
        host.getLocationOnScreen(loc)
        box.offset(loc[0], loc[1])
        info.setBoundsInScreen(box)
        info.isVisibleToUser = true
        info.isEnabled = true
        info.isFocusable = true
        info.isClickable = n.clickable
        info.isSelected = n.selected
        if (n.clickable) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)
        info.isAccessibilityFocused = focused == n.id
        info.addAction(
            if (focused == n.id) AccessibilityNodeInfo.AccessibilityAction.ACTION_CLEAR_ACCESSIBILITY_FOCUS
            else AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS
        )
        return info
    }

    override fun findFocus(focus: Int): AccessibilityNodeInfo? =
        if (focus == AccessibilityNodeInfo.FOCUS_ACCESSIBILITY && focused != View.NO_ID) createAccessibilityNodeInfo(focused) else null

    override fun performAction(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (virtualViewId == View.NO_ID) return host.performAccessibilityAction(action, arguments)
        val n = node(virtualViewId) ?: return false
        return when (action) {
            AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS -> {
                if (focused == n.id) return false
                val old = focused
                focused = n.id
                if (old != View.NO_ID) send(old, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                send(n.id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                host.invalidate()
                true
            }
            AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS -> {
                if (focused != n.id) return false
                focused = View.NO_ID
                send(n.id, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED)
                host.invalidate()
                true
            }
            AccessibilityNodeInfo.ACTION_CLICK -> {
                val act = n.act ?: return false
                act()
                send(n.id, AccessibilityEvent.TYPE_VIEW_CLICKED)
                refresh()
                true
            }
            else -> false
        }
    }

    /** Explore by touch: the node under the finger is announced. False when touch exploration is off. */
    fun onHover(event: MotionEvent): Boolean {
        val am = host.context.getSystemService(AccessibilityManager::class.java) ?: return false
        if (!am.isEnabled || !am.isTouchExplorationEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                val hit = nodes().firstOrNull { it.bounds.contains(event.x, event.y) }?.id ?: View.NO_ID
                setHovered(hit)
                return hit != View.NO_ID
            }
            MotionEvent.ACTION_HOVER_EXIT -> { setHovered(View.NO_ID); return true }
        }
        return false
    }

    private fun setHovered(id: Int) {
        if (id == hovered) return
        val old = hovered
        hovered = id
        if (id != View.NO_ID) send(id, AccessibilityEvent.TYPE_VIEW_HOVER_ENTER)
        if (old != View.NO_ID) send(old, AccessibilityEvent.TYPE_VIEW_HOVER_EXIT)
    }

    /** Compares the nodes with the last ones told; when they changed, TalkBack is told to read them again. */
    fun refresh() {
        val list = nodes()
        val sig = signature(list)
        if (sig == lastSig) return
        lastSig = sig
        if (list.none { it.id == focused }) focused = View.NO_ID
        if (list.none { it.id == hovered }) hovered = View.NO_ID
        send(View.NO_ID, AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    @Suppress("DEPRECATION")
    private fun send(id: Int, type: Int) {
        val am = host.context.getSystemService(AccessibilityManager::class.java) ?: return
        if (!am.isEnabled) return
        val parent = host.parent ?: return
        val e = AccessibilityEvent.obtain(type)
        e.packageName = host.context.packageName
        if (id == View.NO_ID) {
            host.onInitializeAccessibilityEvent(e)
            if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
                e.contentChangeTypes = AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE
        } else {
            val n = node(id)
            e.className = if (n?.clickable == true) "android.widget.Button" else "android.view.View"
            if (n != null) e.contentDescription = n.label
            e.setSource(host, id)
        }
        parent.requestSendAccessibilityEvent(host, e)
    }

    companion object {
        /** What TalkBack is told about: ids, words, selection and rounded bounds, never the actions. */
        fun signature(list: List<AccessNode>): String = buildString {
            for (n in list) {
                append(n.id).append('|').append(n.label).append('|').append(n.selected).append('|')
                append(n.bounds.left.toInt()).append(',').append(n.bounds.top.toInt()).append(',')
                append(n.bounds.right.toInt()).append(',').append(n.bounds.bottom.toInt()).append(';')
            }
        }
    }
}

/** A box on the canvas that TalkBack can read and, when [clickable], press. [bounds] are view pixels. */
internal fun accessNode(id: Int, label: String, r: RectF, act: (() -> Unit)? = null, selected: Boolean = false) =
    AccessNode(id, label, RectF(r), act != null, selected, act)
