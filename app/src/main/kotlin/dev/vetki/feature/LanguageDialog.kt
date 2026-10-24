package dev.vetki.feature

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.vetki.i18n.Language
import dev.vetki.i18n.R as StringsRes
import dev.vetki.i18n.tr

@Composable
fun LanguageDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr(StringsRes.string.language_title)) },
        text = {
            Column {
                Text(
                    tr(StringsRes.string.language_help),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                listOf(
                        Language.SYSTEM to StringsRes.string.language_system,
                        "ru" to StringsRes.string.language_russian,
                        "en" to StringsRes.string.language_english,
                    )
                    .forEach { (tag, label) ->
                        Row(
                            Modifier.fillMaxWidth()
                                .selectable(
                                    selected == tag,
                                    role = Role.RadioButton,
                                    onClick = { onSelect(tag) },
                                )
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == tag, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Text(tr(label))
                        }
                    }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr(StringsRes.string.cancel)) } },
    )
}
