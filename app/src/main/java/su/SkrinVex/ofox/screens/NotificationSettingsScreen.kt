package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    var mutedChatIds by remember { mutableStateOf(setOf<Int>()) }
    var mutedFriendIds by remember { mutableStateOf(setOf<Int>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showChatExceptions by remember { mutableStateOf(false) }
    var showFriendExceptions by remember { mutableStateOf(false) }
    var chats by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Chat>()) }
    var friends by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.User>()) }
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
            repository.updateNotificationSettings(notifyPostComments, notifyFriendPosts, notifyChats, notifyDiscoveryChats)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
            Text("Уведомления", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Общие", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
                    Column {
                        NotifToggleRow("Комментарии к постам", "Когда комментируют ваши посты", notifyPostComments) { notifyPostComments = it; saveGlobal() }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NotifToggleRow("Посты друзей", "Когда друзья публикуют посты", notifyFriendPosts) { notifyFriendPosts = it; saveGlobal() }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NotifToggleRow("Личные чаты", "Новые сообщения", notifyChats) { notifyChats = it; saveGlobal() }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        NotifToggleRow("Чаты открытий", "Сообщения в открытиях", notifyDiscoveryChats) { notifyDiscoveryChats = it; saveGlobal() }
                    }
                }
            }

            item {
                Text("Исключения", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
                    Column {
                        // Чаты
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showChatExceptions = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Личные чаты", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (mutedChatIds.isEmpty()) "Все уведомления включены"
                                    else "Отключено: ${mutedChatIds.size} ${if (mutedChatIds.size == 1) "чат" else "чата/чатов"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (mutedChatIds.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        // Друзья
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { showFriendExceptions = true }.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Посты друзей", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text(
                                    if (mutedFriendIds.isEmpty()) "Все уведомления включены"
                                    else "Отключено: ${mutedFriendIds.size} ${if (mutedFriendIds.size == 1) "друг" else "друга/друзей"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (mutedFriendIds.isEmpty()) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                                )
                            }
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    // Bottomsheet исключений для чатов
    if (showChatExceptions) {
        ExceptionsSheet(
            title = "Уведомления чатов",
            items = chats.map { Triple(it.id, it.name, it.userAvatarUrl.takeIf { u -> u.isNotBlank() }) },
            mutedIds = mutedChatIds,
            onToggle = { id, muted ->
                scope.launch {
                    repository.toggleChatMute(id)
                    mutedChatIds = if (muted) mutedChatIds - id else mutedChatIds + id
                }
            },
            onDismiss = { showChatExceptions = false }
        )
    }

    // Bottomsheet исключений для друзей
    if (showFriendExceptions) {
        ExceptionsSheet(
            title = "Уведомления о постах",
            items = friends.map { Triple(it.id, it.name, it.avatarUrl.takeIf { u -> u.isNotBlank() }) },
            mutedIds = mutedFriendIds,
            onToggle = { id, muted ->
                scope.launch {
                    repository.toggleFriendMute(id)
                    mutedFriendIds = if (muted) mutedFriendIds - id else mutedFriendIds + id
                }
            },
            onDismiss = { showFriendExceptions = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExceptionsSheet(
    title: String,
    items: List<Triple<Int, String, String?>>, // id, name, avatarUrl
    mutedIds: Set<Int>,
    onToggle: (Int, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, items) {
        if (query.isBlank()) items else items.filter { it.second.contains(query, ignoreCase = true) }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (mutedIds.isNotEmpty()) {
                    Text(
                        "Отключено: ${mutedIds.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Поиск...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotBlank()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) } },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    items(filtered, key = { it.first }) { (id, name, avatarUrl) ->
                        val isMuted = id in mutedIds
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(name = name, avatarUrl = avatarUrl, size = 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    if (isMuted) "Уведомления выключены" else "Уведомления включены",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isMuted) MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                            Switch(checked = !isMuted, onCheckedChange = { onToggle(id, isMuted) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
