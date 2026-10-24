package dev.vetki.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.vetki.canvas.BoardCanvas
import dev.vetki.core.WorldPoint
import dev.vetki.data.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.ui.*
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun BoardsScreen(
    boards: List<Board>,
    cards: List<Card>,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onMenu: (Board) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 100.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Outlined.AccountTree, null, tint = Teal)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        tr(StringsRes.string.give_your_thoughts_space),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        tr(StringsRes.string.connect_ideas_make_plans_and_turn_them_into_actions),
                        Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                    )
                }
            }
        }
        item { SectionLabel(tr(StringsRes.string.your_canvases), "${boards.size}") }
        if (boards.isEmpty())
            item {
                EmptyState(
                    Icons.Outlined.AccountTree,
                    tr(StringsRes.string.your_first_canvas),
                    tr(StringsRes.string.start_with_a_blank_canvas_or_a_ready_made),
                    tr(StringsRes.string.create_canvas),
                    onCreate,
                )
            }
        items(boards, key = { it.id }) { board ->
            val boardCards = cards.filter { it.boardId == board.id }
            Surface(
                onClick = { onOpen(board.id) },
                shape = RoundedCornerShape(20.dp),
                color = Panel,
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = Background) {
                            Icon(
                                if (board.template == "travel") Icons.Outlined.FlightTakeoff
                                else Icons.Outlined.AccountTree,
                                null,
                                Modifier.padding(12.dp),
                                tint = Teal,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                board.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                tr(
                                    StringsRes.string.cards_completed,
                                    boardCards.size,
                                    boardCards.count { it.done },
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Muted,
                            )
                        }
                        IconButton(onClick = { onMenu(board) }) {
                            Icon(Icons.Outlined.MoreHoriz, tr(StringsRes.string.canvas_actions))
                        }
                    }
                    Row(
                        Modifier.padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        boardCards
                            .filter { it.parentId != null }
                            .take(3)
                            .forEach { card ->
                                SuggestionChip(
                                    onClick = { onOpen(board.id) },
                                    label = {
                                        Text(
                                            card.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBoardSheet(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
    savedTemplates: List<BoardTemplate> = emptyList(),
    onDeleteTemplate: (String) -> Unit = {},
) {
    var template by rememberSaveable { mutableStateOf("empty") }
    var title by rememberSaveable { mutableStateOf("") }
    val templates =
        listOf(
            Triple(
                "empty",
                tr(StringsRes.string.blank_canvas),
                tr(StringsRes.string.an_empty_space_for_your_ideas),
            ),
            Triple(
                "travel",
                tr(StringsRes.string.travel),
                tr(StringsRes.string.documents_accommodation_budget_and_packing),
            ),
            Triple(
                "project",
                tr(StringsRes.string.project),
                tr(StringsRes.string.goal_milestones_tasks_and_questions),
            ),
            Triple(
                "decision",
                tr(StringsRes.string.make_a_decision),
                tr(StringsRes.string.options_benefits_and_risks),
            ),
            Triple(
                "work",
                tr(StringsRes.string.work),
                tr(StringsRes.string.results_deadlines_owners_and_actions),
            ),
        )
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss,
        containerColor = Background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(tr(StringsRes.string.new_canvas), style = MaterialTheme.typography.headlineSmall)
            Text(tr(StringsRes.string.choose_a_starting_point), color = Muted)
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text(tr(StringsRes.string.canvas_title)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            templates.forEach { (id, name, desc) ->
                Surface(
                    onClick = { template = id },
                    shape = RoundedCornerShape(14.dp),
                    color =
                        if (template == id) MaterialTheme.colorScheme.primaryContainer else Panel,
                    border = if (template == id) BorderStroke(1.dp, Teal) else null,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(template == id, { template = id })
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(name, style = MaterialTheme.typography.titleSmall)
                            Text(desc, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Surface(
                onClick = { template = "custom" },
                shape = RoundedCornerShape(16.dp),
                color =
                    if (template == "custom" || template.startsWith("saved:"))
                        androidx.compose.ui.graphics.Color(0xFF3A3020)
                    else Panel,
                border = BorderStroke(1.dp, Amber),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.AutoAwesome, null, tint = Amber)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            tr(StringsRes.string.your_own_template),
                            color = Amber,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            tr(StringsRes.string.save_your_own_structure_and_use_it_again),
                            color = Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (template == "custom" || template.startsWith("saved:")) {
                Text(
                    tr(StringsRes.string.start_with_a_blank_canvas_and_choose_save_as),
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall,
                )
                savedTemplates.forEach { saved ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            template == "saved:${saved.id}",
                            { template = "saved:${saved.id}" },
                        )
                        TextButton(
                            onClick = { template = "saved:${saved.id}" },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(saved.title)
                        }
                        IconButton(
                            onClick = {
                                onDeleteTemplate(saved.id)
                                if (template == "saved:${saved.id}") template = "custom"
                            }
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                tr(StringsRes.string.delete_template, saved.title),
                                tint = Muted,
                            )
                        }
                    }
                }
            }
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank {
                            if (template == "custom") tr(StringsRes.string.my_template)
                            else if (template.startsWith("saved:"))
                                savedTemplates.firstOrNull { "saved:${it.id}" == template }?.title
                                    ?: tr(StringsRes.string.my_canvas)
                            else templates.first { it.first == template }.second
                        },
                        template,
                    )
                },
                Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    if (template == "custom") tr(StringsRes.string.create_template_draft)
                    else tr(StringsRes.string.create_canvas)
                )
            }
        }
    }
}

@Composable
fun BoardScreen(
    board: Board,
    cards: List<Card>,
    reminders: List<Reminder>,
    list: Boolean,
    onOpen: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    notes: List<Note> = emptyList(),
    attachments: AttachmentStore? = null,
    onCanvasAction: (String, WorldPoint) -> Unit = { _, _ -> },
    onOption: (String, Boolean) -> Unit = { _, _ -> },
    onConnect: (String, String, String) -> Unit = { _, _, _ -> },
) {
    val boardCards = remember(cards, board.id) { cards.filter { it.boardId == board.id } }
    val due = remember(reminders) { reminders.filter { !it.done }.associateBy { it.cardId } }
    var menuAnchor by remember(board.id) { mutableStateOf<Pair<WorldPoint, Offset>?>(null) }
    var help by remember(board.id) { mutableStateOf(false) }
    Box(modifier.fillMaxSize()) {
        if (!list) {
            BoardCanvas(
                board.id,
                boardCards.map { card ->
                    val source = notes.firstOrNull { it.id == card.noteId }
                    card.copy(body = source?.body ?: card.body).asCanvas(due[card.id]?.at)
                },
                onOpen,
                onMove,
                Modifier.fillMaxSize(),
                board.snapGrid,
                board.snapObjects,
                board.readOnly,
                onConnect = onConnect,
                onBlankTap = { world, screen -> menuAnchor = world to screen },
                onHelp = { help = true },
                loadImage = { uri -> withContext(Dispatchers.IO) { attachments?.thumbnail(uri) } },
            )
            menuAnchor?.let { (world, screen) ->
                CanvasActionMenu(
                    board,
                    IntOffset(screen.x.roundToInt(), screen.y.roundToInt()),
                    { menuAnchor = null },
                    { onCanvasAction(it, world) },
                    onOption,
                )
            }
        } else {
            val rows =
                remember(boardCards) {
                    dev.vetki.core.visibleTree(
                        boardCards.map {
                            dev.vetki.core.TreeEntry(it.id, it.parentId, it.title, true)
                        },
                        boardCards.map { it.id }.toSet(),
                    )
                }
            LazyColumn(
                modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.entry.id }) { row ->
                    val c = boardCards.first { it.id == row.entry.id }
                    Surface(
                        onClick = { onOpen(c.id) },
                        Modifier.padding(start = (row.depth * 16).coerceAtMost(64).dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Panel,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (c.done) Icons.Outlined.CheckCircle
                                else Icons.Outlined.RadioButtonUnchecked,
                                null,
                                tint = StatusColors[c.status.mod(4)],
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.title, style = MaterialTheme.typography.titleSmall)
                                if (c.body.isNotBlank())
                                    Text(
                                        c.body,
                                        color = Muted,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                due[c.id]?.let {
                                    Text(
                                        "◷ ${dateTime(it.at)}",
                                        color = Amber,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Icon(Icons.Outlined.ChevronRight, null, tint = Muted)
                        }
                    }
                }
            }
        }
    }
    if (help) CanvasHelpSheet(board) { help = false }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CardSheet(
    card: Card,
    reminder: Reminder?,
    onDismiss: () -> Unit,
    onSave: (Card) -> Unit,
    onRemind: (Card) -> Unit,
    onDelete: () -> Unit,
    readOnly: Boolean = false,
    onOpenReference: () -> Unit = {},
) {
    var title by rememberSaveable(card.id) { mutableStateOf(card.title) }
    var body by rememberSaveable(card.id) { mutableStateOf(card.body) }
    var status by rememberSaveable(card.id) { mutableIntStateOf(card.status) }
    var done by rememberSaveable(card.id) { mutableStateOf(card.done) }
    var confirm by remember { mutableStateOf(false) }
    var groupWidth by rememberSaveable(card.id) { mutableStateOf(card.width.toInt().toString()) }
    var groupHeight by rememberSaveable(card.id) { mutableStateOf(card.height.toInt().toString()) }
    fun draft() =
        card.copy(
            title = title.trim().ifBlank { tr(StringsRes.string.untitled) },
            body = body,
            status = status,
            done = done,
            width =
                if (card.kind == "group") (groupWidth.toFloatOrNull() ?: 440f).coerceIn(220f, 2400f)
                else card.width,
            height =
                if (card.kind == "group")
                    (groupHeight.toFloatOrNull() ?: 300f).coerceIn(140f, 2400f)
                else card.height,
        )
    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = {
            if (!readOnly) onSave(draft())
            onDismiss()
        },
        containerColor = Background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                if (readOnly) tr(StringsRes.string.view_card)
                else if (card.kind == "group") tr(StringsRes.string.group)
                else tr(StringsRes.string.card),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                title,
                { title = it },
                Modifier.fillMaxWidth(),
                label = { Text(tr(StringsRes.string.title)) },
                readOnly = readOnly,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                body,
                { body = it },
                Modifier.fillMaxWidth().heightIn(min = 120.dp),
                label = { Text(tr(StringsRes.string.notes_and_questions)) },
                readOnly = readOnly,
                shape = RoundedCornerShape(14.dp),
            )
            if (card.kind == "group")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        groupWidth,
                        { groupWidth = it.filter(Char::isDigit) },
                        Modifier.weight(1f),
                        label = { Text(tr(StringsRes.string.width)) },
                        singleLine = true,
                        readOnly = readOnly,
                    )
                    OutlinedTextField(
                        groupHeight,
                        { groupHeight = it.filter(Char::isDigit) },
                        Modifier.weight(1f),
                        label = { Text(tr(StringsRes.string.height)) },
                        singleLine = true,
                        readOnly = readOnly,
                    )
                }
            if (card.kind in setOf("note", "image", "file", "link"))
                OutlinedButton(
                    onClick = {
                        if (!readOnly) onSave(draft())
                        onOpenReference()
                    },
                    Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (card.kind == "note") tr(StringsRes.string.open_note)
                        else if (card.kind == "link") tr(StringsRes.string.open_website)
                        else tr(StringsRes.string.open_file)
                    )
                }
            if (!readOnly && card.kind != "group")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusNames.forEachIndexed { i, name ->
                        FilterChip(
                            status == i,
                            {
                                status = i
                                done = i == 3
                            },
                            label = { Text(name) },
                        )
                    }
                }
            if (!readOnly && card.kind != "group")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        done,
                        {
                            done = it
                            if (it) status = 3 else if (status == 3) status = 0
                        },
                    )
                    Text(tr(StringsRes.string.completed))
                }
            if (!readOnly && card.kind != "group")
                OutlinedButton(
                    onClick = {
                        onSave(draft())
                        onRemind(draft())
                    },
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Outlined.NotificationsActive, null)
                    Spacer(Modifier.width(10.dp))
                    Text(reminder?.let { dateTime(it.at) } ?: tr(StringsRes.string.add_reminder))
                }
            if (!readOnly)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { confirm = true }) {
                        Icon(Icons.Outlined.DeleteOutline, tr(StringsRes.string.delete_card))
                    }
                }
            Button(
                onClick = {
                    if (!readOnly) onSave(draft())
                    onDismiss()
                },
                Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (readOnly) tr(StringsRes.string.close) else tr(StringsRes.string.save))
            }
        }
    }
    if (confirm)
        ConfirmDelete(
            if (card.kind == "group") tr(StringsRes.string.delete_group_frame)
            else tr(StringsRes.string.delete_branch),
            if (card.kind == "group") tr(StringsRes.string.cards_inside_will_stay_on_the_canvas)
            else tr(StringsRes.string.this_card_its_child_cards_and_their_reminders_will),
            { confirm = false },
            {
                onDelete()
                onDismiss()
            },
        )
}
