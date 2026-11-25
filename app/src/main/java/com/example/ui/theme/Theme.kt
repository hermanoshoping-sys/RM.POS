package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = POSPrimary,
    secondary = POSSecondary,
    tertiary = POSTertiary,
    background = POSBackgroundDark,
    surface = POSCardDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = POSTextDark,
    onSurface = POSTextDark
)

val LightColorScheme = lightColorScheme(
    primary = POSPrimary,
    secondary = POSSecondary,
    tertiary = POSTertiary,
    background = POSBackgroundLight,
    surface = POSCardLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = POSTextLight,
    onSurface = POSTextLight
)

@Composable
fun RmPosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography
    ) {
        androidx.compose.material3.ProvideTextStyle(value = Typography.bodyLarge) {
            content()
        }
    }
}
