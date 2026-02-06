package su.SkrinVex.ofox.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// Orange Theme (Default)
val Orange = Color(0xFFFF6B35)
val OrangeVariant = Color(0xFFE55A2B)

// Blue Theme
val Blue = Color(0xFF2196F3)
val BlueVariant = Color(0xFF1976D2)

// Purple Theme
val Purple = Color(0xFF9C27B0)
val PurpleVariant = Color(0xFF7B1FA2)

// Green Theme
val Green = Color(0xFF4CAF50)
val GreenVariant = Color(0xFF388E3C)

// Red Theme
val Red = Color(0xFFF44336)
val RedVariant = Color(0xFFD32F2F)

val Black = Color(0xFF000000)
val DarkGray = Color(0xFF121212)
val MediumGray = Color(0xFF1E1E1E)
val LightGray = Color(0xFF2C2C2C)

fun getColorScheme(themeName: String) = when(themeName) {
    "Blue" -> darkColorScheme(
        primary = Blue,
        onPrimary = Black,
        primaryContainer = BlueVariant,
        onPrimaryContainer = Color.White,
        secondary = Blue,
        onSecondary = Black,
        background = Black,
        onBackground = Color.White,
        surface = DarkGray,
        onSurface = Color.White,
        surfaceVariant = MediumGray,
        onSurfaceVariant = Color.White,
        error = Red
    )
    "Purple" -> darkColorScheme(
        primary = Purple,
        onPrimary = Black,
        primaryContainer = PurpleVariant,
        onPrimaryContainer = Color.White,
        secondary = Purple,
        onSecondary = Black,
        background = Black,
        onBackground = Color.White,
        surface = DarkGray,
        onSurface = Color.White,
        surfaceVariant = MediumGray,
        onSurfaceVariant = Color.White,
        error = Red
    )
    "Green" -> darkColorScheme(
        primary = Green,
        onPrimary = Black,
        primaryContainer = GreenVariant,
        onPrimaryContainer = Color.White,
        secondary = Green,
        onSecondary = Black,
        background = Black,
        onBackground = Color.White,
        surface = DarkGray,
        onSurface = Color.White,
        surfaceVariant = MediumGray,
        onSurfaceVariant = Color.White,
        error = Red
    )
    "Red" -> darkColorScheme(
        primary = Red,
        onPrimary = Color.White,
        primaryContainer = RedVariant,
        onPrimaryContainer = Color.White,
        secondary = Red,
        onSecondary = Color.White,
        background = Black,
        onBackground = Color.White,
        surface = DarkGray,
        onSurface = Color.White,
        surfaceVariant = MediumGray,
        onSurfaceVariant = Color.White,
        error = Red
    )
    else -> darkColorScheme(
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
        onSurfaceVariant = Color.White,
        error = Red
    )
}
