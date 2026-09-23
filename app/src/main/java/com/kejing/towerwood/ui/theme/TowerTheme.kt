package com.kejing.towerwood.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.kejing.towerwood.R
import com.pico.spatial.ui.design.*

@Composable
fun TowerTheme(content: @Composable () -> Unit) {
    val system = systemColorScheme(LocalContext.current)
    val colors = system.copy(
        fillPrimary = Color(0xFF273B35), fillSecondary = Color(0xFF354A42), // design-style: fixed-figma-color Tower Wood visual reference palette
        fillTertiary = system.fillTertiary, fillLight = system.fillLight,
        labelPrimaryLight = Color(0xFF17342B), labelPrimary = Color(0xFFF3F3E9), // design-style: fixed-figma-color Tower Wood visual reference palette
        labelSecondary = Color(0xFFC5D0C6), labelTertiary = system.labelTertiary, // design-style: fixed-figma-color Tower Wood visual reference palette
        labelQuaternary = system.labelQuaternary, lightenHover = system.lightenHover,
        lightenPressed = system.lightenPressed, error = system.error, alert = system.alert,
        passable = system.passable, interaction = Color(0xFFD6E8B9), dividerLine = system.dividerLine // design-style: fixed-figma-color Tower Wood visual reference palette
    )
    val f = FontFamily(Font(R.font.noto_sans_sc))
    val t = PicoTheme.typography
    val typography = t.copy(
        bodyLarge=t.bodyLarge.copy(fontFamily=f), bodyLargeMultiline=t.bodyLargeMultiline.copy(fontFamily=f),
        bodyMedium=t.bodyMedium.copy(fontFamily=f), bodyMediumMultiline=t.bodyMediumMultiline.copy(fontFamily=f),
        bodySmall=t.bodySmall.copy(fontFamily=f), bodyTiny=t.bodyTiny.copy(fontFamily=f),
        displayLarge=t.displayLarge.copy(fontFamily=f), displayMedium=t.displayMedium.copy(fontFamily=f), displaySmall=t.displaySmall.copy(fontFamily=f),
        headlineLarge=t.headlineLarge.copy(fontFamily=f), headlineMedium=t.headlineMedium.copy(fontFamily=f), headlineSmall=t.headlineSmall.copy(fontFamily=f),
        titleLarge=t.titleLarge.copy(fontFamily=f), titleMedium=t.titleMedium.copy(fontFamily=f), titleSmall=t.titleSmall.copy(fontFamily=f),
        labelLarge=t.labelLarge.copy(fontFamily=f), labelMedium=t.labelMedium.copy(fontFamily=f), labelSmall=t.labelSmall.copy(fontFamily=f)
    )
    PicoTheme(colorScheme = colors, typography = typography, content = content)
}
