package com.eric.pucklauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.eric.pucklauncher.ui.PuckApp
import com.eric.pucklauncher.ui.PuckTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration

class MainActivity : ComponentActivity() {
    private val vm: ScheduleViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Auto refresh about every 5 minutes while the app is visible (also refreshes on first show).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    vm.refreshIfStale(Duration.ofMinutes(5))
                    delay(20_000)
                }
            }
        }
        setContent {
            val size = calculateWindowSizeClass(this)
            PuckTheme {
                PuckApp(
                    vm = vm,
                    widthClass = size.widthSizeClass,
                    onOpenBrowser = { openInBrowser(it.url) },
                    onPlayInApp = { game, stream ->
                        startActivity(PlayerActivity.intent(this, stream, "${game.awayCity} @ ${game.homeCity}"))
                    },
                )
            }
        }
    }
}
