package xyz.intiluha.thecrag.ui.topo

/**
 * Pinch-zoom state of a full-screen topo: the image is laid out at the viewport's
 * width, centred vertically, then scaled about its own top-left corner and
 * translated by ([x], [y]) in viewport pixels. Pure arithmetic so it can be tested.
 */
data class ZoomState(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    /**
     * One gesture step: [zoom] factor and [panX]/[panY] from the detector, with the
     * fingers' centroid ([cx], [cy]) in viewport coordinates. The point under the
     * fingers stays put; the image stays inside the view while it fits and covers it once it is larger.
     *
     * The subtlety is the letterbox: the unscaled image's top-left sits at
     * `(0, (viewH - contentH) / 2)`, not at the viewport origin, so the centroid
     * has to be taken relative to that corner before pivoting around it.
     */
    fun step(zoom: Float, cx: Float, cy: Float, panX: Float, panY: Float, viewW: Float, viewH: Float, aspect: Float): ZoomState {
        val contentH = viewW / aspect
        val originY = (viewH - contentH) / 2f
        val newScale = (scale * zoom).coerceIn(MIN, MAX)
        if (newScale == MIN) return ZoomState()
        val k = newScale / scale
        val c = cy - originY
        val nx = (x - cx) * k + cx + panX
        val ny = (y - c) * k + c + panY
        return ZoomState(newScale, nx, ny).clamped(viewW, viewH, contentH)
    }

    private fun clamped(viewW: Float, viewH: Float, contentH: Float): ZoomState {
        val w = viewW * scale
        val h = contentH * scale
        val originY = (viewH - contentH) / 2f
        // Horizontally the image is at least as wide as the view: keep it covering.
        val cx = x.coerceIn(viewW - w, 0f)
        // Vertically: inside the view while it fits (so the pivot is honoured), covering once taller.
        val cy = if (h <= viewH) y.coerceIn(-originY, viewH - h - originY) else y.coerceIn(viewH - originY - h, -originY)
        return copy(x = cx, y = cy)
    }

    companion object {
        const val MIN = 1f
        const val MAX = 8f
    }
}
