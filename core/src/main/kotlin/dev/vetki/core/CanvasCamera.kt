package dev.vetki.core

/** Transform around one screen-space anchor; zoom limits must not move that anchor. */
data class CanvasCamera(val zoom: Float, val x: Float, val y: Float) {
    fun transform(anchor: WorldPoint, delta: WorldPoint, scale: Float): CanvasCamera {
        if (!scale.isFinite() || scale <= 0 || !anchor.x.isFinite() || !anchor.y.isFinite())
            return this
        val next = (zoom * scale).coerceIn(.15f, 2.5f)
        if (next == zoom) {
            if (delta.x == 0f && delta.y == 0f) return this
            return copy(x = x + delta.x, y = y + delta.y)
        }
        val ratio = next / zoom
        return CanvasCamera(
            next,
            anchor.x - (anchor.x - x) * ratio + delta.x,
            anchor.y - (anchor.y - y) * ratio + delta.y,
        )
    }
}

/** Keep dot density bounded in screen space at every zoom level. */
fun canvasGridStep(zoom: Float): Float {
    require(zoom.isFinite() && zoom > 0)
    var step = 12f
    while (step * zoom < 12f) step *= 2
    return step
}
