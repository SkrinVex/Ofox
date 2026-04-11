package su.SkrinVex.ofox.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.api.models.BadgeResponse
import su.SkrinVex.ofox.data.api.models.CommentResponse
import su.SkrinVex.ofox.utils.formatTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    comment: CommentResponse,
    currentUserId: Int?,
    isHighlighted: Boolean = false,
    onLongPress: () -> Unit,
    onMenuClick: () -> Unit,
    onAuthorClick: () -> Unit,
    onReply: () -> Unit = {}
) {
    val highlightAlpha by animateFloatAsState(
        targetValue = if (isHighlighted) 0.15f else 0f,
        animationSpec = tween(400)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (comment.is_pinned) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                else Modifier
            )
            .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha))
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        UserAvatar(
            name = comment.author_name,
            avatarUrl = comment.author_avatar_url?.takeIf { it.isNotBlank() },
            size = 32.dp,
            modifier = Modifier.clickable(onClick = onAuthorClick)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = comment.author_name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(onClick = onAuthorClick)
                )
                if (!comment.author_badges.isNullOrEmpty()) {
                    UserBadges(badges = comment.author_badges)
                }
                if (comment.is_pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = "Закреплено",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            if (comment.reply_to_author_name != null) {
                Text(
                    text = "↩ ${comment.reply_to_author_name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LinkedText(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(parseTimestamp(comment.created_at)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "Ответить",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onReply)
                )
            }
        }

        IconButton(onClick = onMenuClick, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Опции",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun parseTimestamp(dateStr: String): Long {
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(dateStr)?.time ?: System.currentTimeMillis()
    } catch (e: Exception) {
        System.currentTimeMillis()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    postId: Int,
    comments: List<CommentResponse>,
    currentUserId: Int?,
    postAuthorId: Int? = null,
    commentDrafts: MutableMap<Int, String>,
    repository: su.SkrinVex.ofox.data.Repository,
    onDismiss: () -> Unit,
    onSendComment: (String, Int?) -> Unit,
    onDeleteComment: (Int) -> Unit,
    onAuthorClick: (Int) -> Unit
) {
    var commentText by remember { mutableStateOf(commentDrafts[postId] ?: "") }
    var selectedCommentId by remember { mutableStateOf<Int?>(null) }
    var commentIdToDelete by remember { mutableStateOf<Int?>(null) }
    var commentIdToReport by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }
    var reportReason by remember { mutableStateOf("") }
    var showRules by remember { mutableStateOf(false) }
    var replyTo by remember { mutableStateOf<CommentResponse?>(null) }
    var localPinnedId by remember { mutableStateOf(comments.find { it.is_pinned }?.id) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var highlightedCommentId by remember { mutableStateOf<Int?>(null) }
    val expandedReplies = remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(commentText) {
        commentDrafts[postId] = commentText
    }

    // Строим дерево: топ-уровень + ответы
    // Все ответы в треде показываем под корневым (плоский список как в TikTok)
    val topLevel = remember(comments) { comments.filter { it.reply_to_id == null } }
    // Для каждого ответа находим корневой комментарий (может быть ответ на ответ)
    val commentById = remember(comments) { comments.associateBy { it.id } }
    fun rootOf(c: CommentResponse): Int {
        var cur = c
        while (cur.reply_to_id != null) {
            cur = commentById[cur.reply_to_id] ?: break
        }
        return cur.id
    }
    val repliesMap = remember(comments) {
        comments.filter { it.reply_to_id != null }.groupBy { rootOf(it) }
    }

    // Применяем локальный pin
    fun effectiveComment(c: CommentResponse) = c.copy(is_pinned = c.id == localPinnedId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Комментарии",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { showRules = true }) {
                    Icon(Icons.Default.Info, contentDescription = "Правила", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Divider()

            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Пока нет комментариев",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                } else {
                    // Закреплённый первым
                    val pinnedComment = comments.find { it.id == localPinnedId }
                    if (pinnedComment != null) {
                        item(key = "pinned_${pinnedComment.id}") {
                            CommentItem(
                                comment = effectiveComment(pinnedComment),
                                currentUserId = currentUserId,
                                isHighlighted = highlightedCommentId == pinnedComment.id,
                                onLongPress = { selectedCommentId = pinnedComment.id },
                                onMenuClick = { selectedCommentId = pinnedComment.id },
                                onAuthorClick = { if (pinnedComment.author_id != currentUserId) onAuthorClick(pinnedComment.author_id) },
                                onReply = { replyTo = pinnedComment; commentText = "" }
                            )
                            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }

                    // Топ-уровень (не ответы), пропускаем закреплённый — он уже вверху
                    val displayTop = topLevel.filter { it.id != localPinnedId }
                    items(displayTop.size) { idx ->
                        val comment = effectiveComment(displayTop[idx])
                        CommentItem(
                            comment = comment,
                            currentUserId = currentUserId,
                            isHighlighted = highlightedCommentId == comment.id,
                            onLongPress = { selectedCommentId = comment.id },
                            onMenuClick = { selectedCommentId = comment.id },
                            onAuthorClick = { if (comment.author_id != currentUserId) onAuthorClick(comment.author_id) },
                            onReply = { replyTo = comment; commentText = "" }
                        )

                        // Ответы на этот комментарий (все в треде, плоско)
                        val replies = repliesMap[comment.id] ?: emptyList()
                        if (replies.isNotEmpty()) {
                            val expanded = comment.id in expandedReplies.value
                            // Кнопка показать/скрыть
                            Row(
                                modifier = Modifier
                                    .padding(start = 44.dp, bottom = 4.dp)
                                    .clickable {
                                        expandedReplies.value = if (expanded)
                                            expandedReplies.value - comment.id
                                        else
                                            expandedReplies.value + comment.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(modifier = Modifier.width(20.dp).height(1.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)))
                                Text(
                                    text = if (expanded) "Скрыть ответы" else "Показать ответы (${replies.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (expanded) {
                                // Вертикальная линия иерархии (Reddit-стиль)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 28.dp, end = 8.dp)
                                            .width(2.dp)
                                            .fillMaxHeight()
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                                RoundedCornerShape(1.dp)
                                            )
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        replies.forEach { reply ->
                                            val r = effectiveComment(reply)
                                            CommentItem(
                                                comment = r,
                                                currentUserId = currentUserId,
                                                isHighlighted = highlightedCommentId == r.id,
                                                onLongPress = { selectedCommentId = r.id },
                                                onMenuClick = { selectedCommentId = r.id },
                                                onAuthorClick = { if (r.author_id != currentUserId) onAuthorClick(r.author_id) },
                                                onReply = { replyTo = r; commentText = "" }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable {
                            val idx = comments.indexOfFirst { it.id == replyTo!!.id }
                            if (idx != -1) {
                                scope.launch {
                                    listState.animateScrollToItem(idx)
                                    highlightedCommentId = replyTo!!.id
                                    kotlinx.coroutines.delay(1500)
                                    highlightedCommentId = null
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(3.dp).height(32.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(replyTo!!.author_name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text(
                                replyTo!!.content.take(60) + if (replyTo!!.content.length > 60) "…" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(onClick = { replyTo = null }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    TextField(
                        value = commentText,
                        onValueChange = { if (it.length <= 450) commentText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (replyTo != null) "Ответить ${replyTo!!.author_name}..."
                                else "Написать комментарий...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 5
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (commentText.isNotBlank()) {
                            onSendComment(commentText, replyTo?.id)
                            // Авто-раскрываем ответы если отвечаем
                            replyTo?.id?.let { parentId ->
                                expandedReplies.value = expandedReplies.value + parentId
                            }
                            commentText = ""
                            replyTo = null
                            commentDrafts.remove(postId)
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            if (commentText.isNotEmpty()) {
                Text(
                    text = "${commentText.length}/450",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (commentText.length > 400) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }
    }

    if (selectedCommentId != null && !showDeleteDialog && !showReportDialog) {
        val selectedComment = comments.find { it.id == selectedCommentId }
        CommentOptionsBottomSheet(
            onDismiss = { selectedCommentId = null },
            onDelete = {
                commentIdToDelete = selectedCommentId
                selectedCommentId = null
                showDeleteDialog = true
            },
            onPin = if (postAuthorId == currentUserId) {
                {
                    val cid = selectedCommentId!!
                    scope.launch {
                        repository.pinComment(postId, cid)
                        localPinnedId = if (localPinnedId == cid) null else cid
                    }
                    selectedCommentId = null
                }
            } else null,
            onReport = if (selectedComment?.author_id != currentUserId) {
                {
                    commentIdToReport = selectedCommentId
                    selectedCommentId = null
                    reportReason = ""
                    showReportDialog = true
                }
            } else null,
            isPinned = selectedComment?.id == localPinnedId,
            content = selectedComment?.content ?: "",
            canDelete = selectedComment?.author_id == currentUserId || postAuthorId == currentUserId
        )
    }

    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false; commentIdToDelete = null }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text("Удалить комментарий?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Это действие нельзя отменить", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showDeleteDialog = false }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("Отмена") }
                        Button(
                            onClick = {
                                commentIdToDelete?.let { onDeleteComment(it) }
                                if (commentIdToDelete == localPinnedId) localPinnedId = null
                                showDeleteDialog = false
                                commentIdToDelete = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("Удалить") }
                    }
                }
            }
        }
    }

    if (showRules) {
        CommentRulesBottomSheet(repository = repository, onDismiss = { showRules = false })
    }

    if (showReportDialog) {
        val reportReasons = listOf("Спам", "Оскорбление", "Угрозы", "Нарушение правил", "Другое")
        Dialog(onDismissRequest = { showReportDialog = false; commentIdToReport = null }) {
            Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Report, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Пожаловаться", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    reportReasons.forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .background(if (reportReason == reason) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { reportReason = reason }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = reportReason == reason, onClick = { reportReason = reason },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                            Spacer(Modifier.width(8.dp))
                            Text(reason, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showReportDialog = false; commentIdToReport = null }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium) { Text("Отмена") }
                        Button(
                            onClick = {
                                commentIdToReport?.let { cid ->
                                    scope.launch {
                                        repository.reportComment(cid, reportReason)
                                        android.widget.Toast.makeText(context, "Жалоба отправлена. Спасибо!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                                showReportDialog = false
                                commentIdToReport = null
                            },
                            enabled = reportReason.isNotBlank(), modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text("Отправить") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentOptionsBottomSheet(
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onPin: (() -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    isPinned: Boolean = false,
    content: String = "",
    canDelete: Boolean = true
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Действия с комментарием", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().clickable {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("comment", content))
                    android.widget.Toast.makeText(context, "Текст скопирован", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                }.padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(16.dp))
                Text("Копировать текст", style = MaterialTheme.typography.bodyMedium)
            }

            if (onPin != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPin(); onDismiss() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(if (isPinned) "Открепить" else "Закрепить", style = MaterialTheme.typography.bodyMedium)
                }
            }

            if (onReport != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onReport(); onDismiss() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Report, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Пожаловаться", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            if (canDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onDelete(); onDismiss() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Удалить комментарий", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentRulesBottomSheet(
    repository: su.SkrinVex.ofox.data.Repository,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var rulesContent by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { rulesContent = repository.getCommentRules() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            if (rulesContent.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                MarkdownText(rulesContent)
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String) {
    markdown.split("\n").forEach { line ->
        val t = line.trim()
        when {
            t.startsWith("### ") -> Text(t.removePrefix("### "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
            t.startsWith("## ")  -> Text(t.removePrefix("## "),  style = MaterialTheme.typography.titleLarge,  fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            t.startsWith("# ")   -> Text(t.removePrefix("# "),   style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 12.dp))
            t.matches(Regex("^-{3,}$")) || t.matches(Regex("^\\*{3,}$")) -> Divider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            t.startsWith("- ") || t.startsWith("* ") -> Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                Text("• ", style = MaterialTheme.typography.bodyLarge)
                FormattedText(t.removePrefix("- ").removePrefix("* "), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            t.matches(Regex("^\\d+\\.\\s+.*")) -> Row(modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)) {
                Text("${t.substringBefore(".")}. ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                FormattedText(t.substringAfter(". ").trim(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            t.isNotBlank() -> FormattedText(t, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 4.dp))
            else -> Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun FormattedText(text: String, style: androidx.compose.ui.text.TextStyle, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    val annotated = remember(text) {
        androidx.compose.ui.text.buildAnnotatedString {
            val regex = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
            var last = 0
            regex.findAll(text).forEach { m ->
                if (m.range.first > last) append(text.substring(last, m.range.first))
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(m.groupValues[1].ifEmpty { m.groupValues[2] })
                pop()
                last = m.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    Text(text = annotated, style = style, color = color, modifier = modifier)
}
