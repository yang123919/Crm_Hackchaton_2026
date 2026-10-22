package com.example.hackchaton_2026_crm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// CS Group CRM palette: red brand colour on neutral greys.
val CsRed = Color(0xFFC8102E)
val CsRedDark = Color(0xFF9B0B22)
val CsRedLight = Color(0xFFFFB3B5)
val CsGrey = Color(0xFF4A4F55)
val CsGreyLight = Color(0xFFF2F3F5)
val CsGreyMid = Color(0xFFDADDE1)

private val LightColors = lightColorScheme(
    primary = CsRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD9),
    onPrimaryContainer = Color(0xFF410006),
    secondary = CsGrey,
    onSecondary = Color.White,
    secondaryContainer = CsGreyMid,
    onSecondaryContainer = Color(0xFF1D2024),
    tertiary = Color(0xFF6B5E00),
    background = Color.White,
    onBackground = Color(0xFF1D2024),
    surface = Color.White,
    onSurface = Color(0xFF1D2024),
    surfaceVariant = CsGreyLight,
    onSurfaceVariant = CsGrey,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FA),
    surfaceContainer = CsGreyLight,
    surfaceContainerHigh = Color(0xFFEBEDF0),
    surfaceContainerHighest = CsGreyMid,
    outline = Color(0xFF8A9097),
    outlineVariant = CsGreyMid,
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = CsRedLight,
    onPrimary = Color(0xFF680011),
    primaryContainer = CsRedDark,
    onPrimaryContainer = Color(0xFFFFDAD9),
    secondary = Color(0xFFC3C7CD),
    onSecondary = Color(0xFF2D3136),
    secondaryContainer = CsGrey,
    onSecondaryContainer = Color(0xFFE1E3E6),
    background = Color(0xFF16181B),
    surface = Color(0xFF16181B),
    surfaceVariant = Color(0xFF2D3136),
    onSurfaceVariant = Color(0xFFC3C7CD),
    surfaceContainer = Color(0xFF212428),
    surfaceContainerHigh = Color(0xFF2B2E33),
    outline = Color(0xFF8D9399),
    outlineVariant = Color(0xFF41464C),
)

/** Status colours used for follow-up urgency (not part of the M3 scheme). */
@Immutable
data class StatusColors(
    val overdue: Color,
    val overdueContainer: Color,
    val dueToday: Color,
    val dueTodayContainer: Color,
    val ok: Color,
    val okContainer: Color,
    val ai: Color,
)

private val LightStatus = StatusColors(
    overdue = Color(0xFFB3261E),
    overdueContainer = Color(0xFFFDE7E7),
    dueToday = Color(0xFF9A6200),
    dueTodayContainer = Color(0xFFFFF1D6),
    ok = Color(0xFF1E7B3A),
    okContainer = Color(0xFFE3F4E8),
    ai = Color(0xFF6A4BC4),
)

private val DarkStatus = StatusColors(
    overdue = Color(0xFFFFB4AB),
    overdueContainer = Color(0xFF5C1712),
    dueToday = Color(0xFFFFCF7A),
    dueTodayContainer = Color(0xFF4A3400),
    ok = Color(0xFF8EDBA3),
    okContainer = Color(0xFF113A1E),
    ai = Color(0xFFCBB8FF),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

@Composable
fun QuickLeadTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            content = content,
        )
    }
}
