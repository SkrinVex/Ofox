package su.SkrinVex.ofox.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import su.SkrinVex.ofox.utils.ActiveChatTracker
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.StickerPicker
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.utils.formatTime
import su.SkrinVex.ofox.components.UserBadges
import su.SkrinVex.ofox.data.api.models.BadgeResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(repository: Repository, chatId: Int, initialName: String? = null, onBack: () -> Unit, onNavigateToProfile: (Int) -> Unit = {}) {
    val messages = remember { androidx.compose.runtime.snapshots.SnapshotStateList<su.SkrinVex.ofox.data.Message>() }
    var messageText by remember { mutableStateOf("") }
    var chat by remember { mutableStateOf<su.SkrinVex.ofox.data.Chat?>(null) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var stickerPickerInitialPackId by remember { mutableStateOf<Int?>(null) }
    // url → (packId, packName), packId=null означает "недавние"
    var stickerPackMap by remember { mutableStateOf(mapOf<String, Pair<Int?, String>>()) }
    // Bottomsheet инфо о наборе при нажатии на стикер
    var stickerInfoPackId by remember { mutableStateOf<Int?>(null) }
    var stickerInfoPackName by remember { mutableStateOf<String?>(null) }
    var myPackIds by remember { mutableStateOf(emptySet<Int>()) }
    var currentUserId by remember { mutableStateOf(0) }
    val reactionsOverride = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, String>() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val wsClient = remember { su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(context) }

    val autoResponses = listOf(
        "Привет! Как дела?",
        "Отлично, спасибо!",
        "Да, согласен",
        "Интересная мысль!",
        "Давай обсудим это позже",
        "Звучит здорово!",
        "Хорошая идея 👍",
        "Понял, спасибо!",
        "Отправлю чуть позже",
        "Супер! 🎉"
    )

    var lastTypingSent by remember { mutableStateOf(0L) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var replyTo by remember { mutableStateOf<su.SkrinVex.ofox.data.Message?>(null) }
    var selectedMessage by remember { mutableStateOf<su.SkrinVex.ofox.data.Message?>(null) }
    var highlightedMessageTimestamp by remember { mutableStateOf<Long?>(null) }
    var isVoiceRecording by remember { mutableStateOf(false) }

    fun loadMessages(before: Int? = null) {
        scope.launch {
            if (before == null) {
                val loaded = repository.getMessages(chatId)
                messages.clear()
                messages.addAll(loaded)
                hasMore = loaded.size >= 30
                if (loaded.isNotEmpty()) listState.scrollToItem(loaded.size - 1)
            } else {
                isLoadingMore = true
                val loaded = repository.getMessages(chatId, before = before)
                if (loaded.isNotEmpty()) messages.addAll(0, loaded)
                hasMore = loaded.size >= 30
                isLoadingMore = false
            }
        }
    }

    // Polling — только добавляем новые сообщения, не трогаем весь список
    fun pollNewMessages() {
        scope.launch {
            try {
                val loaded = repository.getMessages(chatId)
                if (loaded.isEmpty()) return@launch
                // Максимальный реальный id (положительный)
                val lastKnownId = messages.filter { it.id > 0 }.maxOfOrNull { it.id } ?: 0
                loaded.filter { it.id > lastKnownId }.forEach { msg ->
                    if (messages.none { it.id == msg.id }) messages.add(msg)
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(chatId) {
        ActiveChatTracker.activeChatId = chatId
        currentUserId = repository.getCurrentUserId()
        chat = repository.getChatById(chatId)
        loadMessages()
        val data = repository.getStickers()
        val packById = data.packs.associateBy { it.id }
        myPackIds = data.packs.map { it.id }.toSet()
        val map = mutableMapOf<String, Pair<Int?, String>>()
        data.packs.forEach { pack ->
            pack.stickers?.forEach { s -> map[s.url] = Pair(pack.id, pack.name) }
        }
        // Недавние — pack_name приходит с сервера напрямую
        data.recent.forEach { s ->
            if (!map.containsKey(s.url)) {
                val packId = s.pack_id
                val packName = s.pack_name?.takeIf { it.isNotBlank() }
                    ?: packById[packId]?.name
                    ?: "Недавние"
                map[s.url] = Pair(packId, packName)
            }
        }
        stickerPackMap = map
    }

    DisposableEffect(chatId) {
        onDispose {
            if (ActiveChatTracker.activeChatId == chatId) {
                ActiveChatTracker.activeChatId = null
            }
        }
    }

    // Скролл вниз при новом сообщении — только если пользователь уже внизу
    // Отслеживаем ID последнего сообщения, а не size (size меняется при polling)
    val lastMessageId = remember { derivedStateOf { messages.lastOrNull()?.let { it.id * 1000L + it.timestamp } ?: 0L } }
    val isAtBottom by remember { derivedStateOf {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= messages.size - 2
    }}

    LaunchedEffect(lastMessageId.value) {
        if (isAtBottom && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Скролл при открытии/закрытии клавиатуры — всегда если был внизу
    val imeBottom = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && messages.isNotEmpty()) {
            // Небольшая задержка чтобы layout успел пересчитаться
            kotlinx.coroutines.delay(80)
            listState.scrollToItem(messages.size - 1)
        }
    }

    // Скролл при появлении плашки ответа
    LaunchedEffect(replyTo) {
        if (replyTo != null && messages.isNotEmpty()) {
            kotlinx.coroutines.delay(50)
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    var isTyping by remember { mutableStateOf(false) }
    // Для групповых: userId -> userName
    val typingUsers = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, String>() }
    var isOtherUserOnline by remember { mutableStateOf(false) }

    // Загружаем онлайн статус при открытии чата
    LaunchedEffect(chatId) {
        try {
            val onlineIds = repository.getOnlineUserIds()
            val otherUserId = chat?.userId ?: 0
            isOtherUserOnline = otherUserId != 0 && otherUserId in onlineIds
        } catch (_: Exception) {}
    }

    LaunchedEffect(wsClient.events) {
        wsClient.events.collect { event ->
            when (event) {
                is su.SkrinVex.ofox.data.api.WSEvent.Typing -> {
                    if (event.chatId == chatId) {
                        isTyping = true
                        typingUsers[event.userId] = event.userName
                        kotlinx.coroutines.delay(3000)
                        isTyping = false
                        typingUsers.remove(event.userId)
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.UserOnline -> {
                    if (event.userId == (chat?.userId ?: 0)) isOtherUserOnline = true
                }
                is su.SkrinVex.ofox.data.api.WSEvent.UserOffline -> {
                    if (event.userId == (chat?.userId ?: 0)) isOtherUserOnline = false
                }
                is su.SkrinVex.ofox.data.api.WSEvent.ChatRead -> {
                    if (event.chatId == chatId) {
                        messages.indices.forEach { i ->
                            if (messages[i].isFromMe && messages[i].status == "sent") {
                                messages[i] = messages[i].copy(status = "read")
                            }
                        }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.MessageReaction -> {
                    if (event.chatId == chatId) {
                        val newJson = org.json.JSONObject().apply {
                            event.reactions.forEach { r ->
                                val arr = org.json.JSONArray()
                                r.user_ids.forEach { arr.put(it) }
                                put(r.emoji, arr)
                            }
                        }.toString()
                        reactionsOverride[event.messageId] = newJson
                        val idx = messages.indexOfFirst { it.id == event.messageId }
                        if (idx != -1) {
                            messages[idx] = messages[idx].copy(reactions = newJson)
                        }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.NewMessage -> {
                    if (event.chatId == chatId) {
                        // Обновляем кэш пользователей
                        if (event.senderId != 0 && event.senderName.isNotBlank()) {
                            su.SkrinVex.ofox.data.UserCache.put(event.senderId, event.senderName, event.senderAvatarUrl)
                        }
                        // Берём имя из кэша если WS не прислал
                        val cachedName = if (event.senderName.isBlank()) su.SkrinVex.ofox.data.UserCache.getName(event.senderId) ?: "" else event.senderName
                        val cachedAvatar = event.senderAvatarUrl ?: su.SkrinVex.ofox.data.UserCache.getAvatar(event.senderId)
                        val newMessage = su.SkrinVex.ofox.data.Message(
                            id = 0, chatId = chatId, text = event.message,
                            timestamp = event.timestamp, isFromMe = false,
                            senderId = event.senderId, senderName = cachedName,
                            senderAvatarUrl = cachedAvatar ?: "",
                            messageType = event.messageType,
                            replyToId = event.replyToId, replyToText = event.replyToText,
                            replyToSenderName = event.replyToSenderName
                        )
                        if (!messages.any { it.text == event.message && it.timestamp == event.timestamp }) {
                            messages.add(newMessage)
                        }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.DiscoveryMessage -> {
                    if (event.chatId == chatId) {
                        if (event.senderId != 0 && event.senderName.isNotBlank()) {
                            su.SkrinVex.ofox.data.UserCache.put(event.senderId, event.senderName, event.senderAvatarUrl)
                        }
                        val cachedName = if (event.senderName.isBlank()) su.SkrinVex.ofox.data.UserCache.getName(event.senderId) ?: "" else event.senderName
                        val cachedAvatar = event.senderAvatarUrl ?: su.SkrinVex.ofox.data.UserCache.getAvatar(event.senderId)
                        val newMessage = su.SkrinVex.ofox.data.Message(
                            id = 0, chatId = chatId, text = event.message,
                            timestamp = event.timestamp, isFromMe = false,
                            senderId = event.senderId, senderName = cachedName,
                            senderAvatarUrl = cachedAvatar ?: "",
                            messageType = event.messageType,
                            replyToId = event.replyToId, replyToText = event.replyToText,
                            replyToSenderName = event.replyToSenderName
                        )
                        if (!messages.any { it.text == event.message && it.timestamp == event.timestamp }) {
                            messages.add(newMessage)
                        }
                    }
                }
                else -> {}
            }
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            pollNewMessages()
        }
    }
    
    val badges = remember(chat) {
        try {
            if (chat?.userBadges?.isNotEmpty() == true) {
                val jsonArray = org.json.JSONArray(chat!!.userBadges)
                (0 until jsonArray.length()).map { i ->
                    val obj = jsonArray.getJSONObject(i)
                    BadgeResponse(
                        badge_type = obj.getString("badge_type"),
                        description = obj.getString("description")
                    )
                }
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        val isDiscoveryChat = (chat?.discoveryId ?: 0) != 0
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            if (isDiscoveryChat) {
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(40.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = androidx.compose.ui.Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        chat?.name ?: initialName ?: "Открытие",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (typingUsers.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TypingIndicator()
                            Text(typingUsers.values.joinToString(", ") + " печатает...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    } else {
                        Text("Чат открытия", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            } else {
                val profileClickModifier = Modifier.clickable {
                    chat?.userId?.let { onNavigateToProfile(it) }
                }
                su.SkrinVex.ofox.components.UserAvatar(
                    name = chat?.name ?: "?",
                    avatarUrl = chat?.userAvatarUrl?.takeIf { it.isNotBlank() },
                    size = 40.dp,
                    modifier = profileClickModifier
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = profileClickModifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(chat?.name ?: "Чат", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (badges.isNotEmpty()) UserBadges(badges)
                    }
                    if (isTyping) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TypingIndicator()
                            Text("печатает...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (isOtherUserOnline) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF4CAF50)))
                            Text("в сети", style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF4CAF50))
                        }
                    }
                }
            }
        }

        val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
        LaunchedEffect(firstVisibleIndex) {
            if (firstVisibleIndex == 0 && hasMore && !isLoadingMore && messages.isNotEmpty()) {
                loadMessages(before = messages.first().id)
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            if (isLoadingMore) {
                item { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }}
            }
            items(messages, key = { it.id.takeIf { id -> id != 0 } ?: it.timestamp }) { message ->
                SwipeToReply(
                    isFromMe = message.isFromMe,
                    onReply = { replyTo = message },
                    onLongPress = { selectedMessage = message }
                ) {
                    MessageBubble(
                        message = message,
                        repository = repository,
                        chatName = chat?.name ?: "",
                        stickerPackMap = stickerPackMap,
                        onStickerClick = { stickerUrl ->
                            val known = stickerPackMap[stickerUrl]
                            if (known != null) {
                                stickerInfoPackId = known.first
                                stickerInfoPackName = known.second
                            } else {
                                scope.launch {
                                    val pack = repository.getPackByStickerUrl(stickerUrl)
                                    if (pack != null) {
                                        stickerInfoPackId = pack.id
                                        stickerInfoPackName = pack.name
                                        stickerPackMap = stickerPackMap + (stickerUrl to Pair(pack.id, pack.name))
                                    }
                                }
                            }
                        },
                        onSenderClick = if ((chat?.discoveryId ?: 0) != 0) onNavigateToProfile else null,
                        onReplyClick = { replyToId ->
                            val idx = messages.indexOfFirst { it.id == replyToId }
                            if (idx != -1) scope.launch {
                                listState.animateScrollToItem(idx)
                                highlightedMessageTimestamp = messages[idx].timestamp
                                kotlinx.coroutines.delay(1500)
                                highlightedMessageTimestamp = null
                            }
                        },
                        onReact = { emoji ->
                            val msgId = if (message.id > 0) message.id
                                        else messages.firstOrNull { it.timestamp == message.timestamp && it.id > 0 }?.id ?: 0
                            if (msgId > 0) {
                                val currentReactions = reactionsOverride[msgId] ?: message.reactions
                                scope.launch {
                                    val newReactions = repository.toggleReaction(chatId, msgId, emoji, currentUserId, currentReactions)
                                    reactionsOverride[msgId] = newReactions
                                    val idx = messages.indexOfFirst { it.id == msgId || (it.id == 0 && it.timestamp == message.timestamp) }
                                    if (idx != -1) messages[idx] = messages[idx].copy(reactions = newReactions)
                                }
                            }
                        },
                        onLongPress = { selectedMessage = message },
                        currentUserId = currentUserId,
                        reactionsJson = reactionsOverride[message.id] ?: message.reactions,
                        isHighlighted = message.timestamp == highlightedMessageTimestamp,
                        isDiscoveryChat = (chat?.discoveryId ?: 0) != 0
                    )
                }
            }
        }
        
        // Плашка ответа
        androidx.compose.animation.AnimatedVisibility(
            visible = replyTo != null,
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()
        ) {
            replyTo?.let { reply ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier
                        .width(3.dp).height(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            reply.senderName.ifBlank { "Вы" },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            when {
                                reply.messageType == "sticker" -> "Стикер"
                                reply.messageType == "voice" -> "Голосовое сообщение"
                                else -> reply.text.take(80)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            maxLines = 1
                        )
                    }
                    IconButton(onClick = { replyTo = null }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showStickerPicker = true }) {
                Icon(
                    Icons.Default.EmojiEmotions,
                    contentDescription = "Стикеры",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                TextField(
                    value = messageText,
                    onValueChange = {
                        val wasBlank = messageText.isBlank()
                        messageText = it
                        val now = System.currentTimeMillis()
                        if (it.isNotBlank()) {
                            // Сбрасываем таймер если только что начали печатать (поле было пустым)
                            if (wasBlank) lastTypingSent = 0L
                            if (now - lastTypingSent > 2000) {
                                lastTypingSent = now
                                scope.launch { repository.sendTyping(chatId) }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            if (isVoiceRecording) "Запись..." else "Сообщение...",
                            color = if (isVoiceRecording) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (messageText.isNotBlank()) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val text = messageText
                            val replyId = replyTo?.id?.takeIf { it != 0 }
                            val replyText = replyTo?.text
                            val replySenderName = replyTo?.senderName
                            messageText = ""
                            replyTo = null
                            val localId = -(System.currentTimeMillis().toInt())
                            val tempMessage = su.SkrinVex.ofox.data.Message(
                                id = localId, chatId = chatId, text = text,
                                timestamp = System.currentTimeMillis(), isFromMe = true,
                                replyToId = replyId, replyToText = replyText, replyToSenderName = replySenderName,
                                status = "sending"
                            )
                            messages.add(tempMessage)
                            scope.launch {
                                listState.scrollToItem(messages.size - 1)
                                val sent = repository.sendMessage(chatId, text, replyId)
                                val idx = messages.indexOfFirst { it.id == localId }
                                if (idx != -1) {
                                    if (sent != null) messages[idx] = sent
                                    else messages.removeAt(idx)
                                }
                            }
                        }
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            } else {
                su.SkrinVex.ofox.components.VoiceMessageButton(
                    repository = repository,
                    chatId = chatId,
                    onRecordingStateChange = { isVoiceRecording = it },
                    onVoiceSent = { key, duration ->
                        val replyMsg = replyTo
                        val replyId = replyMsg?.let { r ->
                            if (r.id > 0) r.id
                            else messages.firstOrNull { it.timestamp == r.timestamp && it.id > 0 }?.id
                        }
                        replyTo = null
                        val localId = -(System.currentTimeMillis().toInt())
                        val tempMsg = su.SkrinVex.ofox.data.Message(
                            id = localId, chatId = chatId, text = "",
                            timestamp = System.currentTimeMillis(), isFromMe = true,
                            messageType = "voice", voiceKey = key, voiceDuration = duration,
                            status = "sending"
                        )
                        messages.add(tempMsg)
                        scope.launch {
                            listState.scrollToItem(messages.size - 1)
                            val sent = repository.sendVoiceMessage(chatId, key, duration, replyId)
                            val idx = messages.indexOfFirst { it.id == localId }
                            if (idx != -1) {
                                if (sent != null) {
                                    messages[idx] = sent
                                } else {
                                    // Не удаляем — перезагружаем чтобы получить реальный id
                                    val loaded = repository.getMessages(chatId)
                                    messages.clear()
                                    messages.addAll(loaded)
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Bottomsheet инфо о наборе стикеров
    if (stickerInfoPackId != null) {
        val packId = stickerInfoPackId!!
        var isInstalling by remember { mutableStateOf(false) }
        val isMyPack = packId in myPackIds

        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { stickerInfoPackId = null; stickerInfoPackName = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stickerInfoPackName ?: "Набор стикеров",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                val isInstalled = packId in myPackIds
                val isMyPack = packId in myPackIds
                if (isInstalled || isMyPack) {
                    Button(
                        onClick = {
                            stickerPickerInitialPackId = packId
                            stickerInfoPackId = null
                            stickerInfoPackName = null
                            showStickerPicker = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) { Text("Открыть набор") }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                isInstalling = true
                                repository.installPack(packId)
                                stickerPickerInitialPackId = packId
                                stickerInfoPackId = null
                                stickerInfoPackName = null
                                showStickerPicker = true
                            }
                        },
                        enabled = !isInstalling,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (isInstalling) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        else Text("Добавить набор себе")
                    }
                }
            }
        }
    }

    // Меню сообщения (долгое нажатие)
    if (selectedMessage != null) {
        val msg = selectedMessage!!
        val ctx = androidx.compose.ui.platform.LocalContext.current
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { selectedMessage = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Быстрые реакции — для сохранённых сообщений (id>0) или если можно найти реальный id
                val realMsgId = if (msg.id > 0) msg.id
                                else messages.firstOrNull { it.timestamp == msg.timestamp && it.id > 0 }?.id ?: 0
                if (realMsgId > 0) {
                    val quickEmojis = listOf("❤️", "😂", "👍", "😮", "😢", "🔥")
                    val currentReactions = remember(msg.reactions) { repository.parseReactions(msg.reactions) }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        quickEmojis.forEach { emoji ->
                            val isMine = currentUserId in (currentReactions[emoji] ?: emptyList())
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        val cur = reactionsOverride[realMsgId] ?: msg.reactions
                                        val newReactions = repository.toggleReaction(chatId, realMsgId, emoji, currentUserId, cur)
                                        reactionsOverride[realMsgId] = newReactions
                                        val idx = messages.indexOfFirst { it.id == realMsgId || (it.id == 0 && it.timestamp == msg.timestamp) }
                                        if (idx != -1) messages[idx] = messages[idx].copy(reactions = newReactions)
                                    }
                                    selectedMessage = null
                                }
                            ) {
                                Text(emoji, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                    Divider(modifier = Modifier.padding(bottom = 4.dp))
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { replyTo = msg; selectedMessage = null }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Ответить", style = MaterialTheme.typography.bodyLarge)
                }
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("msg", msg.text))
                            android.widget.Toast.makeText(ctx, "Скопировано", android.widget.Toast.LENGTH_SHORT).show()
                            selectedMessage = null
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.onSurface)
                    Text("Копировать", style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showStickerPicker) {        StickerPicker(
            repository = repository,
            initialPackId = stickerPickerInitialPackId,
            onStickerSelected = { sticker ->
                showStickerPicker = false
                stickerPickerInitialPackId = null
                val replyMsg = replyTo
                val replyId = replyMsg?.let { r ->
                    if (r.id > 0) r.id
                    else messages.firstOrNull { it.timestamp == r.timestamp && it.id > 0 }?.id
                }
                val replyText = when (replyMsg?.messageType) {
                    "voice" -> "Голосовое сообщение"
                    "sticker" -> "Стикер"
                    else -> replyMsg?.text
                }
                val replySenderName = replyMsg?.senderName
                replyTo = null
                val localId = -(System.currentTimeMillis().toInt())
                val tempMessage = su.SkrinVex.ofox.data.Message(
                    id = localId, chatId = chatId, text = sticker.url,
                    timestamp = System.currentTimeMillis(), isFromMe = true, messageType = "sticker",
                    replyToId = replyId, replyToText = replyText, replyToSenderName = replySenderName,
                    status = "sending"
                )
                messages.add(tempMessage)
                scope.launch {
                    listState.scrollToItem(messages.size - 1)
                    val sent = repository.sendSticker(chatId, sticker.url, replyId)
                    val idx = messages.indexOfFirst { it.id == localId }
                    if (idx != -1) {
                        if (sent != null) messages[idx] = sent else messages.removeAt(idx)
                    }
                    repository.markStickerUsed(sticker.id)
                }
            },
            onDismiss = { showStickerPicker = false; stickerPickerInitialPackId = null }
        )
    }
}

@Composable
fun MessageStatusIcon(status: String, isFromMe: Boolean) {
    if (!isFromMe) return
    val sentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
    val readColor = MaterialTheme.colorScheme.onPrimary

    when (status) {
        "sending" -> CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = readColor.copy(alpha = 0.6f))
        "sent" -> Icon(Icons.Filled.Done, null, tint = sentColor, modifier = Modifier.size(14.dp))
        "read" -> Icon(Icons.Filled.DoneAll, null, tint = readColor, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun TypingIndicator() {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.height(14.dp)
    ) {
        repeat(3) { i ->
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f, targetValue = 4f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(350, delayMillis = i * 100),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .offset(y = offsetY.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SwipeToReply(
    isFromMe: Boolean,
    onReply: () -> Unit,
    onLongPress: () -> Unit,
    content: @Composable () -> Unit
) {
    val offsetX = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = rememberCoroutineScope()
    var triggered by remember { mutableStateOf(false) }
    val threshold = 56f
    // Коэффициент сопротивления — как в Telegram, движение в 2.5 раза медленнее пальца
    val resistance = 0.38f

    Box(modifier = Modifier.fillMaxWidth()) {
        val progress = (kotlin.math.abs(offsetX.value) / threshold).coerceIn(0f, 1f)
        // Иконка ответа
        val iconSize = androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (progress > 0.5f) 22f else 18f * progress,
            animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessLow)
        ).value
        if (progress > 0.05f) {
            Box(
                modifier = Modifier
                    .align(if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 14.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = (progress * 0.18f).coerceAtMost(0.18f))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = progress.coerceAtMost(1f)),
                    modifier = Modifier.size(iconSize.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .offset(x = offsetX.value.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val shouldTrigger = if (isFromMe) offsetX.value < -threshold else offsetX.value > threshold
                            if (shouldTrigger && !triggered) {
                                triggered = true
                                onReply()
                            }
                            scope.launch {
                                offsetX.animateTo(
                                    0f,
                                    androidx.compose.animation.core.spring(
                                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                                    )
                                )
                                triggered = false
                            }
                        },
                        onHorizontalDrag = { change: androidx.compose.ui.input.pointer.PointerInputChange, delta: Float ->
                            change.consume()
                            scope.launch {
                                val newVal = if (isFromMe)
                                    (offsetX.value + delta * resistance).coerceIn(-threshold * 1.3f, 0f)
                                else
                                    (offsetX.value + delta * resistance).coerceIn(0f, threshold * 1.3f)
                                offsetX.snapTo(newVal)
                            }
                        }
                    )
                }
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
        ) {
            content()
        }
    }
}

@Composable
private fun ReplyQuote(message: su.SkrinVex.ofox.data.Message, onReplyClick: ((Int) -> Unit)?, isFromMe: Boolean) {
    if (message.replyToText == null && message.replyToSenderName == null) return
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .then(if (onReplyClick != null && message.replyToId != null) Modifier.clickable { onReplyClick(message.replyToId) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(message.replyToSenderName ?: "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            Text(message.replyToText ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2,
                color = if (isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ReplyQuotePlain(message: su.SkrinVex.ofox.data.Message, onReplyClick: ((Int) -> Unit)?) {
    Row(
        modifier = Modifier.widthIn(max = 200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onReplyClick != null && message.replyToId != null) Modifier.clickable { onReplyClick(message.replyToId) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(message.replyToSenderName ?: "", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(message.replyToText ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MessageBubble(
    message: su.SkrinVex.ofox.data.Message,
    repository: su.SkrinVex.ofox.data.Repository,
    chatName: String = "",
    stickerPackMap: Map<String, Pair<Int?, String>> = emptyMap(),
    onStickerClick: (stickerUrl: String) -> Unit = {},
    onSenderClick: ((Int) -> Unit)? = null,
    onReplyClick: ((Int) -> Unit)? = null,
    onReact: ((String) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    currentUserId: Int = 0,
    reactionsJson: String = "",
    isHighlighted: Boolean = false,
    isDiscoveryChat: Boolean = false
) {
    val highlightAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isHighlighted) 0.12f else 0f,
        animationSpec = androidx.compose.animation.core.tween(400)
    )
    var reactionPopupEmoji by remember { mutableStateOf<String?>(null) }

    val reactionsMap = try {
        if (reactionsJson.isBlank() || reactionsJson == "{}") emptyMap()
        else {
            val obj = org.json.JSONObject(reactionsJson)
            obj.keys().asSequence().associateWith { key ->
                val arr = obj.getJSONArray(key)
                (0 until arr.length()).map { arr.getInt(it) }
            }.filter { it.value.isNotEmpty() }
        }
    } catch (_: Exception) { emptyMap<String, List<Int>>() }

    val startPadding = if (message.isFromMe) 0.dp else if (isDiscoveryChat) 36.dp else 0.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha)),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        // Ник + аватар в групповом чате
        if (!message.isFromMe && isDiscoveryChat) {
            val mod = if (onSenderClick != null && message.senderId != 0)
                Modifier.clickable { onSenderClick(message.senderId) } else Modifier
            Row(modifier = Modifier.padding(start = 4.dp, bottom = 2.dp).then(mod), verticalAlignment = Alignment.CenterVertically) {
                su.SkrinVex.ofox.components.UserAvatar(name = message.senderName, avatarUrl = message.senderAvatarUrl.takeIf { it.isNotBlank() }, size = 28.dp)
                Spacer(Modifier.width(6.dp))
                Text(message.senderName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        when (message.messageType) {
            "voice" -> {
                if (message.voiceKey != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 260.dp).padding(start = startPadding)
                            .combinedClickable(onClick = {}, onLongClick = { onLongPress?.invoke() })
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            ReplyQuote(message, onReplyClick, isFromMe = message.isFromMe)
                            su.SkrinVex.ofox.components.VoiceMessagePlayer(
                                voiceKey = message.voiceKey,
                                durationMs = message.voiceDuration,
                                isFromMe = message.isFromMe,
                                repository = repository
                            )
                            Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall,
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                if (message.isFromMe) MessageStatusIcon(message.status, true)
                            }
                        }
                    }
                }
            }
            "sticker" -> {
                Column(modifier = Modifier.padding(start = startPadding), horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start) {
                    if (message.replyToText != null || message.replyToSenderName != null) {
                        ReplyQuotePlain(message, onReplyClick)
                        Spacer(Modifier.height(4.dp))
                    }
                    val stickerCtx = androidx.compose.ui.platform.LocalContext.current
                    var isLoading by remember { mutableStateOf(true) }
                    Box(modifier = Modifier.size(120.dp).combinedClickable(onClick = { onStickerClick(message.text) }, onLongClick = { onLongPress?.invoke() })) {
                        AsyncImage(
                            model = remember(message.text) {
                                coil.request.ImageRequest.Builder(stickerCtx).data(message.text)
                                    .memoryCacheKey(message.text).diskCacheKey(message.text.substringBefore("?"))
                                    .crossfade(false).allowHardware(true).build()
                            },
                            contentDescription = "Стикер",
                            onSuccess = { isLoading = false }, onError = { isLoading = false },
                            modifier = Modifier.fillMaxSize()
                        )
                        if (isLoading) {
                            val inf = androidx.compose.animation.core.rememberInfiniteTransition()
                            val a by inf.animateFloat(0.2f, 0.6f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse))
                            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = a)), contentAlignment = Alignment.Center) {
                                Text("🎭", style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.padding(top = 2.dp))
                }
            }
            else -> {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(topStart = if (message.isFromMe) 16.dp else 4.dp, topEnd = if (message.isFromMe) 4.dp else 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
                    modifier = Modifier.widthIn(max = 280.dp).padding(start = startPadding)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        ReplyQuote(message, onReplyClick, isFromMe = message.isFromMe)
                        val textColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        su.SkrinVex.ofox.components.LinkedText(message.text, style = MaterialTheme.typography.bodyLarge.copy(color = textColor), linkColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall,
                                color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            if (message.isFromMe) MessageStatusIcon(message.status, true)
                        }
                    }
                }
            }
        }

        // Реакции — под любым типом сообщения
        if (reactionsMap.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                reactionsMap.forEach { (emoji, userIds) ->
                    val isMine = currentUserId != 0 && currentUserId in userIds
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isMine) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isMine) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
                        modifier = Modifier.combinedClickable(onClick = { onReact?.invoke(emoji) }, onLongClick = { reactionPopupEmoji = emoji })
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(emoji, style = MaterialTheme.typography.labelMedium)
                            userIds.take(3).forEach { uid ->
                                su.SkrinVex.ofox.components.UserAvatar(name = su.SkrinVex.ofox.data.UserCache.getName(uid) ?: "?", avatarUrl = su.SkrinVex.ofox.data.UserCache.getAvatar(uid), size = 16.dp)
                            }
                            if (userIds.size > 3) Text("+${userIds.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }

    if (reactionPopupEmoji != null) {
        val emoji = reactionPopupEmoji!!
        val userIds = reactionsMap[emoji] ?: emptyList()
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { reactionPopupEmoji = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("$emoji  ${userIds.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                userIds.forEach { uid ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        su.SkrinVex.ofox.components.UserAvatar(name = su.SkrinVex.ofox.data.UserCache.getName(uid) ?: "?", avatarUrl = su.SkrinVex.ofox.data.UserCache.getAvatar(uid), size = 36.dp)
                        Text(if (uid == currentUserId) "Вы" else su.SkrinVex.ofox.data.UserCache.getName(uid) ?: "Пользователь", style = MaterialTheme.typography.bodyMedium, fontWeight = if (uid == currentUserId) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}
