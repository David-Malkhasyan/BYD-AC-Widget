package com.bydmate.app.acbubble

/** Pure geometry of the bubble window, kept out of the controller so it can be unit-tested. */
object AcBubbleLayout {

    /** The strip opens toward the side of the bubble with more room. */
    fun opensLeft(bubbleX: Int, bubbleW: Int, screenW: Int): Boolean =
        bubbleX > screenW - (bubbleX + bubbleW)

    /**
     * Scale that lets a strip measured [stripPx] wide at [userScale] fit into [availablePx]:
     * the user's own scale when it already fits, proportionally smaller otherwise, never below
     * the smallest size the settings allow (a strip that small is still readable in a car).
     */
    fun fitScale(userScale: Float, stripPx: Int, availablePx: Int): Float {
        if (stripPx <= 0 || stripPx <= availablePx) return userScale
        if (availablePx <= 0) return AcBubblePreferences.SCALE_MIN
        return (userScale * availablePx / stripPx).coerceAtLeast(AcBubblePreferences.SCALE_MIN)
    }

    /** Window origin that keeps a [windowW]-wide window fully on a [screenW]-wide screen. */
    fun clampWindowX(x: Int, windowW: Int, screenW: Int): Int =
        x.coerceIn(0, (screenW - windowW).coerceAtLeast(0))

    /** A released tab docks to the edge its centre is nearer to; the exact middle goes right. */
    fun docksRight(x: Int, tabW: Int, screenW: Int): Boolean = x + tabW / 2 >= screenW / 2

    /** Left of a tab docked flush with the chosen edge. */
    fun dockX(right: Boolean, tabW: Int, screenW: Int): Int = if (right) (screenW - tabW).coerceAtLeast(0) else 0
}
