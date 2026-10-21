package com.bydmate.app.acbubble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AcBubbleLayoutTest {

    // --- opensLeft: the strip opens toward the side with more room ---

    @Test
    fun `a bubble on the right half opens its strip to the left`() {
        assertTrue(AcBubbleLayout.opensLeft(bubbleX = 900, bubbleW = 100, screenW = 1080))
        assertFalse(AcBubbleLayout.opensLeft(bubbleX = 50, bubbleW = 100, screenW = 1080))
    }

    // --- fitScale: the strip shrinks until it fits beside the bubble ---

    @Test
    fun `a strip that fits keeps the user's scale`() {
        assertEquals(1.2f, AcBubbleLayout.fitScale(userScale = 1.2f, stripPx = 600, availablePx = 900), 0f)
    }

    @Test
    fun `a strip wider than the room shrinks in proportion`() {
        // 1.0 × 900/1200 = 0.75
        assertEquals(0.75f, AcBubbleLayout.fitScale(userScale = 1.0f, stripPx = 1200, availablePx = 900), 0.001f)
    }

    @Test
    fun `the strip never shrinks below the smallest size setting`() {
        val s = AcBubbleLayout.fitScale(userScale = 1.0f, stripPx = 5000, availablePx = 100)
        assertEquals(AcBubblePreferences.SCALE_MIN, s, 0f)
    }

    @Test
    fun `no room at all still gives a usable scale`() {
        assertEquals(AcBubblePreferences.SCALE_MIN, AcBubbleLayout.fitScale(1.0f, stripPx = 600, availablePx = 0), 0f)
    }

    // --- clampWindowX: the whole window stays on screen ---

    @Test
    fun `a window hanging off the right edge is pulled back`() {
        assertEquals(480, AcBubbleLayout.clampWindowX(x = 700, windowW = 600, screenW = 1080))
    }

    @Test
    fun `a window hanging off the left edge is pulled back`() {
        assertEquals(0, AcBubbleLayout.clampWindowX(x = -40, windowW = 600, screenW = 1080))
    }

    @Test
    fun `a window wider than the screen sits at the left edge`() {
        assertEquals(0, AcBubbleLayout.clampWindowX(x = 100, windowW = 2000, screenW = 1080))
    }

    // --- docked tab: snaps to the nearer side edge ---

    @Test
    fun `a tab released past the middle docks right`() {
        assertTrue(AcBubbleLayout.docksRight(x = 600, tabW = 64, screenW = 1080))
        assertFalse(AcBubbleLayout.docksRight(x = 400, tabW = 64, screenW = 1080))
    }

    @Test
    fun `a tab released exactly centred docks right`() {
        // centre = 508 + 32 = 540 = screenW / 2
        assertTrue(AcBubbleLayout.docksRight(x = 508, tabW = 64, screenW = 1080))
    }

    @Test
    fun `a docked tab sits flush with its edge`() {
        assertEquals(0, AcBubbleLayout.dockX(right = false, tabW = 64, screenW = 1080))
        assertEquals(1016, AcBubbleLayout.dockX(right = true, tabW = 64, screenW = 1080))
    }
}
