package dev.vetki.canvas

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import dev.vetki.core.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

private val colors =
    listOf(Color(0xFF4BC6B6), Color(0xFFC5A76B), Color(0xFF87A4C4), Color(0xFFAA94C2))

@Composable
fun BoardCanvas(
    boardId: String,
    cards: List<CanvasCard>,
    onOpen: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    snapGrid: Boolean = false,
    snapObjects: Boolean = false,
    readOnly: Boolean = false,
    onBlankTap: (WorldPoint, Offset) -> Unit = { _, _ -> },
    onHelp: () -> Unit = {},
    loadImage: suspend (String) -> Bitmap? = { null },
    onConnect: (String, String, String) -> Unit = { _, _, _ -> },
) {
    var connecting by remember(boardId) { mutableStateOf<Pair<String, String>?>(null) }
    val connect by rememberUpdatedState(onConnect)
    val density = LocalDensity.current.density
    val haptics = LocalHapticFeedback.current
    var zoom by remember(boardId) { mutableFloatStateOf(.8f) }
    var pan by remember(boardId) { mutableStateOf(Offset.Unspecified) }
    var dimensions by remember { mutableStateOf(Size.Zero) }
    var fitted by remember(boardId) { mutableStateOf(false) }
    var moving by remember(boardId) { mutableStateOf<CanvasCard?>(null) }
    var preview by remember(boardId) { mutableStateOf<Offset?>(null) }
    val pending = remember(boardId) { mutableStateMapOf<String, Offset>() }
    val currentCards by rememberUpdatedState(cards)
    val open by rememberUpdatedState(onOpen)
    val move by rememberUpdatedState(onMove)
    val blank by rememberUpdatedState(onBlankTap)
    val currentReadOnly by rememberUpdatedState(readOnly)
    val grid by rememberUpdatedState(snapGrid)
    val objects by rememberUpdatedState(snapObjects)
    val imageLoader by rememberUpdatedState(loadImage)
    fun camera() =
        if (pan.x.isFinite()) pan
        else
            Offset(
                dimensions.width / 2 - 95 * density * zoom,
                dimensions.height / 2 - 56 * density * zoom,
            )
    fun position(c: CanvasCard): Offset {
        val dragging = moving
        if (dragging != null && preview != null) {
            if (c.id == dragging.id) return preview!!
            if (dragging.kind == "group" && c.groupId == dragging.id)
                return Offset(c.x, c.y) + (preview!! - Offset(dragging.x, dragging.y))
        }
        return pending[c.id] ?: Offset(c.x, c.y)
    }
    fun world(screen: Offset) = (screen - camera()) / (density * zoom)
    fun hit(screen: Offset): CanvasCard? {
        val p = world(screen)
        return currentCards
            .asReversed()
            .sortedBy { it.kind == "group" }
            .firstOrNull { c ->
                val xy = position(c)
                p.x in xy.x..xy.x + c.width &&
                    p.y in xy.y..xy.y + (if (c.kind == "group") 34f else c.height)
            }
    }
    fun selectCard(id: String) {
        val source = connecting
        if (source != null) {
            if (source.first != id && currentCards.firstOrNull { it.id == id }?.kind != "group") {
                connecting = null
                connect(source.first, source.second, id)
            }
        } else open(id)
    }
    LaunchedEffect(readOnly, cards.map { it.id }) {
        if (readOnly || connecting?.first?.let { id -> cards.none { it.id == id } } == true)
            connecting = null
    }
    fun finishDrag() {
        val c = moving ?: return
        val p = preview ?: Offset(c.x, c.y)
        val placed = snapPosition(WorldPoint(p.x, p.y), c, currentCards, grid, objects)
        pending[c.id] = Offset(placed.x, placed.y)
        if (c.kind == "group")
            currentCards
                .filter { it.groupId == c.id }
                .forEach { child ->
                    pending[child.id] = Offset(child.x + placed.x - c.x, child.y + placed.y - c.y)
                }
        moving = null
        preview = null
        move(c.id, placed.x, placed.y)
    }
    fun fit() {
        if (currentCards.isEmpty() || dimensions.width <= 0) return
        val left = currentCards.minOf { it.x }
        val right = currentCards.maxOf { it.x + it.width }
        val top = currentCards.minOf { it.y }
        val bottom = currentCards.maxOf { it.y + it.height }
        zoom =
            min(
                    (dimensions.width - 40 * density) / ((right - left) * density),
                    (dimensions.height - 140 * density) / ((bottom - top) * density),
                )
                .coerceIn(.15f, 1f)
        pan =
            Offset(
                (dimensions.width - (right - left) * density * zoom) / 2 - left * density * zoom,
                (dimensions.height - (bottom - top) * density * zoom) / 2 - top * density * zoom,
            )
    }
    fun changeZoom(value: Float) {
        val next = value.coerceIn(.15f, 2.5f)
        val center = Offset(dimensions.width / 2, dimensions.height / 2)
        pan = center - (center - camera()) * (next / zoom)
        zoom = next
    }
    LaunchedEffect(boardId, dimensions, cards.isNotEmpty()) {
        if (!fitted && dimensions.width > 0 && cards.isNotEmpty()) {
            fit()
            fitted = true
        }
    }
    LaunchedEffect(cards) {
        pending.keys.toList().forEach { id ->
            val c = cards.firstOrNull { it.id == id }
            val p = pending[id]!!
            if (c == null || (abs(c.x - p.x) < .5f && abs(c.y - p.y) < .5f)) pending.remove(id)
        }
    }
    val visible =
        cards.filter { c ->
            val top = camera() + position(c) * density * zoom
            top.x < dimensions.width &&
                top.y < dimensions.height &&
                top.x + c.width * density * zoom > 0 &&
                top.y + c.height * density * zoom > 0
        }
    val imageUris = visible.filter { it.kind == "image" }.mapNotNull { it.uri }.distinct().take(8)
    var images by remember(boardId) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(imageUris) {
        images = buildMap { imageUris.forEach { uri -> imageLoader(uri)?.let { put(uri, it) } } }
    }
    Box(modifier.background(Color(0xFF1B1B1B))) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { dimensions = Size(it.width.toFloat(), it.height.toFloat()) }
                .semantics { contentDescription = "Холст" }
                .canvasGestures(
                    boardId,
                    ::hit,
                    { currentReadOnly },
                    { point, card ->
                        if (card != null) selectCard(card.id)
                        else if (connecting != null) connecting = null
                        else {
                            val p = world(point)
                            blank(WorldPoint(p.x, p.y), point)
                        }
                    },
                    { c ->
                        moving = c.copy(x = position(c).x, y = position(c).y)
                        preview = position(c)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    { delta -> preview = (preview ?: Offset.Zero) + delta / (density * zoom) },
                    ::finishDrag,
                    { centroid, delta, scale ->
                        val next = (zoom * scale).coerceIn(.15f, 2.5f)
                        pan = centroid - (centroid - camera()) * (next / zoom) + delta
                        zoom = next
                    },
                )
        ) {
            val origin = camera()
            val unit = density * zoom
            val spacing = 12f * unit
            if (spacing >= 5) {
                var dx = ((origin.x % spacing) + spacing) % spacing
                while (dx < size.width) {
                    var dy = ((origin.y % spacing) + spacing) % spacing
                    while (dy < size.height) {
                        drawCircle(Color(0xFF393939), .85f, Offset(dx, dy))
                        dy += spacing
                    }
                    dx += spacing
                }
            }
            cards
                .filter { it.kind == "group" }
                .forEach { c ->
                    val xy = origin + position(c) * unit
                    drawRoundRect(
                        Color(0xFF30302E).copy(alpha = .65f),
                        xy,
                        Size(c.width * unit, c.height * unit),
                        CornerRadius(12 * unit),
                    )
                    drawRoundRect(
                        Color(0xFF777365),
                        xy,
                        Size(c.width * unit, c.height * unit),
                        CornerRadius(12 * unit),
                        style =
                            Stroke(
                                density,
                                pathEffect =
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(8 * density, 5 * density)
                                    ),
                            ),
                    )
                    drawIntoCanvas { canvas ->
                        val paint =
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.rgb(183, 178, 159)
                                textSize = 13 * unit
                                typeface = Typeface.DEFAULT_BOLD
                            }
                        canvas.nativeCanvas.drawText(
                            c.title,
                            xy.x + 14 * unit,
                            xy.y + 24 * unit,
                            paint,
                        )
                    }
                }
            val byId = cards.associateBy { it.id }
            fun drawArrow(curve: ArrowCurve) {
                fun screen(p: WorldPoint) = origin + Offset(p.x, p.y) * unit
                val a = screen(curve.start)
                val b = screen(curve.end)
                val c1 = screen(curve.control1)
                val c2 = screen(curve.control2)
                if (
                    max(a.x, b.x) < -60 ||
                        min(a.x, b.x) > size.width + 60 ||
                        max(a.y, b.y) < -60 ||
                        min(a.y, b.y) > size.height + 60
                )
                    return
                val path =
                    Path().apply {
                        moveTo(a.x, a.y)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
                    }
                val color = Color(0xFF8A8A87)
                drawPath(path, color, style = Stroke(1.1f * density))
                val v = b - c2
                val length = v.getDistance().coerceAtLeast(1f)
                val direction = v / length
                val back = b - direction * (7f * density * zoom.coerceIn(.6f, 1f))
                val side =
                    Offset(-direction.y, direction.x) * 3.5f * density * zoom.coerceIn(.6f, 1f)
                drawPath(
                    Path().apply {
                        moveTo(b.x, b.y)
                        lineTo((back + side).x, (back + side).y)
                        lineTo((back - side).x, (back - side).y)
                        close()
                    },
                    color,
                )
            }
            cards
                .filter { it.kind != "group" }
                .forEach { source ->
                    val p = position(source)
                    val sourceRect = WorldRect(p.x, p.y, source.width, source.height)
                    source.connections.forEach { link ->
                        val target = byId[link.targetId] ?: return@forEach
                        val q = position(target)
                        drawArrow(
                            connectionCurve(
                                sourceRect,
                                WorldRect(q.x, q.y, target.width, target.height),
                                link.side,
                            )
                        )
                    }
                    val parent = byId[source.parentId]
                    if (parent != null && parent.connections.none { it.targetId == source.id }) {
                        val q = position(parent)
                        drawArrow(
                            arrowCurve(WorldRect(q.x, q.y, parent.width, parent.height), sourceRect)
                        )
                    }
                }
            visible
                .filter { it.kind != "group" }
                .forEach { card ->
                    val top = origin + position(card) * unit
                    val w = card.width * unit
                    val h = card.height * unit
                    val color = colors[card.status.mod(4)]
                    drawRoundRect(Color(0xFF252727), top, Size(w, h), CornerRadius(9 * unit))
                    drawRoundRect(
                        if (card.done) Color(0xFF56826B)
                        else if (card.id == moving?.id) color else Color(0xFF5C615F),
                        top,
                        Size(w, h),
                        CornerRadius(9 * unit),
                        style = Stroke(if (card.id == moving?.id) 2.5f * density else density),
                    )
                    drawRoundRect(
                        color,
                        top + Offset(0f, 12 * unit),
                        Size(2.5f * unit, 24 * unit),
                        CornerRadius(2 * unit),
                    )
                    if (zoom < .25f) return@forEach
                    drawIntoCanvas { canvas ->
                        val paint =
                            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                this.color = android.graphics.Color.rgb(232, 234, 232)
                                textSize = 14 * unit
                                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                            }
                        fun lines(input: String, count: Int): List<String> {
                            val result = mutableListOf<String>()
                            var remaining = input.replace('\n', ' ')
                            repeat(count) {
                                if (remaining.isNotEmpty()) {
                                    var n =
                                        paint
                                            .breakText(remaining, true, w - 26 * unit, null)
                                            .coerceAtLeast(1)
                                    if (n < remaining.length) {
                                        val space = remaining.lastIndexOf(' ', n)
                                        if (space > n / 2) n = space
                                    }
                                    var part = remaining.take(n)
                                    remaining = remaining.drop(n).trimStart()
                                    if (it == count - 1 && remaining.isNotEmpty())
                                        part = part.dropLast(1) + "…"
                                    result += part
                                }
                            }
                            return result
                        }
                        val icon =
                            when (card.kind) {
                                "note" -> "▤ "
                                "link" -> "↗ "
                                "file" -> "▧ "
                                else -> ""
                            }
                        lines((if (card.done) "✓ " else icon) + card.title, 2).forEachIndexed {
                            i,
                            line ->
                            canvas.nativeCanvas.drawText(
                                line,
                                top.x + 13 * unit,
                                top.y + (24 + i * 18) * unit,
                                paint,
                            )
                        }
                        val image = images[card.uri]
                        if (card.kind == "image" && image != null) {
                            val destination =
                                android.graphics.RectF(
                                    top.x + 10 * unit,
                                    top.y + 52 * unit,
                                    top.x + w - 10 * unit,
                                    top.y + h - 12 * unit,
                                )
                            canvas.nativeCanvas.drawBitmap(
                                image,
                                null,
                                destination,
                                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
                            )
                        } else {
                            paint.textSize = 10.5f * unit
                            paint.typeface = Typeface.DEFAULT
                            paint.color = android.graphics.Color.rgb(163, 171, 167)
                            lines(card.body, 2).forEachIndexed { i, line ->
                                canvas.nativeCanvas.drawText(
                                    line,
                                    top.x + 13 * unit,
                                    top.y + (65 + i * 15) * unit,
                                    paint,
                                )
                            }
                        }
                        card.due?.let { due ->
                            paint.color = android.graphics.Color.rgb(219, 185, 122)
                            paint.textSize = 10 * unit
                            val date =
                                Instant.ofEpochMilli(due)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("dd.MM · HH:mm"))
                            canvas.nativeCanvas.drawText(
                                "◷ $date",
                                top.x + 13 * unit,
                                top.y + (card.height - 10) * unit,
                                paint,
                            )
                        }
                    }
                }
        }
        visible.forEach { card ->
            val top = camera() + position(card) * density * zoom
            Box(
                Modifier.offset { IntOffset(top.x.roundToInt(), top.y.roundToInt()) }
                    .size((card.width * zoom).dp, (card.height * zoom).dp)
                    .semantics {
                        contentDescription = "Карточка: ${card.title}"
                        role = Role.Button
                        onClick(
                            label =
                                if (connecting != null) "Соединить с карточкой"
                                else "Открыть карточку"
                        ) {
                            selectCard(card.id)
                            true
                        }
                    }
            )
        }
        if (!readOnly)
            visible
                .filter { it.kind != "group" }
                .forEach { card ->
                    listOf("top", "bottom", "left", "right").forEach { side ->
                        val p = position(card)
                        val port = WorldRect(p.x, p.y, card.width, card.height).port(side)
                        val center = camera() + Offset(port.x, port.y) * density * zoom
                        val selected = connecting == (card.id to side)
                        val sideLabel =
                            when (side) {
                                "top" -> "сверху"
                                "bottom" -> "снизу"
                                "left" -> "слева"
                                else -> "справа"
                            }
                        Box(
                            Modifier.offset {
                                    IntOffset(
                                        (center.x - 16 * density).roundToInt(),
                                        (center.y - 16 * density).roundToInt(),
                                    )
                                }
                                .size(32.dp)
                                .semantics {
                                    contentDescription = "Соединить $sideLabel: ${card.title}"
                                }
                                .clickable(role = Role.Button) {
                                    connecting = if (selected) null else card.id to side
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(if (selected) 12.dp else 8.dp)
                                    .background(
                                        if (selected) Color(0xFF4BC6B6) else Color(0xFF303737),
                                        CircleShape,
                                    )
                                    .border(1.5.dp, Color(0xFF76B7AC), CircleShape)
                            )
                        }
                    }
                }
        if (connecting != null)
            Surface(
                Modifier.align(Alignment.TopCenter)
                    .padding(top = 60.dp, start = 12.dp, end = 12.dp),
                color = Color(0xF0303C39),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp),
                ) {
                    Text("Выбери другую карточку", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { connecting = null }) { Text("Отмена") }
                }
            }
        if (cards.isEmpty())
            Text(
                "Нажми на свободное место,\nчтобы добавить первую карточку",
                Modifier.align(Alignment.Center).padding(50.dp),
                color = Color(0xFF9A9E9B),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        Surface(
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
            color = Color(0xEE282C2B),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { changeZoom(zoom * 1.25f) },
                    modifier = Modifier.width(48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("+")
                }
                Text("${(zoom*100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                TextButton(
                    onClick = { changeZoom(zoom * .8f) },
                    modifier = Modifier.width(48.dp),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("−")
                }
                TextButton(onClick = { fit() }) { Text("Обзор") }
            }
        }
        FilledTonalIconButton(
            onClick = onHelp,
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp).size(40.dp),
        ) {
            Text("?", style = MaterialTheme.typography.titleMedium)
        }
        if (readOnly)
            Surface(
                Modifier.align(Alignment.TopStart).padding(12.dp),
                color = Color(0xEE282C2B),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    "Только просмотр",
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFC5B48B),
                )
            }
    }
}
