package dev.vetki.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import dev.vetki.core.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import kotlin.math.*

private val portSides = listOf("top", "bottom", "left", "right")

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
    val locale = LocalConfiguration.current.locales.toLanguageTags()
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
    val byId = remember(cards) { cards.associateBy { it.id } }
    val ordinaryCards = remember(cards) { cards.filter { it.kind != "group" } }
    val textRenderer = remember(boardId) { CanvasText() }
    val gridRenderer = remember { CanvasGrid() }
    val arrowPath = remember { Path() }
    val arrowHead = remember { Path() }
    val arrowStroke = remember(density) { Stroke(1.1f * density) }
    val groupStroke =
        remember(density) {
            Stroke(
                density,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8 * density, 5 * density)),
            )
        }

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
    fun sideLabel(side: String) =
        when (side) {
            "top" -> tr(StringsRes.string.at_the_top)
            "bottom" -> tr(StringsRes.string.at_the_bottom)
            "left" -> tr(StringsRes.string.on_the_left)
            else -> tr(StringsRes.string.on_the_right)
        }
    fun choosePort(id: String, side: String) {
        connecting = if (connecting == (id to side)) null else id to side
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    fun hitPort(screen: Offset): Pair<String, String>? {
        if (currentReadOnly || zoom < .55f) return null
        val origin = camera()
        val unit = density * zoom
        val radius = 16 * density
        var found: Pair<String, String>? = null
        var closest = radius * radius
        for (card in currentCards.asReversed()) {
            if (card.kind == "group") continue
            val p = position(card)
            for (side in portSides) {
                val port = WorldRect(p.x, p.y, card.width, card.height).port(side)
                val dx = origin.x + port.x * unit - screen.x
                val dy = origin.y + port.y * unit - screen.y
                val distance = dx * dx + dy * dy
                if (distance < closest) {
                    closest = distance
                    found = card.id to side
                }
            }
        }
        return found
    }
    fun hit(screen: Offset): CanvasCard? {
        val p = world(screen)
        // Preserve z-order without sorting the whole board for every touch.
        fun contains(c: CanvasCard): Boolean {
            val xy = position(c)
            return p.x in xy.x..xy.x + c.width &&
                p.y in xy.y..xy.y + (if (c.kind == "group") 34f else c.height)
        }
        return currentCards.lastOrNull { it.kind != "group" && contains(it) }
            ?: currentCards.lastOrNull { it.kind == "group" && contains(it) }
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
    LaunchedEffect(readOnly, byId.keys) {
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
    fun transform(anchor: Offset, delta: Offset, scale: Float) {
        val origin = camera()
        val next =
            CanvasCamera(zoom, origin.x, origin.y)
                .transform(WorldPoint(anchor.x, anchor.y), WorldPoint(delta.x, delta.y), scale)
        pan = Offset(next.x, next.y)
        zoom = next.zoom
    }
    fun changeZoom(value: Float) {
        transform(Offset(dimensions.width / 2, dimensions.height / 2), Offset.Zero, value / zoom)
    }
    LaunchedEffect(boardId, dimensions, cards.isNotEmpty()) {
        if (!fitted && dimensions.width > 0 && cards.isNotEmpty()) {
            fit()
            fitted = true
        }
    }
    LaunchedEffect(cards) {
        textRenderer.retain(byId.keys)
        pending.keys.toList().forEach { id ->
            val c = byId[id]
            val p = pending[id]!!
            if (c == null || (abs(c.x - p.x) < .5f && abs(c.y - p.y) < .5f)) pending.remove(id)
        }
    }
    val detailed by remember(boardId) { derivedStateOf { zoom >= .55f } }
    val visible by
        remember(boardId, cards, density) {
            derivedStateOf {
                cards.filter { c ->
                    val top = camera() + position(c) * density * zoom
                    top.x < dimensions.width &&
                        top.y < dimensions.height &&
                        top.x + c.width * density * zoom > 0 &&
                        top.y + c.height * density * zoom > 0
                }
            }
        }
    val imageUris =
        if (detailed) visible.filter { it.kind == "image" }.mapNotNull { it.uri }.distinct().take(8)
        else emptyList()
    var images by remember(boardId) { mutableStateOf<Map<String, Bitmap>>(emptyMap()) }
    LaunchedEffect(imageUris) {
        images = buildMap { imageUris.forEach { uri -> imageLoader(uri)?.let { put(uri, it) } } }
    }
    Box(modifier.background(Color(0xFF1B1B1B))) {
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { dimensions = Size(it.width.toFloat(), it.height.toFloat()) }
                .semantics { contentDescription = tr(StringsRes.string.canvas) }
                .canvasGestures(
                    boardId,
                    ::hit,
                    { currentReadOnly },
                    { point, card ->
                        val port = hitPort(point)
                        if (port != null) choosePort(port.first, port.second)
                        else if (card != null) selectCard(card.id)
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
                    ::transform,
                )
        ) {
            val origin = camera()
            val unit = density * zoom
            drawIntoCanvas {
                gridRenderer.draw(
                    it.nativeCanvas,
                    size.width,
                    size.height,
                    origin.x,
                    origin.y,
                    density,
                    zoom,
                )
            }
            visible
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
                        style = groupStroke,
                    )
                    if (zoom >= .25f)
                        drawIntoCanvas { textRenderer.group(it.nativeCanvas, c, xy.x, xy.y, unit) }
                }
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
                    arrowPath.apply {
                        reset()
                        moveTo(a.x, a.y)
                        cubicTo(c1.x, c1.y, c2.x, c2.y, b.x, b.y)
                    }
                val color = Color(0xFF8A8A87)
                drawPath(path, color, style = arrowStroke)
                val v = b - c2
                val length = v.getDistance().coerceAtLeast(1f)
                val direction = v / length
                val back = b - direction * (7f * density * zoom.coerceIn(.6f, 1f))
                val side =
                    Offset(-direction.y, direction.x) * 3.5f * density * zoom.coerceIn(.6f, 1f)
                drawPath(
                    arrowHead.apply {
                        reset()
                        moveTo(b.x, b.y)
                        lineTo((back + side).x, (back + side).y)
                        lineTo((back - side).x, (back - side).y)
                        close()
                    },
                    color,
                )
            }
            ordinaryCards.forEach { source ->
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
                        textRenderer.card(
                            canvas.nativeCanvas,
                            card,
                            top.x,
                            top.y,
                            unit,
                            zoom >= .55f,
                            images[card.uri],
                        )
                    }
                }
            if (!readOnly && zoom >= .55f)
                visible
                    .filter { it.kind != "group" }
                    .forEach { card ->
                        val p = position(card)
                        portSides.forEach { side ->
                            val port = WorldRect(p.x, p.y, card.width, card.height).port(side)
                            val center = origin + Offset(port.x, port.y) * unit
                            val selected = connecting == (card.id to side)
                            val radius = if (selected) 6 * density else 4 * density
                            drawCircle(
                                if (selected) Color(0xFF4BC6B6) else Color(0xFF303737),
                                radius,
                                center,
                            )
                            drawCircle(
                                Color(0xFF76B7AC),
                                radius,
                                center,
                                style = Stroke(1.5f * density),
                            )
                        }
                    }
        }
        visible.forEach { card ->
            key(card.id) {
                val description =
                    remember(card.title, locale) { tr(StringsRes.string.card_2, card.title) }
                val actionLabels =
                    remember(card.title, locale) {
                        portSides.map { side ->
                            tr(StringsRes.string.connect, sideLabel(side), card.title)
                        }
                    }
                val openLabel =
                    remember(locale, connecting != null) {
                        if (connecting != null) tr(StringsRes.string.connect_to_card)
                        else tr(StringsRes.string.open_card)
                    }
                Box(
                    Modifier.offset {
                            val top = camera() + position(card) * density * zoom
                            IntOffset(top.x.roundToInt(), top.y.roundToInt())
                        }
                        .layout { measurable, constraints ->
                            val width =
                                constraints.constrainWidth(
                                    (card.width * density * zoom).roundToInt()
                                )
                            val height =
                                constraints.constrainHeight(
                                    (card.height * density * zoom).roundToInt()
                                )
                            val placeable = measurable.measure(Constraints.fixed(width, height))
                            layout(width, height) { placeable.place(0, 0) }
                        }
                        .semantics {
                            contentDescription = description
                            role = Role.Button
                            if (!readOnly && card.kind != "group")
                                customActions =
                                    portSides.mapIndexed { index, side ->
                                        CustomAccessibilityAction(actionLabels[index]) {
                                            choosePort(card.id, side)
                                            true
                                        }
                                    }
                            onClick(label = openLabel) {
                                selectCard(card.id)
                                true
                            }
                        }
                )
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
                    Text(
                        tr(StringsRes.string.choose_another_card),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    TextButton(onClick = { connecting = null }) {
                        Text(tr(StringsRes.string.cancel))
                    }
                }
            }
        if (cards.isEmpty())
            Text(
                tr(StringsRes.string.tap_an_empty_area_nto_add_your_first_card),
                Modifier.align(Alignment.Center).padding(50.dp),
                color = Color(0xFF9A9E9B),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        ZoomControls(
            { zoom },
            ::changeZoom,
            ::fit,
            Modifier.align(Alignment.BottomStart).padding(start = 12.dp, bottom = 12.dp),
        )
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
                    tr(StringsRes.string.read_only),
                    Modifier.padding(10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFC5B48B),
                )
            }
    }
}

@Composable
private fun ZoomControls(
    zoom: () -> Float,
    onZoom: (Float) -> Unit,
    onFit: () -> Unit,
    modifier: Modifier,
) {
    Surface(modifier, color = Color(0xEE282C2B), shape = MaterialTheme.shapes.large) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { onZoom(zoom() * 1.25f) },
                modifier = Modifier.width(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("+")
            }
            Text("${(zoom()*100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
            TextButton(
                onClick = { onZoom(zoom() * .8f) },
                modifier = Modifier.width(48.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("−")
            }
            TextButton(onClick = onFit) { Text(tr(StringsRes.string.fit_all)) }
        }
    }
}
