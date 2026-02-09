package su.SkrinVex.ofox.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.screens.CreativePost
import su.SkrinVex.ofox.screens.PostType
import su.SkrinVex.ofox.utils.formatTime
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun HashtagText(
    text: String,
    style: TextStyle,
    color: Color,
    hashtagColor: Color,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {},
    onHashtagClick: (String) -> Unit = {}
) {
    val annotatedString = buildAnnotatedString {
        val regex = Regex("""(#\w+)|(\s+)|(\n)""")
        var lastIndex = 0

        regex.findAll(text).forEach { match ->
            // Добавляем текст до совпадения
            if (match.range.first > lastIndex) {
                withStyle(style = SpanStyle(color = color)) {
                    append(text.substring(lastIndex, match.range.first))
                }
            }

            when {
                match.value.startsWith("#") -> {
                    pushStringAnnotation(tag = "HASHTAG", annotation = match.value)
                    withStyle(style = SpanStyle(color = hashtagColor, fontWeight = FontWeight.SemiBold)) {
                        append(match.value)
                    }
                    pop()
                }
                else -> append(match.value)
            }
            lastIndex = match.range.last + 1
        }

        // Добавляем оставшийся текст
        if (lastIndex < text.length) {
            withStyle(style = SpanStyle(color = color)) {
                append(text.substring(lastIndex))
            }
        }
    }

    ClickableText(
        text = annotatedString,
        style = style,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "HASHTAG", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    onHashtagClick(annotation.item)
                }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class) // Важно для FlowRow
@Composable
fun CreativePostCard(
    post: CreativePost,
    isLiked: Boolean = false,
    isHighlighted: Boolean = false,
    onLike: () -> Unit,
    onComment: () -> Unit,
    onShare: () -> Unit,
    onMoreClick: () -> Unit,
    onVote: (Int) -> Unit = {},
    onAuthorClick: () -> Unit = {},
    onHashtagClick: (String) -> Unit = {}
) {
    var liked by remember { mutableStateOf(isLiked) }
    var likesCount by remember { mutableStateOf(post.likes) }
    var selectedPollOption by remember { mutableStateOf<String?>(null) }
    var shouldHighlight by remember { mutableStateOf(false) }

    LaunchedEffect(isLiked) {
        liked = isLiked
    }

    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            shouldHighlight = true
            kotlinx.coroutines.delay(2000)
            shouldHighlight = false
        }
    }

    val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shouldHighlight) 0.2f else 0f,
        animationSpec = androidx.compose.animation.core.tween(500)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (animatedAlpha > 0) {
                MaterialTheme.colorScheme.primary.copy(alpha = animatedAlpha)
            } else when (post.type) {
                PostType.MOOD -> MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                PostType.QUOTE -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
                PostType.POLL -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // --- HEADER START ---
            Row(
                verticalAlignment = Alignment.Top, // ИСПРАВЛЕНО: было crossAxisAlignment
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAuthorClick)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = post.author.first().toString(),
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Используем FlowRow для умного переноса элементов
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Имя автора
                        Text(
                            text = post.author,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        // Бейджи пользователя
                        if (post.authorBadges.isNotEmpty()) {
                            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                                UserBadges(badges = post.authorBadges)
                            }
                        }

                        // Бейдж типа поста
                        if (post.type != PostType.TEXT) {
                            Badge(
                                containerColor = when (post.type) {
                                    PostType.MOOD -> Color(0xFFFFB74D)
                                    PostType.QUOTE -> Color(0xFF81C784)
                                    PostType.POLL -> Color(0xFF64B5F6)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = when (post.type) {
                                        PostType.MOOD -> "Настроение"
                                        PostType.QUOTE -> "Цитата"
                                        PostType.POLL -> "Опрос"
                                        else -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Бейдж открытия (#Hashtag)
                        if (post.discoveryId > 0 && post.discoveryColor.isNotBlank()) {
                            val bgColor = try {
                                Color(android.graphics.Color.parseColor(post.discoveryColor))
                            } catch (e: Exception) {
                                MaterialTheme.colorScheme.primary
                            }

                            // Используем Surface вместо Badge для создания "Тега"
                            Surface(
                                color = bgColor,
                                shape = RoundedCornerShape(16.dp), // Сильное закругление (как у чипов)
                                modifier = Modifier.align(Alignment.CenterVertically)
                            ) {
                                Text(
                                    text = "#${post.discoveryTitle}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White // Белый текст читается лучше на цветных плашках
                                    ),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp) // Компактные отступы
                                    // Убрали maxLines и overflow, теперь текст будет показываться полностью
                                    // и переноситься на новую строку внутри FlowRow, если нужно
                                )
                            }
                        }
                    }

                    // Время поста
                    Text(
                        text = post.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
            // --- HEADER END ---

            Spacer(modifier = Modifier.height(12.dp))

            // Content
            var isExpanded by remember { mutableStateOf(false) }
            val hasOverflow = remember { mutableStateOf(false) }

            Column {
                HashtagText(
                    text = post.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    hashtagColor = MaterialTheme.colorScheme.primary,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 5,
                    onTextLayout = { result ->
                        if (!isExpanded && !hasOverflow.value) {
                            hasOverflow.value = result.hasVisualOverflow
                        }
                    },
                    onHashtagClick = onHashtagClick
                )

                if (hasOverflow.value) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "Свернуть" else "Читать далее",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Polls
            if (post.type == PostType.POLL && post.pollOptions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                val totalVotes = post.pollVotes.sum()
                val hasVoted = post.userVote >= 0

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    post.pollOptions.forEachIndexed { index, option ->
                        val votes = post.pollVotes.getOrNull(index) ?: 0
                        val percentage = if (totalVotes > 0) (votes * 100f / totalVotes) else 0f
                        val isSelected = post.userVote == index

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !hasVoted) { onVote(index) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                if (hasVoted) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(percentage / 100f)
                                            .height(48.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                                RoundedCornerShape(12.dp)
                                            )
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (hasVoted) {
                                        Text(
                                            text = "${percentage.toInt()}%",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (hasVoted) {
                        Text(
                            text = "$totalVotes ${if (totalVotes == 1) "голос" else if (totalVotes < 5) "голоса" else "голосов"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Actions
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        liked = !liked
                        onLike()
                    }
                ) {
                    Icon(
                        if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Лайк",
                        tint = if (liked) Color.Red else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.likes.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onComment() }
                ) {
                    Icon(
                        Icons.Default.ChatBubbleOutline,
                        contentDescription = "Комментарии",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.comments.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onShare() }
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Поделиться",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = post.shares.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareBottomSheet(
    postId: Int,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    var showSnackbar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Поделиться",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val link = "https://api.skrinvex.su/ofox/post/$postId"
                        val clip = android.content.ClipData.newPlainText("Post Link", link)
                        clipboardManager.setPrimaryClip(clip)
                        showSnackbar = true
                        scope.launch {
                            kotlinx.coroutines.delay(1500)
                            onDismiss()
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = "Копировать ссылку",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Копировать ссылку",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (showSnackbar) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Ссылка скопирована",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostMenuBottomSheet(
    isMyPost: Boolean,
    onDismiss: () -> Unit,
    onAction: (String) -> Unit
) {
    val menuOptions = if (isMyPost) {
        listOf(
            "Скрыть пост" to Icons.Default.VisibilityOff,
            "Скопировать текст" to Icons.Default.ContentCopy,
            "Удалить пост" to Icons.Default.Delete
        )
    } else {
        listOf(
            "Скрыть пост" to Icons.Default.VisibilityOff,
            "Пожаловаться" to Icons.Default.Report,
            "Скопировать текст" to Icons.Default.ContentCopy,
            "Не показывать от автора" to Icons.Default.Block
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Действия с постом",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            menuOptions.forEach { (name, icon) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onAction(name)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        icon,
                        contentDescription = name,
                        tint = if (name == "Удалить пост")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (name == "Удалить пост")
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareDiscoveryBottomSheet(
    discoveryId: Int,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    var showSnackbar by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Поделиться",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val link = "https://api.skrinvex.su/ofox/discovery/$discoveryId"
                        val clip = android.content.ClipData.newPlainText("Discovery Link", link)
                        clipboardManager.setPrimaryClip(clip)
                        showSnackbar = true
                        scope.launch {
                            kotlinx.coroutines.delay(1500)
                            onDismiss()
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Link,
                    contentDescription = "Копировать ссылку",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Копировать ссылку",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (showSnackbar) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.material3.Card(
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Ссылка скопирована",
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashtagSearchDialog(
    hashtag: String,
    posts: List<su.SkrinVex.ofox.data.Post>,
    currentUserId: Int?,
    onDismiss: () -> Unit,
    onPostClick: (Int) -> Unit,
    onLike: (su.SkrinVex.ofox.data.Post) -> Unit,
    onShare: (Int) -> Unit,
    onMoreClick: (su.SkrinVex.ofox.data.Post) -> Unit,
    onVote: (Int, Int) -> Unit,
    onAuthorClick: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Посты с $hashtag",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (posts.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Постов с этим хештегом не найдено",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(posts.size) { index ->
                        val post = posts[index]
                        val badges = try {
                            if (post.authorBadges.isNotEmpty()) {
                                val jsonArray = org.json.JSONArray(post.authorBadges)
                                (0 until jsonArray.length()).map { i ->
                                    val obj = jsonArray.getJSONObject(i)
                                    su.SkrinVex.ofox.data.api.models.BadgeResponse(
                                        badge_type = obj.getString("badge_type"),
                                        description = obj.getString("description")
                                    )
                                }
                            } else emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }

                        CreativePostCard(
                            post = CreativePost(
                                post.authorName,
                                post.content,
                                post.likes,
                                post.comments,
                                post.shares,
                                formatTime(post.timestamp),
                                PostType.valueOf(post.type),
                                post.pollOptions.split("|||").filter { it.isNotEmpty() },
                                post.pollVotes.split(",").mapNotNull { it.toIntOrNull() },
                                post.userVote,
                                post.authorId,
                                post.discoveryId,
                                post.discoveryTitle,
                                post.discoveryColor,
                                badges
                            ),
                            isLiked = post.isLiked,
                            onLike = { onLike(post) },
                            onComment = { },
                            onShare = { onShare(post.id) },
                            onMoreClick = { onMoreClick(post) },
                            onVote = { optionIndex -> onVote(post.id, optionIndex) },
                            onAuthorClick = {
                                if (post.authorId != currentUserId) {
                                    onAuthorClick(post.authorId)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
