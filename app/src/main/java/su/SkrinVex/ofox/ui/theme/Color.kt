package su.SkrinVex.ofox.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

val Orange = Color(0xFFFF6B35)
val OrangeVariant = Color(0xFFE55A2B)
val Black = Color(0xFF000000)
val DarkGray = Color(0xFF121212)
val MediumGray = Color(0xFF1E1E1E)
val LightGray = Color(0xFF2C2C2C)

val DarkColorScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Black,
    primaryContainer = OrangeVariant,
    onPrimaryContainer = Color.White,
    secondary = Orange,
    onSecondary = Black,
    background = Black,
    onBackground = Color.White,
    surface = DarkGray,
    onSurface = Color.White,
    surfaceVariant = MediumGray,
    onSurfaceVariant = Color.White
)
