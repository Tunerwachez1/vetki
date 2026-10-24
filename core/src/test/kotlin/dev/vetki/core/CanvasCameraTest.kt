package dev.vetki.core

import org.junit.Assert.*
import org.junit.Test

class CanvasCameraTest {
    @Test
    fun zoomKeepsTheWorldPointUnderItsAnchor() {
        val before = CanvasCamera(.5f, -140f, 320f)
        val anchor = WorldPoint(517f, 1240f)
        val after = before.transform(anchor, WorldPoint(0f, 0f), .3f)
        assertEquals(.15f, after.zoom, 0.00001f)
        assertEquals((anchor.x - before.x) / before.zoom, (anchor.x - after.x) / after.zoom, .001f)
        assertEquals((anchor.y - before.y) / before.zoom, (anchor.y - after.y) / after.zoom, .001f)
    }

    @Test
    fun repeatedButtonZoomDoesNotDrift() {
        var camera = CanvasCamera(.5f, -133.7f, 242.3f)
        val before = camera
        val center = WorldPoint(540f, 991.5f)
        repeat(1000) {
            camera = camera.transform(center, WorldPoint(0f, 0f), 1.25f)
            camera = camera.transform(center, WorldPoint(0f, 0f), .8f)
        }
        assertEquals(before.zoom, camera.zoom, .0001f)
        assertEquals(before.x, camera.x, .1f)
        assertEquals(before.y, camera.y, .1f)
    }

    @Test
    fun clampedZoomDoesNotMoveTheCamera() {
        val camera = CanvasCamera(.15f, 123.4f, -561.7f)
        assertSame(camera, camera.transform(WorldPoint(900f, 1300f), WorldPoint(0f, 0f), .8f))
        val maximum = camera.copy(zoom = 2.5f)
        assertSame(maximum, maximum.transform(WorldPoint(100f, 250f), WorldPoint(0f, 0f), 1.25f))
    }

    @Test
    fun panAndZoomUseTheSameAnchor() {
        val anchor = WorldPoint(300f, 700f)
        val delta = WorldPoint(40f, -25f)
        val before = CanvasCamera(.8f, 30f, 90f)
        val after = before.transform(anchor, delta, 1.5f)
        assertEquals(
            (anchor.x - before.x) / before.zoom,
            (anchor.x + delta.x - after.x) / after.zoom,
            .001f,
        )
        assertEquals(
            (anchor.y - before.y) / before.zoom,
            (anchor.y + delta.y - after.y) / after.zoom,
            .001f,
        )
    }

    @Test
    fun invalidScaleIsIgnored() {
        val camera = CanvasCamera(1f, 0f, 0f)
        listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY).forEach {
            assertSame(camera, camera.transform(WorldPoint(50f, 50f), WorldPoint(0f, 0f), it))
        }
    }

    @Test
    fun gridDensityStaysBoundedFromFifteenToTwoHundredFiftyPercent() {
        repeat(236) { step ->
            val zoom = .15f + step * .01f
            val spacing = canvasGridStep(zoom) * zoom
            assertTrue("zoom=$zoom spacing=$spacing", spacing >= 12f && spacing <= 30.01f)
        }
        assertEquals(12f, canvasGridStep(1f), 0f)
        assertEquals(24f, canvasGridStep(.5f), 0f)
        assertEquals(96f, canvasGridStep(.15f), 0f)
    }
}
