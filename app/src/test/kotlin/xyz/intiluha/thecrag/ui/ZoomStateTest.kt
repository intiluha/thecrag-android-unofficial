package xyz.intiluha.thecrag.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import xyz.intiluha.thecrag.ui.topo.ZoomState

class ZoomStateTest {
    // Portrait phone showing a 16:9 topo: 1080 wide, image 607.5 tall, letterboxed in 2400.
    private val w = 1080f
    private val h = 2400f
    private val aspect = 16f / 9f
    private val contentH = w / aspect
    private val originY = (h - contentH) / 2f

    /** Where image point (px, py) ends up on screen for a state. */
    private fun ZoomState.screen(px: Float, py: Float) = Pair(x + px * scale, originY + y + py * scale)

    @Test
    fun pointUnderTheFingersStaysPut() {
        val start = ZoomState()
        // Fingers on an image point well off-centre, to the right and below the image centre.
        val px = 800f
        val py = 500f
        val (cx, cy) = start.screen(px, py)
        val zoomed = start.step(2f, cx, cy, 0f, 0f, w, h, aspect)
        assertEquals(2f, zoomed.scale, 1e-4f)
        val (sx, sy) = zoomed.screen(px, py)
        assertEquals(cx, sx, 0.01f)
        assertEquals(cy, sy, 0.01f)
    }

    @Test
    fun zoomingTheCentreKeepsTheImageCentred() {
        val start = ZoomState()
        val zoomed = start.step(1.5f, w / 2f, h / 2f, 0f, 0f, w, h, aspect)
        // Still fits vertically: centred. Horizontally the middle stays in the middle.
        val (sx, sy) = zoomed.screen(w / 2f, contentH / 2f)
        assertEquals(w / 2f, sx, 0.01f)
        assertEquals(h / 2f, sy, 0.01f)
    }

    @Test
    fun cannotPanAwayFromTheImage() {
        val zoomed = ZoomState().step(3f, w / 2f, h / 2f, 0f, 0f, w, h, aspect)
        val dragged = zoomed.step(1f, w / 2f, h / 2f, 5000f, 0f, w, h, aspect)
        assertEquals(0f, dragged.x, 0.01f) // left edge of the image at the left edge of the view
        val back = dragged.step(1f, w / 2f, h / 2f, -9000f, 0f, w, h, aspect)
        assertEquals(w - w * 3f, back.x, 0.01f)
    }

    @Test
    fun zoomingOutFullyResets() {
        val zoomed = ZoomState().step(4f, 300f, 900f, 0f, 0f, w, h, aspect)
        val out = zoomed.step(0.1f, 300f, 900f, 0f, 0f, w, h, aspect)
        assertEquals(ZoomState(), out)
    }
}
