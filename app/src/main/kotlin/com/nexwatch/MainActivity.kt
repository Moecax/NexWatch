package com.nexwatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.navigation.NexWatchNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WatchTheme {
                PremiumBackground {
                    NexWatchNavHost()
                }
            }
        }
    }
}
