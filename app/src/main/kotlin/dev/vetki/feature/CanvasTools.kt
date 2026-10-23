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
            item("Добавить карточку", Icons.Outlined.CropLandscape, "text")
            item("Из моих заметок", Icons.Outlined.Description, "note")
            item("Изображение или файл", Icons.Outlined.AttachFile, "file")
            item("Добавить веб-ссылку", Icons.Outlined.OpenInNew, "link")
            item("Создать группу", Icons.Outlined.SelectAll, "group")
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            item("Вставить текст", Icons.Outlined.ContentPaste, "paste")
            HorizontalDivider(Modifier.padding(vertical = 5.dp))
            DropdownMenuItem(
                text = { Text("Привязка к сетке") },
                leadingIcon = { Icon(Icons.Outlined.GridOn, null, Modifier.size(20.dp)) },
                trailingIcon = { Checkbox(board.snapGrid, null) },
                enabled = !board.readOnly,
                onClick = {
                    onOption("grid", !board.snapGrid)
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text("Привязка к карточкам") },
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
                text = { Text("Только просмотр") },
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
                    "Начни с результата" to
                        "Одна главная карточка отвечает на вопрос: что я хочу понять или получить? Например: «Подготовить поездку без забытых дел».",
                    "Одна карточка — одна мысль" to
                        "Если в формулировке несколько самостоятельных вопросов, раздели их на соседние ветки.",
                    "От общего к конкретному" to
                        "Тема → направления → вопросы → решения → ближайшие действия. Не обязательно заполнять всё сразу.",
                    "Как управлять холстом" to
                        "Нажми на свободное место для меню. Удерживай карточку до отклика, затем двигай. Два пальца меняют масштаб; «Обзор» показывает всё дерево.",
                    "Используй группы" to
                        "Группа — общая рамка. Перемести карточки внутрь: при переносе рамки за заголовок они поедут вместе. При удалении рамки карточки сохранятся.",
                )
            1 ->
                listOf(
                    "Спроси конкретно" to
                        "Вместо «Что с работой?» — «Какие материалы нужны, чтобы закончить отчёт к пятнице?».",
                    "Уточни границы" to
                        "Добавь контекст: для кого, к какому сроку, с каким бюджетом и ограничениями. Вопрос станет проще исследовать.",
                    "Разделяй факты и предположения" to
                        "«Цена ещё неизвестна» — факт. «Наверное, будет дорого» — предположение. Для него нужна ветка «Как проверить?».",
                    "Спроси о следующем шаге" to
                        "Вместо «Как всё успеть?» — «Какое действие на 15 минут я могу сделать сейчас?».",
                    "Оставляй место неизвестному" to
                        "Не придумывай ответ ради заполненной карточки. Запиши, где узнать ответ и что именно проверить.",
                )
            else ->
                listOf(
                    "Построй цепочку" to
                        "Вопрос → возможные ответы → что проверить → решение → действие. Каждая стрелка должна объяснять, почему следующий шаг связан с предыдущим.",
                    "Проверяй развилки" to
                        "Если есть выбор, создай соседние варианты и сравни их по одинаковым критериям: время, цена, риск, результат.",
                    "Делай действия проверяемыми" to
                        "«Заняться жильём» → «Выбрать три варианта до четверга». Понятно, когда действие закончено.",
                    "Проверь дерево" to
                        "Нет ли одинаковых карточек? Не пропущен ли важный шаг? Есть ли ответственный и срок? Где пока только догадка?",
                    "Не усложняй ради схемы" to
                        "Если обычный список понятнее — переключись в список. Дерево помогает думать, а не требует идеальной формы.",
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
                Text("Помощник мысли", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Небольшие подсказки для ясного дерева",
                    color = Muted,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Начало", "Вопросы", "Цепочка").forEachIndexed { index, title ->
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
                                "Для работы: начни с результата и критерия готовности, затем уточни срок, ответственных и первый шаг."
                            else
                                "Для путешествия: сначала даты и бюджет, затем документы, жильё, дорога и запасной план.",
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
        title = { Text("Из моих заметок") },
        text = {
            Column {
                OutlinedTextField(
                    query,
                    { query = it },
                    singleLine = true,
                    label = { Text("Поиск заметки") },
                )
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                    val available = notes.filter { !it.folder && it.title.contains(query, true) }
                    if (available.isEmpty())
                        Text(
                            "Подходящих заметок пока нет",
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

@Composable
fun LinkDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить веб-ссылку") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                )
                OutlinedTextField(
                    url,
                    {
                        url = it
                        error = false
                    },
                    label = { Text("Адрес сайта") },
                    singleLine = true,
                    isError = error,
                    supportingText = {
                        if (error) Text("Введи адрес сайта, например https://example.com")
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
                Text("Добавить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
