package com.donuts.game

/** Remembers which finger owns the current chain, so a second finger cannot hijack it. Pure. */
class ChainPointer {
    var activeId: Int = NONE
        private set

    /** The first finger goes down: it becomes the active one. */
    fun onDown(id: Int) { activeId = id }

    /** An extra finger goes down: ignored. */
    @Suppress("UNUSED_PARAMETER")
    fun onPointerDown(id: Int) {}

    /** A finger lifts. True when it is the active finger, i.e. the chain is released. */
    fun onPointerUp(id: Int): Boolean {
        if (activeId == NONE || id != activeId) return false
        activeId = NONE
        return true
    }

    fun reset() { activeId = NONE }

    companion object { const val NONE = -1 }
}
