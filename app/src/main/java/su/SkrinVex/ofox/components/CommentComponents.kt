package su.SkrinVex.ofox.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.api.models.BadgeResponse
import su.SkrinVex.ofox.data.api.models.CommentResponse
import su.SkrinVex.ofox.utils.formatTime

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
    comment: CommentResponse,
    currentUserId: Int?,
    onLongPress: () -> Unit,
    onMenuClick: () -> Unit,
    onAuthorClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress
            )
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
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatTime(parseTimestamp(comment.created_at)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        if (comment.author_id == currentUserId) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Опции",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
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
    commentDrafts: MutableMap<Int, String>,
    repository: su.SkrinVex.ofox.data.Repository,
    onDismiss: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Int) -> Unit,
    onAuthorClick: (Int) -> Unit
) {
    var commentText by remember { mutableStateOf(commentDrafts[postId] ?: "") }
    var selectedCommentId by remember { mutableStateOf<Int?>(null) }
    var commentIdToDelete by remember { mutableStateOf<Int?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(commentText) {
        commentDrafts[postId] = commentText
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Правила",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Divider()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (comments.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
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
                    items(comments.size) { index ->
                        CommentItem(
                            comment = comments[index],
                            currentUserId = currentUserId,
                            onLongPress = { 
                                if (comments[index].author_id == currentUserId) {
                                    selectedCommentId = comments[index].id
                                }
                            },
                            onMenuClick = {
                                if (comments[index].author_id == currentUserId) {
                                    selectedCommentId = comments[index].id
                                }
                            },
                            onAuthorClick = {
                                if (comments[index].author_id != currentUserId) {
                                    onAuthorClick(comments[index].author_id)
                                }
                            }
                        )
                    }
                }
            }

            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    TextField(
                        value = commentText,
                        onValueChange = { 
                            if (it.length <= 450) {
                                commentText = it
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { 
                            Text(
                                "Написать комментарий...",
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
                            onSendComment(commentText)
                            commentText = ""
                            commentDrafts.remove(postId)
                        }
                    },
                    enabled = commentText.isNotBlank(),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (commentText.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }

            if (commentText.isNotEmpty()) {
                Text(
                    text = "${commentText.length}/450",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (commentText.length > 400) 
                        MaterialTheme.colorScheme.error 
                    else 
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }
    }

    if (selectedCommentId != null && !showDeleteDialog) {
        CommentOptionsBottomSheet(
            onDismiss = { selectedCommentId = null },
            onDelete = {
                commentIdToDelete = selectedCommentId
                selectedCommentId = null
                showDeleteDialog = true
            }
        )
    }

    if (showDeleteDialog) {
        Dialog(onDismissRequest = { 
            showDeleteDialog = false
            commentIdToDelete = null
        }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Удалить комментарий?",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Это действие нельзя отменить",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDeleteDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Отмена")
                        }
                        
                        Button(
                            onClick = {
                                android.util.Log.d("CommentDelete", "Delete button clicked, commentId: $commentIdToDelete")
                                commentIdToDelete?.let { 
                                    android.util.Log.d("CommentDelete", "Calling onDeleteComment with id: $it")
                                    onDeleteComment(it) 
                                }
                                showDeleteDialog = false
                                commentIdToDelete = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("Удалить")
                        }
                    }
                }
            }
        }
    }

    if (showRules) {
        CommentRulesBottomSheet(
            repository = repository,
            onDismiss = { showRules = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentOptionsBottomSheet(
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Действия с комментарием",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onDelete()
                        onDismiss()
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Удалить комментарий",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        rulesContent = repository.getCommentRules()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            if (rulesContent.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                MarkdownText(rulesContent)
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String) {
    val lines = markdown.split("\n")
    var i = 0
    
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        
        when {
            // Заголовки - проверяем от большего к меньшему
            trimmed.startsWith("### ") -> {
                Text(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
            }
            trimmed.startsWith("## ") -> {
                Text(
                    text = trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
            }
            trimmed.startsWith("# ") -> {
                Text(
                    text = trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )
            }
            // Горизонтальная линия
            trimmed.matches(Regex("^-{3,}$")) || trimmed.matches(Regex("^\\*{3,}$")) -> {
                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                )
            }
            // Списки
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    FormattedText(
                        text = trimmed.removePrefix("- ").removePrefix("* "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Нумерованные списки
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val number = trimmed.substringBefore(".").trim()
                val text = trimmed.substringAfter(". ").trim()
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "$number. ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    FormattedText(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Обычный текст
            trimmed.isNotBlank() -> {
                FormattedText(
                    text = trimmed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            // Пустая строка
            else -> {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        i++
    }
}

@Composable
fun FormattedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        var currentText = text
        var currentIndex = 0
        
        // Паттерны для форматирования
        val patterns = listOf(
            Regex("""\*\*(.+?)\*\*""") to FontWeight.Bold,  // **жирный**
            Regex("""__(.+?)__""") to FontWeight.Bold,      // __жирный__
        )
        
        val allMatches = mutableListOf<Triple<Int, Int, FontWeight>>()
        
        // Находим все совпадения
        patterns.forEach { (regex, weight) ->
            regex.findAll(text).forEach { match ->
                allMatches.add(Triple(match.range.first, match.range.last + 1, weight))
            }
        }
        
        // Сортируем по позиции
        allMatches.sortBy { it.first }
        
        if (allMatches.isEmpty()) {
            append(text)
        } else {
            var lastIndex = 0
            allMatches.forEach { (start, end, weight) ->
                // Добавляем текст до форматирования
                if (start > lastIndex) {
                    append(text.substring(lastIndex, start))
                }
                
                // Извлекаем текст внутри маркеров
                val markerLength = if (text.substring(start, minOf(start + 2, text.length)) == "**" || 
                                       text.substring(start, minOf(start + 2, text.length)) == "__") 2 else 1
                val innerText = text.substring(start + markerLength, end - markerLength)
                
                // Добавляем форматированный текст
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = weight))
                append(innerText)
                pop()
                
                lastIndex = end
            }
            
            // Добавляем оставшийся текст
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        color = color,
        modifier = modifier
    )
}
