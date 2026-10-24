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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.vetki.core.*
import dev.vetki.data.Note
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.ui.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    notes: List<Note>,
    onOpen: (String) -> Unit,
    onActions: (Note) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(notes.isNotEmpty()) {
        if (!initialized && notes.isNotEmpty()) {
            expanded = notes.filter { it.folder }.map { it.id }
            initialized = true
        }
    }
    val rows =
        remember(notes, expanded, query) {
            visibleTree(
                notes.map { TreeEntry(it.id, it.parentId, it.title, it.folder) },
                expanded.toSet(),
                query,
            )
        }
    Column(modifier.fillMaxSize()) {
        OutlinedTextField(
            query,
            { query = it },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text(tr(StringsRes.string.find_a_note_or_folder)) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp)) {
            item {
                SectionLabel(
                    tr(StringsRes.string.my_space_2),
                    tr(StringsRes.string.notes_2, notes.count { !it.folder }),
                )
            }
            if (rows.isEmpty())
                item {
                    EmptyState(
                        Icons.Outlined.FolderOpen,
                        tr(StringsRes.string.nothing_here_yet),
                        if (query.isBlank())
                            tr(StringsRes.string.create_a_note_or_folder_with_the_button)
                        else tr(StringsRes.string.try_a_different_title),
                    )
                }
            items(rows, key = { it.entry.id }) { row ->
                val note = notes.first { it.id == row.entry.id }
                Row(
                    Modifier.fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (note.folder)
                                    expanded =
                                        if (note.id in expanded) expanded - note.id
                                        else expanded + note.id
                                else onOpen(note.id)
                            },
                            onLongClick = { onActions(note) },
                        )
                        .padding(
                            start = (row.depth * 16).coerceAtMost(80).dp,
                            end = 4.dp,
                            top = 5.dp,
                            bottom = 5.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (note.folder)
                        Icon(
                            if (note.id in expanded) Icons.Outlined.ExpandMore
                            else Icons.Outlined.ChevronRight,
                            null,
                            Modifier.size(20.dp),
                            tint = Muted,
                        )
                    else Spacer(Modifier.width(20.dp))
                    Icon(
                        if (note.folder) Icons.Outlined.Folder else Icons.Outlined.Description,
                        null,
                        Modifier.padding(horizontal = 10.dp).size(23.dp),
                        tint = if (note.folder) Amber else Muted,
                    )
                    Text(
                        note.title,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onActions(note) }) {
                        Icon(
                            Icons.Outlined.MoreHoriz,
                            tr(StringsRes.string.actions, note.title),
                            Modifier.size(19.dp),
                            tint = Muted,
                        )
                    }
                }
            }
            if (query.isBlank() && notes.any { !it.folder }) {
                item {
                    Spacer(Modifier.height(20.dp))
                    SectionLabel(tr(StringsRes.string.recent))
                }
                items(notes.filter { !it.folder }.take(3), key = { "recent-${it.id}" }) { note ->
                    Surface(
                        onClick = { onOpen(note.id) },
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Panel,
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Description, null, tint = Teal)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(note.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    note.body.replace("#", "").trim().ifBlank {
                                        tr(StringsRes.string.empty_note)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEditor(note: Note, onSave: (Note) -> Unit, modifier: Modifier = Modifier) {
    var title by rememberSaveable(note.id) { mutableStateOf(note.title) }
    var body by rememberSaveable(note.id) { mutableStateOf(note.body) }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        Text(
            tr(StringsRes.string.note_markdown),
            style = MaterialTheme.typography.labelSmall,
            color = Teal,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        TextField(
            title,
            {
                title = it
                onSave(
                    note.copy(title = it.ifBlank { tr(StringsRes.string.untitled) }, body = body)
                )
            },
            Modifier.fillMaxWidth(),
            placeholder = { Text(tr(StringsRes.string.title)) },
            textStyle =
                MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedIndicatorColor = Background,
                    unfocusedIndicatorColor = Background,
                ),
        )
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Panel)
        TextField(
            body,
            {
                body = it
                onSave(
                    note.copy(title = title.ifBlank { tr(StringsRes.string.untitled) }, body = it)
                )
            },
            Modifier.fillMaxWidth().heightIn(min = 400.dp),
            placeholder = { Text(tr(StringsRes.string.start_writing_n_n_heading_n_list_n_task)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background,
                    focusedIndicatorColor = Background,
                    unfocusedIndicatorColor = Background,
                ),
        )
        Text(
            tr(StringsRes.string.changes_are_saved_automatically),
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
            modifier = Modifier.padding(vertical = 20.dp),
        )
    }
}
