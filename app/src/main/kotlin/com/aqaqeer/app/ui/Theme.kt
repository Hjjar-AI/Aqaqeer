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
import androidx.compose.ui.unit.sp
import android.content.res.Resources
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
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
internal object AppShapeTokens {
    val extraSmall = RoundedCornerShape(Radius.xs)
    val extraLarge = RoundedCornerShape(Radius.materialExtraLarge)
    val small = RoundedCornerShape(Radius.sm)
    val medium = RoundedCornerShape(Radius.md)
    val large = RoundedCornerShape(Radius.lg)
    val search = RoundedCornerShape(Radius.xl)
}
private val AppShapes = Shapes(
    extraSmall = AppShapeTokens.extraSmall,
    extraLarge = AppShapeTokens.extraLarge,
    small = AppShapeTokens.small,
    medium = AppShapeTokens.medium,
    large = AppShapeTokens.large,
)
private val ArabicTypography = Typography(
    displayLarge = BaseTypography.displayLarge.copy(fontFamily = ArabicFont),
    displayMedium = BaseTypography.displayMedium.copy(fontFamily = ArabicFont),
    displaySmall = BaseTypography.displaySmall.copy(fontFamily = ArabicFont),
    headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = ArabicFont, lineHeight = Leading.headlineLarge),
    headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = ArabicFont, lineHeight = Leading.headlineMedium),
    headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = ArabicFont, lineHeight = Leading.headlineSmall),
    titleLarge = BaseTypography.titleLarge.copy(fontFamily = ArabicFont, lineHeight = Leading.title),
    titleMedium = BaseTypography.titleMedium.copy(fontFamily = ArabicFont, lineHeight = Leading.relaxed),
    titleSmall = BaseTypography.titleSmall.copy(fontFamily = ArabicFont, lineHeight = Leading.body),
    bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = ArabicFont, lineHeight = Leading.relaxed),
    bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = ArabicFont, lineHeight = Leading.body),
    bodySmall = BaseTypography.bodySmall.copy(fontFamily = ArabicFont, fontSize = 14.sp, lineHeight = Leading.body),
    labelLarge = BaseTypography.labelLarge.copy(fontFamily = ArabicFont, lineHeight = Leading.body),
    labelMedium = BaseTypography.labelMedium.copy(fontFamily = ArabicFont, fontSize = 14.sp, lineHeight = Leading.body),
    labelSmall = BaseTypography.labelSmall.copy(fontFamily = ArabicFont, fontSize = 12.sp, lineHeight = Leading.small),
)
private val LightColors = lightColorScheme(
    onBackground = Color(0xFF1C1A23), onSurface = Color(0xFF1C1A23),
    onSurfaceVariant = Color(0xFF4E4859), outline = Color(0xFF777083),
    outlineVariant = Color(0xFFD8D2E1),
    tertiary = Color(0xFF376468), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFEDEF), onTertiaryContainer = Color(0xFF173E42),
    inverseSurface = Color(0xFF302C38), inverseOnSurface = Color(0xFFF6EFFB),
)
private val DarkColors = darkColorScheme(
    onBackground = Color(0xFFF0EBF6), surface = Color(0xFF201E27), onSurface = Color(0xFFF0EBF6),
    surfaceDim = Color(0xFF16151C), surfaceBright = Color(0xFF393541),
    surfaceContainerLowest = Color(0xFF121017), surfaceContainerLow = Color(0xFF1D1B24),
    surfaceContainer = Color(0xFF24212C), surfaceContainerHigh = Color(0xFF2D2936),
    surfaceContainerHighest = Color(0xFF373242), surfaceVariant = Color(0xFF373242),
    onSurfaceVariant = Color(0xFFD0C8DB), outline = Color(0xFF9A90A8),
    outlineVariant = Color(0xFF51485E),
    tertiary = Color(0xFF9BD3D7), onTertiary = Color(0xFF12363A),
    tertiaryContainer = Color(0xFF254B50), onTertiaryContainer = Color(0xFFCFEDEF),
    inverseSurface = Color(0xFFF0EBF6), inverseOnSurface = Color(0xFF302C38),
)

private data class AccentPalette(
    val primary: Color,
    val lightContainer: Color,
    val deep: Color,
    val darkPrimary: Color,
    val darkContainer: Color,
)

private fun accentColors(accent: ThemeAccent, dark: Boolean, resources: Resources): ColorScheme {
    val palette = when (accent) {
        ThemeAccent.PURPLE -> AccentPalette(launchColor(resources, R.color.accent_violet_light),
            Color(0xFFF3E0FB), Color(0xFF38104B), Color(0xFFE4AEF7), Color(0xFF592971))
        ThemeAccent.BLUE -> AccentPalette(Color(0xFF155B99),
            Color(0xFFDCEEFF), Color(0xFF002D54), Color(0xFFA1CDFF), Color(0xFF164575))
        ThemeAccent.GREEN -> AccentPalette(Color(0xFF006548),
            Color(0xFFC9F5E2), Color(0xFF003826), Color(0xFF76DCB5), Color(0xFF064C37))
        ThemeAccent.ROSE -> AccentPalette(Color(0xFFA12D57),
            Color(0xFFFFD9E5), Color(0xFF480D26), Color(0xFFFFA6C4), Color(0xFF702343))
    }
    val base = if (dark) DarkColors.copy(background = launchColor(resources, R.color.launch_dark)) else LightColors
    val primary = if (dark) palette.darkPrimary else palette.primary
    val onPrimary = if (dark) palette.deep else Color.White
    val container = if (dark) palette.darkContainer else palette.lightContainer
    val onContainer = if (dark) palette.lightContainer else palette.deep
    return base.copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = primary, onSecondary = onPrimary,
        secondaryContainer = container, onSecondaryContainer = onContainer,
        inversePrimary = if (dark) palette.primary else palette.darkPrimary,
        surfaceTint = primary,
    )
}

/** Black backgrounds/cards with subtly lifted interactive and elevated containers. */
private fun amoledColors(colors: ColorScheme): ColorScheme = colors.copy(
    background = Color.Black, onBackground = Color(0xFFF1F1F1),
    surface = Color.Black, onSurface = Color(0xFFF1F1F1),
    surfaceDim = Color.Black, surfaceBright = Color(0xFF141414), surfaceVariant = Color(0xFF121212),
    surfaceContainerLowest = Color(0xFF0E0E0E), surfaceContainerLow = Color(0xFF101010),
    surfaceContainer = Color(0xFF121212), surfaceContainerHigh = Color(0xFF131313), surfaceContainerHighest = Color(0xFF141414),
    onSurfaceVariant = Color(0xFFC7C7C7), surfaceTint = Color.Transparent,
    outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFF444444),
    inverseSurface = Color(0xFF141414), inverseOnSurface = Color(0xFFF1F1F1), inversePrimary = colors.primary,
)

internal data class StatusTone(val container: Color, val content: Color, val edge: Color)
internal data class StatusColors(val available: StatusTone, val low: StatusTone, val out: StatusTone,
    val info: StatusTone)
private fun statusColors(resources: Resources, colors: ColorScheme): StatusColors {
    fun tone(container: Int, content: Int, edge: Int) = StatusTone(
        Color(resources.getColor(container, null)), Color(resources.getColor(content, null)), Color(resources.getColor(edge, null)))
    return StatusColors(
        tone(R.color.status_available_container, R.color.status_available_content, R.color.status_available_edge),
        tone(R.color.status_low_container, R.color.status_low_content, R.color.status_low_edge),
        tone(R.color.status_out_container, R.color.status_out_content, R.color.status_out_edge),
        StatusTone(colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary))
}
internal val LocalStatusColors = staticCompositionLocalOf<StatusColors> { error("AqaqeerTheme is required") }

internal fun themeIsDark(choice: ThemeChoice, systemDark: Boolean): Boolean =
    choice == ThemeChoice.DARK || choice == ThemeChoice.AMOLED || (choice == ThemeChoice.SYSTEM && systemDark)

/** Near-white backgrounds keep a faint accent tint; cards remain white. */
private fun launchColor(resources: Resources, id: Int): Color = Color(resources.getColor(id, null))
internal fun lightBackground(accent: ThemeAccent, resources: Resources): Color = launchColor(resources, when (accent) {
    ThemeAccent.BLUE -> R.color.launch_blue
    ThemeAccent.GREEN -> R.color.launch_green
    ThemeAccent.PURPLE -> R.color.launch_violet
    ThemeAccent.ROSE -> R.color.launch_rose
})

private fun lightModeColors(colors: ColorScheme, accent: ThemeAccent, resources: Resources): ColorScheme = colors.copy(
    background = lightBackground(accent, resources), surface = Color.White, surfaceBright = Color.White,
    surfaceDim = lerp(Color.White, colors.primary, 0.10f),
    surfaceContainerLowest = Color.White, surfaceContainerLow = lightBackground(accent, resources),
    surfaceContainer = lerp(Color.White, colors.primary, 0.035f),
    surfaceContainerHigh = lerp(Color.White, colors.primary, 0.05f),
    surfaceContainerHighest = lerp(Color.White, colors.primary, 0.07f),
    surfaceVariant = lerp(Color.White, colors.primary, 0.05f),
    surfaceTint = Color.Transparent,
)

internal fun appColorScheme(choice: ThemeChoice, accent: ThemeAccent, systemDark: Boolean, resources: Resources): ColorScheme {
    val dark = themeIsDark(choice, systemDark)
    val colors = accentColors(accent, dark, resources)
    return when {
        choice == ThemeChoice.AMOLED -> amoledColors(colors)
        !dark -> lightModeColors(colors, accent, resources)
        else -> colors
    }
}

@Composable fun AqaqeerTheme(choice: ThemeChoice, accent: ThemeAccent = ThemeAccent.PURPLE, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = themeIsDark(choice, systemDark)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val colors = remember(choice, accent, systemDark, context, configuration) { appColorScheme(choice, accent, systemDark, context.resources) }
    // Resource night qualifiers follow the selected app theme, including manual light/dark choices.
    val statuses = remember(context, configuration, dark, colors) {
        val selected = Configuration(configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        statusColors(context.createConfigurationContext(selected).resources, colors)
    }
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
        LocalStatusColors provides statuses) {
        MaterialTheme(colorScheme = colors,
            typography = ArabicTypography, shapes = AppShapes) { ProvideLayoutMetrics(content) }
    }
}
