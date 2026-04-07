package su.SkrinVex.ofox.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import su.SkrinVex.ofox.components.UserBadges
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.data.api.models.CommentNotification
import su.SkrinVex.ofox.utils.formatTime
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationsScreen(
    repository: Repository,
    onPostClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    var notifications by remember { mutableStateOf(listOf<CommentNotification>()) }
    var isLoading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    val isSelecting = selected.isNotEmpty()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        notifications = repository.getCommentNotifications()
        isLoading = false
        repository.markNotificationsRead()
    }

    fun deleteSelected() {
        val toDelete = notifications.filter { it.id in selected }
        scope.launch {
            repository.deleteNotifications(toDelete.map { it.id }, toDelete.map { it.type })
            notifications = notifications.filter { it.id !in selected }
            selected = emptySet()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSelecting) {
                Text("Выбрано: ${selected.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        selected = if (selected.size == notifications.size) emptySet()
                                   else notifications.map { it.id }.toSet()
                    }) {
                        Text(if (selected.size == notifications.size) "Снять всё" else "Выбрать всё")
                    }
                    IconButton(onClick = { deleteSelected() }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text("Уведомления", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
                    val isSelected = notif.id in selected
                    NotificationItem(
                        notif = notif,
                        isSelected = isSelected,
                        isSelecting = isSelecting,
                        onClick = {
                            if (isSelecting) {
                                selected = if (isSelected) selected - notif.id else selected + notif.id
                            } else if (notif.type == "comment_reply" && notif.post_id != null) {
                                onPostClick(notif.post_id)
                            }
                        },
                        onLongClick = {
                            selected = if (isSelected) selected - notif.id else selected + notif.id
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationItem(
    notif: CommentNotification,
    isSelected: Boolean,
    isSelecting: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isSystem = notif.type == "system"

    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                !notif.is_read -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isSelecting) {
                Icon(
                    if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(22.dp)
                )
            }

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
                    if (!notif.actor_badges.isNullOrEmpty() && !isSystem) {
                        UserBadges(badges = notif.actor_badges)
                    }
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

            if (!isSystem && !isSelecting) {
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
