package com.eric.pucklauncher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ice = Color(0xFF8AD8FF)
val IceDeep = Color(0xFF1B3A5C)
val GoalRed = Color(0xFFFF3B3B)
val Rink = Color(0xFF070C16)
val Board = Color(0xFF111C2E)
val BoardHover = Color(0xFF1A2A44)
val Muted = Color(0xFF9FB3C8)

private val scheme = darkColorScheme(
    primary = Ice, onPrimary = Color(0xFF00141F),
    primaryContainer = IceDeep, onPrimaryContainer = Color(0xFFDDF3FF),
    secondary = GoalRed, onSecondary = Color.White,
    secondaryContainer = Color(0xFF3A1820), onSecondaryContainer = Color(0xFFFFDADA),
    background = Rink, onBackground = Color(0xFFEAF2FA),
    surface = Rink, onSurface = Color(0xFFEAF2FA),
    surfaceVariant = Board, onSurfaceVariant = Muted,
    surfaceContainer = Board, surfaceContainerHigh = BoardHover, surfaceContainerHighest = BoardHover,
    outline = Color(0xFF3B5272),
)

@Composable
fun PuckTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
