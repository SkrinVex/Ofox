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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import su.SkrinVex.ofox.data.BadgeCache
import su.SkrinVex.ofox.data.api.models.BadgeDefinition
import su.SkrinVex.ofox.data.api.models.BadgeResponse

@Composable
fun UserBadges(badges: List<BadgeResponse>?, modifier: Modifier = Modifier) {
    if (badges.isNullOrEmpty()) return
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        badges.forEach { badge ->
            // Берём definition из кэша или из полей самого badge (если сервер уже прислал)
            val def = BadgeCache.get(badge.badge_type) ?: BadgeDefinition(
                badge_type = badge.badge_type,
                name = badge.name ?: badge.badge_type,
                description = badge.description,
                icon_type = badge.icon_type ?: "emoji",
                icon = badge.icon ?: "🏅",
                color = badge.color ?: "#FFD700"
            )
            BadgeIcon(def, badge.description.takeIf { it.isNotBlank() })
        }
    }
}

@Composable
fun BadgeIcon(def: BadgeDefinition, customDescription: String? = null) {
    var showDialog by remember { mutableStateOf(false) }
    val bgColor = remember(def.color) {
        try { Color(android.graphics.Color.parseColor(def.color)) }
        catch (_: Exception) { Color(0xFFFFD700) }
    }

    Box(
        modifier = Modifier
            .size(24.dp)
            .shadow(2.dp, CircleShape)
            .background(bgColor, CircleShape)
            .clickable { showDialog = true },
        contentAlignment = Alignment.Center
    ) {
        BadgeIconContent(def, size = 14)
    }

    if (showDialog) {
        BadgeDialog(def, bgColor, customDescription) { showDialog = false }
    }
}

@Composable
private fun BadgeIconContent(def: BadgeDefinition, size: Int) {
    if (def.icon_type == "image") {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(def.icon)
                .crossfade(false)
                .build(),
            contentDescription = def.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Text(
            text = def.icon,
            fontSize = size.sp,
            lineHeight = size.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BadgeDialog(def: BadgeDefinition, bgColor: Color, customDescription: String?, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(8.dp, CircleShape)
                        .background(bgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    BadgeIconContent(def, size = 40)
                }
                Text(def.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val desc = customDescription?.takeIf { it.isNotBlank() } ?: def.description.takeIf { it.isNotBlank() }
                if (desc != null) {
                    Text(desc, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Text("Закрыть", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
