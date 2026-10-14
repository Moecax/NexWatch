package com.nexwatch.feature.onboarding.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexwatch.core.designsystem.component.EntranceItem
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.StepProgressBar
import com.nexwatch.core.designsystem.component.WatchRender
import com.nexwatch.core.designsystem.theme.WatchTheme

/** design-prompt.md Batch 1 #1 — Welcome. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
    ) {
        EntranceItem(index = 0) {
            StepProgressBar(total = ONBOARDING_STEPS, current = 1, modifier = Modifier.padding(bottom = 24.dp))
        }
        EntranceItem(index = 1) { WatchRender(headSize = 176.dp, modifier = Modifier.padding(bottom = 40.dp)) }
        EntranceItem(index = 2) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NexWatch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = "Your watch.\nYour data. On your phone.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Text(
                    text = "Everything NexWatch reads stays on this device. Export it, or share it, on your terms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
        }
        EntranceItem(index = 3, modifier = Modifier.padding(top = 40.dp)) {
            PrimaryButton(text = "Get started", onClick = onGetStarted)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    WatchTheme { PremiumBackground { WelcomeScreen(onGetStarted = {}) } }
}
