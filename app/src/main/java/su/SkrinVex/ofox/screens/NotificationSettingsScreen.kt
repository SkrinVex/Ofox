package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.Repository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(repository: Repository, onBack: () -> Unit) {
    var notifyPostComments by remember { mutableStateOf(true) }
    var notifyFriendPosts by remember { mutableStateOf(true) }
    var notifyChats by remember { mutableStateOf(true) }
    var notifyDiscoveryChats by remember { mutableStateOf(true) }

    var chats by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Chat>()) }
    var friends by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.User>()) }
    var mutedChatIds by remember { mutableStateOf(setOf<Int>()) }
    var mutedFriendIds by remember { mutableStateOf(setOf<Int>()) }
    var isLoading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val settings = repository.getNotificationSettings()
        if (settings != null) {
            notifyPostComments = settings.notify_post_comments
            notifyFriendPosts = settings.notify_friend_posts
            notifyChats = settings.notify_chats
            notifyDiscoveryChats = settings.notify_discovery_chats
            mutedChatIds = settings.muted_chats.toSet()
            mutedFriendIds = settings.muted_friends.toSet()
        }
        chats = repository.getAllChats().filter { it.discoveryId == 0 }
        friends = repository.getMutualFriends()
        isLoading = false
    }

    fun saveGlobal() {
        scope.launch {
            repository.updateNotificationSettings(
                notifyPostComments, notifyFriendPosts, notifyChats, notifyDiscoveryChats
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Уведомления",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Глобальные переключатели
            item {
                Text("Общие настройки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column {
                        NotifToggleRow(
                            title = "Комментарии к постам",
                            subtitle = "Когда кто-то комментирует ваши посты",
                            checked = notifyPostComments,
                            onCheckedChange = { notifyPostComments = it; saveGlobal() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NotifToggleRow(
                            title = "Посты друзей",
                            subtitle = "Когда друзья публикуют новые посты",
                            checked = notifyFriendPosts,
                            onCheckedChange = { notifyFriendPosts = it; saveGlobal() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NotifToggleRow(
                            title = "Личные чаты",
                            subtitle = "Новые сообщения в личных чатах",
                            checked = notifyChats,
                            onCheckedChange = { notifyChats = it; saveGlobal() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NotifToggleRow(
                            title = "Чаты открытий",
                            subtitle = "Сообщения в чатах открытий",
                            checked = notifyDiscoveryChats,
                            onCheckedChange = { notifyDiscoveryChats = it; saveGlobal() }
                        )
                    }
                }
            }

            // Список чатов
            if (chats.isNotEmpty()) {
                item {
                    Text(
                        "Личные чаты",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Отключите уведомления от конкретных чатов",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                items(chats) { chat ->
                    val isMuted = chat.id in mutedChatIds
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = chat.name,
                                avatarUrl = chat.userAvatarUrl.takeIf { it.isNotBlank() },
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = chat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = !isMuted,
                                onCheckedChange = {
                                    scope.launch {
                                        repository.toggleChatMute(chat.id)
                                        mutedChatIds = if (isMuted) mutedChatIds - chat.id else mutedChatIds + chat.id
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Список друзей
            if (friends.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Посты друзей",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Отключите уведомления о постах конкретных друзей",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                items(friends) { friend ->
                    val isMuted = friend.id in mutedFriendIds
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                name = friend.name,
                                avatarUrl = friend.avatarUrl.takeIf { it.isNotBlank() },
                                size = 40.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = friend.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = !isMuted,
                                onCheckedChange = {
                                    scope.launch {
                                        repository.toggleFriendMute(friend.id)
                                        mutedFriendIds = if (isMuted) mutedFriendIds - friend.id else mutedFriendIds + friend.id
                                    }
                                }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun NotifToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
