package com.nexwatch

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.nexwatch.core.designsystem.component.PremiumBackground
import com.nexwatch.core.designsystem.theme.WatchTheme
import com.nexwatch.core.watchapi.WatchClient
import com.nexwatch.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var watchClient: WatchClient

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

    override fun onResume() {
        super.onResume()
        // §8.6: the SDK does not persist this across activity resumes on its own.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch { runCatching { watchClient.notifyPhoneStatePermissionGranted() } }
        }
    }
}
