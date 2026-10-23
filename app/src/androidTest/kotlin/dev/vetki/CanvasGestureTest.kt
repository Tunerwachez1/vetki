package dev.vetki

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.vetki.canvas.BoardCanvas
import dev.vetki.core.*
import dev.vetki.ui.VetkiTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasGestureTest {
    @get:Rule val compose = createComposeRule()

    private fun card(id: String, x: Float = 0f, y: Float = 0f) =
        CanvasCard(id, null, id, "Текст", x, y, 0, false, null)

    @Test
    fun longDragDoesNotCancelBeyondTouchSlopOrJumpDuringSave() {
        val commit = AtomicReference<WorldPoint?>(null)
        compose.setContent {
            var cards by remember { mutableStateOf(listOf(card("A"), card("B", 350f, 0f))) }
            VetkiTheme {
                BoardCanvas(
                    "drag",
                    cards,
                    {},
                    { id, x, y ->
                        commit.set(WorldPoint(x, y))
                        Handler(Looper.getMainLooper())
                            .postDelayed(
                                {
                                    cards =
                                        cards.map { if (it.id == id) it.copy(x = x, y = y) else it }
                                },
                                800,
                            )
                    },
                    Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        val canvas = compose.onNodeWithContentDescription("Холст").fetchSemanticsNode().boundsInRoot
        val before =
            compose.onNodeWithContentDescription("Карточка: A").fetchSemanticsNode().boundsInRoot
        val start = before.center - canvas.topLeft
        compose.onNodeWithContentDescription("Холст").performTouchInput {
            down(start)
            advanceEventTime(650)
            moveBy(Offset(8f, 0f))
            advanceEventTime(20)
            repeat(12) {
                moveBy(Offset(12f, 7f))
                advanceEventTime(16)
            }
            up()
        }
        compose.waitUntil(3000) { commit.get() != null }
        assertTrue("Long drag must move more than a few millimetres", commit.get()!!.x > 20f)
        val during =
            compose.onNodeWithContentDescription("Карточка: A").fetchSemanticsNode().boundsInRoot
        assertTrue(during.left - before.left > 60)
        Thread.sleep(1100)
        compose.waitForIdle()
        val after =
            compose.onNodeWithContentDescription("Карточка: A").fetchSemanticsNode().boundsInRoot
        assertEquals(during.left, after.left, 2f)
        assertEquals(during.top, after.top, 2f)
    }

    @Test
    fun backgroundTapOpensMenuButPanningDoesNot() {
        val count = AtomicInteger()
        compose.setContent {
            VetkiTheme {
                BoardCanvas(
                    "empty",
                    emptyList(),
                    {},
                    { _, _, _ -> },
                    Modifier.fillMaxSize(),
                    onBlankTap = { _, _ -> count.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithContentDescription("Холст").performTouchInput { click(Offset(90f, 160f)) }
        assertEquals(1, count.get())
        compose.onNodeWithContentDescription("Холст").performTouchInput {
            down(Offset(90f, 160f))
            moveBy(Offset(100f, 100f))
            up()
        }
        assertEquals(1, count.get())
    }

    @Test
    fun readOnlyBoardCannotMoveCards() {
        val count = AtomicInteger()
        compose.setContent {
            VetkiTheme {
                BoardCanvas(
                    "locked",
                    listOf(card("A")),
                    {},
                    { _, _, _ -> count.incrementAndGet() },
                    Modifier.fillMaxSize(),
                    readOnly = true,
                )
            }
        }
        compose.waitForIdle()
        val canvas = compose.onNodeWithContentDescription("Холст").fetchSemanticsNode().boundsInRoot
        val point =
            compose
                .onNodeWithContentDescription("Карточка: A")
                .fetchSemanticsNode()
                .boundsInRoot
                .center - canvas.topLeft
        compose.onNodeWithContentDescription("Холст").performTouchInput {
            down(point)
            advanceEventTime(650)
            moveBy(Offset(150f, 100f))
            up()
        }
        assertEquals(0, count.get())
    }

    @Test
    fun twoFingersZoomWithoutMovingACard() {
        val count = AtomicInteger()
        compose.setContent {
            VetkiTheme {
                BoardCanvas(
                    "zoom",
                    listOf(card("A")),
                    {},
                    { _, _, _ -> count.incrementAndGet() },
                    Modifier.fillMaxSize(),
                )
            }
        }
        compose.waitForIdle()
        val node =
            compose.onNodeWithContentDescription("Карточка: A").fetchSemanticsNode().boundsInRoot
        val canvas = compose.onNodeWithContentDescription("Холст").fetchSemanticsNode().boundsInRoot
        val center = node.center - canvas.topLeft
        compose.onNodeWithContentDescription("Холст").performTouchInput {
            down(0, center - Offset(70f, 0f))
            down(1, center + Offset(70f, 0f))
            moveTo(0, center - Offset(110f, 0f))
            moveTo(1, center + Offset(110f, 0f))
            up(0)
            up(1)
        }
        compose.waitForIdle()
        assertEquals(0, count.get())
        assertTrue(
            compose
                .onNodeWithContentDescription("Карточка: A")
                .fetchSemanticsNode()
                .boundsInRoot
                .width > node.width
        )
    }
}
