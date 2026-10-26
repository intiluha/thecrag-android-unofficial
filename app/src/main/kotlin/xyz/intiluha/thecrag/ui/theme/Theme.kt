package xyz.intiluha.thecrag.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF2E5E3A),
    secondary = Color(0xFF8A5A2B),
    tertiary = Color(0xFF4A6FA5),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF9FD3A8),
    secondary = Color(0xFFE0B07A),
    tertiary = Color(0xFFA9C4F0),
)

@Composable
fun TheCragTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

/**
 * Approximation of theCrag's grade band colours (gb0..gb5). theCrag's own palette
 * is in its CSS; tune when topos are rendered side by side.
 */
fun gradeBandColor(band: Int): Color = when (band) {
    0 -> Color(0xFF9E9E9E)
    1 -> Color(0xFF4CAF50)
    2 -> Color(0xFF2196F3)
    3 -> Color(0xFFFFB300)
    4 -> Color(0xFFE53935)
    else -> Color(0xFF6A1B9A)
}
