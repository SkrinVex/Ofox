package su.SkrinVex.ofox.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import su.SkrinVex.ofox.data.api.models.BadgeResponse

@Composable
fun UserBadges(badges: List<BadgeResponse>?, modifier: Modifier = Modifier) {
    if (badges.isNullOrEmpty()) return
    
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEach { badge ->
            BadgeIcon(badge)
        }
    }
}

@Composable
private fun BadgeIcon(badge: BadgeResponse) {
    var showDialog by remember { mutableStateOf(false) }
    
    val (icon, color, name) = when (badge.badge_type) {
        "donor" -> Triple("💎", Color(0xFFFF6B9D), "Донатер")
        "developer" -> Triple("💻", Color(0xFF00D9FF), "Разработчик")
        "moderator" -> Triple("🛡️", Color(0xFF9C27B0), "Модератор")
        "verified" -> Triple("✓", Color(0xFF4CAF50), "Верифицирован")
        "vip" -> Triple("⭐", Color(0xFFFFD700), "VIP")
        "founder" -> Triple("👑", Color(0xFFFF9800), "Основатель")
        "contributor" -> Triple("🎨", Color(0xFF00BCD4), "Контрибьютор")
        "supporter" -> Triple("❤️", Color(0xFFE91E63), "Поддержка")
        "crazy" -> Triple("🤪", Color(0xFFFF5722), "Сумасшедший")
        else -> Triple("🏅", Color(0xFFFFD700), badge.badge_type)
    }
    
    Box(
        modifier = Modifier
            .size(24.dp)
            .shadow(2.dp, CircleShape)
            .background(color, CircleShape)
            .clickable { showDialog = true },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = icon,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
    
    if (showDialog) {
        BadgeDialog(badge, icon, color, name) { showDialog = false }
    }
}

@Composable
private fun BadgeDialog(badge: BadgeResponse, icon: String, color: Color, name: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (badge.description.isNotBlank()) 20.dp else 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(8.dp, CircleShape)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = icon,
                        fontSize = 40.sp,
                        textAlign = TextAlign.Center
                    )
                }
                
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                if (badge.description.isNotBlank()) {
                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Закрыть", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
