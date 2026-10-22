package com.example.hackchaton_2026_crm.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// QuickLead palette (docs: QuickLead-视觉风格.md): deep navy and teal for structure, mint for actions.
val Navy = Color(0xFF0B1B33)
val Teal = Color(0xFF103B4B)
val Mint = Color(0xFF55DFC2)
val MintLight = Color(0xFFDDF8F1)
val FormBackground = Color(0xFFF8FAFC)

/** Mint is too pale for text on white (about 1.6:1), so links and outlines use this deeper teal. */
val LinkTeal = Color(0xFF0A7F73)

private val InkMuted = Color(0xFF4A5B6D)

private val LightColors = lightColorScheme(
    primary = LinkTeal,
    onPrimary = Color.White,
    primaryContainer = MintLight,
    onPrimaryContainer = Navy,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = MintLight,
    onSecondaryContainer = Navy,
    tertiary = Color(0xFF9A6200),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF1D6),
    onTertiaryContainer = Color(0xFF3B2600),
    background = FormBackground,
    onBackground = Navy,
    surface = Color.White,
    onSurface = Navy,
    surfaceVariant = Color(0xFFE9EFF4),
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFEEF3F7),
    surfaceContainerHigh = Color(0xFFE6EDF2),
    surfaceContainerHighest = Color(0xFFDDE6EE),
    outline = Color(0xFF7A8A9A),
    outlineVariant = Color(0xFFD5DEE6),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Mint,
    onPrimary = Navy,
    primaryContainer = Color(0xFF0F4A47),
    onPrimaryContainer = MintLight,
    secondary = Color(0xFF9FD8CB),
    onSecondary = Navy,
    secondaryContainer = Color(0xFF0F4A47),
    onSecondaryContainer = MintLight,
    tertiary = Color(0xFFFFCF7A),
    background = Color(0xFF0E2238),
    onBackground = Color(0xFFEAF2F7),
    surface = Color(0xFF0E2238),
    onSurface = Color(0xFFEAF2F7),
    surfaceVariant = Color(0xFF1B3552),
    onSurfaceVariant = Color(0xFFB7C7D6),
    surfaceContainerLow = Color(0xFF12294A),
    surfaceContainer = Color(0xFF15304F),
    surfaceContainerHigh = Color(0xFF1A3859),
    surfaceContainerHighest = Color(0xFF204363),
    outline = Color(0xFF6F8BA3),
    outlineVariant = Color(0xFF27425E),
)

/** Rounded inputs and cards, as in the wireframes. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().let {
    it.copy(
        headlineMedium = it.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = it.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = it.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = it.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

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
            shapes = AppShapes,
            typography = AppTypography,
            content = content,
        )
    }
}
