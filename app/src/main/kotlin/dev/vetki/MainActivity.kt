package dev.vetki

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vetki.core.*
import dev.vetki.data.*
import dev.vetki.feature.*
import dev.vetki.i18n.Language
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.reminders.AlarmPlaybackState
import dev.vetki.ui.*
import dev.vetki.viewmodel.*
import java.time.LocalDate
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(Language.wrap(newBase))
    }

    private val vm: VetkiViewModel by viewModels()
    private var incoming by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incoming = intent
        setContent { VetkiTheme { VetkiApp(vm, incoming) { incoming = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = intent
    }

    override fun onStop() {
        vm.flush()
        super.onStop()
    }
}

private data class ReminderDraft(
    val existing: Reminder?,
    val cardId: String?,
    val boardId: String?,
    val title: String,
)

private val reminderDraftSaver =
    Saver<ReminderDraft?, ArrayList<String>>(
        save = { d ->
            if (d == null) arrayListOf()
            else
                arrayListOf(
                    d.cardId.orEmpty(),
                    d.boardId.orEmpty(),
                    d.title,
                    d.existing?.id.orEmpty(),
                    (d.existing?.at ?: 0).toString(),
                    (d.existing?.alarm ?: false).toString(),
                    (d.existing?.nag ?: false).toString(),
                    d.existing?.repeat ?: "none",
                    (d.existing?.done ?: false).toString(),
                    (d.existing?.delivered ?: false).toString(),
                    d.existing?.nextAt?.toString().orEmpty(),
                )
        },
        restore = { a ->
            if (a.isEmpty()) null
            else {
                val card = a[0].ifEmpty { null }
                val board = a[1].ifEmpty { null }
                val r =
                    if (a[3].isEmpty()) null
                    else
                        Reminder(
                            id = a[3],
                            title = a[2],
                            cardId = card,
                            boardId = board,
                            at = a[4].toLong(),
                            alarm = a[5].toBoolean(),
                            nag = a[6].toBoolean(),
                            repeat = a[7],
                            done = a[8].toBoolean(),
                            delivered = a[9].toBoolean(),
                            nextAt = a[10].toLongOrNull(),
                        )
                ReminderDraft(r, card, board, a[2])
            }
        },
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VetkiApp(vm: VetkiViewModel, incoming: Intent?, consumeIntent: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ringing by AlarmPlaybackState.current.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var noteId by rememberSaveable { mutableStateOf<String?>(null) }
    var boardId by rememberSaveable { mutableStateOf<String?>(null) }
    var cardId by rememberSaveable { mutableStateOf<String?>(null) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    var selectedDateString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val selectedDate = LocalDate.parse(selectedDateString)
    var menu by remember { mutableStateOf(false) }
    var createMenu by remember { mutableStateOf(false) }
    var createBoard by remember { mutableStateOf(false) }
    var newNote by remember { mutableStateOf<Boolean?>(null) }
    var parentId by remember { mutableStateOf<String?>(null) }
    var noteActions by remember { mutableStateOf<Note?>(null) }
    var renameNote by remember { mutableStateOf<Note?>(null) }
    var deleteNote by remember { mutableStateOf<Note?>(null) }
    var moveNote by remember { mutableStateOf<Note?>(null) }
    var boardActions by remember { mutableStateOf<Board?>(null) }
    var renameBoard by remember { mutableStateOf<Board?>(null) }
    var deleteBoard by remember { mutableStateOf<Board?>(null) }
    var reminderDraft by
        rememberSaveable(stateSaver = reminderDraftSaver) { mutableStateOf<ReminderDraft?>(null) }
    var insertBoardId by rememberSaveable { mutableStateOf<String?>(null) }
    var insertX by rememberSaveable { mutableFloatStateOf(0f) }
    var insertY by rememberSaveable { mutableFloatStateOf(0f) }
    var pickNote by remember { mutableStateOf(false) }
    var addLink by remember { mutableStateOf(false) }
    var templateBoard by remember { mutableStateOf<Board?>(null) }
    var about by remember { mutableStateOf(false) }
    var chooseLanguage by remember { mutableStateOf(false) }
    var pendingPermissionReminder by remember { mutableStateOf<Reminder?>(null) }
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingPermissionReminder?.let { vm.saveReminder(it) }
            pendingPermissionReminder = null
            if (!granted)
                scope.launch {
                    snackbar.showSnackbar(
                        tr(
                            StringsRes.string
                                .reminder_saved_but_notifications_are_disabled_enable_them_in
                        )
                    )
                }
        }
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        vm.flushPending()
                        withContext(Dispatchers.IO) {
                            val text = Backup.export(vm.database, vm.attachments)
                            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                it.write(text)
                            } ?: error(tr(StringsRes.string.could_not_open_the_file))
                        }
                        snackbar.showSnackbar(tr(StringsRes.string.backup_saved))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(tr(StringsRes.string.export_failed, e.message))
                    }
                }
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null)
                scope.launch {
                    try {
                        vm.flushPending()
                        withContext(Dispatchers.IO) {
                            val text =
                                context.contentResolver
                                    .openInputStream(uri)
                                    ?.bufferedReader()
                                    ?.use { reader ->
                                        val b = StringBuilder()
                                        val buffer = CharArray(8192)
                                        while (true) {
                                            val count = reader.read(buffer)
                                            if (count < 0) break
                                            b.append(buffer, 0, count)
                                            require(b.length < 40_000_000) {
                                                tr(StringsRes.string.the_file_is_too_large)
                                            }
                                        }
                                        b.toString()
                                    } ?: error(tr(StringsRes.string.could_not_read_the_file))
                            val old = vm.dao.allReminders()
                            Backup.restore(vm.database, text, vm.attachments)
                            old.forEach { vm.scheduler.cancel(it.id) }
                            vm.scheduler.restore()
                        }
                        snackbar.showSnackbar(tr(StringsRes.string.backup_data_added))
                    } catch (e: Exception) {
                        snackbar.showSnackbar(
                            tr(StringsRes.string.could_not_restore_the_backup, e.message)
                        )
                    }
                }
        }
    val attachmentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val target = insertBoardId
            if (uri != null && target != null)
                scope.launch {
                    try {
                        val stored = withContext(Dispatchers.IO) { vm.attachments.copyFrom(uri) }
                        vm.addContent(
                            target,
                            WorldPoint(insertX, insertY),
                            kind = if (stored.mime.startsWith("image/")) "image" else "file",
                            title = stored.name,
                            body = if (stored.mime.startsWith("image/")) "" else stored.mime,
                            uri = stored.uri,
                            mime = stored.mime,
                        ) {
                            cardId = it
                        }
                    } catch (e: Exception) {
                        snackbar.showSnackbar(
                            e.message ?: tr(StringsRes.string.could_not_add_the_file)
                        )
                    }
                }
        }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(incoming, state.loaded, state.boards.size) {
        if (incoming != null && state.loaded) {
            val id = incoming.getStringExtra("boardId")
            if (id != null && state.boards.any { it.id == id }) {
                tab = 1
                boardId = id
                cardId = incoming.getStringExtra("cardId")
            } else
                incoming.getStringExtra("reminderId")?.let { rid ->
                    state.reminders
                        .firstOrNull { it.id == rid }
                        ?.let { reminderDraft = ReminderDraft(it, it.cardId, it.boardId, it.title) }
                }
            consumeIntent()
        }
    }
    val note = state.notes.firstOrNull { it.id == noteId }
    val board = state.boards.firstOrNull { it.id == boardId }
    val card = state.cards.firstOrNull { it.id == cardId }
    BackHandler(noteId != null || boardId != null) {
        vm.flush()
        if (noteId != null) noteId = null
        else {
            boardId = null
            cardId = null
        }
    }
    fun canvasAction(action: String, point: WorldPoint) {
        val target = board ?: return
        if (target.readOnly) return
        insertBoardId = target.id
        insertX = point.x
        insertY = point.y
        when (action) {
            "text" -> vm.addContent(target.id, point) { cardId = it }
            "group" ->
                vm.addContent(
                    target.id,
                    point,
                    kind = "group",
                    title = tr(StringsRes.string.new_group),
                    body = tr(StringsRes.string.move_cards_inside_the_frame_hold_the_group_title),
                ) {
                    cardId = it
                }
            "note" -> pickNote = true
            "file" -> attachmentPicker.launch(arrayOf("*/*"))
            "link" -> addLink = true
            "paste" -> {
                val text = clipboard.getText()?.text?.trim().orEmpty()
                if (text.isBlank())
                    scope.launch {
                        snackbar.showSnackbar(tr(StringsRes.string.the_clipboard_has_no_text))
                    }
                else
                    vm.addContent(
                        target.id,
                        point,
                        title = text.lineSequence().first().take(60),
                        body = text.take(100000),
                    )
            }
        }
    }
    fun openReference(c: Card) {
        try {
            when (c.kind) {
                "note" ->
                    if (state.notes.any { it.id == c.noteId && !it.folder }) {
                        noteId = c.noteId
                        cardId = null
                    } else
                        scope.launch {
                            snackbar.showSnackbar(
                                tr(
                                    StringsRes.string
                                        .the_original_note_was_deleted_its_saved_text_is
                                )
                            )
                        }
                "link" -> {
                    val uri = Uri.parse(c.uri)
                    require(uri.scheme in setOf("http", "https"))
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    cardId = null
                }
                "image",
                "file" -> {
                    val uri = vm.attachments.externalUri(requireNotNull(c.uri))
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW)
                            .setDataAndType(uri, c.mime ?: "application/octet-stream")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    )
                    cardId = null
                }
            }
        } catch (e: Exception) {
            scope.launch {
                snackbar.showSnackbar(
                    tr(
                        StringsRes.string.could_not_open,
                        e.message ?: tr(StringsRes.string.no_compatible_app_found),
                    )
                )
            }
        }
    }
    fun newReminder(r: Reminder) {
        reminderDraft = ReminderDraft(r, r.cardId, r.boardId, r.title)
    }
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        note?.title
                            ?: board?.title
                            ?: listOf(
                                tr(StringsRes.string.notes),
                                tr(StringsRes.string.canvases),
                                tr(StringsRes.string.calendar),
                                tr(StringsRes.string.today),
                            )[tab],
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (noteId != null || boardId != null)
                        IconButton(
                            onClick = {
                                vm.flush()
                                if (noteId != null) noteId = null
                                else {
                                    boardId = null
                                    cardId = null
                                }
                            }
                        ) {
                            Icon(Icons.Outlined.ArrowBack, tr(StringsRes.string.back))
                        }
                },
                actions = {
                    if (board != null) {
                        IconButton(onClick = { listMode = !listMode }) {
                            Icon(
                                if (listMode) Icons.Outlined.AccountTree
                                else Icons.Outlined.FormatListBulleted,
                                if (listMode) tr(StringsRes.string.show_canvas)
                                else tr(StringsRes.string.show_list),
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Outlined.MoreVert, tr(StringsRes.string.menu))
                        }
                        DropdownMenu(menu, { menu = false }) {
                            if (board != null) {
                                DropdownMenuItem(
                                    text = { Text(tr(StringsRes.string.arrange_tree)) },
                                    enabled = !board.readOnly,
                                    onClick = {
                                        vm.layoutBoard(board.id)
                                        menu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(tr(StringsRes.string.rename_canvas)) },
                                    onClick = {
                                        renameBoard = board
                                        menu = false
                                    },
                                )
                            }
                            if (board != null)
                                DropdownMenuItem(
                                    text = { Text(tr(StringsRes.string.save_as_template)) },
                                    onClick = {
                                        templateBoard = board
                                        menu = false
                                    },
                                )
                            if (note != null)
                                DropdownMenuItem(
                                    text = { Text(tr(StringsRes.string.note_actions)) },
                                    onClick = {
                                        noteActions = note
                                        menu = false
                                    },
                                )
                            DropdownMenuItem(
                                text = { Text(tr(StringsRes.string.export_backup)) },
                                onClick = {
                                    vm.flush()
                                    export.launch("vetki-${LocalDate.now()}.json")
                                    menu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(StringsRes.string.import_backup)) },
                                onClick = {
                                    import.launch(
                                        arrayOf(
                                            "application/json",
                                            "text/plain",
                                            "application/octet-stream",
                                        )
                                    )
                                    menu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(StringsRes.string.language)) },
                                onClick = {
                                    chooseLanguage = true
                                    menu = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(tr(StringsRes.string.about)) },
                                onClick = {
                                    about = true
                                    menu = false
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        bottomBar = {
            if (noteId == null && boardId == null)
                NavigationBar(containerColor = Background, tonalElevation = 0.dp) {
                    val icons =
                        listOf(
                            Icons.Outlined.Description,
                            Icons.Outlined.AccountTree,
                            Icons.Outlined.CalendarMonth,
                            Icons.Outlined.CheckBox,
                        )
                    listOf(
                            tr(StringsRes.string.notes),
                            tr(StringsRes.string.canvases),
                            tr(StringsRes.string.calendar),
                            tr(StringsRes.string.today),
                        )
                        .forEachIndexed { i, label ->
                            NavigationBarItem(
                                tab == i,
                                { tab = i },
                                icon = { Icon(icons[i], label) },
                                label = { Text(label) },
                                colors =
                                    NavigationBarItemDefaults.colors(
                                        selectedIconColor = Teal,
                                        selectedTextColor = Teal,
                                        indicatorColor = Panel,
                                    ),
                            )
                        }
                }
        },
        floatingActionButton = {
            if (noteId == null && board?.readOnly != true)
                FloatingActionButton(
                    onClick = {
                        when {
                            board != null -> vm.addContent(board.id) { cardId = it }
                            tab == 0 -> createMenu = true
                            tab == 1 -> createBoard = true
                            else -> reminderDraft = ReminderDraft(null, null, null, "")
                        }
                    },
                    containerColor = Teal,
                    contentColor = Background,
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        if (board != null) tr(StringsRes.string.add_card)
                        else tr(StringsRes.string.create),
                    )
                }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                note != null -> NoteEditor(note, vm::saveNote)
                board != null ->
                    BoardScreen(
                        board,
                        state.cards,
                        state.reminders,
                        listMode,
                        { cardId = it },
                        vm::moveCard,
                        notes = state.notes,
                        attachments = vm.attachments,
                        onCanvasAction = ::canvasAction,
                        onOption = { option, value -> vm.setBoardOption(board.id, option, value) },
                        onConnect = vm::connectCards,
                    )
                tab == 0 -> NotesScreen(state.notes, { noteId = it }, { noteActions = it })
                tab == 1 ->
                    BoardsScreen(
                        state.boards,
                        state.cards,
                        { boardId = it },
                        { createBoard = true },
                        { boardActions = it },
                    )
                tab == 2 ->
                    CalendarScreen(
                        state.reminders,
                        state.boards,
                        selectedDate,
                        { selectedDateString = it.toString() },
                        ::newReminder,
                        vm::completeReminder,
                    )
                else ->
                    TodayScreen(state.reminders, state.boards, ::newReminder, vm::completeReminder)
            }
        }
    }
    if (createMenu)
        AlertDialog(
            onDismissRequest = { createMenu = false },
            title = { Text(tr(StringsRes.string.create)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            parentId = null
                            newNote = false
                            createMenu = false
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text(tr(StringsRes.string.note))
                    }
                    TextButton(
                        onClick = {
                            parentId = null
                            newNote = true
                            createMenu = false
                        },
                        Modifier.fillMaxWidth(),
                    ) {
                        Text(tr(StringsRes.string.folder))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { createMenu = false }) { Text(tr(StringsRes.string.cancel)) }
            },
        )
    newNote?.let { folder ->
        NameDialog(
            if (folder) tr(StringsRes.string.new_folder) else tr(StringsRes.string.new_note),
            onDismiss = { newNote = null },
            onSave = { vm.createNote(parentId, folder, it) { id -> if (!folder) noteId = id } },
        )
    }
    noteActions?.let { n ->
        AlertDialog(
            onDismissRequest = { noteActions = null },
            title = { Text(n.title) },
            text = {
                Column {
                    if (n.folder) {
                        TextButton(
                            onClick = {
                                parentId = n.id
                                newNote = false
                                noteActions = null
                            }
                        ) {
                            Text(tr(StringsRes.string.add_note))
                        }
                        TextButton(
                            onClick = {
                                parentId = n.id
                                newNote = true
                                noteActions = null
                            }
                        ) {
                            Text(tr(StringsRes.string.add_folder))
                        }
                    }
                    TextButton(
                        onClick = {
                            renameNote = n
                            noteActions = null
                        }
                    ) {
                        Text(tr(StringsRes.string.rename))
                    }
                    TextButton(
                        onClick = {
                            moveNote = n
                            noteActions = null
                        }
                    ) {
                        Text(tr(StringsRes.string.move))
                    }
                    TextButton(
                        onClick = {
                            deleteNote = n
                            noteActions = null
                        }
                    ) {
                        Text(tr(StringsRes.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { noteActions = null }) { Text(tr(StringsRes.string.close)) }
            },
        )
    }
    renameNote?.let { n ->
        NameDialog(tr(StringsRes.string.rename), n.title, { renameNote = null }) {
            vm.saveNote(n.copy(title = it))
            vm.flush()
        }
    }
    deleteNote?.let { n ->
        ConfirmDelete(
            tr(
                StringsRes.string.delete_2,
                if (n.folder) tr(StringsRes.string.folder_2) else tr(StringsRes.string.note_2),
            ),
            if (n.folder) tr(StringsRes.string.all_folder_contents_will_be_deleted_you_can_export)
            else tr(StringsRes.string.this_note_will_be_deleted),
            { deleteNote = null },
        ) {
            if (noteId == n.id) noteId = null
            vm.deleteNote(n)
        }
    }
    moveNote?.let { n ->
        val forbidden =
            descendants(
                state.notes.map { TreeEntry(it.id, it.parentId, it.title, it.folder) },
                n.id,
            )
        AlertDialog(
            onDismissRequest = { moveNote = null },
            title = { Text(tr(StringsRes.string.move_to)) },
            text = {
                Column(Modifier.heightIn(max = 350.dp).verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = {
                            vm.moveNote(n, null)
                            moveNote = null
                        }
                    ) {
                        Text(tr(StringsRes.string.my_space))
                    }
                    state.notes
                        .filter { it.folder && it.id !in forbidden }
                        .forEach { folder ->
                            TextButton(
                                onClick = {
                                    vm.moveNote(n, folder.id)
                                    moveNote = null
                                }
                            ) {
                                Text(folder.title)
                            }
                        }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { moveNote = null }) { Text(tr(StringsRes.string.cancel)) }
            },
        )
    }
    if (createBoard)
        CreateBoardSheet(
            onDismiss = { createBoard = false },
            onCreate = { title, template ->
                vm.createBoard(title, template) {
                    boardId = it
                    tab = 1
                }
                createBoard = false
            },
            savedTemplates = state.templates,
            onDeleteTemplate = vm::deleteTemplate,
        )
    boardActions?.let { b ->
        AlertDialog(
            onDismissRequest = { boardActions = null },
            title = { Text(b.title) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            templateBoard = b
                            boardActions = null
                        }
                    ) {
                        Text(tr(StringsRes.string.save_as_template))
                    }
                    TextButton(
                        onClick = {
                            renameBoard = b
                            boardActions = null
                        }
                    ) {
                        Text(tr(StringsRes.string.rename))
                    }
                    TextButton(
                        onClick = {
                            deleteBoard = b
                            boardActions = null
                        }
                    ) {
                        Text(tr(StringsRes.string.delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { boardActions = null }) { Text(tr(StringsRes.string.close)) }
            },
        )
    }
    renameBoard?.let { b ->
        NameDialog(tr(StringsRes.string.canvas_title), b.title, { renameBoard = null }) {
            vm.saveBoard(b.copy(title = it))
        }
    }
    deleteBoard?.let { b ->
        ConfirmDelete(
            tr(StringsRes.string.delete_canvas),
            tr(StringsRes.string.all_cards_and_reminders_on_this_canvas_will_be),
            { deleteBoard = null },
        ) {
            vm.deleteBoard(b)
            if (boardId == b.id) boardId = null
        }
    }
    if (card != null && reminderDraft == null)
        CardSheet(
            card,
            state.reminders.firstOrNull { it.cardId == card.id && !it.done },
            { cardId = null },
            vm::saveCard,
            { draft ->
                cardId = null
                reminderDraft =
                    ReminderDraft(
                        state.reminders.firstOrNull { it.cardId == draft.id && !it.done },
                        draft.id,
                        draft.boardId,
                        draft.title,
                    )
            },
            { vm.deleteCard(card) },
            readOnly = board?.readOnly == true,
            onOpenReference = { openReference(card) },
        )
    if (pickNote)
        NotePicker(state.notes, { pickNote = false }) { selected ->
            insertBoardId?.let { target ->
                vm.addContent(
                    target,
                    WorldPoint(insertX, insertY),
                    kind = "note",
                    title = selected.title,
                    body = selected.body,
                    noteId = selected.id,
                )
            }
        }
    if (addLink)
        LinkDialog({ addLink = false }) { title, url ->
            insertBoardId?.let { target ->
                vm.addContent(
                    target,
                    WorldPoint(insertX, insertY),
                    kind = "link",
                    title = title,
                    body = url,
                    uri = url,
                )
            }
        }
    templateBoard?.let { target ->
        NameDialog(
            tr(StringsRes.string.save_your_template),
            target.title,
            { templateBoard = null },
        ) { title ->
            vm.saveTemplate(target, title)
        }
    }
    reminderDraft?.let { draft ->
        ReminderSheet(
            draft.existing,
            draft.cardId,
            draft.boardId,
            draft.title,
            if (tab == 2) selectedDate else LocalDate.now(),
            vm.scheduler::exactAvailable,
            {
                if (Build.VERSION.SDK_INT >= 31)
                    context.startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        )
                    )
            },
            { reminderDraft = null },
            { r ->
                if (
                    Build.VERSION.SDK_INT >= 33 &&
                        androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    pendingPermissionReminder = r
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else vm.saveReminder(r)
            },
            vm::deleteReminder,
        )
    }
    if (chooseLanguage)
        LanguageDialog(
            selected = Language.selected(context),
            onDismiss = { chooseLanguage = false },
            onSelect = { language ->
                chooseLanguage = false
                if (language != Language.selected(context))
                    scope.launch {
                        vm.flushPending()
                        Language.set(context, language)
                        if (Build.VERSION.SDK_INT < 33) (context as? Activity)?.recreate()
                    }
            },
        )
    ringing?.let { RingingScreen(it) }
    if (about)
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text(tr(StringsRes.string.vetki, BuildConfig.VERSION_NAME)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tr(StringsRes.string.notes_connected_ideas_and_plans_in_one_place))
                    Text(
                        tr(StringsRes.string.your_data_is_stored_on_this_phone_export_a),
                        color = Muted,
                    )
                    Text(
                        tr(
                            StringsRes.string
                                .for_reliable_reminders_allow_notifications_and_exact_alarms_on
                        ),
                        color = Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { about = false }) { Text(tr(StringsRes.string.got_it)) }
            },
        )
}
