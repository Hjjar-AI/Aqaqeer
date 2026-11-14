package com.aqaqeer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aqaqeer.app.R
import com.aqaqeer.app.data.ThemeChoice
import com.aqaqeer.app.data.ThemeAccent

internal val MedicationHeadingFont = FontFamily(
    Font(R.font.noto_naskh_arabic_regular, FontWeight.Normal),
    Font(R.font.noto_naskh_arabic_bold, FontWeight.Bold),
)
private val ArabicFont = FontFamily(
    Font(R.font.noto_sans_arabic, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.noto_sans_arabic, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.noto_sans_arabic, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.noto_sans_arabic, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
private val BaseTypography = Typography()
private val AppShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
)
private val ArabicTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = ArabicFont),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = ArabicFont),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = ArabicFont),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = ArabicFont),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = ArabicFont),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = ArabicFont),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = ArabicFont),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = ArabicFont),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = ArabicFont),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = ArabicFont, lineHeight = 26.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = ArabicFont, lineHeight = 23.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = ArabicFont, fontSize = 14.sp, lineHeight = 23.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = ArabicFont, lineHeight = 23.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = ArabicFont, fontSize = 14.sp, lineHeight = 23.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = ArabicFont, fontSize = 12.sp, lineHeight = 20.sp),
)
private val LightColors = lightColorScheme(
    primary = Color(0xFF7A5A12), onPrimary = Color.White,
    primaryContainer = Color(0xFFF5E7BE), onPrimaryContainer = Color(0xFF3B2A06),
    secondary = Color(0xFF6D613E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFE8D5), onSecondaryContainer = Color(0xFF302B1B),
    tertiary = Color(0xFF92400E), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFEF3C7), onTertiaryContainer = Color(0xFF78350F),
    surfaceTint = Color(0xFF7A5A12),
    surfaceContainerLowest = Color(0xFFFFFEFA), surfaceContainerLow = Color(0xFFF8F4E9),
    surfaceContainer = Color(0xFFF3EFE4), surfaceContainerHigh = Color(0xFFEDE7D8),
    surfaceContainerHighest = Color(0xFFE7DFCE),
    background = Color(0xFFFAF8F1), onBackground = Color(0xFF171914),
    surface = Color(0xFFFFFEFA), onSurface = Color(0xFF171914),
    surfaceVariant = Color(0xFFF3EFE4), onSurfaceVariant = Color(0xFF625C4C),
    outline = Color(0xFF7C725C), outlineVariant = Color(0xFFDED7C5),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8C66D), onPrimary = Color(0xFF302200),
    primaryContainer = Color(0xFF3B2A06), onPrimaryContainer = Color(0xFFF5E7BE),
    secondary = Color(0xFFD5C7A0), onSecondary = Color(0xFF302B1B),
    secondaryContainer = Color(0xFF4B432D), onSecondaryContainer = Color(0xFFF1E8CB),
    tertiary = Color(0xFFF6CD84), onTertiary = Color(0xFF452000),
    tertiaryContainer = Color(0xFF78350F), onTertiaryContainer = Color(0xFFFEF3C7),
    surfaceTint = Color(0xFFE8C66D),
    surfaceContainerLowest = Color(0xFF12140F), surfaceContainerLow = Color(0xFF1D1F18),
    surfaceContainer = Color(0xFF24251E), surfaceContainerHigh = Color(0xFF2D2F25),
    surfaceContainerHighest = Color(0xFF37392E),
    background = launchColor(R.color.launch_dark), onBackground = Color(0xFFF3EFE4),
    surface = Color(0xFF24251E), onSurface = Color(0xFFF3EFE4),
    surfaceVariant = Color(0xFF3B3C31), onSurfaceVariant = Color(0xFFD5D1C3),
    outline = Color(0xFFA9A18C), outlineVariant = Color(0xFF3B3C31),
)

private data class AccentPalette(val primary: Color, val container: Color, val ink: Color, val darkPrimary: Color)
private val AccentPalettes = mapOf(
    ThemeAccent.BLUE to AccentPalette(Color(0xFF00658A), Color(0xFFCBF0FF), Color(0xFF002F40), Color(0xFF66D9F2)),
    ThemeAccent.GREEN to AccentPalette(Color(0xFF006B3C), Color(0xFFB9F6CE), Color(0xFF00391D), Color(0xFF5DE5A0)),
    ThemeAccent.PURPLE to AccentPalette(Color(0xFF8E247A), Color(0xFFFFD6F5), Color(0xFF49003D), Color(0xFFF3A7ED)),
    ThemeAccent.CRIMSON to AccentPalette(Color(0xFFC00038), Color(0xFFFFD9E3), Color(0xFF4A0014), Color(0xFFFF7096)),
)

private fun accentColors(accent: ThemeAccent, dark: Boolean): ColorScheme {
    val base = if (dark) DarkColors else LightColors
    val palette = AccentPalettes[accent] ?: return base
    val colors = base.copy(
        primary = if (dark) palette.darkPrimary else palette.primary,
        onPrimary = if (dark) palette.ink else Color.White,
        primaryContainer = if (dark) palette.primary else palette.container,
        onPrimaryContainer = if (dark) palette.container else palette.ink,
        secondary = if (dark) palette.darkPrimary else palette.primary,
        onSecondary = if (dark) palette.ink else Color.White,
        secondaryContainer = if (dark) palette.primary else palette.container,
        onSecondaryContainer = if (dark) palette.container else palette.ink,
        inversePrimary = if (dark) palette.primary else palette.darkPrimary,
        surfaceTint = if (dark) palette.darkPrimary else palette.primary,
    )
    if (accent != ThemeAccent.CRIMSON) return colors
    val crimson = if (dark) colors.copy(
        background = launchColor(R.color.launch_crimson_dark), onBackground = Color(0xFFFFEFF5),
        surface = Color(0xFF1C1016), onSurface = Color(0xFFFFEFF5),
        surfaceDim = Color(0xFF12090D), surfaceBright = Color(0xFF35252D),
        surfaceContainerLowest = Color(0xFF0C0508), surfaceContainerLow = Color(0xFF1C1016),
        surfaceContainer = Color(0xFF25171E), surfaceContainerHigh = Color(0xFF30212A),
        surfaceContainerHighest = Color(0xFF3B2A34),
        surfaceVariant = Color(0xFF3B2A34), onSurfaceVariant = Color(0xFFE6BFCF),
        outline = Color(0xFFB48C9D), outlineVariant = Color(0xFF59404D),
    ) else colors.copy(
        background = Color(0xFFFFF8FA), onBackground = Color(0xFF211019),
        surface = Color(0xFFFFFBFC), onSurface = Color(0xFF211019),
        surfaceDim = Color(0xFFE8CDD9), surfaceBright = Color(0xFFFFFBFC),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBEFF3),
        surfaceContainer = Color(0xFFF5E4EA), surfaceContainerHigh = Color(0xFFEED8E1),
        surfaceContainerHighest = Color(0xFFE8CDD9),
        surfaceVariant = Color(0xFFF5E4EA), onSurfaceVariant = Color(0xFF674953),
        outline = Color(0xFF8E6575), outlineVariant = Color(0xFFE5C5D3),
    )
    return crimson.copy(tertiary = crimson.primary, onTertiary = crimson.onPrimary,
        tertiaryContainer = crimson.primaryContainer, onTertiaryContainer = crimson.onPrimaryContainer)
}

/** Neutral surfaces stay truly black, including elevated sheets/dialogs; accents remain legible. */
private fun amoledColors(colors: ColorScheme): ColorScheme = colors.copy(
    background = Color.Black, onBackground = Color(0xFFF1F1F1),
    surface = Color.Black, onSurface = Color(0xFFF1F1F1),
    surfaceDim = Color.Black, surfaceBright = Color.Black, surfaceVariant = Color.Black,
    surfaceContainerLowest = Color.Black, surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black, surfaceContainerHigh = Color.Black, surfaceContainerHighest = Color.Black,
    onSurfaceVariant = Color(0xFFC7C7C7), surfaceTint = Color.Transparent,
    outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFF444444),
    inverseSurface = Color.Black, inverseOnSurface = Color(0xFFF1F1F1), inversePrimary = colors.primary,
)

internal data class StatusTone(val container: Color, val content: Color, val edge: Color)
internal data class StatusColors(val available: StatusTone, val low: StatusTone, val out: StatusTone, val unknown: StatusTone)
private val LightStatusColors = StatusColors(
    StatusTone(Color(0xFFDCFCE7), Color(0xFF166534), Color(0xFF15803D)), StatusTone(Color(0xFFFEF3C7), Color(0xFF92400E), Color(0xFFD97706)),
    StatusTone(Color(0xFFFEE2E2), Color(0xFF991B1B), Color(0xFFB91C1C)), StatusTone(Color(0xFFE2E8F0), Color(0xFF334155), Color(0xFF64748B)),
)
private val DarkStatusColors = StatusColors(
    StatusTone(Color(0xFF14532D), Color(0xFFDCFCE7), Color(0xFF4ADE80)), StatusTone(Color(0xFF78350F), Color(0xFFFEF3C7), Color(0xFFFBBF24)),
    StatusTone(Color(0xFF7F1D1D), Color(0xFFFEE2E2), Color(0xFFF87171)), StatusTone(Color(0xFF334155), Color(0xFFF1F5F9), Color(0xFF94A3B8)),
)
internal val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

internal fun themeIsDark(choice: ThemeChoice, systemDark: Boolean): Boolean =
    choice == ThemeChoice.DARK || choice == ThemeChoice.AMOLED || (choice == ThemeChoice.SYSTEM && systemDark)

/** Near-white backgrounds keep a faint accent tint; cards remain white. */
private fun launchColor(id: Int): Color = Color(com.aqaqeer.app.AppStrings.resources.getColor(id, null))
internal fun lightBackground(accent: ThemeAccent): Color = launchColor(when (accent) {
    ThemeAccent.GOLD -> R.color.launch_gold
    ThemeAccent.BLUE -> R.color.launch_blue
    ThemeAccent.GREEN -> R.color.launch_green
    ThemeAccent.PURPLE -> R.color.launch_pink
    ThemeAccent.CRIMSON -> R.color.launch_crimson
})

private fun lightModeColors(colors: ColorScheme, accent: ThemeAccent): ColorScheme = colors.copy(
    background = lightBackground(accent), surface = Color.White, surfaceBright = Color.White,
    surfaceDim = lerp(Color.White, colors.primary, 0.10f),
    surfaceContainerLowest = Color.White, surfaceContainerLow = lightBackground(accent),
    surfaceContainer = lerp(Color.White, colors.primary, 0.035f),
    surfaceContainerHigh = lerp(Color.White, colors.primary, 0.05f),
    surfaceContainerHighest = lerp(Color.White, colors.primary, 0.07f),
    surfaceVariant = lerp(Color.White, colors.primary, 0.05f),
    surfaceTint = Color.Transparent,
)

internal fun appColorScheme(choice: ThemeChoice, accent: ThemeAccent, systemDark: Boolean): ColorScheme {
    val dark = themeIsDark(choice, systemDark)
    val colors = accentColors(accent, dark)
    return when {
        choice == ThemeChoice.AMOLED -> amoledColors(colors)
        !dark -> lightModeColors(colors, accent)
        else -> colors
    }
}

@Composable fun AqaqeerTheme(choice: ThemeChoice, accent: ThemeAccent = ThemeAccent.GOLD, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = themeIsDark(choice, systemDark)
    val colors = remember(choice, accent, systemDark) { appColorScheme(choice, accent, systemDark) }
    val view = LocalView.current
    if (!view.isInEditMode) SideEffect {
        (view.context as? android.app.Activity)?.let { activity ->
            WindowCompat.getInsetsController(activity.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalStatusColors provides if (dark) DarkStatusColors else LightStatusColors) {
        MaterialTheme(colorScheme = colors,
            typography = ArabicTypography, shapes = AppShapes, content = content)
    }
}
