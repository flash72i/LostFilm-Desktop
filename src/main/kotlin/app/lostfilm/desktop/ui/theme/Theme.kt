package app.lostfilm.desktop.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LostFilmDark = darkColorScheme(
    primary = Color(0xFFE53935),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF5C1A18),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFFFB74D),
    onSecondary = Color(0xFF2A1700),
    background = Color(0xFF0E0E10),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF232327),
    onSurfaceVariant = Color(0xFFBDBDBD),
    outline = Color(0xFF3A3A40),
)

@Composable
fun LostFilmTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LostFilmDark, content = content)
}
