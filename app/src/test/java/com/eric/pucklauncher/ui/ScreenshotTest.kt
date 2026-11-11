package com.eric.pucklauncher.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.eric.pucklauncher.UiState
import com.eric.pucklauncher.data.NhlApi
import com.eric.pucklauncher.data.OnHockeyParser
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.nio.charset.Charset
import java.time.Instant

/** Renders the real UI with the captured 2026-11-10 data on S24 Ultra-like and DeX-like screens (JVM, no emulator). */
@RunWith(Parameterized::class)
class ScreenshotTest(private val name: String, private val device: DeviceConfig, private val widthName: String) {
    companion object {
        // Galaxy S24 Ultra at its default FHD+ (1080x2340, ~450dpi -> 384dp x 832dp).
        private val S24U = DeviceConfig.PIXEL_6_PRO.copy(
            screenWidth = 1080, screenHeight = 2340, xdpi = 450, ydpi = 450, density = Density(450),
        )
        private val S24U_LAND = S24U.copy(screenWidth = 2340, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE)
        // DeX on a 1080p touch monitor (mdpi-ish -> 1920dp x 1080dp window).
        private val DEX = S24U.copy(screenWidth = 1920, screenHeight = 1080, xdpi = 160, ydpi = 160, density = Density(160), orientation = ScreenOrientation.LANDSCAPE)

        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun params() = listOf(
            arrayOf("phone", S24U, "compact"),
            arrayOf("phoneLandscape", S24U_LAND, "medium"),
            arrayOf("dex", DEX, "expanded"),
        )
    }

    @get:Rule val paparazzi = Paparazzi(deviceConfig = device, theme = "android:Theme.Material.NoActionBar", maxPercentDifference = 0.1)

    private val width = when (widthName) { "compact" -> WindowWidthSizeClass.Compact; "medium" -> WindowWidthSizeClass.Medium; else -> WindowWidthSizeClass.Expanded }
    private val now = Instant.parse("2026-11-11T00:51:00Z")
    private val games = NhlApi.merge(
        OnHockeyParser.parse(
            javaClass.getResourceAsStream("/schedule_table_cp1251.html")!!.readBytes().toString(Charset.forName("windows-1251")), now,
        ),
        NhlApi.parseScore(javaClass.getResourceAsStream("/nhl_score_2026-11-10.json")!!.readBytes().toString(Charsets.UTF_8)),
    )
    private val loaded = UiState(games = games, loadedOnce = true, updatedAt = now)

    private fun shot(state: UiState, selected: String? = null) = paparazzi.snapshot {
        PuckTheme { PuckScreen(state, width, {}, {}, { _, _ -> }, fixedNow = now, initialSelectedId = selected) }
    }

    @Test fun list() = shot(loaded)
    @Test fun gameSelected() = shot(loaded, selected = games.first { it.home?.abbrev == "TOR" }.id)
    @Test fun error() = shot(UiState(loadedOnce = true, error = "Unable to resolve host \"www.onhockey.tv\""))
    @Test fun empty() = shot(UiState(loadedOnce = true, updatedAt = now))
}
