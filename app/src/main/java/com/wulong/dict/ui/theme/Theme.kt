package com.wulong.dict.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.core.view.WindowCompat
import com.wulong.dict.R

object WulongFonts {
    val PlayfairDisplay = FontFamily(
        Font(R.font.playfair_display_regular, FontWeight.Normal),
        Font(R.font.playfair_display_regular, FontWeight.Bold),
    )
}

// ── Brand palette ─────────────────────────────────────────────────────────
// Warm, paper-like, restrained — "oolong tea dictionary" aesthetic.
object WulongColors {
    /** Page background: warm paper-white */
    val Background = Color(0xFFFDFBF7)
    /** Card / search-bar surface: light cream */
    val Surface = Color(0xFFF7F3E8)
    /** Search-bar fill: warm cream */
    val SearchFill = Color(0xFFF5F1E6)
    /** Subtle warm gray for placeholder text */
    val Placeholder = Color(0xFFA8A29B)
    /** Soft dark for body text (not pure black) */
    val BodyText = Color(0xFF3A3A3A)
}

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5B3E96),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF625B71),
    background = WulongColors.Background,
    surface = WulongColors.Surface,
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFCFBDFF),
    onPrimary = Color(0xFF3A0D70),
    primaryContainer = Color(0xFF522592),
    secondary = Color(0xFFCCC2DC),
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
)

@Composable
fun WulongDictTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
