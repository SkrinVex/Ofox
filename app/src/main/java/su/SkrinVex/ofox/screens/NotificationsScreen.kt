package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.data.api.models.CommentNotification
import su.SkrinVex.ofox.utils.formatTime
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Composable
fun NotificationsScreen(
    repository: Repository,
    onPostClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    var notifications by remember { mutableStateOf(listOf<CommentNotification>()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        notifications = repository.getCommentNotifications()
        isLoading = false
        repository.markNotificationsRead()
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Text(
            "Уведомления",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет уведомлений", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
            ) {
                items(notifications, key = { "${it.type}_${it.id}" }) { notif ->
                    NotificationItem(
                        notif = notif,
                        onClick = {
                            if (notif.type == "comment_reply" && notif.post_id != null) {
                                onPostClick(notif.post_id)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationItem(notif: CommentNotification, onClick: () -> Unit) {
    val isSystem = notif.type == "system"

    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = !isSystem, onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (!notif.is_read)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSystem) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            } else {
                UserAvatar(
                    name = notif.actor_name,
                    avatarUrl = notif.actor_avatar_url?.takeIf { it.isNotBlank() },
                    size = 44.dp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (isSystem) (notif.title ?: "OFOX") else notif.actor_name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (!notif.is_read) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    }
                }
                Text(
                    text = if (isSystem) (notif.body ?: "")
                           else "ответил на ваш комментарий: ${notif.comment_content ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = parseTs(notif.created_at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            if (!isSystem) {
                Icon(Icons.Default.Forum, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(16.dp))
            }
        }
    }
}

private fun parseTs(dateStr: String): String = try {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    formatTime(fmt.parse(dateStr)?.time ?: 0L)
} catch (_: Exception) { "" }
