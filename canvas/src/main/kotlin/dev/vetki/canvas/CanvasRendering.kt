package dev.vetki.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import dev.vetki.core.CanvasCard
import dev.vetki.core.canvasGridStep
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

/** One native draw call and a reusable buffer, rather than thousands of Compose commands. */
internal class CanvasGrid {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(57, 57, 57)
            strokeCap = Paint.Cap.ROUND
        }
    private var points = FloatArray(0)
    private var lastSpacing = Float.NaN
    private var columns = 0
    private var rows = 0

    fun draw(
        canvas: Canvas,
        width: Float,
        height: Float,
        x: Float,
        y: Float,
        density: Float,
        zoom: Float,
    ) {
        val spacing = canvasGridStep(zoom) * density * zoom
        val cols = ceil(width / spacing).toInt() + 1
        val lines = ceil(height / spacing).toInt() + 1
        val count = cols * lines * 2
        if (points.size < count) points = FloatArray(count)
        if (lastSpacing != spacing || columns != cols || rows != lines) {
            var index = 0
            repeat(cols) { col ->
                repeat(lines) { row ->
                    points[index++] = col * spacing
                    points[index++] = row * spacing
                }
            }
            lastSpacing = spacing
            columns = cols
            rows = lines
        }
        paint.strokeWidth = 1.7f
        val save = canvas.save()
        canvas.clipRect(0f, 0f, width, height)
        canvas.translate(((x % spacing) + spacing) % spacing, ((y % spacing) + spacing) % spacing)
        canvas.drawPoints(points, 0, count, paint)
        canvas.restoreToCount(save)
    }
}

/** Text wrapping is done in world units and reused across zoom/pan frames. */
internal class CanvasText {
    private val titlePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(232, 234, 232)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    private val bodyPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(163, 171, 167)
            textSize = 10.5f
            typeface = Typeface.DEFAULT
        }
    private val duePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(219, 185, 122)
            textSize = 10f
        }
    private val groupPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(183, 178, 159)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val imageBounds = RectF()
    private val dateFormat = DateTimeFormatter.ofPattern("dd.MM · HH:mm")

    private data class Entry(
        val title: String,
        val body: String,
        val kind: String,
        val done: Boolean,
        val width: Float,
        val due: Long?,
        val zone: ZoneId,
        val headings: List<String>,
        val paragraphs: List<String>,
        val date: String?,
    )

    private val entries = HashMap<String, Entry>()

    fun retain(ids: Set<String>) {
        entries.keys.retainAll(ids)
    }

    private fun wrap(input: String, paint: Paint, width: Float): List<String> {
        val result = ArrayList<String>(2)
        var remaining = input.replace('\n', ' ')
        repeat(2) { line ->
            if (remaining.isNotEmpty()) {
                var count =
                    paint.breakText(remaining, true, width.coerceAtLeast(1f), null).coerceAtLeast(1)
                if (count < remaining.length) {
                    val space = remaining.lastIndexOf(' ', count)
                    if (space > count / 2) count = space
                }
                var part = remaining.take(count)
                remaining = remaining.drop(count).trimStart()
                if (line == 1 && remaining.isNotEmpty()) part = part.dropLast(1) + "…"
                result += part
            }
        }
        return result
    }

    private fun entry(card: CanvasCard): Entry {
        val zone = ZoneId.systemDefault()
        val cached = entries[card.id]
        if (
            cached != null &&
                cached.title == card.title &&
                cached.body == card.body &&
                cached.kind == card.kind &&
                cached.done == card.done &&
                cached.width == card.width &&
                cached.due == card.due &&
                cached.zone == zone
        )
            return cached
        val prefix =
            if (card.done) "✓ "
            else
                when (card.kind) {
                    "note" -> "▤ "
                    "link" -> "↗ "
                    "file" -> "▧ "
                    else -> ""
                }
        return Entry(
                card.title,
                card.body,
                card.kind,
                card.done,
                card.width,
                card.due,
                zone,
                wrap(prefix + card.title, titlePaint, card.width - 26),
                wrap(card.body, bodyPaint, card.width - 26),
                card.due?.let { "◷ " + Instant.ofEpochMilli(it).atZone(zone).format(dateFormat) },
            )
            .also { entries[card.id] = it }
    }

    fun group(canvas: Canvas, card: CanvasCard, x: Float, y: Float, unit: Float) {
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.scale(unit, unit)
        canvas.drawText(card.title, 14f, 24f, groupPaint)
        canvas.restoreToCount(save)
    }

    fun card(
        canvas: Canvas,
        card: CanvasCard,
        x: Float,
        y: Float,
        unit: Float,
        detail: Boolean,
        image: Bitmap?,
    ) {
        val text = entry(card)
        val save = canvas.save()
        canvas.translate(x, y)
        canvas.scale(unit, unit)
        text.headings.forEachIndexed { index, line ->
            canvas.drawText(line, 13f, 24f + index * 18, titlePaint)
        }
        if (detail) {
            if (card.kind == "image" && image != null) {
                imageBounds.set(10f, 52f, card.width - 10f, card.height - 12f)
                canvas.drawBitmap(image, null, imageBounds, imagePaint)
            } else
                text.paragraphs.forEachIndexed { index, line ->
                    canvas.drawText(line, 13f, 65f + index * 15, bodyPaint)
                }
            text.date?.let { canvas.drawText(it, 13f, card.height - 10f, duePaint) }
        }
        canvas.restoreToCount(save)
    }
}
