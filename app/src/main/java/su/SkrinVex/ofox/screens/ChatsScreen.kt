package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.data.api.models.BadgeResponse
import su.SkrinVex.ofox.components.UserBadges
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.utils.formatTime

@Composable
fun ChatsScreen(repository: Repository, navController: NavController, bottomPadding: Dp = 80.dp) {
    var chats by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Chat>()) }
    var showAddChatDialog by remember { mutableStateOf(false) }
    var notifUnread by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val wsClient = remember { su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(context) }
    val personalChats by remember(chats) { derivedStateOf { chats.filter { it.discoveryId == 0 } } }
    val discoveryChats by remember(chats) { derivedStateOf { chats.filter { it.discoveryId != 0 } } }
    val personalUnread by remember(personalChats) { derivedStateOf { personalChats.sumOf { it.unreadCount } } }
    val discoveryUnread by remember(discoveryChats) { derivedStateOf { discoveryChats.sumOf { it.unreadCount } } }

    fun loadChats() {
        scope.launch {
            chats = repository.getAllChats()
            notifUnread = repository.getNotificationsUnreadCount()
        }
    }

    LaunchedEffect(Unit) { loadChats() }

    LaunchedEffect(wsClient.events) {
        wsClient.events.collect { event ->
            when (event) {
                is su.SkrinVex.ofox.data.api.WSEvent.ChatUpdate,
                is su.SkrinVex.ofox.data.api.WSEvent.NewMessage -> loadChats()
                is su.SkrinVex.ofox.data.api.WSEvent.CommentReply -> {
                    notifUnread++
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            loadChats()
        }
    }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val personalListState = rememberLazyListState()
    val discoveryListState = rememberLazyListState()
    val isFabVisible by remember {
        derivedStateOf {
            if (selectedTab == 0) personalListState.firstVisibleItemIndex == 0 && personalListState.firstVisibleItemScrollOffset < 100
            else true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Text(
                text = "Чаты",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Личные",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (personalUnread > 0) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (personalUnread > 9) "9+" else personalUnread.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)
                                )
                            }
                        }
                    }
                }
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            "Открытия",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (discoveryUnread > 0) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (discoveryUnread > 9) "9+" else discoveryUnread.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedTab == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp)
                                )
                            }
                        }
                    }
                }
            }

            if (selectedTab == 0) {
                // Личные чаты
                LazyColumn(
                    state = personalListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { navController.navigate("notifications") },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Уведомления", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("Системные уведомления", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                                if (notifUnread > 0) {
                                    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                        Text(if (notifUnread > 9) "9+" else notifUnread.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    if (personalChats.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    Spacer(Modifier.height(16.dp))
                                    Text("Нет личных чатов", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                    Text("Нажмите + чтобы начать общение", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                                }
                            }
                        }
                    } else {
                        items(personalChats) { chat ->
                            PersonalChatItem(chat = chat, onClick = { navController.navigate("chat/${chat.id}") })
                        }
                    }
                }
            } else {
                // Чаты открытий
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = bottomPadding)
                ) {
                    if (discoveryChats.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    Spacer(Modifier.height(16.dp))
                                    Text("Нет чатов открытий", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                                    Text("Вступите в открытие чтобы общаться", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                                }
                            }
                        }
                    } else {
                        items(discoveryChats) { chat ->
                            DiscoveryChatItem(chat = chat, onClick = { navController.navigate("chat/${chat.id}") })
                        }
                    }
                }
            }
        }

        if (selectedTab == 0) {
            androidx.compose.animation.AnimatedVisibility(
                visible = isFabVisible,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
                modifier = Modifier.align(Alignment.BottomEnd)
            ) {
                FloatingActionButton(
                    onClick = { showAddChatDialog = true },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(end = 16.dp, bottom = 96.dp),
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить чат")
                }
            }
        }
    }
    
    if (showAddChatDialog) {
        AddChatDialog(
            repository = repository,
            onDismiss = { showAddChatDialog = false },
            onChatAdded = {
                scope.launch {
                    chats = repository.getAllChats()
                    showAddChatDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddChatDialog(
    repository: Repository,
    onDismiss: () -> Unit,
    onChatAdded: () -> Unit
) {
    var users by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.User>()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        val allUsers = repository.getMutualFriends()
        val existingChats = repository.getAllChats()
        val existingUserIds = existingChats.map { it.userId }.toSet()
        users = allUsers.filter { it.id !in existingUserIds }
        isLoading = false
    }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Добавить чат",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Для общения вам нужно взаимно подписаться друг на друга",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (users.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Нет доступных пользователей",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users) { user ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        scope.launch {
                                            val chatId = repository.createChat(user.id, user.name)
                                            if (chatId != null) {
                                                onChatAdded()
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        name = user.name,
                                        avatarUrl = user.avatarUrl.takeIf { it.isNotBlank() },
                                        size = 40.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = user.name,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}


@Composable
fun PersonalChatItem(chat: su.SkrinVex.ofox.data.Chat, onClick: () -> Unit) {
    val badges = try {
        if (chat.userBadges.isNotEmpty()) {
            val arr = org.json.JSONArray(chat.userBadges)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BadgeResponse(badge_type = o.getString("badge_type"), description = o.getString("description"))
            }
        } else emptyList()
    } catch (e: Exception) { emptyList() }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            UserAvatar(name = chat.name, avatarUrl = chat.userAvatarUrl.takeIf { it.isNotBlank() }, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(chat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (badges.isNotEmpty()) UserBadges(badges)
                }
                Text(
                    chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatTime(chat.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (chat.unreadCount > 9) "9+" else chat.unreadCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryChatItem(chat: su.SkrinVex.ofox.data.Chat, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Explore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    chat.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatTime(chat.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (chat.unreadCount > 9) "9+" else chat.unreadCount.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
