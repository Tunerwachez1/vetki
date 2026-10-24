package dev.vetki.feature

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import dev.vetki.core.Calculator
import dev.vetki.data.*
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.Strings
import dev.vetki.i18n.tr
import dev.vetki.ui.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat

/** The tool list can grow without adding more navigation destinations. */
private enum class Tool {
    CALCULATOR
}

@Composable
fun ToolsScreen(onCalculator: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(tr(StringsRes.string.tools_intro), color = Muted)
        Tool.entries.forEach { tool ->
            when (tool) {
                Tool.CALCULATOR ->
                    Surface(
                        onClick = onCalculator,
                        shape = RoundedCornerShape(20.dp),
                        color = Panel,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Outlined.Calculate,
                                null,
                                tint = Teal,
                                modifier = Modifier.size(32.dp),
                            )
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(
                                    tr(StringsRes.string.calculator),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Text(
                                    tr(StringsRes.string.calculator_description),
                                    color = Muted,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Icon(Icons.Outlined.ChevronRight, null)
                        }
                    }
            }
        }
    }
}

private fun decimal(value: BigDecimal, money: Boolean = false): String =
    NumberFormat.getNumberInstance(Strings.locale)
        .apply {
            maximumFractionDigits = if (money) 2 else 10
            minimumFractionDigits = if (money) 2 else 0
            roundingMode = RoundingMode.HALF_UP
        }
        .format(value)

private data class CalculationResult(
    val answer: String,
    val details: List<Pair<String, String>>,
    val summary: String,
)

private fun calculate(d: CalculationDraft): CalculationResult {
    fun n(s: String) = Calculator.number(s)
    fun optional(s: String) = if (s.isBlank()) BigDecimal.ZERO else n(s)
    return when (d.mode) {
        "percent" -> {
            val base = n(d.base)
            val rate = n(d.rate)
            val value =
                when (d.operation) {
                    "discount" -> Calculator.discount(base, rate)
                    "increase" -> base + Calculator.percent(base, rate)
                    "ratio" -> Calculator.ratio(base, rate)
                    else -> Calculator.percent(base, rate)
                }
            val label =
                tr(
                    when (d.operation) {
                        "discount" -> StringsRes.string.calc_discount
                        "increase" -> StringsRes.string.calc_increase
                        "ratio" -> StringsRes.string.calc_ratio
                        else -> StringsRes.string.calc_percent_amount
                    }
                )
            val answer = decimal(value) + if (d.operation == "ratio") " %" else ""
            val detail =
                if (d.operation == "discount")
                    listOf(tr(StringsRes.string.calc_saved) to decimal(base - value))
                else emptyList()
            CalculationResult(
                answer,
                detail,
                "$label: ${decimal(base)}, ${decimal(rate)}\n= $answer",
            )
        }
        "salary" -> {
            val pay =
                Calculator.salary(
                    n(d.pay),
                    n(d.worked),
                    if (d.monthly) n(d.planned) else null,
                    optional(d.bonus),
                    optional(d.deduction),
                )
            val answer = decimal(pay.net, true)
            val details =
                listOf(
                    tr(StringsRes.string.calc_gross) to decimal(pay.gross, true),
                    tr(StringsRes.string.calc_deduction_total) to decimal(pay.deduction, true),
                )
            CalculationResult(
                answer,
                details,
                tr(StringsRes.string.calc_salary) +
                    "\n" +
                    details.joinToString("\n") { "${it.first}: ${it.second}" } +
                    "\n" +
                    tr(StringsRes.string.calc_net) +
                    ": $answer",
            )
        }
        else -> {
            val answer = decimal(Calculator.expression(d.expression))
            CalculationResult(answer, emptyList(), "${d.expression}\n= $answer")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorSheet(
    sessionKey: String,
    initial: String?,
    onDismiss: () -> Unit,
    onStoreDraft: (String) -> Unit,
    card: Card? = null,
    readOnly: Boolean = false,
    onSaveCard: (Card) -> Unit = {},
    onCardSettings: () -> Unit = {},
) {
    var payload by rememberSaveable(sessionKey) { mutableStateOf(initial) }
    var title by rememberSaveable(sessionKey) { mutableStateOf(card?.title.orEmpty()) }
    val draft =
        remember(payload) {
            runCatching { CalculationJson.decode(payload) }.getOrDefault(CalculationDraft())
        }
    var showError by rememberSaveable(sessionKey) { mutableStateOf(false) }
    val outcome = remember(draft, Strings.locale) { runCatching { calculate(draft) } }
    val result = outcome.getOrNull()
    fun update(next: CalculationDraft) {
        payload = CalculationJson.encode(next)
        showError = false
    }
    fun store() {
        val encoded = CalculationJson.encode(draft)
        onStoreDraft(encoded)
        if (card != null && !readOnly)
            onSaveCard(
                card.copy(
                    title = title.trim().ifBlank { tr(StringsRes.string.calculator) },
                    calculation = encoded,
                    body =
                        result?.let { "= ${it.answer}\n${it.summary}" }
                            ?: tr(StringsRes.string.calc_unfinished),
                )
            )
    }
    fun close() {
        store()
        onDismiss()
    }
    ModalBottomSheet(
        onDismissRequest = ::close,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Background,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tr(StringsRes.string.calculator),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (card != null)
                    IconButton(
                        onClick = {
                            store()
                            onCardSettings()
                        }
                    ) {
                        Icon(Icons.Outlined.MoreHoriz, tr(StringsRes.string.calc_card_settings))
                    }
                IconButton(onClick = ::close) {
                    Icon(Icons.Outlined.Close, tr(StringsRes.string.close))
                }
            }
            if (card != null)
                OutlinedTextField(
                    title,
                    { title = it.take(200) },
                    Modifier.fillMaxWidth(),
                    label = { Text(tr(StringsRes.string.title)) },
                    singleLine = true,
                    readOnly = readOnly,
                )
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                        "basic" to StringsRes.string.calc_basic,
                        "percent" to StringsRes.string.calc_percent,
                        "salary" to StringsRes.string.calc_salary,
                    )
                    .forEach { (mode, label) ->
                        FilterChip(
                            draft.mode == mode,
                            { update(draft.copy(mode = mode)) },
                            enabled = !readOnly,
                            label = { Text(tr(label)) },
                        )
                    }
            }
            when (draft.mode) {
                "basic" ->
                    BasicCalculatorInput(
                        draft.expression,
                        !readOnly,
                        { update(draft.copy(expression = it)) },
                        { showError = true },
                        { CalculationResultPanel(result, false) },
                    )
                "percent" -> {
                    var menu by remember { mutableStateOf(false) }
                    val options =
                        listOf(
                            "amount" to StringsRes.string.calc_percent_amount,
                            "discount" to StringsRes.string.calc_discount,
                            "increase" to StringsRes.string.calc_increase,
                            "ratio" to StringsRes.string.calc_ratio,
                        )
                    Box {
                        OutlinedButton(
                            onClick = { menu = true },
                            enabled = !readOnly,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                tr(options.first { it.first == draft.operation }.second),
                                Modifier.weight(1f),
                            )
                            Icon(Icons.Outlined.ExpandMore, null)
                        }
                        DropdownMenu(menu, { menu = false }) {
                            options.forEach { (id, label) ->
                                DropdownMenuItem(
                                    text = { Text(tr(label)) },
                                    onClick = {
                                        update(draft.copy(operation = id))
                                        menu = false
                                    },
                                )
                            }
                        }
                    }
                    NumberField(
                        draft.base,
                        if (draft.operation == "ratio") StringsRes.string.calc_part
                        else StringsRes.string.calc_base,
                        !readOnly,
                    ) {
                        update(draft.copy(base = it))
                    }
                    NumberField(
                        draft.rate,
                        if (draft.operation == "ratio") StringsRes.string.calc_whole
                        else StringsRes.string.calc_percent,
                        !readOnly,
                    ) {
                        update(draft.copy(rate = it))
                    }
                }
                "salary" -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            !draft.monthly,
                            { update(draft.copy(monthly = false)) },
                            enabled = !readOnly,
                            label = { Text(tr(StringsRes.string.calc_hourly)) },
                        )
                        FilterChip(
                            draft.monthly,
                            { update(draft.copy(monthly = true)) },
                            enabled = !readOnly,
                            label = { Text(tr(StringsRes.string.calc_monthly)) },
                        )
                    }
                    NumberField(
                        draft.pay,
                        if (draft.monthly) StringsRes.string.calc_month_pay
                        else StringsRes.string.calc_hour_rate,
                        !readOnly,
                    ) {
                        update(draft.copy(pay = it))
                    }
                    NumberField(
                        draft.worked,
                        if (draft.monthly) StringsRes.string.calc_days_worked
                        else StringsRes.string.calc_hours,
                        !readOnly,
                    ) {
                        update(draft.copy(worked = it))
                    }
                    if (draft.monthly)
                        NumberField(draft.planned, StringsRes.string.calc_days_planned, !readOnly) {
                            update(draft.copy(planned = it))
                        }
                    NumberField(draft.bonus, StringsRes.string.calc_bonus, !readOnly) {
                        update(draft.copy(bonus = it))
                    }
                    NumberField(draft.deduction, StringsRes.string.calc_deduction, !readOnly) {
                        update(draft.copy(deduction = it))
                    }
                    Text(
                        tr(StringsRes.string.calc_salary_hint),
                        color = Muted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (draft.mode != "basic") CalculationResultPanel(result, draft.mode == "salary")
            if (showError && result == null)
                Text(
                    tr(
                        if (outcome.exceptionOrNull() is ArithmeticException)
                            StringsRes.string.calc_division_zero
                        else
                            when (draft.mode) {
                                "salary" -> StringsRes.string.calc_invalid_salary
                                "percent" -> StringsRes.string.calc_invalid_percent
                                else -> StringsRes.string.calc_invalid
                            }
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            if (draft.mode != "basic" && !readOnly)
                OutlinedButton(onClick = { showError = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(tr(StringsRes.string.calc_calculate))
                }
            Button(onClick = ::close, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(
                    tr(
                        if (card != null && !readOnly) StringsRes.string.save
                        else StringsRes.string.close
                    )
                )
            }
        }
    }
}

@Composable
private fun NumberField(value: String, label: Int, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value,
        { if (it.length <= 80) onChange(it) },
        Modifier.fillMaxWidth(),
        label = { Text(tr(label)) },
        singleLine = true,
        readOnly = !enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun BasicCalculatorInput(
    expression: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
    onEquals: () -> Unit,
    result: @Composable () -> Unit,
) {
    var field by
        rememberSaveable(stateSaver = TextFieldValue.Saver) {
            mutableStateOf(TextFieldValue(expression, TextRange(expression.length)))
        }
    fun change(next: TextFieldValue) {
        if (next.text.length <= 512) {
            field = next
            onChange(next.text)
        }
    }
    OutlinedTextField(
        field,
        ::change,
        Modifier.fillMaxWidth(),
        label = { Text(tr(StringsRes.string.calc_expression)) },
        readOnly = !enabled,
        maxLines = 3,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
    )
    result()
    Text(
        tr(StringsRes.string.calc_percent_hint),
        color = Muted,
        style = MaterialTheme.typography.bodySmall,
    )
    listOf(
            listOf("(", ")", "⌫", "C"),
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf("%", "0", ".", "+"),
        )
        .forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = {
                            val start = field.selection.min
                            val end = field.selection.max
                            when (key) {
                                "C" -> change(TextFieldValue(""))
                                "⌫" -> {
                                    val from =
                                        if (start == end) (start - 1).coerceAtLeast(0) else start
                                    change(
                                        TextFieldValue(
                                            field.text.removeRange(from, end),
                                            TextRange(from),
                                        )
                                    )
                                }
                                else ->
                                    change(
                                        TextFieldValue(
                                            field.text.replaceRange(start, end, key),
                                            TextRange(start + key.length),
                                        )
                                    )
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = enabled,
                        contentPadding = PaddingValues(4.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(key, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    Button(onClick = onEquals, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text("=", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun CalculationResultPanel(result: CalculationResult?, salary: Boolean) {
    val clipboard = LocalClipboardManager.current
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                tr(if (salary) StringsRes.string.calc_net else StringsRes.string.calc_result),
                color = Muted,
                style = MaterialTheme.typography.labelMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                SelectionContainer(Modifier.weight(1f)) {
                    Text(
                        result?.answer ?: "—",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Teal,
                    )
                }
                IconButton(
                    onClick = { result?.let { clipboard.setText(AnnotatedString(it.answer)) } },
                    enabled = result != null,
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        tr(StringsRes.string.calc_copy),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            result?.details?.forEach { (label, value) ->
                Row {
                    Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
