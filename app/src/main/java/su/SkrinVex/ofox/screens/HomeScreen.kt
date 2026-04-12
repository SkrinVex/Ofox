package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.CreativePostCard
import su.SkrinVex.ofox.components.ShareBottomSheet
import su.SkrinVex.ofox.components.PostMenuBottomSheet
import su.SkrinVex.ofox.components.CommentsBottomSheet
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.utils.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: Repository,
    navController: androidx.navigation.NavController? = null,
    highlightPostId: Int? = null,
    hideOnScroll: Boolean = false,
    onBarsVisibilityChange: (Boolean) -> Unit = {}
) {
    var showShareMenu by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    var showCreatePost by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showHashtagSearch by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var selectedHashtag by remember { mutableStateOf("") }
    var hashtagPosts by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Post>()) }
    var selectedPostId by remember { mutableStateOf(0) }
    var selectedPost by remember { mutableStateOf<su.SkrinVex.ofox.data.Post?>(null) }
    var comments by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.api.models.CommentResponse>()) }
    val commentDrafts = remember { mutableMapOf<Int, String>() }
    var postDraftContent by remember { mutableStateOf("") }
    var postDraftType by remember { mutableStateOf("TEXT") }
    var postDraftPollOptions by remember { mutableStateOf(listOf("", "")) }
    var postDraftDiscovery by remember { mutableStateOf<su.SkrinVex.ofox.data.Discovery?>(null) }
    val posts = remember { androidx.compose.runtime.snapshots.SnapshotStateList<su.SkrinVex.ofox.data.Post>() }
    var currentUser by remember { mutableStateOf<su.SkrinVex.ofox.data.User?>(null) }
    var isInitialized by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var localHighlightPostId by remember { mutableStateOf<Int?>(null) }
    val subscribedToMeMap = remember { mutableStateMapOf<Int, Boolean>() }
    var hiddenAuthorIds by remember { mutableStateOf(repository.getHiddenAuthorIds()) }
    var imageUploadError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Обновляем список скрытых авторов при каждом появлении экрана
    LaunchedEffect(Unit) {
        hiddenAuthorIds = repository.getHiddenAuthorIds()
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val wsClient = remember { su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(context) }

    // Скрытие/показ баров при скролле
    var barsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var prevIndex = 0
        var prevOffset = 0
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                val scrollingDown = index > prevIndex || (index == prevIndex && offset > prevOffset + 10)
                val scrollingUp = index < prevIndex || (index == prevIndex && offset < prevOffset - 10)
                if (scrollingDown && barsVisible) {
                    barsVisible = false
                    if (hideOnScroll) onBarsVisibilityChange(false)
                } else if (scrollingUp && !barsVisible) {
                    barsVisible = true
                    if (hideOnScroll) onBarsVisibilityChange(true)
                }
                prevIndex = index
                prevOffset = offset
            }
    }
    val topBarAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (!hideOnScroll || barsVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200)
    )
    // FAB скрывается при скролле всегда
    val fabVisible by remember { derivedStateOf { posts.isEmpty() || (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 50) } }
    val fabScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (fabVisible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200)
    )

    LaunchedEffect(highlightPostId) {
        if (highlightPostId != null && highlightPostId != localHighlightPostId) {
            localHighlightPostId = highlightPostId
            scope.launch { repository.markNotificationsReadByPost(highlightPostId) }
            kotlinx.coroutines.delay(5000)
            localHighlightPostId = null
        }
    }

    fun loadPosts(offset: Int = 0, force: Boolean = false) {
        if (offset == 0) {
            if (isLoading) return
            isLoading = true
        } else {
            if (isLoadingMore) return
            isLoadingMore = true
        }

        scope.launch {
            try {
                val newPosts = repository.getAllPosts(limit = 20, offset = offset)
                if (offset == 0 && force) {
                    posts.clear()
                    posts.addAll(newPosts)
                } else if (offset == 0) {
                    // Обновление: мержим с существующими
                    val merged = (newPosts + posts).distinctBy { it.id }
                    posts.clear()
                    posts.addAll(merged)
                } else {
                    // Пагинация
                    val existingIds = posts.map { it.id }.toSet()
                    newPosts.filter { it.id !in existingIds }.forEach { posts.add(it) }
                }
                hasMore = newPosts.size == 20
                
                // Загружаем информацию о подписках для всех авторов
                val authorIds = newPosts.map { it.authorId }.distinct()
                authorIds.forEach { authorId ->
                    if (!subscribedToMeMap.containsKey(authorId)) {
                        try {
                            val subscribedToMe = repository.isSubscribedToMe(authorId)
                            val iSubscribed = repository.isSubscribed(authorId)
                            subscribedToMeMap[authorId] = subscribedToMe && !iSubscribed
                        } catch (e: Exception) {
                            subscribedToMeMap[authorId] = false
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("HomeScreen", "Error loading posts", e)
            } finally {
                isLoading = false
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            if (currentUser == null) {
                currentUser = repository.getCurrentUser()
            }
            if (posts.isEmpty() && isInitialized.not()) {
                loadPosts(0, force = true)
                isInitialized = true
            }
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Error loading user", e)
        }
    }

    LaunchedEffect(localHighlightPostId) {
        localHighlightPostId?.let { postId ->
            var index = posts.indexOfFirst { it.id == postId }
            if (index == -1) {
                // Пост не найден, загружаем до него
                var offset = posts.size
                while (index == -1 && offset < 200) {
                    try {
                        val newPosts = repository.getAllPosts(limit = 10, offset = offset)
                        if (newPosts.isEmpty()) break
                        val existingIds = posts.map { it.id }.toSet()
                        newPosts.filter { it.id !in existingIds }.forEach { posts.add(it) }
                        index = posts.indexOfFirst { it.id == postId }
                        offset += 10
                        kotlinx.coroutines.delay(100)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error loading posts for highlight", e)
                        break
                    }
                }
            }
            if (index != -1) {
                kotlinx.coroutines.delay(500)
                listState.animateScrollToItem(index)
            }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000)
            if (highlightPostId == null) {
                try {
                    val freshPosts = repository.getAllPosts(limit = 20, offset = 0)
                    freshPosts.forEach { fresh ->
                        val index = posts.indexOfFirst { it.id == fresh.id }
                        if (index != -1) {
                            // Обновляем существующий если изменились данные
                            if (posts[index].likes != fresh.likes ||
                                posts[index].shares != fresh.shares ||
                                posts[index].authorBadges != fresh.authorBadges ||
                                posts[index].images != fresh.images ||
                                posts[index].authorAvatarUrl != fresh.authorAvatarUrl) {
                                posts[index] = fresh
                            }
                        } else {
                            // Новый пост — добавляем в начало
                            posts.add(0, fresh)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("HomeScreen", "Polling error", e)
                }
            }
        }
    }

    LaunchedEffect(wsClient.events) {
        wsClient.events.collect { event ->
            android.util.Log.d("HomeScreen", "Received event: $event")
            when (event) {
                is su.SkrinVex.ofox.data.api.WSEvent.BadgeUpdate -> {
                    android.util.Log.d("HomeScreen", "Badge update for user ${event.userId}, badges: ${event.badges}")
                    val badgesJson = org.json.JSONArray(event.badges.map { badge ->
                        org.json.JSONObject().apply {
                            put("badge_type", badge.type)
                            put("description", badge.description)
                        }
                    }).toString()

                    var updatedCount = 0
                    posts.indices.forEach { index ->
                        if (posts[index].authorId == event.userId) {
                            posts[index] = posts[index].copy(authorBadges = badgesJson)
                            updatedCount++
                        }
                    }
                    android.util.Log.d("HomeScreen", "Updated $updatedCount posts for user ${event.userId}")
                }
                is su.SkrinVex.ofox.data.api.WSEvent.PostUpdate -> {
                    android.util.Log.d("HomeScreen", "Post update: ${event.postId}, likes: ${event.likes}, shares: ${event.shares}")
                    val index = posts.indexOfFirst { it.id == event.postId }
                    if (index != -1) {
                        val post = posts[index]
                        posts[index] = post.copy(
                            likes = if (event.likes >= 0) event.likes else post.likes,
                            shares = if (event.shares >= 0) event.shares else post.shares,
                            // Сохраняем userVote и другие поля
                            userVote = post.userVote,
                            isLiked = post.isLiked
                        )
                        android.util.Log.d("HomeScreen", "Updated post ${event.postId}")
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.NewPost -> {
                    scope.launch {
                        try {
                            if (posts.any { it.id == event.postId }) return@launch
                            // Загружаем полный пост с изображениями и аватаром
                            repository.getPostById(event.postId)?.let { posts.add(0, it) }
                        } catch (e: Exception) {
                            android.util.Log.e("HomeScreen", "Error loading new post", e)
                        }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.NewMessage,
                is su.SkrinVex.ofox.data.api.WSEvent.ChatUpdate,
                is su.SkrinVex.ofox.data.api.WSEvent.Typing,
                is su.SkrinVex.ofox.data.api.WSEvent.Warning,
                is su.SkrinVex.ofox.data.api.WSEvent.Ban,
                is su.SkrinVex.ofox.data.api.WSEvent.CommentReply,
                is su.SkrinVex.ofox.data.api.WSEvent.PostComment,
                is su.SkrinVex.ofox.data.api.WSEvent.DiscoveryMessage,
                is su.SkrinVex.ofox.data.api.WSEvent.UserOnline,
                is su.SkrinVex.ofox.data.api.WSEvent.UserOffline,
                is su.SkrinVex.ofox.data.api.WSEvent.ChatRead,
                is su.SkrinVex.ofox.data.api.WSEvent.NewFollower -> {}
                is su.SkrinVex.ofox.data.api.WSEvent.ContentDeleted -> {
                    if (event.contentType == "post") {
                        posts.removeAll { it.id == event.contentId }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.NewComment -> {
                    android.util.Log.d("HomeScreen", "New comment for post ${event.postId}")
                    if (showComments && selectedPostId == event.postId) {
                        if (comments.none { it.id == event.comment.id }) {
                            comments = comments + event.comment
                        }
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.DeleteComment -> {
                    android.util.Log.d("HomeScreen", "Delete comment ${event.commentId} from post ${event.postId}")
                    if (showComments && selectedPostId == event.postId) {
                        comments = comments.filter { it.id != event.commentId }
                    }
                }
                null -> {}
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= posts.size - 3 && !isLoadingMore && !isLoading && hasMore) {
                    loadPosts(posts.size)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp).zIndex(10f),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.medium,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    actionColor = MaterialTheme.colorScheme.primary
                )
            }
        )
        if (isLoading && posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().zIndex(0f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (!isLoading && posts.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().zIndex(0f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Нет постов",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Создайте первый пост",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(0f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val uniquePosts by remember {
                derivedStateOf { posts.distinctBy { it.id }.filter { it.authorId !in hiddenAuthorIds } }
            }

            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(start = 16.dp, top = 96.dp, end = 16.dp, bottom = 80.dp)
            ) {
                items(uniquePosts, key = { it.id }) { post ->
                    val badges = remember(post.authorBadges) {
                        try {
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
                        } catch (e: Exception) { emptyList() }
                    }

                    val isHighlighted = localHighlightPostId == post.id
                    
                    // Получаем информацию о подписке из кеша
                    val isAuthorSubscribedToMe = subscribedToMeMap[post.authorId] ?: false

                    CreativePostCard(
                        post = su.SkrinVex.ofox.screens.CreativePost(
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
                            badges,
                            post.authorAvatarUrl,
                            post.images.split("|||").filter { it.isNotEmpty() }
                        ),
                        isLiked = post.isLiked,
                        isHighlighted = isHighlighted,
                        isAuthorSubscribedToMe = isAuthorSubscribedToMe,
                        currentUserId = currentUser?.id,
                        isDiscoveryPost = post.isDiscoveryPost,
                        onLike = {
                            scope.launch {
                                repository.toggleLike(post)?.let { updatedPost ->
                                    val index = posts.indexOfFirst { it.id == updatedPost.id }
                                    if (index != -1) posts[index] = updatedPost
                                }
                            }
                        },
                        onComment = {
                            selectedPostId = post.id
                            scope.launch {
                                comments = repository.getComments(post.id)
                                showComments = true
                            }
                        },
                        onShare = {
                            selectedPostId = post.id
                            showShareMenu = true
                        },
                        onMoreClick = {
                            selectedPost = post
                            selectedPostId = post.id
                            showPostMenu = true
                        },
                        onVote = { optionIndex ->
                            scope.launch {
                                repository.voteOnPoll(post.id, optionIndex)?.let { updatedPost ->
                                    val index = posts.indexOfFirst { it.id == updatedPost.id }
                                    if (index != -1) posts[index] = updatedPost
                                }
                            }
                        },
                        onAuthorClick = {
                            if (post.authorId != currentUser?.id) {
                                navController?.navigate("user_profile/${post.authorId}")
                            }
                        },
                        onHashtagClick = { hashtag ->
                            selectedHashtag = hashtag
                            hashtagPosts = posts.filter { it.content.contains(hashtag) }.distinctBy { it.id }
                            showHashtagSearch = true
                        },
                        onSubscribe = {
                            scope.launch {
                                repository.toggleSubscription(post.authorId)
                                // Обновляем статус подписки
                                val subscribedToMe = repository.isSubscribedToMe(post.authorId)
                                val iSubscribed = repository.isSubscribed(post.authorId)
                                subscribedToMeMap[post.authorId] = subscribedToMe && !iSubscribed
                            }
                        }
                    )
                }

                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }

            // Топбар поверх контента
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .zIndex(5f)
                    .graphicsLayer { alpha = topBarAlpha; translationY = -size.height * (1f - topBarAlpha) }
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "OFOX",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                UserAvatar(
                    name = currentUser?.name ?: "",
                    avatarUrl = currentUser?.avatarUrl?.takeIf { it.isNotBlank() },
                    size = 36.dp,
                    modifier = Modifier.clickable { navController?.navigate("my_profile") }
                )
            }

        FloatingActionButton(
            onClick = { showCreatePost = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 16.dp, bottom = 96.dp)
                .graphicsLayer { alpha = fabScale; scaleX = fabScale; scaleY = fabScale }
                .zIndex(5f),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать пост")
        }
    }
    }

    if (showShareMenu) {
        ShareBottomSheet(
            postId = selectedPostId,
            onDismiss = {
                scope.launch {
                    posts.find { it.id == selectedPostId }?.let {
                        repository.sharePost(it)?.let { updatedPost ->
                            val index = posts.indexOfFirst { p -> p.id == updatedPost.id }
                            if (index != -1) posts[index] = updatedPost
                        }
                    }
                }
                showShareMenu = false
            }
        )
    }

    if (showPostMenu) {
        PostMenuBottomSheet(
            isMyPost = selectedPost?.authorId == currentUser?.id,
            authorName = selectedPost?.authorName ?: "",
            authorAvatarUrl = selectedPost?.authorAvatarUrl,
            onDismiss = { showPostMenu = false },
            onAction = { action ->
                when {
                    action == "Удалить пост" -> {
                        showPostMenu = false
                        showDeleteDialog = true
                    }
                    action.startsWith("report:") -> {
                        val reason = action.removePrefix("report:")
                        scope.launch {
                            try {
                                repository.reportPost(selectedPostId, reason)
                                snackbarHostState.showSnackbar("Жалоба отправлена. Спасибо!")
                            } catch (_: Exception) {
                                snackbarHostState.showSnackbar("Не удалось отправить жалобу")
                            }
                        }
                        showPostMenu = false
                    }
                    action == "Не показывать от автора" -> {
                        selectedPost?.authorId?.let { authorId ->
                            repository.hideAuthor(authorId)
                            hiddenAuthorIds = repository.getHiddenAuthorIds()
                        }
                        showPostMenu = false
                    }
                    action == "Скопировать текст" -> {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("post", selectedPost?.content ?: ""))
                        scope.launch { snackbarHostState.showSnackbar("Текст скопирован") }
                        showPostMenu = false
                    }
                    else -> showPostMenu = false
                }
            }
        )
    }

    if (showDeleteDialog) {
        Dialog(onDismissRequest = { showDeleteDialog = false }) {
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
                        text = "Удалить пост",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Вы уверены, что хотите удалить этот пост? Это действие нельзя отменить.",
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
                                scope.launch {
                                    selectedPost?.let {
                                        repository.deletePost(it.id)
                                        posts.removeIf { p -> p.id == it.id }
                                    }
                                    showDeleteDialog = false
                                }
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

        // Показываем ошибку загрузки фото
        imageUploadError?.let { error ->
            Box(modifier = Modifier.fillMaxSize()) {
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { imageUploadError = null }) { Text("OK") } }
                ) { Text(error) }
            }
        }
    }

    if (showCreatePost) {
        CreatePostDialog(
            initialContent = postDraftContent,
            initialType = postDraftType,
            initialPollOptions = postDraftPollOptions,
            initialDiscovery = postDraftDiscovery,
            onDismiss = { showCreatePost = false },
            onDraftChange = { content, type, pollOptions, discovery ->
                postDraftContent = content
                postDraftType = type
                postDraftPollOptions = pollOptions
                postDraftDiscovery = discovery
            },
            repository = repository,
            onCreate = { content, type, pollOptions, discovery, imageData, imageUris ->
                showCreatePost = false
                postDraftContent = ""
                postDraftType = "TEXT"
                postDraftPollOptions = listOf("", "")
                postDraftDiscovery = null
                scope.launch {
                    try {
                        val newPost = if (type == "POLL" && pollOptions.isNotEmpty()) {
                            repository.createPoll(content, pollOptions, discovery?.id)
                        } else {
                            repository.createPost(content, type, discovery?.id)
                        }
                        newPost?.let { post ->
                            val postWithAvatar = if (post.authorAvatarUrl.isBlank() && currentUser?.avatarUrl?.isNotBlank() == true) {
                                post.copy(authorAvatarUrl = currentUser!!.avatarUrl)
                            } else post
                            // Показываем локальные URI сразу как placeholder
                            val postWithLocalImages = if (imageUris.isNotEmpty()) postWithAvatar.copy(images = imageUris.joinToString("|||")) else postWithAvatar
                            posts.add(0, postWithLocalImages)
                            if (imageData.isNotEmpty()) {
                                val uploadResult = repository.uploadPostImages(post.id, imageData)
                                uploadResult.fold(
                                    onSuccess = {
                                        repository.getPostById(post.id)?.let { updated ->
                                            val idx = posts.indexOfFirst { it.id == post.id }
                                            if (idx != -1) posts[idx] = updated
                                        }
                                    },
                                    onFailure = { e ->
                                        imageUploadError = e.message ?: "Ошибка загрузки фото"
                                    }
                                )
                            }
                        }
                        listState.animateScrollToItem(0)
                    } catch (e: Exception) {
                        android.util.Log.e("HomeScreen", "Error creating post", e)
                    }
                }
            }
        )
    }

    if (showHashtagSearch) {
        su.SkrinVex.ofox.components.HashtagSearchDialog(
            hashtag = selectedHashtag,
            posts = hashtagPosts,
            currentUserId = currentUser?.id,
            onDismiss = { showHashtagSearch = false },
            onPostClick = { },
            onLike = { post ->
                scope.launch {
                    repository.toggleLike(post)?.let { updatedPost ->
                        val index = posts.indexOfFirst { it.id == updatedPost.id }
                        if (index != -1) posts[index] = updatedPost
                        val hashIndex = hashtagPosts.indexOfFirst { it.id == updatedPost.id }
                        if (hashIndex != -1) {
                            hashtagPosts = hashtagPosts.toMutableList().apply { this[hashIndex] = updatedPost }
                        }
                    }
                }
            },
            onShare = { postId ->
                selectedPostId = postId
                showHashtagSearch = false
                showShareMenu = true
            },
            onMoreClick = { post ->
                selectedPost = post
                selectedPostId = post.id
                showHashtagSearch = false
                showPostMenu = true
            },
            onVote = { postId, optionIndex ->
                scope.launch {
                    repository.voteOnPoll(postId, optionIndex)?.let { updatedPost ->
                        val index = posts.indexOfFirst { it.id == updatedPost.id }
                        if (index != -1) posts[index] = updatedPost
                        val hashIndex = hashtagPosts.indexOfFirst { it.id == updatedPost.id }
                        if (hashIndex != -1) {
                            hashtagPosts = hashtagPosts.toMutableList().apply { this[hashIndex] = updatedPost }
                        }
                    }
                }
            },
            onAuthorClick = { authorId ->
                showHashtagSearch = false
                navController?.navigate("user_profile/$authorId")
            }
        )
    }

    if (showComments) {
        key(selectedPostId) {
        su.SkrinVex.ofox.components.CommentsBottomSheet(
            postId = selectedPostId,
            comments = comments,
            currentUserId = currentUser?.id,
            postAuthorId = posts.find { it.id == selectedPostId }?.authorId,
            commentDrafts = commentDrafts,
            repository = repository,
            onDismiss = {
                showComments = false
                scope.launch {
                    repository.getPostById(selectedPostId)?.let { updatedPost ->
                        val index = posts.indexOfFirst { it.id == selectedPostId }
                        if (index != -1) {
                            posts[index] = updatedPost
                        }
                    }
                }
            },
            onSendComment = { content, replyToId ->
                android.util.Log.d("HomeScreen", "sendComment: content=$content replyToId=$replyToId")
                scope.launch {
                    repository.createComment(selectedPostId, content, replyToId)
                    val updated = repository.getComments(selectedPostId)
                    if (updated != null) {
                        comments = updated
                    }
                }
            },
            onDeleteComment = { commentId ->
                android.util.Log.d("HomeScreen", "onDeleteComment called with id: $commentId")
                scope.launch {
                    android.util.Log.d("HomeScreen", "Calling repository.deleteComment($commentId)")
                    repository.deleteComment(commentId)
                    android.util.Log.d("HomeScreen", "deleteComment completed")
                }
            },
            onAuthorClick = { authorId ->
                showComments = false
                navController?.navigate("user_profile/$authorId")
            }
        )
        } // key
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostDialog(
    initialContent: String = "",
    initialType: String = "TEXT",
    initialPollOptions: List<String> = listOf("", ""),
    initialDiscovery: su.SkrinVex.ofox.data.Discovery? = null,
    onDismiss: () -> Unit,
    onDraftChange: (String, String, List<String>, su.SkrinVex.ofox.data.Discovery?) -> Unit = { _, _, _, _ -> },
    onCreate: (String, String, List<String>, su.SkrinVex.ofox.data.Discovery?, List<Pair<ByteArray, String>>, List<String>) -> Unit,
    repository: Repository
) {
    var content by remember { mutableStateOf(initialContent) }
    var selectedType by remember { mutableStateOf(initialType) }
    var pollOptions by remember { mutableStateOf(initialPollOptions) }
    var selectedDiscovery by remember { mutableStateOf(initialDiscovery) }
    var discoveries by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Discovery>()) }
    // Храним (байты, mimeType, previewUri) — байты читаем сразу при выборе
    var selectedImages by remember { mutableStateOf(listOf<Triple<ByteArray, String, android.net.Uri>>()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val imagePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        val newImages = uris.mapNotNull { uri -> readImageUri(context, uri) }
        selectedImages = (selectedImages + newImages).take(5)
    }
    LaunchedEffect(Unit) {
        try {
            discoveries = repository.getAllDiscoveries().filter { it.isJoined }
        } catch (e: Exception) {
            discoveries = emptyList()
        }
    }

    LaunchedEffect(content, selectedType, pollOptions, selectedDiscovery) {
        onDraftChange(content, selectedType, pollOptions, selectedDiscovery)
    }

    val postTypes = listOf(
        "TEXT" to "Текст",
        "MOOD" to "Настроение",
        "POLL" to "Опрос",
        "QUOTE" to "Цитата"
    )

    val placeholder = when(selectedType) {
        "TEXT" -> "Что у вас нового?"
        "MOOD" -> "Какое у вас настроение?"
        "QUOTE" -> "Напишите вашу цитату"
        "POLL" -> "Заголовок опроса"
        else -> "Что у вас нового?"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp)
            ) {
                Text(
                    text = "Создать пост",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Тип поста",
                    style = MaterialTheme.typography.titleSmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    postTypes.take(2).forEach { (type, name) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    postTypes.drop(2).forEach { (type, name) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(name) },
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (selectedType == "POLL") {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { if (it.length <= su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH) content = it },
                        label = { Text(placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        supportingText = { 
                            Text(
                                "${content.length}/${su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH}",
                                color = if (content.length >= su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ) 
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Варианты ответа",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    pollOptions.forEachIndexed { index, option ->
                        OutlinedTextField(
                            value = option,
                            onValueChange = {
                                if (it.length <= 150) {
                                    pollOptions = pollOptions.toMutableList().apply { this[index] = it }
                                }
                            },
                            label = { Text("Вариант ${index + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            supportingText = { 
                                Text(
                                    "${option.length}/150",
                                    color = if (option.length >= 150) 
                                        MaterialTheme.colorScheme.error 
                                    else 
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                ) 
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    if (pollOptions.size < 15) {
                        TextButton(
                            onClick = { pollOptions = pollOptions + "" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Добавить вариант (${pollOptions.size}/15)")
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { if (it.length <= su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH) content = it },
                        label = { Text(placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
                        ),
                        supportingText = { 
                            Text(
                                "${content.length}/${su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH}",
                                color = if (content.length >= su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            ) 
                        }
                    )
                }

                if (discoveries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Открытие (необязательно)",
                        style = MaterialTheme.typography.titleSmall
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        discoveries.forEach { discovery ->
                            FilterChip(
                                selected = selectedDiscovery?.id == discovery.id,
                                onClick = {
                                    selectedDiscovery = if (selectedDiscovery?.id == discovery.id) null else discovery
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(discovery.colorHex)))
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(discovery.title)
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // Превью выбранных изображений
            if (selectedImages.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedImages.size) { i ->
                        Box {
                            coil.compose.AsyncImage(
                                model = selectedImages[i].third,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            IconButton(
                                onClick = { selectedImages = selectedImages.toMutableList().also { it.removeAt(i) } },
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Удалить", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (selectedImages.size < 5) imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = selectedImages.size < 5
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = "Добавить фото",
                        tint = if (selectedImages.size < 5) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = "${selectedImages.size}/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Отмена")
                }

                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            val imageData = selectedImages.map { it.first to it.second }
                            val imageUris = selectedImages.map { it.third.toString() }
                            if (selectedType == "POLL") {
                                val validOptions = pollOptions.filter { it.isNotBlank() }
                                onCreate(content, selectedType, validOptions, selectedDiscovery, imageData, imageUris)
                            } else {
                                onCreate(content, selectedType, emptyList(), selectedDiscovery, imageData, imageUris)
                            }
                        }
                    },
                    enabled = content.isNotBlank() && (selectedType != "POLL" || pollOptions.count { it.isNotBlank() } >= 2),
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Опубликовать")
                }
            }
        }
    }
}

private fun detectMimeType(bytes: ByteArray): String {
    if (bytes.size < 12) return "image/jpeg"
    return when {
        bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
        bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() && bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() -> "image/gif"
        bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() -> "image/webp"
        // HEIC/HEIF: ftyp box на байтах 4-11
        bytes[4] == 0x66.toByte() && bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte() -> "image/heic"
        else -> "image/jpeg"
    }
}

private fun readImageUri(context: android.content.Context, uri: android.net.Uri): Triple<ByteArray, String, android.net.Uri>? {
    return try {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: run {
            android.util.Log.e("ImagePicker", "openInputStream returned null for $uri")
            return null
        }
        val realMime = detectMimeType(rawBytes)
        android.util.Log.d("ImagePicker", "Read ${rawBytes.size} bytes, detected=$realMime for $uri")
        val webSafe = setOf("image/jpeg", "image/png", "image/webp", "image/gif")
        val (finalBytes, finalMime) = if (realMime !in webSafe) {
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: run {
                android.util.Log.e("ImagePicker", "BitmapFactory returned null for $uri")
                return null
            }
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            bitmap.recycle()
            out.toByteArray() to "image/jpeg"
        } else {
            rawBytes to realMime
        }
        android.util.Log.d("ImagePicker", "Final: $finalMime ${finalBytes.size} bytes")
        Triple(finalBytes, finalMime, uri)
    } catch (e: Exception) {
        android.util.Log.e("ImagePicker", "Failed to read image: $uri", e)
        null
    }
}

data class CreativePost(
    val author: String,
    val content: String,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val time: String,
    val type: PostType = PostType.TEXT,
    val pollOptions: List<String> = emptyList(),
    val pollVotes: List<Int> = emptyList(),
    val userVote: Int = -1,
    val authorId: Int = 0,
    val discoveryId: Int = 0,
    val discoveryTitle: String = "",
    val discoveryColor: String = "",
    val authorBadges: List<su.SkrinVex.ofox.data.api.models.BadgeResponse> = emptyList(),
    val authorAvatarUrl: String = "",
    val images: List<String> = emptyList()
)

enum class PostType { TEXT, POLL, QUOTE, MOOD }
