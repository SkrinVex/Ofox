package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.CreativePostCard
import su.SkrinVex.ofox.components.ShareBottomSheet
import su.SkrinVex.ofox.components.PostMenuBottomSheet
import su.SkrinVex.ofox.data.Repository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(repository: Repository, navController: androidx.navigation.NavController? = null) {
    var showShareMenu by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    var showCreatePost by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedPostId by remember { mutableStateOf(0) }
    var selectedPost by remember { mutableStateOf<su.SkrinVex.ofox.data.Post?>(null) }
    val posts = remember { androidx.compose.runtime.snapshots.SnapshotStateList<su.SkrinVex.ofox.data.Post>() }
    var currentUser by remember { mutableStateOf<su.SkrinVex.ofox.data.User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val wsClient = remember { su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(context) }

    fun loadPosts(offset: Int = 0) {
        scope.launch {
            try {
                if (offset == 0) isLoading = true else isLoadingMore = true
                val newPosts = repository.getAllPosts(limit = 20, offset = offset)
                if (offset == 0) {
                    posts.clear()
                    posts.addAll(newPosts)
                } else {
                    posts.addAll(newPosts)
                }
                hasMore = newPosts.size == 20
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
            currentUser = repository.getCurrentUser()
            loadPosts(0)
            wsClient.connect()
        } catch (e: Exception) {
            android.util.Log.e("HomeScreen", "Error loading user", e)
            isLoading = false
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30000) // 30 секунд
            if (posts.isNotEmpty()) {
                try {
                    val freshPosts = repository.getAllPosts(limit = posts.size.coerceAtMost(20), offset = 0)
                    freshPosts.forEach { fresh ->
                        val index = posts.indexOfFirst { it.id == fresh.id }
                        if (index != -1 && (posts[index].likes != fresh.likes || 
                            posts[index].shares != fresh.shares || 
                            posts[index].authorBadges != fresh.authorBadges)) {
                            posts[index] = fresh
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
                            shares = if (event.shares >= 0) event.shares else post.shares
                        )
                        android.util.Log.d("HomeScreen", "Updated post ${event.postId}")
                    }
                }
                is su.SkrinVex.ofox.data.api.WSEvent.NewPost -> {
                    scope.launch {
                        try {
                            val newPosts = repository.getAllPosts(limit = 1, offset = 0)
                            if (newPosts.isNotEmpty() && newPosts[0].id == event.postId) {
                                posts.add(0, newPosts[0])
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("HomeScreen", "Error loading new post", e)
                        }
                    }
                }
                null -> {}
            }
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            wsClient.disconnect()
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= posts.size - 3 && !isLoadingMore && hasMore) {
                    loadPosts(posts.size)
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                items(posts, key = { it.id }) { post ->
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
                        post = su.SkrinVex.ofox.screens.CreativePost(
                            post.authorName,
                            post.content,
                            post.likes,
                            post.comments,
                            post.shares,
                            formatTime(post.timestamp),
                            PostType.valueOf(post.type),
                            post.pollOptions.split(",").filter { it.isNotEmpty() },
                            post.pollVotes.split(",").mapNotNull { it.toIntOrNull() },
                            post.userVote,
                            post.authorId,
                            post.discoveryId,
                            post.discoveryTitle,
                            post.discoveryColor,
                            badges
                        ),
                        isLiked = post.isLiked,
                        onLike = {
                            scope.launch {
                                repository.toggleLike(post)?.let { updatedPost ->
                                    val index = posts.indexOfFirst { it.id == updatedPost.id }
                                    if (index != -1) posts[index] = updatedPost
                                }
                            }
                        },
                        onComment = { },
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

        FloatingActionButton(
            onClick = { showCreatePost = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать пост")
        }
    }
    
    if (showShareMenu) {
        ShareBottomSheet(
            onDismiss = { showShareMenu = false },
            onShare = { 
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
            onDismiss = { showPostMenu = false },
            onAction = { action ->
                when (action) {
                    "Удалить пост" -> {
                        showPostMenu = false
                        showDeleteDialog = true
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
    }

    if (showCreatePost) {
        CreatePostDialog(
            onDismiss = { showCreatePost = false },
            repository = repository,
            onCreate = { content, type, pollOptions, discovery ->
                scope.launch {
                    try {
                        val newPost = if (type == "POLL" && pollOptions.isNotEmpty()) {
                            repository.createPoll(content, pollOptions, discovery?.id)
                        } else {
                            repository.createPost(content, type, discovery?.id)
                        }
                        newPost?.let { posts.add(0, it) }
                        showCreatePost = false
                        listState.animateScrollToItem(0)
                    } catch (e: Exception) {
                        showCreatePost = false
                    }
                }
            }
        )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostDialog(
    onDismiss: () -> Unit, 
    onCreate: (String, String, List<String>, su.SkrinVex.ofox.data.Discovery?) -> Unit,
    repository: Repository
) {
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("TEXT") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }
    var discoveries by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Discovery>()) }
    var selectedDiscovery by remember { mutableStateOf<su.SkrinVex.ofox.data.Discovery?>(null) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        try {
            discoveries = repository.getAllDiscoveries().filter { it.isJoined }
        } catch (e: Exception) {
            discoveries = emptyList()
        }
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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
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
                        supportingText = { Text("${content.length}/${su.SkrinVex.ofox.utils.ValidationConstants.MAX_POST_CONTENT_LENGTH}") }
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = "Варианты ответа",
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    pollOptions.forEachIndexed { index, option ->
                        OutlinedTextField(
                            value = option,
                            onValueChange = { 
                                pollOptions = pollOptions.toMutableList().apply { this[index] = it }
                            },
                            label = { Text("Вариант ${index + 1}") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    TextButton(
                        onClick = { pollOptions = pollOptions + "" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавить вариант")
                    }
                } else {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text(placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        shape = MaterialTheme.shapes.medium
                    )
                }
                
                if (discoveries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Открытие (необязательно)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
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
                            modifier = Modifier.fillMaxWidth(),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
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
                                if (selectedType == "POLL") {
                                    val validOptions = pollOptions.filter { it.isNotBlank() }
                                    onCreate(content, selectedType, validOptions, selectedDiscovery)
                                } else {
                                    onCreate(content, selectedType, emptyList(), selectedDiscovery)
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
}

fun formatTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        seconds < 5 -> "только что"
        seconds < 60 -> "$seconds сек назад"
        minutes < 60 -> "$minutes мин назад"
        hours < 24 -> "$hours ч назад"
        else -> "$days дн назад"
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
    val authorBadges: List<su.SkrinVex.ofox.data.api.models.BadgeResponse> = emptyList()
)

enum class PostType { TEXT, POLL, QUOTE, MOOD }
