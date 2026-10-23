package dev.vetki.canvas

import android.os.SystemClock
import androidx.compose.foundation.gestures.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import dev.vetki.core.CanvasCard

/** A gesture has one owner. Pan/zoom can never cancel a card drag. */
internal fun Modifier.canvasGestures(
    boardId: String,
    hit: (Offset) -> CanvasCard?,
    readOnly: () -> Boolean,
    onTap: (Offset, CanvasCard?) -> Unit,
    onDragStart: (CanvasCard) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
) =
    pointerInput(boardId) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val target = hit(down.position)
            var candidate = target != null && !readOnly()
            var dragging = false
            var panning = false
            var transformed = false
            var last = down.position
            val deadline = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
            try {
                while (true) {
                    val event =
                        if (candidate && !dragging && !panning) {
                            withTimeoutOrNull(
                                (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1)
                            ) {
                                awaitPointerEvent()
                            }
                        } else awaitPointerEvent()
                    if (event == null) {
                        candidate = false
                        dragging = true
                        target?.let(onDragStart)
                        continue
                    }
                    val pressed = event.changes.filter { it.pressed }
                    val pointer =
                        event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                    if (pressed.size >= 2) {
                        if (dragging) {
                            onDragEnd()
                            dragging = false
                        }
                        candidate = false
                        transformed = true
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (!centroid.x.isNaN())
                            onTransform(centroid, event.calculatePan(), event.calculateZoom())
                        event.changes.forEach { it.consume() }
                        last = pointer.position
                        continue
                    }
                    val distance = (pointer.position - down.position).getDistance()
                    if (candidate && pointer.uptimeMillis >= deadline) {
                        candidate = false
                        dragging = true
                        target?.let(onDragStart)
                    }
                    if (dragging) {
                        onDrag(pointer.position - last)
                        pointer.consume()
                    } else if (
                        !transformed &&
                            pressed.isNotEmpty() &&
                            (panning || distance > viewConfiguration.touchSlop)
                    ) {
                        val delta =
                            if (panning) pointer.position - last
                            else pointer.position - down.position
                        panning = true
                        candidate = false
                        onTransform(pointer.position, delta, 1f)
                        pointer.consume()
                    } else if (transformed && pressed.isNotEmpty()) {
                        onTransform(pointer.position, pointer.position - last, 1f)
                        pointer.consume()
                    }
                    last = pointer.position
                    if (pressed.isEmpty()) {
                        if (dragging) {
                            onDragEnd()
                            dragging = false
                        } else if (
                            !panning && !transformed && distance <= viewConfiguration.touchSlop
                        )
                            onTap(pointer.position, target)
                        break
                    }
                }
            } finally {
                // A system cancellation must retain the position already reached by the finger.
                if (dragging) onDragEnd()
            }
        }
    }
