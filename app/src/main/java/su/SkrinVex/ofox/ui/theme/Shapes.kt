package su.SkrinVex.ofox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun getScaledShapes(cornerRadius: Dp = 12.dp) = Shapes(
    extraSmall = RoundedCornerShape(cornerRadius * 0.33f),
    small = RoundedCornerShape(cornerRadius * 0.66f),
    medium = RoundedCornerShape(cornerRadius),
    large = RoundedCornerShape(cornerRadius * 1.33f),
    extraLarge = RoundedCornerShape(cornerRadius * 1.66f)
)

val DefaultShapes = getScaledShapes()

// Глобальные функции для использования везде
@Composable
fun cardShape() = androidx.compose.material3.MaterialTheme.shapes.medium

@Composable
fun buttonShape() = androidx.compose.material3.MaterialTheme.shapes.medium

@Composable
fun dialogShape() = androidx.compose.material3.MaterialTheme.shapes.large

@Composable
fun chipShape() = androidx.compose.material3.MaterialTheme.shapes.small

