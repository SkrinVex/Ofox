package su.SkrinVex.ofox.screens

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Block
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
    // Кеш presigned URL для фото чата: imageKey -> url
    val imageUrlCache = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>() }
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
    // Скачивание голосового
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var downloadingVoiceKey by remember { mutableStateOf<String?>(null) }
    var downloadingChatName by remember { mutableStateOf("") }
    var downloadingSentAt by remember { mutableStateOf(0L) }
    var downloadSavedPath by remember { mutableStateOf<String?>(null) }
    var messageToDelete by remember { mutableStateOf<su.SkrinVex.ofox.data.Message?>(null) }
    // Отправка фото
    var imageUploadProgress by remember { mutableStateOf<Float?>(null) }
    var imageUploadError by remember { mutableStateOf<String?>(null) }
    var showImageWarningDialog by remember { mutableStateOf(false) }
    // Файлы
    var showAttachSheet by remember { mutableStateOf(false) }
    var fileUploadProgress by remember { mutableStateOf<Float?>(null) }
    var fileUploadError by remember { mutableStateOf<String?>(null) }
    var showFileWarningDialog by remember { mutableStateOf(false) }
    var showFileDownloadDialog by remember { mutableStateOf(false) }
    var downloadingFileKey by remember { mutableStateOf<String?>(null) }
    var downloadingFileName by remember { mutableStateOf("") }
    var fileDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var fileDownloadError by remember { mutableStateOf<String?>(null) }
    var fileDownloadSavedPath by remember { mutableStateOf<String?>(null) }
    var pendingFileReplyId by remember { mutableStateOf<Int?>(null) }
    // Открытие файла
    var showOpenFileDialog by remember { mutableStateOf(false) }
    var openingFileKey by remember { mutableStateOf<String?>(null) }
    var openingFileName by remember { mutableStateOf("") }
    var openFileSentAt by remember { mutableStateOf(0L) }
    var showApkWarningDialog by remember { mutableStateOf(false) }
    var pendingApkFileKey by remember { mutableStateOf<String?>(null) }
    var pendingApkFileName by remember { mutableStateOf("") }
    var pendingApkSentAt by remember { mutableStateOf(0L) }
    // Кроп + подпись перед отправкой
    var pendingCropUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingCropReplyId by remember { mutableStateOf<Int?>(null) }
    var imageCaption by remember { mutableStateOf("") }
    // Полноэкранный просмотр фото
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    // Скачивание фото
    var showImageDownloadDialog by remember { mutableStateOf(false) }
    var downloadingImageKey by remember { mutableStateOf<String?>(null) }
    var imageDownloadProgress by remember { mutableStateOf<Float?>(null) }
    var imageDownloadError by remember { mutableStateOf<String?>(null) }
    var imageDownloadSavedPath by remember { mutableStateOf<String?>(null) }

    fun sendImageFromUri(uri: android.net.Uri, caption: String, replyId: Int?) {
        scope.launch {
            imageUploadProgress = 0f
            imageUploadError = null
            val result = uploadChatImage(
                ctx = context,
                repository = repository,
                chatId = chatId,
                uri = uri,
                onProgress = { imageUploadProgress = it }
            )
            if (result != null) {
                imageUploadProgress = null
                // Предзагружаем presigned URL в кеш ДО добавления tempMsg
                // чтобы при замене tempMsg→sent пузырь не мигал
                val preloadedUrl = repository.getChatImagePlayUrl(result)
                if (preloadedUrl != null) imageUrlCache[result] = preloadedUrl

                val localId = -(System.currentTimeMillis().toInt())
                val tempMsg = su.SkrinVex.ofox.data.Message(
                    id = localId, chatId = chatId, text = caption,
                    timestamp = System.currentTimeMillis(), isFromMe = true,
                    messageType = "image", imageKey = result,
                    status = "sending"
                )
                messages.add(tempMsg)
                listState.scrollToItem(messages.size - 1)
                val sent = repository.sendImageMessage(chatId, result, replyId, caption)
                val idx = messages.indexOfFirst { it.id == localId }
                if (idx != -1) {
                    if (sent != null) {
                        // Удаляем дубликат от polling если он уже появился
                        messages.removeAll { it.id == sent.id && it.id != localId }
                        messages[idx] = sent
                    } else {
                        messages.removeAt(idx)
                    }
                }
            } else {
                imageUploadProgress = null
                imageUploadError = "Не удалось загрузить фото"
            }
        }
    }

    val cropLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val croppedUri = com.yalantis.ucrop.UCrop.getOutput(result.data!!)
            if (croppedUri != null) {
                pendingCropUri = croppedUri
                // Показываем диалог подписи
            }
        }
    }

    val imagePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val replyMsg = replyTo
            pendingCropReplyId = replyMsg?.let { r ->
                if (r.id > 0) r.id
                else messages.firstOrNull { it.timestamp == r.timestamp && it.id > 0 }?.id
            }
            replyTo = null
            // Запускаем UCrop
            val destUri = android.net.Uri.fromFile(
                java.io.File(context.cacheDir, "chat_crop_${System.currentTimeMillis()}.jpg")
            )
            val cropIntent = com.yalantis.ucrop.UCrop.of(uri, destUri)
                .withOptions(com.yalantis.ucrop.UCrop.Options().apply {
                    setFreeStyleCropEnabled(true)
                    setCompressionQuality(90)
                })
                .withMaxResultSize(1280, 1280)
                .getIntent(context)
            cropLauncher.launch(cropIntent)
        }
    }

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val replyId = pendingFileReplyId
            pendingFileReplyId = null
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val cursor = context.contentResolver.query(uri, arrayOf(
                android.provider.OpenableColumns.DISPLAY_NAME,
                android.provider.OpenableColumns.SIZE
            ), null, null, null)
            var fileName = "Файл"
            var fileSize = 0L
            cursor?.use { if (it.moveToFirst()) { fileName = it.getString(0) ?: "Файл"; fileSize = it.getLong(1) } }

            val localId = -(System.currentTimeMillis())
            val tempMsg = su.SkrinVex.ofox.data.Message(
                id = localId.toInt(), chatId = chatId, text = "",
                timestamp = System.currentTimeMillis(), isFromMe = true,
                messageType = "file", fileName = fileName, fileSize = fileSize,
                fileMime = context.contentResolver.getType(uri) ?: "application/octet-stream",
                status = "sending"
            )
            messages.add(tempMsg)
            scope.launch { listState.scrollToItem(messages.size - 1) }

            su.SkrinVex.ofox.FileUploadService.start(context, chatId, uri, replyId, localId)
        }
    }

    // SnapshotStateMap для прогресса — гарантирует рекомпозицию карточки
    val fileProgressMap = remember { androidx.compose.runtime.snapshots.SnapshotStateMap<Int, Float>() }

    // Подписка на StateFlow сервиса
    val uploadState by su.SkrinVex.ofox.FileUploadService.state.collectAsState()
    LaunchedEffect(uploadState) {
        when (val s = uploadState) {
            is su.SkrinVex.ofox.FileUploadService.Companion.UploadState.Uploading -> {
                if (s.chatId == chatId) {
                    fileProgressMap[s.localId.toInt()] = s.progress
                    // Восстанавливаем карточку если её нет (перезашли в чат)
                    if (messages.none { it.id == s.localId.toInt() }) {
                        messages.add(su.SkrinVex.ofox.data.Message(
                            id = s.localId.toInt(), chatId = chatId, text = "",
                            timestamp = System.currentTimeMillis(), isFromMe = true,
                            messageType = "file", fileName = s.fileName, fileSize = 0L,
                            status = "sending"
                        ))
                    }
                }
            }
            is su.SkrinVex.ofox.FileUploadService.Companion.UploadState.Done -> {
                fileProgressMap.remove(s.localId.toInt())
                val idx = messages.indexOfFirst { it.id == s.localId.toInt() }
                if (idx != -1) {
                    messages.removeAll { it.id == s.message.id && it.id != s.localId.toInt() }
                    messages[idx] = s.message
                } else {
                    // Карточки нет — просто добавляем готовое сообщение
                    messages.add(s.message)
                }
                su.SkrinVex.ofox.FileUploadService.resetState()
            }
            is su.SkrinVex.ofox.FileUploadService.Companion.UploadState.Error -> {
                fileProgressMap.remove(s.localId.toInt())
                val idx = messages.indexOfFirst { it.id == s.localId.toInt() }
                if (idx != -1) messages[idx] = messages[idx].copy(status = "error")
                su.SkrinVex.ofox.FileUploadService.resetState()
            }
            is su.SkrinVex.ofox.FileUploadService.Companion.UploadState.Cancelled -> {
                fileProgressMap.remove(s.localId.toInt())
                messages.removeAll { it.id == s.localId.toInt() }
                su.SkrinVex.ofox.FileUploadService.resetState()
            }
            else -> {}
        }
    }

    // Диалог отмены загрузки файла
    var showCancelUploadDialog by remember { mutableStateOf(false) }
    if (showCancelUploadDialog) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCancelUploadDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Отменить отправку?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text("Файл не будет отправлен.", style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showCancelUploadDialog = false }, modifier = Modifier.weight(1f)) { Text("Нет") }
                        Button(
                            onClick = {
                                showCancelUploadDialog = false
                                context.startService(android.content.Intent(context, su.SkrinVex.ofox.FileUploadService::class.java).apply {
                                    action = su.SkrinVex.ofox.FileUploadService.ACTION_CANCEL
                                })
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) { Text("Отменить") }
                    }
                }
            }
        }
    }

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
                val lastKnownId = messages.filter { it.id > 0 }.maxOfOrNull { it.id } ?: 0
                // Ключи медиа из pending tempMsg (id < 0) — не добавляем дубликаты
                val pendingVoiceKeys = messages.filter { it.id < 0 }.mapNotNull { it.voiceKey }.toSet()
                val pendingImageKeys = messages.filter { it.id < 0 }.mapNotNull { it.imageKey }.toSet()
                val pendingFileKeys = messages.filter { it.id < 0 }.mapNotNull { it.fileKey }.toSet()
                loaded.filter { it.id > lastKnownId }.forEach { msg ->
                    val isDuplicate = messages.any { it.id == msg.id }
                        || (msg.voiceKey != null && msg.voiceKey in pendingVoiceKeys)
                        || (msg.imageKey != null && msg.imageKey in pendingImageKeys)
                        || (msg.fileKey != null && msg.fileKey in pendingFileKeys)
                    if (!isDuplicate) messages.add(msg)
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
                is su.SkrinVex.ofox.data.api.WSEvent.MessageDeleted -> {
                    if (event.chatId == chatId) {
                        messages.removeAll { it.id == event.messageId }
                        // Убираем reply-ссылки на удалённое сообщение
                        messages.indices.forEach { i ->
                            if (messages[i].replyToId == event.messageId) {
                                messages[i] = messages[i].copy(replyToId = null, replyToText = null, replyToSenderName = null)
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
                            replyToSenderName = event.replyToSenderName,
                            imageKey = event.imageKey,
                            fileKey = event.fileKey, fileName = event.fileName ?: "",
                            fileSize = event.fileSize, fileMime = event.fileMime
                        )
                        if (!messages.any { it.timestamp == event.timestamp && it.senderId == event.senderId }) {
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
                            replyToSenderName = event.replyToSenderName,
                            imageKey = event.imageKey,
                            fileKey = event.fileKey, fileName = event.fileName ?: "",
                            fileSize = event.fileSize, fileMime = event.fileMime
                        )
                        if (!messages.any { it.timestamp == event.timestamp && it.senderId == event.senderId }) {
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
                        onImageClick = { url -> fullscreenImageUrl = url },
                        onFileClick = { key, fileName, sentAt ->
                            val isApk = fileName.endsWith(".apk", ignoreCase = true)
                            if (isApk) {
                                pendingApkFileKey = key; pendingApkFileName = fileName; pendingApkSentAt = sentAt
                                showApkWarningDialog = true
                            } else {
                                openingFileKey = key; openingFileName = fileName; openFileSentAt = sentAt
                                showOpenFileDialog = true
                            }
                        },
                        imageUrlCache = imageUrlCache,
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
                        isDiscoveryChat = (chat?.discoveryId ?: 0) != 0,
                        fileUploadProgress = fileProgressMap[message.id]
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
                                reply.messageType == "image" -> "Фотография"
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
            if (messageText.isBlank()) {
                IconButton(onClick = { showAttachSheet = true }) {
                    if (imageUploadProgress != null || fileUploadProgress != null) {
                        CircularProgressIndicator(
                            progress = { (imageUploadProgress ?: fileUploadProgress)!! },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Прикрепить",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
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

    // Диалог подтверждения удаления сообщения
    if (messageToDelete != null) {
        val msg = messageToDelete!!
        androidx.compose.ui.window.Dialog(onDismissRequest = { messageToDelete = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Delete, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Удалить сообщение?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Сообщение будет удалено без следов для всех участников чата.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            val toDelete = messageToDelete
                            messageToDelete = null
                            if (toDelete != null) {
                                val realId = if (toDelete.id > 0) toDelete.id
                                             else messages.firstOrNull { it.timestamp == toDelete.timestamp && it.id > 0 }?.id ?: 0
                                if (realId > 0) scope.launch {
                                    val ok = repository.deleteMessage(chatId, realId)
                                    if (ok) {
                                        messages.removeAll { it.id == realId }
                                        messages.indices.forEach { i ->
                                            if (messages[i].replyToId == realId) {
                                                messages[i] = messages[i].copy(replyToId = null, replyToText = null, replyToSenderName = null)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Удалить") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { messageToDelete = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("Отмена")
                    }
                }
            }
        }
    }

    // Диалог прогресса скачивания (вне bottomsheet — живёт независимо от selectedMessage)
    if (showDownloadDialog) {        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(downloadingVoiceKey) {
            val key = downloadingVoiceKey ?: return@LaunchedEffect
            downloadVoiceMessage(
                ctx = ctx,
                repository = repository,
                voiceKey = key,
                chatName = downloadingChatName,
                sentAt = downloadingSentAt,
                onProgress = { downloadProgress = it },
                onError = { downloadError = it },
                onSavedPath = { downloadSavedPath = it }
            )
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    when {
                        downloadError != null -> {
                            Icon(Icons.Default.ErrorOutline, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Ошибка загрузки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text(downloadError!!, style = MaterialTheme.typography.bodyMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                showDownloadDialog = false; downloadProgress = null
                                downloadError = null; downloadingVoiceKey = null; downloadSavedPath = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                        }
                        downloadProgress == 1f -> {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Файл сохранён", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (downloadSavedPath != null) {
                                Text(downloadSavedPath!!, style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                showDownloadDialog = false; downloadProgress = null
                                downloadError = null; downloadingVoiceKey = null; downloadSavedPath = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                        }
                        else -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Сохранение...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (downloadProgress != null) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress!! },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                Text("${(downloadProgress!! * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    // Предупреждение об автоудалении фото (первый раз)
    if (showImageWarningDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Фото в чатах", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Фотографии в чатах автоматически удаляются через 7 дней после отправки в целях экономии места на серверах Ofox.\n\nЕсли фото важно — зажмите на него и выберите «Скачать фото», чтобы сохранить на устройство.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            repository.setImageWarningShown()
                            showImageWarningDialog = false
                            imagePickerLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Понятно") }
                }
            }
        }
    }

    // Диалог подписи после кропа
    if (pendingCropUri != null) {
        val croppedUri = pendingCropUri!!
        androidx.compose.ui.window.Dialog(onDismissRequest = { pendingCropUri = null; imageCaption = "" }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AsyncImage(
                        model = croppedUri,
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(MaterialTheme.shapes.medium)
                    )
                    TextField(
                        value = imageCaption,
                        onValueChange = { imageCaption = it },
                        placeholder = { Text("Подпись (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = MaterialTheme.shapes.medium,
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pendingCropUri = null; imageCaption = "" }, modifier = Modifier.weight(1f)) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = {
                                val uri = croppedUri
                                val caption = imageCaption
                                val replyId = pendingCropReplyId
                                pendingCropUri = null
                                imageCaption = ""
                                pendingCropReplyId = null
                                sendImageFromUri(uri, caption, replyId)
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Отправить") }
                    }
                }
            }
        }
    }

    // Полноэкранный просмотр фото
    if (fullscreenImageUrl != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { fullscreenImageUrl = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                me.saket.telephoto.zoomable.coil.ZoomableAsyncImage(
                    model = fullscreenImageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                IconButton(onClick = { fullscreenImageUrl = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = Color.White)
                }
            }
        }
    }

    // Диалог ошибки загрузки фото
    if (imageUploadError != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { imageUploadError = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Ошибка загрузки фото", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(imageUploadError!!, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { imageUploadError = null }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                }
            }
        }
    }

    // Bottom sheet — выбор вложения
    if (showAttachSheet) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Прикрепить", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp))
                // Изображение
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            showAttachSheet = false
                            val replyMsg = replyTo
                            pendingCropReplyId = replyMsg?.let { r ->
                                if (r.id > 0) r.id
                                else messages.firstOrNull { it.timestamp == r.timestamp && it.id > 0 }?.id
                            }
                            replyTo = null
                            if (repository.isImageWarningShown()) {
                                imagePickerLauncher.launch("image/*")
                            } else {
                                showImageWarningDialog = true
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Изображение", style = MaterialTheme.typography.bodyLarge)
                }
                // Видео — в разработке
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.Videocam, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                    Column {
                        Text("Видео", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
                        Text("В разработке", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                    }
                }
                // Файл
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            showAttachSheet = false
                            val replyMsg = replyTo
                            pendingFileReplyId = replyMsg?.let { r ->
                                if (r.id > 0) r.id
                                else messages.firstOrNull { it.timestamp == r.timestamp && it.id > 0 }?.id
                            }
                            replyTo = null
                            if (repository.isFileWarningShown()) {
                                filePickerLauncher.launch("*/*")
                            } else {
                                showFileWarningDialog = true
                            }
                        }
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Файл", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // Предупреждение об автоудалении файлов (первый раз)
    if (showFileWarningDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.InsertDriveFile, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("Файлы в чатах", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Файлы в чатах автоматически удаляются через 3 дня после отправки в целях экономии места на серверах Ofox.\n\nМаксимальный размер файла — 5 ГБ.\n\nЕсли файл важен — сохраните его на устройство до истечения срока.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            repository.setFileWarningShown()
                            showFileWarningDialog = false
                            filePickerLauncher.launch("*/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Понятно") }
                }
            }
        }
    }

    // Диалог скачивания файла
    if (showFileDownloadDialog) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(downloadingFileKey) {
            val key = downloadingFileKey ?: return@LaunchedEffect
            downloadChatFile(
                ctx = ctx,
                repository = repository,
                fileKey = key,
                fileName = downloadingFileName,
                chatName = downloadingChatName,
                sentAt = downloadingSentAt,
                onProgress = { fileDownloadProgress = it },
                onError = { fileDownloadError = it },
                onSavedPath = { fileDownloadSavedPath = it }
            )
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    when {
                        fileDownloadError != null -> {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Ошибка загрузки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text(fileDownloadError!!, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                showFileDownloadDialog = false; fileDownloadProgress = null
                                fileDownloadError = null; downloadingFileKey = null; fileDownloadSavedPath = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                        }
                        fileDownloadProgress == 1f -> {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Файл сохранён", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (fileDownloadSavedPath != null) {
                                Text(fileDownloadSavedPath!!, style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                showFileDownloadDialog = false; fileDownloadProgress = null
                                fileDownloadError = null; downloadingFileKey = null; fileDownloadSavedPath = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                        }
                        else -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Сохранение...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (fileDownloadProgress != null) {
                                LinearProgressIndicator(progress = { fileDownloadProgress!! }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Text("${(fileDownloadProgress!! * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог открытия файла (скачивает во временный кеш и открывает через Intent)
    if (showOpenFileDialog) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        var openProgress by remember { mutableStateOf<Float?>(null) }
        var openError by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(openingFileKey) {
            val key = openingFileKey ?: return@LaunchedEffect
            openProgress = 0f
            openError = null
            try {
                val info = repository.getChatFileDownloadUrl(key, chat?.name ?: "chat", openFileSentAt)
                if (info == null) { openError = "Не удалось получить ссылку"; return@LaunchedEffect }
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val response = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(okhttp3.Request.Builder().url(info.downloadUrl).build()).execute()
                }
                if (!response.isSuccessful) { openError = "Ошибка (${response.code})"; return@LaunchedEffect }
                val body = response.body ?: run { openError = "Пустой ответ"; return@LaunchedEffect }
                val total = body.contentLength().toFloat()
                val file = java.io.File(ctx.cacheDir, info.filename.ifBlank { openingFileName })
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val buf = ByteArray(65536); var downloaded = 0L
                    body.byteStream().use { input -> file.outputStream().use { out ->
                        var n: Int
                        while (input.read(buf).also { n = it } != -1) {
                            out.write(buf, 0, n); downloaded += n
                            if (total > 0) openProgress = (downloaded / total).coerceIn(0f, 0.99f)
                        }
                    }}
                }
                openProgress = 1f
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
                val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                ctx.startActivity(android.content.Intent.createChooser(intent, "Открыть с помощью"))
                showOpenFileDialog = false; openingFileKey = null
            } catch (e: Exception) {
                openError = "Ошибка: ${e.message}"
            }
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { if (openProgress == null || openError != null) { showOpenFileDialog = false; openingFileKey = null } },
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = openError != null, dismissOnClickOutside = false)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        openError != null -> {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Ошибка", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(openError!!, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showOpenFileDialog = false; openingFileKey = null }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                        }
                        else -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(12.dp))
                            Text("Открытие файла…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (openProgress != null && openProgress!! > 0f) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { openProgress!! }, modifier = Modifier.fillMaxWidth())
                                Text("${(openProgress!! * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            } else {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }

    // Предупреждение перед открытием APK
    if (showApkWarningDialog) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val apkWarnShownKey = "apk_warn_shown"
        androidx.compose.ui.window.Dialog(onDismissRequest = { showApkWarningDialog = false }) {
            Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Установка приложения", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Вы собираетесь установить APK-файл «${pendingApkFileName}».\n\nУстанавливайте приложения только от людей, которым доверяете. Вредоносные приложения могут навредить вашему устройству.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            showApkWarningDialog = false
                            openingFileKey = pendingApkFileKey; openingFileName = pendingApkFileName; openFileSentAt = pendingApkSentAt
                            showOpenFileDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Я доверяю, открыть") }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showApkWarningDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
                }
            }
        }
    }

    // Диалог скачивания фото
    if (showImageDownloadDialog) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(downloadingImageKey) {
            val key = downloadingImageKey ?: return@LaunchedEffect
            downloadChatImage(
                ctx = ctx,
                repository = repository,
                imageKey = key,
                chatName = downloadingChatName,
                sentAt = downloadingSentAt,
                onProgress = { imageDownloadProgress = it },
                onError = { imageDownloadError = it },
                onSavedPath = { imageDownloadSavedPath = it }
            )
        }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = {},
            properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    when {
                        imageDownloadError != null -> {
                            Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Ошибка загрузки", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            Text(imageDownloadError!!, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                showImageDownloadDialog = false; imageDownloadProgress = null
                                imageDownloadError = null; downloadingImageKey = null; imageDownloadSavedPath = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Закрыть") }
                        }
                        imageDownloadProgress == 1f -> {
                            Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Фото сохранено", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (imageDownloadSavedPath != null) {
                                Text(imageDownloadSavedPath!!, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = {
                                showImageDownloadDialog = false; imageDownloadProgress = null
                                imageDownloadError = null; downloadingImageKey = null; imageDownloadSavedPath = null
                            }, modifier = Modifier.fillMaxWidth()) { Text("Готово") }
                        }
                        else -> {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
                            Spacer(Modifier.height(16.dp))
                            Text("Сохранение...", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(12.dp))
                            if (imageDownloadProgress != null) {
                                LinearProgressIndicator(progress = { imageDownloadProgress!! }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(6.dp))
                                Text("${(imageDownloadProgress!! * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
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
                    HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
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
                if (msg.messageType == "voice" && msg.voiceKey != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                downloadingVoiceKey = msg.voiceKey
                                downloadingChatName = chat?.name ?: "chat"
                                downloadingSentAt = msg.timestamp
                                downloadProgress = null
                                downloadError = null
                                showDownloadDialog = true
                                selectedMessage = null
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Скачать", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (msg.messageType == "image" && msg.imageKey != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                downloadingImageKey = msg.imageKey
                                downloadingChatName = chat?.name ?: "chat"
                                downloadingSentAt = msg.timestamp
                                imageDownloadProgress = null
                                imageDownloadError = null
                                showImageDownloadDialog = true
                                selectedMessage = null
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Скачать фото", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (msg.messageType == "file" && msg.fileKey != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable {
                                downloadingFileKey = msg.fileKey
                                downloadingFileName = msg.fileName ?: "file"
                                downloadingChatName = chat?.name ?: "chat"
                                downloadingSentAt = msg.timestamp
                                fileDownloadProgress = null
                                fileDownloadError = null
                                fileDownloadSavedPath = null
                                showFileDownloadDialog = true
                                selectedMessage = null
                            }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary)
                        Text("Скачать файл", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                // Отменить отправку файла
                if (msg.messageType == "file" && msg.status == "sending" && msg.isFromMe) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { showCancelUploadDialog = true; selectedMessage = null }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
                        Text("Отменить отправку", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
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
                // Удалить — только своё сообщение с реальным id
                val realIdForDelete = if (msg.id > 0) msg.id
                                      else messages.firstOrNull { it.timestamp == msg.timestamp && it.id > 0 }?.id ?: 0
                if (msg.isFromMe && realIdForDelete > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { messageToDelete = msg; selectedMessage = null }
                            .padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        Text("Удалить", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
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
    onImageClick: ((String) -> Unit)? = null,
    onFileClick: ((key: String, fileName: String, sentAt: Long) -> Unit)? = null,
    imageUrlCache: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>? = null,
    currentUserId: Int = 0,
    reactionsJson: String = "",
    isHighlighted: Boolean = false,
    isDiscoveryChat: Boolean = false,
    fileUploadProgress: Float? = null
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
                var showDeletedDialog by remember { mutableStateOf(false) }
                if (showDeletedDialog) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showDeletedDialog = false }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Block, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Голосовое удалено", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Голосовые сообщения автоматически удаляются через 7 дней после отправки в целях экономии места на серверах Ofox.\n\nЧтобы сохранить важные сообщения, зажмите на них и выберите «Скачать» до истечения срока.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { showDeletedDialog = false }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Понятно")
                                }
                            }
                        }
                    }
                }

                if (message.voiceDeletedByServer) {
                    // Затычка удалённого голосового
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.isFromMe)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 260.dp).padding(start = startPadding)
                            .combinedClickable(onClick = { showDeletedDialog = true }, onLongClick = { onLongPress?.invoke() })
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Block,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (message.isFromMe)
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Column {
                                Text(
                                    "Голосовое сообщение удалено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (message.isFromMe)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                                Text(
                                    formatTime(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (message.isFromMe)
                                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                } else if (message.voiceKey != null) {
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
                                repository = repository,
                                senderName = message.senderName,
                                senderAvatarUrl = message.senderAvatarUrl
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
            "image" -> {
                var showDeletedImageDialog by remember { mutableStateOf(false) }
                if (showDeletedImageDialog) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showDeletedImageDialog = false }) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.Block, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Фото удалено", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    "Фотографии в чатах автоматически удаляются через 7 дней после отправки в целях экономии места на серверах Ofox.\n\nЧтобы сохранить важные фото, зажмите на них и выберите «Скачать фото» до истечения срока.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { showDeletedImageDialog = false }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Понятно")
                                }
                            }
                        }
                    }
                }

                if (message.imageDeletedByServer) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.isFromMe)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 260.dp).padding(start = startPadding)
                            .combinedClickable(onClick = { showDeletedImageDialog = true }, onLongClick = { onLongPress?.invoke() })
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Block, null,
                                modifier = Modifier.size(18.dp),
                                tint = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                            )
                            Column {
                                Text(
                                    "Фото удалено",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                )
                                Text(
                                    formatTime(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                } else if (message.imageKey != null) {
                    val imgCtx = androidx.compose.ui.platform.LocalContext.current
                    var imageUrl by remember(message.imageKey) { mutableStateOf(imageUrlCache?.get(message.imageKey)) }
                    var isLoadingUrl by remember(message.imageKey) { mutableStateOf(imageUrl == null) }
                    LaunchedEffect(message.imageKey) {
                        if (imageUrl == null) {
                            val url = repository.getChatImagePlayUrl(message.imageKey)
                            if (url != null) {
                                imageUrlCache?.set(message.imageKey, url)
                                imageUrl = url
                            }
                            isLoadingUrl = false
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.isFromMe) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.widthIn(max = 260.dp).padding(start = startPadding)
                            .combinedClickable(
                                onClick = { imageUrl?.let { onImageClick?.invoke(it) } },
                                onLongClick = { onLongPress?.invoke() }
                            )
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            ReplyQuote(message, onReplyClick, isFromMe = message.isFromMe)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 120.dp, max = 240.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingUrl || imageUrl == null) {
                                    val inf = androidx.compose.animation.core.rememberInfiniteTransition()
                                    val a by inf.animateFloat(0.2f, 0.6f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(700), androidx.compose.animation.core.RepeatMode.Reverse))
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = a)), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                                    }
                                } else {
                                    AsyncImage(
                                        model = imageUrl,
                                        contentDescription = "Фото",
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            if (message.text.isNotBlank()) {
                                Text(
                                    message.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp, start = 2.dp, end = 2.dp)
                                )
                            }
                            if (message.status == "sending") {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                            Row(
                                modifier = Modifier.align(Alignment.End).padding(top = 4.dp, end = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    formatTime(message.timestamp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
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
            "file" -> {
                val isDeleted = message.fileKey == null && message.status != "sending"
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (message.isFromMe)
                            (if (isDeleted) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary)
                        else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.widthIn(max = 280.dp).padding(start = startPadding)
                        .combinedClickable(
                            onClick = {
                                if (!isDeleted && message.fileKey != null)
                                    onFileClick?.invoke(message.fileKey, message.fileName ?: "file", message.timestamp)
                            },
                            onLongClick = { onLongPress?.invoke() }
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        ReplyQuote(message, onReplyClick, isFromMe = message.isFromMe)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(
                                if (isDeleted) Icons.Default.Block else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isDeleted) 0.5f else 1f)
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDeleted) 0.4f else 0.8f)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (isDeleted) "Файл удалён" else (message.fileName ?: "Файл"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = if (isDeleted) 0.6f else 1f)
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDeleted) 0.5f else 1f)
                                )
                                if (!isDeleted && (message.fileSize ?: 0L) > 0L) {
                                    Text(
                                        formatFileSize(message.fileSize!!),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        if (message.status == "sending") {
                            Spacer(Modifier.height(6.dp))
                            if (fileUploadProgress != null) {
                                LinearProgressIndicator(
                                    progress = { fileUploadProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${(fileUploadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f))
                                    TextButton(
                                        onClick = { onLongPress?.invoke() },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                                    ) {
                                        Text("Отменить", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
                                    }
                                }
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f)
                                )
                            }
                        }
                        if (message.status == "error") {
                            Text("Ошибка отправки", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        }
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(formatTime(message.timestamp), style = MaterialTheme.typography.labelSmall,
                                color = if (message.isFromMe) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            if (message.isFromMe) MessageStatusIcon(message.status, true)
                        }
                    }
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
        } // end when

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

// Скачивание голосового сообщения в Ofox/voices
private suspend fun downloadVoiceMessage(
    ctx: android.content.Context,
    repository: su.SkrinVex.ofox.data.Repository,
    voiceKey: String,
    chatName: String,
    sentAt: Long,
    onProgress: (Float) -> Unit,
    onError: (String) -> Unit,
    onSavedPath: (String) -> Unit
) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val info = repository.getVoiceDownloadUrl(voiceKey, chatName, sentAt)
        if (info == null) { onError("Не удалось получить ссылку для скачивания"); return@withContext }

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val response = client.newCall(okhttp3.Request.Builder().url(info.downloadUrl).build()).execute()
        if (!response.isSuccessful) { onError("Ошибка загрузки (${response.code})"); return@withContext }

        val body = response.body ?: run { onError("Пустой ответ сервера"); return@withContext }
        val total = body.contentLength().toFloat()
        val filename = info.filename

        val savedPath: String

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "audio/mp4")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Music/Ofox/voices")
                put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
            }
            val collection = android.provider.MediaStore.Audio.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = ctx.contentResolver.insert(collection, values)
                ?: run { onError("Не удалось создать файл"); return@withContext }
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                body.byteStream().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded / total).coerceIn(0f, 0.99f))
                    }
                }
            }
            val update = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
            }
            ctx.contentResolver.update(uri, update, null, null)
            savedPath = "Music/Ofox/voices/$filename"
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(
                android.os.Environment.getExternalStorageDirectory(),
                "Music/Ofox/voices"
            )
            dir.mkdirs()
            val file = java.io.File(dir, filename)
            val buf = ByteArray(8192)
            var downloaded = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded / total).coerceIn(0f, 0.99f))
                    }
                }
            }
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("audio/mp4"), null)
            savedPath = file.absolutePath
        }

        onProgress(1f)
        onSavedPath(savedPath)
    } catch (e: Exception) {
        android.util.Log.e("VoiceDownload", "Error", e)
        onError("Ошибка: ${e.message}")
    }
}

// Загрузка фото в приватный бакет через presigned PUT URL
private suspend fun uploadChatImage(
    ctx: android.content.Context,
    repository: su.SkrinVex.ofox.data.Repository,
    chatId: Int,
    uri: android.net.Uri,
    onProgress: (Float) -> Unit
): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val mimeType = ctx.contentResolver.getType(uri) ?: "image/jpeg"
        val info = repository.getChatImageUploadUrl(chatId, mimeType) ?: return@withContext null
        val stream = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
        val bytes = stream.readBytes()
        stream.close()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val total = bytes.size.toFloat()
        var uploaded = 0L
        val requestBody = object : okhttp3.RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = bytes.size.toLong()
            override fun writeTo(sink: okio.BufferedSink) {
                val buf = ByteArray(8192)
                bytes.inputStream().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        sink.write(buf, 0, n)
                        uploaded += n
                        if (total > 0) onProgress((uploaded / total).coerceIn(0f, 0.99f))
                    }
                }
            }
        }

        val request = okhttp3.Request.Builder()
            .url(info.uploadUrl)
            .put(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        onProgress(1f)
        info.key
    } catch (e: Exception) {
        android.util.Log.e("ImageUpload", "Error", e)
        null
    }
}

// Скачивание фото из чата в галерею
private suspend fun downloadChatImage(
    ctx: android.content.Context,
    repository: su.SkrinVex.ofox.data.Repository,
    imageKey: String,
    chatName: String,
    sentAt: Long,
    onProgress: (Float) -> Unit,
    onError: (String) -> Unit,
    onSavedPath: (String) -> Unit
) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val info = repository.getChatImageDownloadUrl(imageKey, chatName, sentAt)
        if (info == null) { onError("Не удалось получить ссылку для скачивания"); return@withContext }

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val response = client.newCall(okhttp3.Request.Builder().url(info.downloadUrl).build()).execute()
        if (!response.isSuccessful) { onError("Ошибка загрузки (${response.code})"); return@withContext }

        val body = response.body ?: run { onError("Пустой ответ сервера"); return@withContext }
        val total = body.contentLength().toFloat()
        val filename = info.filename
        val ext = filename.substringAfterLast('.', "jpg")
        val mimeType = when (ext.lowercase()) {
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }

        val savedPath: String

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Ofox")
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
            }
            val collection = android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = ctx.contentResolver.insert(collection, values)
                ?: run { onError("Не удалось создать файл"); return@withContext }
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                val buf = ByteArray(8192)
                var downloaded = 0L
                body.byteStream().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded / total).coerceIn(0f, 0.99f))
                    }
                }
            }
            val update = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
            }
            ctx.contentResolver.update(uri, update, null, null)
            savedPath = "Pictures/Ofox/$filename"
        } else {
            @Suppress("DEPRECATION")
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Ofox")
            dir.mkdirs()
            val file = java.io.File(dir, filename)
            val buf = ByteArray(8192)
            var downloaded = 0L
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded / total).coerceIn(0f, 0.99f))
                    }
                }
            }
            android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf(mimeType), null)
            savedPath = file.absolutePath
        }

        onProgress(1f)
        onSavedPath(savedPath)
    } catch (e: Exception) {
        android.util.Log.e("ImageDownload", "Error", e)
        onError("Ошибка: ${e.message}")
    }
}

private data class FileUploadResult(val key: String, val fileName: String, val fileSize: Long, val mimeType: String)

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f ГБ".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f МБ".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
private suspend fun uploadChatFile(
    ctx: android.content.Context,
    repository: su.SkrinVex.ofox.data.Repository,
    chatId: Int,
    uri: android.net.Uri,
    onProgress: (Float) -> Unit
): FileUploadResult? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val mimeType = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
        val cursor = ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE), null, null, null)
        var fileName = "file"
        var fileSize = 0L
        cursor?.use {
            if (it.moveToFirst()) {
                fileName = it.getString(0) ?: "file"
                fileSize = it.getLong(1)
            }
        }

        val info = repository.getChatFileUploadUrl(chatId, fileName, mimeType) ?: return@withContext null
        val stream = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
        val bytes = stream.readBytes()
        stream.close()
        if (fileSize == 0L) fileSize = bytes.size.toLong()

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val total = bytes.size.toFloat()
        var uploaded = 0L
        val requestBody = object : okhttp3.RequestBody() {
            override fun contentType() = mimeType.toMediaTypeOrNull()
            override fun contentLength() = bytes.size.toLong()
            override fun writeTo(sink: okio.BufferedSink) {
                val buf = ByteArray(8192)
                bytes.inputStream().use { input ->
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        sink.write(buf, 0, n)
                        uploaded += n
                        if (total > 0) onProgress((uploaded / total).coerceIn(0f, 0.99f))
                    }
                }
            }
        }

        val request = okhttp3.Request.Builder()
            .url(info.uploadUrl)
            .put(requestBody)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return@withContext null
        onProgress(1f)
        FileUploadResult(key = info.key, fileName = fileName, fileSize = fileSize, mimeType = mimeType)
    } catch (e: Exception) {
        android.util.Log.e("FileUpload", "Error", e)
        null
    }
}

private suspend fun downloadChatFile(
    ctx: android.content.Context,
    repository: su.SkrinVex.ofox.data.Repository,
    fileKey: String,
    fileName: String,
    chatName: String,
    sentAt: Long,
    onProgress: (Float) -> Unit,
    onError: (String) -> Unit,
    onSavedPath: (String) -> Unit
) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        val info = repository.getChatFileDownloadUrl(fileKey, chatName, sentAt)
        if (info == null) { onError("Не удалось получить ссылку для скачивания"); return@withContext }

        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val response = client.newCall(okhttp3.Request.Builder().url(info.downloadUrl).build()).execute()
        if (!response.isSuccessful) { onError("Ошибка загрузки (${response.code})"); return@withContext }

        val body = response.body ?: run { onError("Пустой ответ сервера"); return@withContext }
        val total = body.contentLength().toFloat()
        val safeFileName = info.filename.ifBlank { fileName }

        val dir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Ofox"
        )
        dir.mkdirs()
        val file = java.io.File(dir, safeFileName)
        val buf = ByteArray(8192)
        var downloaded = 0L
        body.byteStream().use { input ->
            file.outputStream().use { out ->
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    out.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress((downloaded / total).coerceIn(0f, 0.99f))
                }
            }
        }
        android.media.MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), null, null)
        onProgress(1f)
        onSavedPath(file.absolutePath)
    } catch (e: Exception) {
        android.util.Log.e("FileDownload", "Error", e)
        onError("Ошибка: ${e.message}")
    }
}
