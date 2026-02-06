package su.SkrinVex.ofox.ui.theme

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class AppCustomization(
    val primaryColor: Color,
    val themeName: String,
    val fontSize: FontSize,
    val cornerRadius: CornerRadius
)

enum class FontSize(val scale: Float, val displayName: String) {
    SMALL(0.85f, "Маленький"),
    NORMAL(1.0f, "Обычный"),
    LARGE(1.15f, "Большой"),
    EXTRA_LARGE(1.3f, "Очень большой")
}

enum class CornerRadius(val value: Dp, val displayName: String) {
    SHARP(4.dp, "Острые"),
    NORMAL(12.dp, "Обычные"),
    ROUNDED(20.dp, "Округлые"),
    EXTRA_ROUNDED(28.dp, "Очень округлые")
}

object CustomizationManager {
    fun getCustomization(context: Context): AppCustomization {
        val prefs = context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)
        val themeName = prefs.getString("theme", "Оранжевый") ?: "Оранжевый"
        val fontSizeName = prefs.getString("font_size", "NORMAL") ?: "NORMAL"
        val cornerRadiusName = prefs.getString("corner_radius", "NORMAL") ?: "NORMAL"
        
        return AppCustomization(
            primaryColor = getThemeColor(themeName),
            themeName = themeName,
            fontSize = FontSize.valueOf(fontSizeName),
            cornerRadius = CornerRadius.valueOf(cornerRadiusName)
        )
    }
    
    fun saveTheme(context: Context, themeName: String) {
        context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)
            .edit().putString("theme", themeName).apply()
    }
    
    fun saveFontSize(context: Context, fontSize: FontSize) {
        context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)
            .edit().putString("font_size", fontSize.name).apply()
    }
    
    fun saveCornerRadius(context: Context, cornerRadius: CornerRadius) {
        context.getSharedPreferences("ofox_prefs", Context.MODE_PRIVATE)
            .edit().putString("corner_radius", cornerRadius.name).apply()
    }
    
    private fun getThemeColor(themeName: String): Color = when(themeName) {
        "Оранжевый" -> Color(0xFFFF6B35)
        "Синий" -> Color(0xFF2196F3)
        "Фиолетовый" -> Color(0xFF9C27B0)
        "Зелёный" -> Color(0xFF4CAF50)
        "Красный" -> Color(0xFFF44336)
        "Розовый" -> Color(0xFFE91E63)
        "Бирюзовый" -> Color(0xFF00BCD4)
        "Янтарный" -> Color(0xFFFF9800)
        "Лаймовый" -> Color(0xFFCDDC39)
        "Индиго" -> Color(0xFF3F51B5)
        "Коричневый" -> Color(0xFF795548)
        "Серый" -> Color(0xFF607D8B)
        "Малиновый" -> Color(0xFFE91E63)
        "Морская волна" -> Color(0xFF009688)
        "Золотой" -> Color(0xFFFFD700)
        else -> Color(0xFFFF6B35)
    }
}

val LocalCustomization = compositionLocalOf { 
    AppCustomization(
        primaryColor = Color(0xFFFF6B35),
        themeName = "Оранжевый",
        fontSize = FontSize.NORMAL,
        cornerRadius = CornerRadius.NORMAL
    )
}

@Composable
fun rememberCustomization(context: Context): AppCustomization {
    return remember(context) {
        CustomizationManager.getCustomization(context)
    }
}

fun TextUnit.scaled(scale: Float): TextUnit = this * scale
fun Dp.scaled(scale: Float): Dp = this * scale
