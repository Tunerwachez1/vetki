package dev.vetki.core

import org.junit.Assert.*
import org.junit.Test

class CanvasGeometryTest {
    private fun card(id: String, x: Float, y: Float, kind: String = "text", group: String? = null) =
        CanvasCard(id, null, id, "", x, y, 0, false, null, kind, 190f, 112f, group)

    @Test
    fun freeDropRetainsExactPosition() {
        assertEquals(
            WorldPoint(-83.5f, 916f),
            freePosition(WorldPoint(-83.5f, 916f), 190f, 112f, emptyList()),
        )
    }

    @Test
    fun overlappingDropFindsFreePlaceWithGap() {
        val obstacle = WorldRect(0f, 0f, 190f, 112f)
        val point = freePosition(WorldPoint(10f, 10f), 190f, 112f, listOf(obstacle))
        assertFalse(WorldRect(point.x, point.y, 190f, 112f).overlaps(obstacle, 13.9f))
        assertTrue(kotlin.math.abs(point.x - 10) < 210 && kotlin.math.abs(point.y - 10) < 140)
    }

    @Test
    fun gridSnappingStillAvoidsNeighbours() {
        val obstacle = WorldRect(0f, 0f, 190f, 112f)
        val p = freePosition(WorldPoint(50f, 20f), 190f, 112f, listOf(obstacle), grid = true)
        assertEquals(0f, p.x % 24, .001f)
        assertEquals(0f, p.y % 24, .001f)
        assertFalse(WorldRect(p.x, p.y, 190f, 112f).overlaps(obstacle, 13.9f))
    }

    @Test
    fun denseBoardDoesNotTrapDroppedCard() {
        val blocks =
            (0..10).flatMap { row ->
                (0..10).map { col -> WorldRect(col * 210f, row * 135f, 190f, 112f) }
            }
        val point = freePosition(WorldPoint(900f, 600f), 190f, 112f, blocks)
        assertTrue(blocks.none { WorldRect(point.x, point.y, 190f, 112f).overlaps(it, 13.9f) })
    }

    @Test
    fun objectAlignmentIsOptional() {
        val moving = card("a", 0f, 0f)
        val other = card("b", 100f, 500f)
        assertEquals(
            103f,
            snapPosition(WorldPoint(103f, 0f), moving, listOf(other), false, false).x,
            .01f,
        )
        assertEquals(
            100f,
            snapPosition(WorldPoint(103f, 0f), moving, listOf(other), false, true).x,
            .01f,
        )
    }

    @Test
    fun groupBackgroundDoesNotBlockCards() {
        val group = card("g", 0f, 0f, "group")
        assertEquals(
            WorldPoint(12f, 30f),
            snapPosition(WorldPoint(12f, 30f), card("a", 300f, 0f), listOf(group), false, false),
        )
    }

    @Test
    fun movingGroupAvoidsOutsidersAsACluster() {
        val group = card("g", 0f, 0f, "group")
        val member = card("m", 20f, 30f, group = "g")
        val outsider = card("o", 400f, 30f)
        val position =
            snapPosition(WorldPoint(380f, 0f), group, listOf(group, member, outsider), false, false)
        assertFalse(
            WorldRect(member.x + position.x, member.y + position.y, member.width, member.height)
                .overlaps(outsider.rect(), 13.9f)
        )
    }

    @Test
    fun horizontalArrowEndsAtCardBoundary() {
        val curve = arrowCurve(WorldRect(0f, 0f, 190f, 112f), WorldRect(400f, 0f, 190f, 112f))
        assertEquals(WorldPoint(190f, 56f), curve.start)
        assertEquals(WorldPoint(400f, 56f), curve.end)
        assertTrue(curve.control2.x < curve.end.x)
    }

    @Test
    fun verticalArrowPointsDownward() {
        val curve = arrowCurve(WorldRect(0f, 0f, 190f, 112f), WorldRect(0f, 300f, 190f, 112f))
        assertEquals(WorldPoint(95f, 112f), curve.start)
        assertEquals(WorldPoint(95f, 300f), curve.end)
        assertTrue(curve.control2.y < curve.end.y)
    }

    @org.junit.Test
    fun explicitPortsStartAtChosenEdgeAndLeaveOutwards() {
        val source = WorldRect(0f, 0f, 190f, 112f)
        val target = WorldRect(400f, 300f, 190f, 112f)
        listOf("top", "bottom", "left", "right").forEach { side ->
            val curve = connectionCurve(source, target, side)
            org.junit.Assert.assertEquals(source.port(side), curve.start)
            when (side) {
                "top" -> org.junit.Assert.assertTrue(curve.control1.y < curve.start.y)
                "bottom" -> org.junit.Assert.assertTrue(curve.control1.y > curve.start.y)
                "left" -> org.junit.Assert.assertTrue(curve.control1.x < curve.start.x)
                else -> org.junit.Assert.assertTrue(curve.control1.x > curve.start.x)
            }
            org.junit.Assert.assertTrue(
                listOf("top", "bottom", "left", "right").any { target.port(it) == curve.end }
            )
        }
    }
}
