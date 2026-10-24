package dev.vetki.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.*
import dev.vetki.data.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr
import dev.vetki.ui.*

@Composable
fun CanvasActionMenu(
    board: Board,
    anchor: IntOffset,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit,
    onOption: (String, Boolean) -> Unit,
) {
    Box(Modifier.offset { anchor }.size(1.dp)) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = onDismiss,
            modifier = Modifier.width(294.dp).heightIn(max = 520.dp),
            containerColor = Panel,
        ) {
            fun choose(action: String) {
                onDismiss()
                onAction(action)
            }
            @Composable
            fun item(label: String, icon: ImageVector, action: String) {
                DropdownMenuItem(
                    text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
                    leadingIcon = { Icon(icon, null, Modifier.size(20.dp)) },
                    enabled = !board.readOnly,
                    onClick = { choose(action) },
                )
            }
            item(tr(StringsRes.string.calculator), Icons.Outlined.Calculate, "calculator")
            item(tr(StringsRes.string.add_card), Icons.Outlined.CropLandscape, "text")
            item(tr(StringsRes.string.from_my_notes), Icons.Outlined.Description, "note")
            item(tr(StringsRes.string.image_or_file), Icons.Outlined.AttachFile, "file")
            item(tr(StringsRes.string.add_web_link), Icons.Outlined.OpenInNew, "link")
            item(tr(StringsRes.string.create_group), Icons.Outlined.SelectAll, "group")
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            item(tr(StringsRes.string.paste_text), Icons.Outlined.ContentPaste, "paste")
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            DropdownMenuItem(
                text = { Text(tr(StringsRes.string.snap_to_grid)) },
                leadingIcon = { Icon(Icons.Outlined.GridOn, null, Modifier.size(20.dp)) },
                trailingIcon = { Checkbox(board.snapGrid, null) },
                enabled = !board.readOnly,
                onClick = {
                    onOption("grid", !board.snapGrid)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(tr(StringsRes.string.snap_to_cards)) },
                leadingIcon = {
                    Icon(Icons.Outlined.DashboardCustomize, null, Modifier.size(20.dp))
                },
                trailingIcon = { Checkbox(board.snapObjects, null) },
                enabled = !board.readOnly,
                onClick = {
                    onOption("objects", !board.snapObjects)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(tr(StringsRes.string.read_only)) },
                leadingIcon = { Icon(Icons.Outlined.LockOpen, null, Modifier.size(20.dp)) },
                trailingIcon = { Checkbox(board.readOnly, null) },
                onClick = {
                    onOption("readOnly", !board.readOnly)
                    onDismiss()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanvasHelpSheet(board: Board, onDismiss: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val guidance =
        when (page) {
            0 ->
                listOf(
                    tr(StringsRes.string.start_with_the_outcome) to
                        tr(StringsRes.string.one_main_card_answers_what_do_i_want_to),
                    tr(StringsRes.string.one_card_one_thought) to
                        tr(
                            StringsRes.string
                                .if_a_sentence_contains_several_independent_questions_split_them
                        ),
                    tr(StringsRes.string.from_broad_to_specific) to
                        tr(
                            StringsRes.string
                                .topic_areas_questions_decisions_next_actions_you_do_not
                        ),
                    tr(StringsRes.string.using_the_canvas) to
                        tr(StringsRes.string.tap_an_empty_area_to_open_the_menu_hold),
                    tr(StringsRes.string.use_groups) to
                        tr(StringsRes.string.a_group_is_a_shared_frame_move_cards_inside),
                )
            1 ->
                listOf(
                    tr(StringsRes.string.ask_a_specific_question) to
                        tr(StringsRes.string.instead_of_what_about_work_ask_what_materials_do),
                    tr(StringsRes.string.define_the_boundaries) to
                        tr(StringsRes.string.add_context_who_it_is_for_the_deadline_budget),
                    tr(StringsRes.string.separate_facts_from_assumptions) to
                        tr(StringsRes.string.the_price_is_unknown_is_a_fact_it_will),
                    tr(StringsRes.string.ask_about_the_next_step) to
                        tr(StringsRes.string.instead_of_how_can_i_get_everything_done_ask),
                    tr(StringsRes.string.leave_room_for_unknowns) to
                        tr(StringsRes.string.do_not_invent_an_answer_just_to_fill_a),
                )
            else ->
                listOf(
                    tr(StringsRes.string.build_a_chain) to
                        tr(
                            StringsRes.string
                                .question_possible_answers_what_to_check_decision_action_each
                        ),
                    tr(StringsRes.string.check_your_choices) to
                        tr(
                            StringsRes.string
                                .when_choosing_create_neighboring_options_and_compare_them_using
                        ),
                    tr(StringsRes.string.make_actions_verifiable) to
                        tr(
                            StringsRes.string
                                .sort_out_accommodation_choose_three_options_by_thursday_now
                        ),
                    tr(StringsRes.string.review_the_tree) to
                        tr(StringsRes.string.are_any_cards_duplicated_is_an_important_step_missing),
                    tr(StringsRes.string.keep_it_useful) to
                        tr(StringsRes.string.if_a_simple_list_is_clearer_switch_to_list),
                )
        }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Background,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    tr(StringsRes.string.thinking_guide),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    tr(StringsRes.string.small_tips_for_a_clearer_tree),
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                            tr(StringsRes.string.start),
                            tr(StringsRes.string.questions),
                            tr(StringsRes.string.chain),
                        )
                        .forEachIndexed { index, title ->
                            FilterChip(page == index, { page = index }, label = { Text(title) })
                        }
                }
            }
            if (board.template == "work" || board.template == "travel")
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            if (board.template == "work")
                                tr(StringsRes.string.for_work_start_with_the_outcome_and_what_done)
                            else
                                tr(
                                    StringsRes.string
                                        .for_travel_begin_with_dates_and_budget_then_documents
                                ),
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            guidance.forEach { (title, body) ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = Panel) {
                        Column(Modifier.padding(16.dp)) {
                            Text(title, style = MaterialTheme.typography.titleSmall, color = Teal)
                            Text(
                                body,
                                Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotePicker(notes: List<Note>, onDismiss: () -> Unit, onPick: (Note) -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(StringsRes.string.from_my_notes)) },
        text = {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    singleLine = true,
                    label = { Text(tr(StringsRes.string.search_notes)) },
                )
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    val available = notes.filter { !it.folder && it.title.contains(query, true) }
                    if (available.isEmpty())
                        Text(
                            tr(StringsRes.string.no_matching_notes_yet),
                            Modifier.padding(vertical = 20.dp),
                            color = Muted,
                        )
                    available.forEach { note ->
                        TextButton(
                            onClick = {
                                onPick(note)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(note.title, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(StringsRes.string.close)) } },
    )
}

@Composable
fun LinkDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(StringsRes.string.add_web_link)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text(tr(StringsRes.string.title)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    url,
                    {
                        url = it
                        error = false
                    },
                    label = { Text(tr(StringsRes.string.website_address)) },
                    singleLine = true,
                    isError = error,
                    supportingText = {
                        if (error)
                            Text(
                                tr(
                                    StringsRes.string
                                        .enter_a_website_address_such_as_https_example_com
                                )
                            )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val normalized =
                        if (url.contains("://")) url.trim() else "https://${url.trim()}"
                    val uri = android.net.Uri.parse(normalized)
                    if (
                        uri.scheme !in setOf("http", "https") ||
                            uri.host.isNullOrBlank() ||
                            uri.host?.contains('.') != true
                    )
                        error = true
                    else {
                        onSave(title.ifBlank { uri.host!! }, normalized)
                        onDismiss()
                    }
                }
            ) {
                Text(tr(StringsRes.string.add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(StringsRes.string.cancel)) } },
    )
}
