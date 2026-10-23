package dev.vetki.data

suspend fun VetkiDao.createBoard(title: String, template: String): Board {
    val board = Board(title = title, template = template)
    save(board)
    if (template == "empty" || template == "custom") return board
    val root = Card(boardId = board.id, title = title, body = "Главная идея и цель", x = 0f, y = 0f)
    save(root)
    val branches =
        when (template) {
            "travel" ->
                listOf(
                    "Даты и цель" to "Когда едем? На сколько дней? Что хочется увидеть?",
                    "Документы" to "Паспорт, условия въезда, страховка. Где сохранить копии?",
                    "Жильё" to "Какой район? Какой бюджет? Что нужно забронировать?",
                    "Транспорт и связь" to "Как добраться? Трансфер? SIM или eSIM?",
                    "Бюджет" to "Билеты, жильё, еда. Какой нужен резерв?",
                    "Вещи" to "Одежда, лекарства, зарядки. Что взять с собой?",
                )
            "project" ->
                listOf(
                    "Цель" to "Какой результат нужен?",
                    "Этапы" to "С чего начать?",
                    "Задачи" to "Что нужно сделать?",
                    "Вопросы" to "Что пока неизвестно?",
                )
            "work" ->
                listOf(
                    "Результат" to "Что должно получиться? Как понять, что работа завершена?",
                    "Исходные данные" to "Что уже известно? Где материалы и требования?",
                    "Ограничения" to "Какой срок, бюджет и доступные ресурсы?",
                    "Этапы и действия" to "На какие шаги разделить работу?",
                    "Ответственные" to "Кто делает, кто помогает, кто принимает результат?",
                    "Риски и вопросы" to "Что может помешать? Что нужно уточнить?",
                    "Ближайший шаг" to "Какое конкретное действие можно сделать сегодня?",
                )
            "decision" ->
                listOf(
                    "Варианты" to "Какие есть возможности?",
                    "Плюсы" to "Какие преимущества?",
                    "Риски" to "Что может пойти не так?",
                    "Следующий шаг" to "Что можно сделать сегодня?",
                )
            else -> emptyList()
        }
    branches.forEachIndexed { i, (name, body) ->
        val side = if (i % 2 == 0) -1 else 1
        save(
            Card(
                boardId = board.id,
                parentId = root.id,
                title = name,
                body = body,
                x = side * 250f,
                y = (i / 2) * 160f + 180f,
                status = i % 4,
            )
        )
    }
    return board
}

suspend fun VetkiDao.saveAsTemplate(board: Board, title: String): BoardTemplate {
    val cards = allCards().filter { it.boardId == board.id }
    require(cards.isNotEmpty()) { "Добавь хотя бы одну карточку" }
    return BoardTemplate(
            title = title.trim().ifBlank { board.title },
            payload = CardJson.array(cards).toString(),
        )
        .also { save(it) }
}

suspend fun VetkiDao.fromTemplate(title: String, templateId: String): Board {
    val template = requireNotNull(template(templateId)) { "Шаблон не найден" }
    val cards = CardJson.list(template.payload)
    val ids = cards.associate { it.id to java.util.UUID.randomUUID().toString() }
    val board =
        Board(title = title.trim().ifBlank { template.title }, template = "saved:$templateId")
    save(board)
    cards.forEach { c ->
        save(
            c.copy(
                id = ids.getValue(c.id),
                boardId = board.id,
                parentId = ids[c.parentId],
                groupId = ids[c.groupId],
                connections =
                    CardJson.connectionsJson(
                        CardJson.connections(c.connections).mapNotNull { link ->
                            ids[link.targetId]?.let { link.copy(targetId = it) }
                        }
                    ),
                done = false,
                status = if (c.done || c.status == 3) 0 else c.status,
            )
        )
    }
    return board
}
