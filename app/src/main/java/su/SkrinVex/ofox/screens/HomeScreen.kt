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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun HomeScreen(repository: Repository) {
    var showShareMenu by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    var showCreatePost by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedPostId by remember { mutableStateOf(0) }
    var selectedPost by remember { mutableStateOf<su.SkrinVex.ofox.data.Post?>(null) }
    var posts by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Post>()) }
    var currentUser by remember { mutableStateOf<su.SkrinVex.ofox.data.User?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        currentUser = repository.getCurrentUser()
        posts = repository.getAllPosts()
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                            post.userVote
                        ),
                        isLiked = post.isLiked,
                        onLike = {
                            scope.launch {
                                repository.toggleLike(post)
                                posts = repository.getAllPosts()
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
                                repository.voteOnPoll(post.id, optionIndex)
                                posts = repository.getAllPosts()
                            }
                        }
                    )
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
                        repository.sharePost(it)
                        posts = repository.getAllPosts()
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
                shape = RoundedCornerShape(24.dp),
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
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Отмена")
                        }
                        
                        Button(
                            onClick = {
                                scope.launch {
                                    selectedPost?.let { repository.deletePost(it.id) }
                                    posts = repository.getAllPosts()
                                    showDeleteDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
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
            onCreate = { content, type, pollOptions ->
                scope.launch {
                    if (type == "POLL" && pollOptions.isNotEmpty()) {
                        val optionsJson = pollOptions.joinToString(",")
                        val votesJson = pollOptions.joinToString(",") { "0" }
                        repository.createPoll(content, optionsJson, votesJson)
                    } else {
                        repository.createPost(content, type)
                    }
                    posts = repository.getAllPosts()
                    showCreatePost = false
                    listState.animateScrollToItem(0)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostDialog(onDismiss: () -> Unit, onCreate: (String, String, List<String>) -> Unit) {
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("TEXT") }
    var pollOptions by remember { mutableStateOf(listOf("", "")) }
    
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
            shape = RoundedCornerShape(24.dp),
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
                        onValueChange = { content = it },
                        label = { Text(placeholder) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
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
                            shape = RoundedCornerShape(16.dp)
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
                        shape = RoundedCornerShape(16.dp)
                    )
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
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Отмена")
                    }
                    
                    Button(
                        onClick = { 
                            if (content.isNotBlank()) {
                                if (selectedType == "POLL") {
                                    val validOptions = pollOptions.filter { it.isNotBlank() }
                                    onCreate(content, selectedType, validOptions)
                                } else {
                                    onCreate(content, selectedType, emptyList())
                                }
                            }
                        },
                        enabled = content.isNotBlank() && (selectedType != "POLL" || pollOptions.count { it.isNotBlank() } >= 2),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
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
    val hours = diff / (1000 * 60 * 60)
    return when {
        hours < 1 -> "только что"
        hours < 24 -> "$hours ч назад"
        else -> "${hours / 24} дн назад"
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
    val userVote: Int = -1
)

enum class PostType { TEXT, POLL, QUOTE, MOOD }
