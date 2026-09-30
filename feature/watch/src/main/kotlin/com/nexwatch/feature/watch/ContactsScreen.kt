package com.nexwatch.feature.watch

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.watchapi.WatchContact
import com.nexwatch.core.watchapi.WatchSettingChange

@Composable
internal fun ContactsScreen(
    state: WatchSettingsUiState,
    onBack: () -> Unit,
    onSave: (List<WatchSettingChange>) -> Unit,
) {
    SettingsScaffold("Contacts", onBack, state.notice) {
        val contacts = state.settings?.contacts
        val limit = state.capabilities?.contactsLimit
        if (!state.isReady || contacts == null || limit == null) {
            NotConnectedNote()
            return@SettingsScaffold
        }
        var draft by remember(contacts) { mutableStateOf(contacts) }

        Text(
            "The watch keeps up to $limit contacts for calling from your wrist.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        draft.forEachIndexed { index, contact ->
            SettingsCard(contact.name.ifBlank { "New contact" }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = contact.name,
                        onValueChange = { name -> draft = draft.toMutableList().also { it[index] = contact.copy(name = name) } },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { draft = draft.toMutableList().also { it.removeAt(index) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${contact.name.ifBlank { "contact" }}")
                    }
                }
                OutlinedTextField(
                    value = contact.number,
                    onValueChange = { number -> draft = draft.toMutableList().also { it[index] = contact.copy(number = number) } },
                    label = { Text("Number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        SecondaryButton(
            text = "Add contact (${draft.size} of $limit)",
            onClick = { draft = draft + WatchContact(name = "", number = "") },
            enabled = draft.size < limit,
            modifier = Modifier.fillMaxWidth(),
        )
        SaveButton(
            onClick = { onSave(listOf(WatchSettingChange.SetContacts(draft))) },
            enabled = draft != contacts && draft.all { it.name.isNotBlank() && it.number.isNotBlank() },
            busy = state.isBusy,
        )
    }
}
