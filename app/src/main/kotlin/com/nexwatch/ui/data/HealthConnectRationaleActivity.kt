package com.nexwatch.ui.data

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nexwatch.core.designsystem.component.PrimaryButton
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme

/**
 * Health Connect opens this from its permission sheet ("why does this app need access?") and, on Android
 * 14+, from its settings, via the manifest's rationale intent filter and VIEW_PERMISSION_USAGE alias. It is
 * the app's privacy policy for Health Connect data, so it must exist for the permission request to work.
 */
class HealthConnectRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            WatchTheme {
                PremiumBackground {
                    Column(
                        modifier = Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Health Connect and NexWatch", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface)
                        listOf(
                            "NexWatch only writes to Health Connect. It copies the steps, distance, calories, heart " +
                                "rate, SpO2, blood pressure, sleep and workouts your watch recorded, so other health " +
                                "apps on this phone can use them.",
                            "It never reads other apps' data from Health Connect, and nothing is sent off this phone.",
                            "Your watch data stays in NexWatch's own storage too. Turning sync off stops new writes; " +
                                "what was already shared stays in Health Connect until you delete it there.",
                        ).forEach { Text(it, color = MaterialTheme.colorScheme.onSurface) }
                        PrimaryButton("Got it", onClick = ::finish)
                    }
                }
            }
        }
    }
}
