package dev.vetki

import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.vetki.data.*
import dev.vetki.i18n.Language
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real touch events; no Compose idling resource or production-data reset. */
@RunWith(AndroidJUnit4::class)
class CanvasMotionTest {
    private val instrumentation
        get() = InstrumentationRegistry.getInstrumentation()

    private val context
        get() = instrumentation.targetContext

    private val automation
        get() = instrumentation.uiAutomation

    private val db
        get() = VetkiDatabase.get(context)

    private val board = Board(id = "qa-motion-${UUID.randomUUID()}", title = "Canvas motion check")
    private lateinit var previousLanguage: String
    private var downTime = 0L
    private val pressed = linkedMapOf<Int, Pair<Float, Float>>()

    @Before
    fun before() {
        org.junit.Assume.assumeTrue(
            InstrumentationRegistry.getArguments().getString("canvasHostLaunch") == "true"
        )
        check(context.packageName == "dev.vetki.qa")
        runBlocking {
            db.dao()
                .allBoards()
                .filter { it.id.startsWith("qa-motion-") }
                .forEach { db.dao().deleteBoard(it.id) }
        }
        previousLanguage = Language.selected(context)
        Language.set(context, "en")
    }

    @After
    fun after() {
        if (!::previousLanguage.isInitialized) return
        if (pressed.isNotEmpty()) {
            event(MotionEvent.ACTION_CANCEL)
            pressed.clear()
        }
        if (automation.rootInActiveWindow?.packageName?.toString() == context.packageName)
            shell("input keyevent KEYCODE_BACK")
        runBlocking { db.dao().deleteBoard(board.id) }
        Language.set(context, previousLanguage)
    }

    private fun open(count: Int = 1) {
        runBlocking {
            db.dao().save(board.copy(readOnly = count >= 300))
            repeat(count) { index ->
                db.dao()
                    .save(
                        Card(
                            id = "${board.id}-$index",
                            boardId = board.id,
                            title = "Motion card $index",
                            body =
                                "A useful question and next action for the canvas performance check",
                            x = if (count == 1) 0f else (index % 20) * 230f,
                            y = if (count == 1) 0f else (index / 20) * 150f,
                            parentId = if (index == 0) null else "${board.id}-${index-1}",
                        )
                    )
            }
        }
        instrumentation.sendStatus(
            2,
            android.os.Bundle().apply { putString("stream", "CANVAS_READY ${board.id}\n") },
        )
        waitFor {
            find(description = "Canvas") != null &&
                (if (count < 300) find(description = "Card: Motion card 0") != null
                else find(text = "15%") != null)
        }
        Thread.sleep(300)
    }

    private fun shell(command: String): String =
        automation.executeShellCommand(command).use { descriptor ->
            java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use {
                it.readText()
            }
        }

    private fun find(text: String? = null, description: String? = null): AccessibilityNodeInfo? {
        if (android.os.Build.VERSION.SDK_INT >= 33) automation.clearCache()
        val root = automation.rootInActiveWindow ?: return null
        fun walk(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (
                (text != null && node.text?.toString() == text) ||
                    (description != null && node.contentDescription?.toString() == description)
            )
                return node
            repeat(node.childCount) { index ->
                node.getChild(index)?.let { child ->
                    walk(child)?.let {
                        return it
                    }
                }
            }
            return null
        }
        return walk(root)
    }

    private fun waitFor(block: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 20000
        while (SystemClock.uptimeMillis() < deadline) {
            if (block()) return
            Thread.sleep(60)
        }
        fail("Canvas UI did not become ready")
    }

    private fun bounds(description: String): Rect {
        val node = requireNotNull(find(description = description)) { description }
        return Rect().also { node.getBoundsInScreen(it) }
    }

    private fun event(action: Int) {
        val properties =
            pressed.keys
                .map { id ->
                    MotionEvent.PointerProperties().apply {
                        this.id = id
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }
                }
                .toTypedArray()
        val coordinates =
            pressed.values
                .map { (x, y) ->
                    MotionEvent.PointerCoords().apply {
                        this.x = x
                        this.y = y
                        pressure = 1f
                        size = 1f
                    }
                }
                .toTypedArray()
        val event =
            MotionEvent.obtain(
                downTime,
                SystemClock.uptimeMillis(),
                action,
                pressed.size,
                properties,
                coordinates,
                0,
                0,
                1f,
                1f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0,
            )
        try {
            check(automation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (pressed.isEmpty()) downTime = SystemClock.uptimeMillis()
        pressed[id] = x to y
        event(
            if (pressed.size == 1) MotionEvent.ACTION_DOWN
            else
                MotionEvent.ACTION_POINTER_DOWN or
                    ((pressed.size - 1) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        )
    }

    private fun up(id: Int) {
        val index = pressed.keys.indexOf(id)
        event(
            if (pressed.size == 1) MotionEvent.ACTION_UP
            else MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
        )
        pressed.remove(id)
    }

    private fun click(text: String) {
        val rect = Rect().also { requireNotNull(find(text = text)).getBoundsInScreen(it) }
        down(0, rect.exactCenterX(), rect.exactCenterY())
        up(0)
        Thread.sleep(100)
    }

    private fun pinchAndLift(firstLift: Int) {
        open()
        val card = "Card: Motion card 0"
        val before = bounds(card)
        val x = before.exactCenterX()
        val y = before.exactCenterY()
        down(0, x - 90, y)
        down(1, x + 90, y)
        repeat(12) { step ->
            val radius = 90 + (step + 1) * 3f
            pressed[0] = x - radius to y
            pressed[1] = x + radius to y
            event(MotionEvent.ACTION_MOVE)
            Thread.sleep(16)
        }
        Thread.sleep(200)
        val pinched = bounds(card)
        assertTrue("before=$before pinched=$pinched", pinched.width() > before.width() + 80)
        assertEquals(before.exactCenterX(), pinched.exactCenterX(), 2f)
        assertEquals(before.exactCenterY(), pinched.exactCenterY(), 2f)
        up(firstLift)
        val remaining = 1 - firstLift
        val position = pressed.getValue(remaining)
        repeat(3) { step ->
            pressed[remaining] = position.first + (step + 1) * 5 to position.second + (step + 1) * 3
            event(MotionEvent.ACTION_MOVE)
            Thread.sleep(20)
        }
        up(remaining)
        Thread.sleep(250)
        val after = bounds(card)
        assertEquals("Lifting either finger must not shift the camera", pinched, after)
        runBlocking {
            val stored = db.dao().card("${board.id}-0")!!
            assertEquals(0f, stored.x, 0f)
            assertEquals(0f, stored.y, 0f)
        }
        // A fresh one-finger gesture still pans normally.
        down(0, x, y)
        repeat(10) { step ->
            pressed[0] = x + (step + 1) * 6 to y + (step + 1) * 3
            event(MotionEvent.ACTION_MOVE)
            Thread.sleep(16)
        }
        Thread.sleep(20)
        up(0)
        Thread.sleep(200)
        val panned = bounds(card)
        assertTrue("A fresh gesture must pan horizontally", panned.left > after.left + 25)
        assertTrue("A fresh gesture must pan vertically", panned.top > after.top + 10)
    }

    @Test
    fun liftingOriginalFingerDoesNotJump() {
        pinchAndLift(0)
    }

    @Test
    fun liftingSecondFingerDoesNotDrift() {
        pinchAndLift(1)
    }

    @Test
    fun buttonZoomKeepsTheSameCenter() {
        open()
        val before = bounds("Card: Motion card 0")
        repeat(4) {
            click("+")
            click("−")
        }
        val after = bounds("Card: Motion card 0")
        assertEquals(before.exactCenterX(), after.exactCenterX(), 1f)
        assertEquals(before.exactCenterY(), after.exactCenterY(), 1f)
        assertEquals(before.width().toFloat(), after.width().toFloat(), 2f)
    }

    @Test
    fun nativeConnectionPointsStillCreateArrows() {
        open(2)
        val source = bounds("Card: Motion card 0")
        val target = bounds("Card: Motion card 1")
        down(0, source.right.toFloat(), source.exactCenterY())
        Thread.sleep(40)
        up(0)
        waitFor { find(text = "Choose another card") != null }
        down(0, target.exactCenterX(), target.exactCenterY())
        Thread.sleep(40)
        up(0)
        waitFor {
            runBlocking {
                CardJson.connections(db.dao().card("${board.id}-0")!!.connections).any {
                    it.targetId == "${board.id}-1" && it.side == "right"
                }
            }
        }
    }

    @Test
    fun benchmarkLargeCanvas() {
        open(300)
        fun measure(label: String) {
            shell("dumpsys gfxinfo ${context.packageName} reset")
            repeat(4) { pass ->
                val start = if (pass % 2 == 0) 180f else 500f
                down(0, start, 700f)
                repeat(30) { step ->
                    pressed[0] =
                        start + (if (pass % 2 == 0) 1 else -1) * (step + 1) * 10f to
                            700f + (step + 1) * 3f
                    event(MotionEvent.ACTION_MOVE)
                    Thread.sleep(16)
                }
                up(0)
            }
            Thread.sleep(200)
            val metrics = shell("dumpsys gfxinfo ${context.packageName} framestats")
            java.io.File(context.filesDir, "qa-canvas-$label-gfxinfo.txt").writeText(metrics)
            val count =
                Regex("Total frames rendered: (\\d+)").find(metrics)?.groupValues?.get(1)?.toInt()
                    ?: 0
            assertTrue("Not enough rendered frames: $count", count >= 8)
        }
        measure("zoom15")
        repeat(5) { click("+") }
        measure("zoom46")
    }
}
