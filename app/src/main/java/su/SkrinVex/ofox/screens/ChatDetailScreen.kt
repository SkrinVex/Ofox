package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import su.SkrinVex.ofox.utils.ActiveChatTracker
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.StickerPicker
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.utils.formatTime
import su.SkrinVex.ofox.components.UserBadges
import su.SkrinVex.ofox.data.api.models.BadgeResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(repository: Repository, chatId: Int, onBack: () -> Unit, onNavigateToProfile: (Int) -> Unit = {}) {
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

    fun loadMessages(before: Int? = null) {
        scope.launch {
            if (before == null) {
                val loaded = repository.getMessages(chatId)
                messages.clear()
                messages.addAll(loaded)
                hasMore = loaded.size >= 30
                // Скролл вниз сразу после загрузки
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

    LaunchedEffect(chatId) {
        ActiveChatTracker.activeChatId = chatId
        chat = repository.getChatById(chatId)
        loadMessages()
        val data = repository.getStickers()
        val packById = data.packs.associateBy { it.id }
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

    // Скролл вниз при новом сообщении (только если уже внизу)
    LaunchedEffect(messages.size) {
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        val total = messages.size
        if (total > 0 && (lastVisible == null || lastVisible >= total - 3)) {
            listState.animateScrollToItem(total - 1)
        }
    }
    
    var isTyping by remember { mutableStateOf(false) }

    LaunchedEffect(wsClient.events) {
        wsClient.events.collect { event ->
            when (event) {
                is su.SkrinVex.ofox.data.api.WSEvent.Typing -> {
                    if (event.chatId == chatId) {
                        isTyping = true
                        kotlinx.coroutines.delay(3000)
                        isTyping = false
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.NewMessage -> {
                    if (event.chatId == chatId) {
                        val newMessage = su.SkrinVex.ofox.data.Message(
                            id = 0,
                            chatId = chatId,
                            text = event.message,
                            timestamp = event.timestamp,
                            isFromMe = false
                        )
                        if (!messages.any { it.text == event.message && it.timestamp == event.timestamp }) {
                            messages.add(newMessage)
                        }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.DiscoveryMessage -> {
                    if (event.chatId == chatId) {
                        val newMessage = su.SkrinVex.ofox.data.Message(
                            id = 0,
                            chatId = chatId,
                            text = event.message,
                            timestamp = event.timestamp,
                            isFromMe = false
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
            loadMessages()
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
                        chat?.name ?: "Открытие",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Чат открытия",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
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
                Column(modifier = profileClickModifier) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            chat?.name ?: "Чат",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (badges.isNotEmpty()) {
                            UserBadges(badges)
                        }
                    }
                    if (isTyping) {
                        Text(
                            "печатает...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Подгрузка старых сообщений при скролле вверх
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
                MessageBubble(
                    message = message,
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
                    onSenderClick = if ((chat?.discoveryId ?: 0) != 0) onNavigateToProfile else null
                )
            }
        }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Кнопка стикеров
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
                        messageText = it
                        val now = System.currentTimeMillis()
                        if (it.isNotBlank() && now - lastTypingSent > 2000) {
                            lastTypingSent = now
                            scope.launch { repository.sendTyping(chatId) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            "Сообщение...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = MaterialTheme.shapes.medium
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (messageText.isNotBlank()) 
                    MaterialTheme.colorScheme.primary 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            ) {
                IconButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            val text = messageText
                            messageText = ""
                            val tempMessage = su.SkrinVex.ofox.data.Message(
                                id = 0,
                                chatId = chatId,
                                text = text,
                                timestamp = System.currentTimeMillis(),
                                isFromMe = true
                            )
                            messages.add(tempMessage)
                            scope.launch {
                                listState.scrollToItem(messages.size - 1)
                                repository.sendMessage(chatId, text)
                            }
                        }
                    },
                    enabled = messageText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Отправить",
                        tint = if (messageText.isNotBlank()) 
                            MaterialTheme.colorScheme.onPrimary 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    // Bottomsheet инфо о наборе стикеров
    if (stickerInfoPackId != null) {
        val packId = stickerInfoPackId!!
        var installedPackIds by remember { mutableStateOf(emptySet<Int>()) }
        var isInstalling by remember { mutableStateOf(false) }

        LaunchedEffect(packId) {
            installedPackIds = repository.getMyPacks().map { it.id }.toSet()
        }

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
                val isInstalled = packId in installedPackIds
                if (isInstalled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Набор уже добавлен", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    }
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

    if (showStickerPicker) {
        StickerPicker(
            repository = repository,
            initialPackId = stickerPickerInitialPackId,
            onStickerSelected = { sticker ->
                showStickerPicker = false
                stickerPickerInitialPackId = null
                val tempMessage = su.SkrinVex.ofox.data.Message(
                    id = 0, chatId = chatId, text = sticker.url,
                    timestamp = System.currentTimeMillis(), isFromMe = true, messageType = "sticker"
                )
                messages.add(tempMessage)
                scope.launch {
                    listState.scrollToItem(messages.size - 1)
                    repository.sendSticker(chatId, sticker.url)
                    repository.markStickerUsed(sticker.id)
                }
            },
            onDismiss = { showStickerPicker = false; stickerPickerInitialPackId = null }
        )
    }
}

@Composable
fun MessageBubble(
    message: su.SkrinVex.ofox.data.Message,
    chatName: String = "",
    stickerPackMap: Map<String, Pair<Int?, String>> = emptyMap(),
    onStickerClick: (stickerUrl: String) -> Unit = {},
    onSenderClick: ((Int) -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
    ) {
        if (!message.isFromMe) {
            val senderClickMod = if (onSenderClick != null && message.senderId != 0)
                Modifier.clickable { onSenderClick(message.senderId) }
            else Modifier
            Row(
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).then(senderClickMod),
                verticalAlignment = Alignment.CenterVertically
            ) {
                su.SkrinVex.ofox.components.UserAvatar(
                    name = message.senderName,
                    avatarUrl = message.senderAvatarUrl.takeIf { it.isNotBlank() },
                    size = 32.dp
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        if (message.messageType == "sticker") {
            val packInfo = stickerPackMap[message.text]
            Column(
                modifier = Modifier
                    .padding(start = if (message.isFromMe) 0.dp else 40.dp)
                    .clickable { onStickerClick(message.text) },
                horizontalAlignment = if (message.isFromMe) Alignment.End else Alignment.Start
            ) {
                val stickerContext = androidx.compose.ui.platform.LocalContext.current
                AsyncImage(
                    model = remember(message.text) {
                        coil.request.ImageRequest.Builder(stickerContext)
                            .data(message.text)
                            .memoryCacheKey(message.text)
                            .diskCacheKey(message.text.substringBefore("?"))
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    },
                    contentDescription = "Стикер",
                    modifier = Modifier.size(120.dp)
                )
                Text(
                    text = formatTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            return
        }

        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (message.isFromMe)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = if (message.isFromMe) 16.dp else 4.dp,
                topEnd = if (message.isFromMe) 4.dp else 16.dp,
                bottomStart = 16.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier
                .widthIn(max = 280.dp)
                .padding(start = if (message.isFromMe) 0.dp else 40.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                val textColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                // Для своих: белый полупрозрачный с подчёркиванием хорошо виден на цветном фоне
                val linkColor = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                su.SkrinVex.ofox.components.LinkedText(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                    linkColor = linkColor
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isFromMe) 
                            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                        else 
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
