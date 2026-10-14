package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.IconBadge
import com.nexwatch.core.designsystem.component.NexCard
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.SecondaryButton
import com.nexwatch.core.designsystem.component.StatusChip
import com.nexwatch.core.designsystem.component.StatusTone
import com.nexwatch.core.designsystem.component.StepProgressBar
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.feature.onboarding.BackgroundTest

/** design-prompt.md Batch 1 #7 — Keep it running. */
@Composable
fun KeepRunningScreen(
    batteryUnrestricted: Boolean,
    backgroundTest: BackgroundTest,
    onBatteryOptimization: () -> Unit,
    onAutostartHint: () -> Unit,
    onTestBackgroundConnection: () -> Unit,
    onDone: () -> Unit,
) {
    // The rows scroll and Done stays pinned below them, so a small screen or a large font can
    // never push the only way out of onboarding off the bottom.
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            EntranceItem(index = 0) {
                StepProgressBar(total = ONBOARDING_STEPS, current = 7, modifier = Modifier.padding(bottom = 16.dp))
            }
            EntranceItem(index = 1) {
                Text("Keep it running", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            EntranceItem(index = 2) {
                Text(
                    "Android may stop the app in the background. These settings keep the connection alive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                )
            }
            EntranceItem(index = 3) {
                NexCard(contentPadding = PaddingValues(0.dp)) {
                    KeepRunningRow(
                        icon = Icons.Filled.Bolt,
                        iconTint = WatchTheme.colors.success,
                        iconBackground = WatchTheme.colors.success.copy(alpha = 0.14f),
                        title = "Unrestricted battery use",
                        description = "Stops Android from pausing the connection.",
                        trailing = {
                            if (batteryUnrestricted) {
                                StatusChip("Allowed", StatusTone.SUCCESS)
                            } else {
                                SecondaryButton(
                                    text = "Allow",
                                    onClick = onBatteryOptimization,
                                    modifier = Modifier.compactButton(),
                                )
                            }
                        },
                    )
                    HorizontalDivider()
                    KeepRunningRow(
                        icon = Icons.Filled.RestartAlt,
                        title = "Autostart",
                        description = "Lets NexWatch start again after a restart.",
                        hint = "Tecno, Infinix, Xiaomi: Settings > Apps > NexWatch > Autostart > On.",
                        trailing = {
                            SecondaryButton(
                                text = "Enable",
                                onClick = onAutostartHint,
                                modifier = Modifier.compactButton(),
                            )
                        },
                    )
                    HorizontalDivider()
                    KeepRunningRow(
                        icon = Icons.Filled.NotificationsActive,
                        iconTint = WatchTheme.colors.success,
                        iconBackground = WatchTheme.colors.success.copy(alpha = 0.14f),
                        title = "Background connection",
                        description = "Runs a quick check with the app out of view.",
                        // Pulses only while a test is actually in flight; idle, it would claim a
                        // live state that doesn't exist.
                        leading = if (backgroundTest == BackgroundTest.Running) {
                            { LivePulseDot(color = WatchTheme.colors.success) }
                        } else {
                            null
                        },
                        trailing = {
                            if (backgroundTest == BackgroundTest.Running) {
                                StatusChip("Testing…", StatusTone.INFO)
                            } else {
                                SecondaryButton(
                                    text = if (backgroundTest == BackgroundTest.Idle) "Test" else "Test again",
                                    onClick = onTestBackgroundConnection,
                                    modifier = Modifier.compactButton(),
                                )
                            }
                        },
                    )
                }
            }
            when (backgroundTest) {
                BackgroundTest.Passed -> EntranceItem(index = 4, modifier = Modifier.padding(top = 12.dp)) {
                    StatusChip("Sent. Your watch should buzz now.", StatusTone.SUCCESS)
                }
                is BackgroundTest.Failed -> EntranceItem(index = 4, modifier = Modifier.padding(top = 12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusChip("Test failed", StatusTone.WARNING)
                        Text(
                            backgroundTest.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                BackgroundTest.Idle, BackgroundTest.Running -> Unit
            }
        }
        EntranceItem(index = 5, modifier = Modifier.padding(top = 16.dp)) {
            PrimaryButton(text = "Done", onClick = onDone)
        }
    }
}

@Composable
private fun KeepRunningRow(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    hint: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconBadge(icon = icon, tint = iconTint, background = iconBackground)
        Column(modifier = Modifier.weight(1f)) {
            if (leading != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    leading()
                    Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                }
            } else {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
            }
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        trailing()
    }
}

@Preview(showBackground = true)
@Composable
private fun KeepRunningScreenPreview() {
    WatchTheme {
        PremiumBackground {
            KeepRunningScreen(
                batteryUnrestricted = false,
                backgroundTest = BackgroundTest.Idle,
                onBatteryOptimization = {},
                onAutostartHint = {},
                onTestBackgroundConnection = {},
                onDone = {},
            )
        }
    }
}
