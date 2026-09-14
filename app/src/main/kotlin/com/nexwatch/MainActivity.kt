package com.nexwatch

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark-only app (CLAUDE.md): force dark status/nav bar icon styles regardless of
        // the system light/dark setting — enableEdgeToEdge()'s default auto() would follow it.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            WatchTheme {
                PremiumBackground {
                    AppRoot()
                }
            }
        }
    }
}
