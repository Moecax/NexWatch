package com.nexwatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nexwatch.core.designsystem.theme.WatchTheme

/**
 * Stands in for the real per-tab screens until Phase 2 (onboarding) and the
 * later feature phases replace them one tab at a time.
 */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = title)
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    WatchTheme {
        PlaceholderScreen(title = "Today")
    }
}
