package dev.vetki.core

import java.util.PriorityQueue
import kotlin.math.*

data class WorldPoint(val x: Float, val y: Float)

data class WorldRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right
        get() = x + width

    val bottom
        get() = y + height

    fun overlaps(other: WorldRect, gap: Float = 0f): Boolean =
        x < other.right + gap &&
            right + gap > other.x &&
            y < other.bottom + gap &&
            bottom + gap > other.y

    fun contains(point: WorldPoint) = point.x in x..right && point.y in y..bottom
}

fun CanvasCard.rect() = WorldRect(x, y, width, height)

/** Resolves only the dropped card; neighbours never move unexpectedly. */
fun freePosition(
    desired: WorldPoint,
    width: Float,
    height: Float,
    obstacles: List<WorldRect>,
    grid: Boolean = false,
    gap: Float = 14f,
): WorldPoint {
    require(desired.x.isFinite() && desired.y.isFinite() && width > 0 && height > 0)
    val start =
        if (grid) WorldPoint(round(desired.x / 24) * 24, round(desired.y / 24) * 24) else desired
    val queue =
        PriorityQueue<WorldPoint>(
            compareBy<WorldPoint> { (it.x - start.x).pow(2) + (it.y - start.y).pow(2) }
                .thenBy { it.y }
                .thenBy { it.x }
        )
    val visited = mutableSetOf<Pair<Int, Int>>()
    queue.add(start)
    var attempts = 0
    while (queue.isNotEmpty() && attempts++ < 6000) {
        val point = queue.remove()
        if (!visited.add((point.x * 10).roundToInt() to (point.y * 10).roundToInt())) continue
        val collision =
            obstacles.filter { WorldRect(point.x, point.y, width, height).overlaps(it, gap - .01f) }
        if (collision.isEmpty()) return point
        collision.forEach { block ->
            fun low(value: Float) = if (grid) floor(value / 24) * 24 else value
            fun high(value: Float) = if (grid) ceil(value / 24) * 24 else value
            queue.add(WorldPoint(low(block.x - width - gap), point.y))
            queue.add(WorldPoint(high(block.right + gap), point.y))
            queue.add(WorldPoint(point.x, low(block.y - height - gap)))
            queue.add(WorldPoint(point.x, high(block.bottom + gap)))
        }
    }
    // Guaranteed clear fallback even for densely packed imported boards.
    return WorldPoint(start.x, (obstacles.maxOfOrNull { it.bottom } ?: start.y) + gap + 24)
}

fun snapPosition(
    desired: WorldPoint,
    moving: CanvasCard,
    others: List<CanvasCard>,
    grid: Boolean,
    objects: Boolean,
    threshold: Float = 8f,
): WorldPoint {
    var x = desired.x
    var y = desired.y
    if (objects) {
        val neighbours = others.filter { it.id != moving.id && it.kind != "group" }
        val dx =
            neighbours
                .flatMap {
                    listOf(
                        it.x - x,
                        it.x + it.width - moving.width - x,
                        it.x + (it.width - moving.width) / 2 - x,
                    )
                }
                .minByOrNull { abs(it) }
        val dy =
            neighbours
                .flatMap {
                    listOf(
                        it.y - y,
                        it.y + it.height - moving.height - y,
                        it.y + (it.height - moving.height) / 2 - y,
                    )
                }
                .minByOrNull { abs(it) }
        if (dx != null && abs(dx) <= threshold) x += dx
        if (dy != null && abs(dy) <= threshold) y += dy
    }
    if (moving.kind == "group") {
        val start =
            if (grid) WorldPoint(round(x / 24) * 24, round(y / 24) * 24) else WorldPoint(x, y)
        val members = others.filter { it.groupId == moving.id }
        if (members.isEmpty()) return start
        val left = members.minOf { it.x }
        val top = members.minOf { it.y }
        val right = members.maxOf { it.x + it.width }
        val bottom = members.maxOf { it.y + it.height }
        val placed =
            freePosition(
                WorldPoint(left + start.x - moving.x, top + start.y - moving.y),
                right - left,
                bottom - top,
                others.filter { it.kind != "group" && it.groupId != moving.id }.map { it.rect() },
                grid,
            )
        return WorldPoint(moving.x + placed.x - left, moving.y + placed.y - top)
    }
    return freePosition(
        WorldPoint(x, y),
        moving.width,
        moving.height,
        others.filter { it.id != moving.id && it.kind != "group" }.map { it.rect() },
        grid,
    )
}

/** Cubic curve endpoints on card boundaries; last control point defines arrow direction. */
data class ArrowCurve(
    val start: WorldPoint,
    val control1: WorldPoint,
    val control2: WorldPoint,
    val end: WorldPoint,
)

fun arrowCurve(parent: WorldRect, child: WorldRect): ArrowCurve {
    val dx = child.x + child.width / 2 - parent.x - parent.width / 2
    val dy = child.y + child.height / 2 - parent.y - parent.height / 2
    return if (abs(dx) > abs(dy)) {
        val direction = if (dx >= 0) 1f else -1f
        val a =
            WorldPoint(if (direction > 0) parent.right else parent.x, parent.y + parent.height / 2)
        val b = WorldPoint(if (direction > 0) child.x else child.right, child.y + child.height / 2)
        val bend = max(36f, abs(b.x - a.x) * .45f)
        ArrowCurve(
            a,
            WorldPoint(a.x + direction * bend, a.y),
            WorldPoint(b.x - direction * bend, b.y),
            b,
        )
    } else {
        val direction = if (dy >= 0) 1f else -1f
        val a =
            WorldPoint(parent.x + parent.width / 2, if (direction > 0) parent.bottom else parent.y)
        val b = WorldPoint(child.x + child.width / 2, if (direction > 0) child.y else child.bottom)
        val bend = max(36f, abs(b.y - a.y) * .45f)
        ArrowCurve(
            a,
            WorldPoint(a.x, a.y + direction * bend),
            WorldPoint(b.x, b.y - direction * bend),
            b,
        )
    }
}

fun WorldRect.port(side: String): WorldPoint =
    when (side) {
        "top" -> WorldPoint(x + width / 2, y)
        "bottom" -> WorldPoint(x + width / 2, bottom)
        "left" -> WorldPoint(x, y + height / 2)
        else -> WorldPoint(right, y + height / 2)
    }

fun connectionCurve(source: WorldRect, target: WorldRect, side: String): ArrowCurve {
    val a = source.port(side)
    val targetSide =
        listOf("top", "bottom", "left", "right").minBy { candidate ->
            val b = target.port(candidate)
            (a.x - b.x).pow(2) + (a.y - b.y).pow(2)
        }
    val b = target.port(targetSide)
    val bend = max(36f, min(180f, sqrt((a.x - b.x).pow(2) + (a.y - b.y).pow(2)) * .4f))
    fun control(p: WorldPoint, s: String) =
        when (s) {
            "top" -> WorldPoint(p.x, p.y - bend)
            "bottom" -> WorldPoint(p.x, p.y + bend)
            "left" -> WorldPoint(p.x - bend, p.y)
            else -> WorldPoint(p.x + bend, p.y)
        }
    return ArrowCurve(a, control(a, side), control(b, targetSide), b)
}
