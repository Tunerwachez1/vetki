package dev.vetki.data

import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr

suspend fun VetkiDao.createBoard(title: String, template: String): Board {
    val board = Board(title = title, template = template)
    save(board)
    if (template == "empty" || template == "custom") return board
    val root =
        Card(
            boardId = board.id,
            title = title,
            body = tr(StringsRes.string.main_idea_and_goal),
            x = 0f,
            y = 0f,
        )
    save(root)
    val branches =
        when (template) {
            "travel" ->
                listOf(
                    tr(StringsRes.string.dates_and_purpose) to
                        tr(StringsRes.string.when_are_we_going_for_how_many_days_what),
                    tr(StringsRes.string.documents) to
                        tr(
                            StringsRes.string
                                .passport_entry_requirements_and_insurance_where_should_we_keep
                        ),
                    tr(StringsRes.string.accommodation) to
                        tr(StringsRes.string.which_area_what_is_the_budget_what_needs_booking),
                    tr(StringsRes.string.transport_and_connectivity) to
                        tr(StringsRes.string.how_will_we_get_there_airport_transfer_sim_or),
                    tr(StringsRes.string.budget) to
                        tr(StringsRes.string.tickets_accommodation_and_food_how_much_reserve_do_we),
                    tr(StringsRes.string.packing) to
                        tr(StringsRes.string.clothes_medicine_and_chargers_what_should_we_bring),
                )
            "project" ->
                listOf(
                    tr(StringsRes.string.goal) to tr(StringsRes.string.what_outcome_do_we_need),
                    tr(StringsRes.string.milestones) to tr(StringsRes.string.where_should_we_start),
                    tr(StringsRes.string.tasks) to tr(StringsRes.string.what_needs_to_be_done),
                    tr(StringsRes.string.questions) to tr(StringsRes.string.what_is_still_unknown),
                )
            "work" ->
                listOf(
                    tr(StringsRes.string.outcome) to
                        tr(StringsRes.string.what_should_the_result_be_how_will_we_know),
                    tr(StringsRes.string.starting_information) to
                        tr(StringsRes.string.what_do_we_already_know_where_are_the_materials),
                    tr(StringsRes.string.constraints) to
                        tr(StringsRes.string.what_are_the_deadline_budget_and_available_resources),
                    tr(StringsRes.string.stages_and_actions) to
                        tr(StringsRes.string.what_steps_can_we_break_this_work_into),
                    tr(StringsRes.string.owners) to
                        tr(StringsRes.string.who_does_the_work_who_helps_and_who_accepts),
                    tr(StringsRes.string.risks_and_questions) to
                        tr(StringsRes.string.what_could_get_in_the_way_what_needs_clarification),
                    tr(StringsRes.string.next_step) to
                        tr(StringsRes.string.what_specific_action_can_we_take_today),
                )
            "decision" ->
                listOf(
                    tr(StringsRes.string.options) to
                        tr(StringsRes.string.what_possibilities_do_we_have),
                    tr(StringsRes.string.benefits) to tr(StringsRes.string.what_are_the_advantages),
                    tr(StringsRes.string.risks) to tr(StringsRes.string.what_could_go_wrong),
                    tr(StringsRes.string.next_action) to tr(StringsRes.string.what_can_we_do_today),
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
    require(cards.isNotEmpty()) { tr(StringsRes.string.add_at_least_one_card) }
    return BoardTemplate(
            title = title.trim().ifBlank { board.title },
            payload = CardJson.array(cards).toString(),
        )
        .also { save(it) }
}

suspend fun VetkiDao.fromTemplate(title: String, templateId: String): Board {
    val template = requireNotNull(template(templateId)) { tr(StringsRes.string.template_not_found) }
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
