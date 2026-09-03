package com.monolauncher.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.monolauncher.app.data.ThemeMode

private val PureBlack = Color(0xFF000000)
private val PureWhite = Color(0xFFFFFFFF)

private val DarkColors = darkColorScheme(
    background = PureBlack,
    surface = PureBlack,
    onBackground = PureWhite,
    onSurface = PureWhite,
    primary = PureWhite,
    onPrimary = PureBlack,
)

private val LightColors = lightColorScheme(
    background = PureWhite,
    surface = PureWhite,
    onBackground = PureBlack,
    onSurface = PureBlack,
    primary = PureBlack,
    onPrimary = PureWhite,
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/** 흑백 단색만 사용하는 초 미니멀 테마. Material의 색상 강조(accent)는 쓰지 않는다. */
@Composable
fun MonoLauncherTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val colors = if (isDarkTheme(mode)) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
