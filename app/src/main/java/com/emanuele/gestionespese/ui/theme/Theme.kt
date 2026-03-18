/**
 * Theme.kt
 *
 * Configurazione del tema Material 3 dell'app. Definisce le color scheme
 * per la modalità chiara e scura, con supporto al Dynamic Color su Android 12+.
 * Espone il composable [GestioneSpeseTheme] da applicare al root dell'app.
 */
package com.emanuele.gestionespese.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Surface2,
    onPrimaryContainer = Ink,

    secondary = BrandDark,
    onSecondary = Color.White,
    secondaryContainer = Surface2,
    onSecondaryContainer = Ink,

    tertiary = Brand,
    onTertiary = Color.White,
    tertiaryContainer = Surface2,
    onTertiaryContainer = Ink,

    background = Surface0,
    onBackground = Ink,

    surface = Surface1,
    onSurface = Ink,

    surfaceVariant = Surface2,
    onSurfaceVariant = InkSoft,

    outline = OutlineSoft,
    outlineVariant = OutlineSoft,

    error = Danger,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = D_Surface2,
    onPrimaryContainer = D_Ink,

    secondary = BrandDark,
    onSecondary = Color.White,
    secondaryContainer = D_Surface2,
    onSecondaryContainer = D_Ink,

    tertiary = Brand,
    onTertiary = Color.White,
    tertiaryContainer = D_Surface2,
    onTertiaryContainer = D_Ink,

    background = D_Surface0,
    onBackground = D_Ink,

    surface = D_Surface1,
    onSurface = D_Ink,

    surfaceVariant = D_Surface2,
    onSurfaceVariant = D_InkSoft,

    outline = D_OutlineSoft,
    outlineVariant = D_OutlineSoft,

    error = Danger,
    onError = Color.White
)

@Composable
fun GestioneSpeseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // per uno stile “da brand” meglio false (altrimenti Android 12+ ti cambia i colori)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}