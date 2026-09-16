package com.nexwatch.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun WatchDebugScreen(
    modifier: Modifier = Modifier,
    viewModel: WatchDebugViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val useRealWatch by viewModel.useRealWatch.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Current state: $state",
            style = MaterialTheme.typography.titleMedium,
        )
        // §4.5 is otherwise invisible from the app: this is the only place the capability
        // read and the firmware string can be checked against a real watch.
        Text(
            text = capabilities?.let { "Firmware ${it.firmwareVersion} · $it" }
                ?: "Capabilities: not read yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (viewModel.showImplToggle) {
            Row(
                modifier = Modifier.padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (useRealWatch) {
                        "Real watch (restart the app to apply)"
                    } else {
                        "Fake watch (restart the app to apply)"
                    },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 8.dp))
                Switch(
                    checked = useRealWatch,
                    onCheckedChange = viewModel::setUseRealWatch,
                )
            }
        }
        LazyColumn(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(viewModel.forceableStates) { (label, target) ->
                Button(onClick = { viewModel.forceState(target) }) {
                    Text(label)
                }
            }
        }
    }
}
