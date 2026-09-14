package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.theme.WatchShapes
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.DiscoveredDevice

/**
 * design-prompt.md Batch 1 #5 — destructive-bind warning. CLAUDE.md §4.4: this is the
 * only screen in the app that leads to bind(); the checkbox gate is deliberate, not decoration.
 */
@Composable
fun PairConfirmScreen(
    device: DiscoveredDevice,
    understood: Boolean,
    onUnderstoodToggled: () -> Unit,
    onCancel: () -> Unit,
    onPair: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        EntranceItem(index = 0) {
            Text("Pair with ${device.displayName}?", style = MaterialTheme.typography.headlineSmall)
        }
        EntranceItem(index = 1) {
            Text(
                "This sets up the watch for this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        EntranceItem(index = 2, modifier = Modifier.padding(top = 20.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.tertiaryContainer, WatchShapes.card)
                    .padding(16.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Pairing as a new user clears the data currently stored on the watch.")
                        }
                        append(" Anything already on NexWatch stays right where it is.")
                    },
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Checkbox(checked = understood, onCheckedChange = { onUnderstoodToggled() })
                    Text("I understand", color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        EntranceItem(index = 3, modifier = Modifier.padding(top = 24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.weight(1f))
                PrimaryButton(text = "Pair watch", onClick = onPair, enabled = understood, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairConfirmScreenPreview() {
    WatchTheme {
        PremiumBackground {
            PairConfirmScreen(
                device = DiscoveredDevice("AA:BB:CC:DD:EE:FF", "GTR 3 Pro", 3),
                understood = false,
                onUnderstoodToggled = {},
                onCancel = {},
                onPair = {},
            )
        }
    }
}
