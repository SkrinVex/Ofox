package su.SkrinVex.ofox.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.components.UserBadges
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.api.models.BadgeResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userId: Int,
    repository: Repository,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    onPostClick: (Int) -> Unit = {}
) {
    val userFlow = remember(userId) { repository.getUserFlow(userId) }
    val user by userFlow.collectAsState(initial = null)
    
    var userBadges by remember { mutableStateOf<List<BadgeResponse>>(emptyList()) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isMutualSubscription by remember { mutableStateOf(false) }
    var userPosts by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Post>()) }
    var subscribersCount by remember { mutableStateOf(0) }
    var isLoadingInitial by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    val isOwnProfile = remember(userId) { userId == repository.getCurrentUserId() }
    
    val bannerColor = remember(user?.bannerColor) {
        try {
            if (!user?.bannerColor.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(user?.bannerColor))
            } else null
        } catch (e: Exception) { null }
    }
    
    LaunchedEffect(userId) {
        // Проверяем кеш сразу
        val cached = repository.getUserById(userId)
        if (cached != null) isLoadingInitial = false
        
        // Загружаем данные параллельно
        val badgesDef = async { repository.getUserBadges(userId) }
        val postsDef = async { repository.getPostsByUser(userId) }
        val countDef = async { repository.getSubscribersCount(userId) }
        
        if (!isOwnProfile) {
            val subDef = async { repository.isSubscribed(userId) }
            val mutualDef = async { repository.isSubscribedToMe(userId) }
            isSubscribed = subDef.await()
            isMutualSubscription = mutualDef.await()
        }
        
        userBadges = badgesDef.await()
        userPosts = postsDef.await()
        subscribersCount = countDef.await()
        isLoadingInitial = false
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text(
                "Профиль",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (user == null && isLoadingInitial) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Profile header card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Banner & Avatar
                            Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .background(bannerColor ?: MaterialTheme.colorScheme.primary)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .align(Alignment.BottomCenter)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(4.dp)
                                ) {
                                    UserAvatar(
                                        name = user?.name ?: "?",
                                        avatarUrl = user?.avatarUrl?.takeIf { it.isNotBlank() },
                                        size = 92.dp
                                    )
                                }
                            }
                            
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = user?.name ?: "Загрузка...",
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (userBadges.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        UserBadges(badges = userBadges)
                                    }
                                }
                                
                                if (!user?.bio.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = user?.bio ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                // Social Links
                                val socialLinks = remember(user?.socialLinks) {
                                    try {
                                        val jsonStr = user?.socialLinks ?: "{}"
                                        if (jsonStr.isBlank() || jsonStr == "null") emptyMap<String, String>()
                                        else {
                                            val json = org.json.JSONObject(jsonStr)
                                            val map = mutableMapOf<String, String>()
                                            val keys = json.keys()
                                            while(keys.hasNext()) {
                                                val key = keys.next()
                                                val value = json.optString(key)
                                                if (value.isNotBlank() && key.lowercase() != "discord") map[key] = value
                                            }
                                            map
                                        }
                                    } catch (e: Exception) { emptyMap() }
                                }

                                if (socialLinks.isNotEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        socialLinks.forEach { (platform, url) ->
                                            SocialIcon(platform, url)
                                            Spacer(Modifier.width(12.dp))
                                        }
                                    }
                                }
                                
                                Spacer(Modifier.height(24.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    val postCountText = if (userPosts.size > 100) "100+" else "${userPosts.size}"
                                    StatItem(postCountText, "Постов")
                                    StatItem("$subscribersCount", "Подписчиков")
                                }
                                
                                Spacer(Modifier.height(20.dp))
                                
                                if (isOwnProfile) {
                                    Button(
                                        onClick = onEditProfile,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text("Редактировать профиль")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val newSubscribed = repository.toggleSubscription(userId)
                                                isSubscribed = newSubscribed
                                                subscribersCount = repository.getSubscribersCount(userId)
                                                isMutualSubscription = repository.isSubscribedToMe(userId)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                        ),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Text(
                                            text = if (isSubscribed) (if (isMutualSubscription) "Отписаться (взаимно)" else "Отписаться") 
                                                   else (if (isMutualSubscription) "Подписаться в ответ" else "Подписаться"),
                                            color = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Text("Посты", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                
                items(userPosts, key = { it.id }) { post ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onPostClick(post.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = post.content, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "${post.likes} лайков • ${post.comments} комментариев",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun SocialIcon(platform: String, url: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val (iconRes, color) = when (platform.lowercase()) {
        "tg", "telegram" -> su.SkrinVex.ofox.R.drawable.ic_telegram to Color(0xFF26A5E4)
        "vk" -> su.SkrinVex.ofox.R.drawable.ic_vk to Color(0xFF0077FF)
        "github" -> su.SkrinVex.ofox.R.drawable.ic_github to Color(0xFF333333)
        "website", "site" -> 0 to Color(0xFF4CAF50)
        "youtube" -> su.SkrinVex.ofox.R.drawable.ic_youtube to Color(0xFFFF0000)
        "twitch" -> su.SkrinVex.ofox.R.drawable.ic_twitch to Color(0xFF9146FF)
        "twitter", "x" -> su.SkrinVex.ofox.R.drawable.ic_twitter to Color(0xFF000000)
        else -> 0 to MaterialTheme.colorScheme.primary
    }

    Surface(
        onClick = {
            try {
                val uriString = when {
                    url.startsWith("http://") || url.startsWith("https://") -> url
                    platform.lowercase() in listOf("tg", "telegram") -> {
                        val clean = url.replace("@", "")
                        if (clean.contains("t.me")) "https://$clean" else "https://t.me/$clean"
                    }
                    platform.lowercase() == "vk" -> {
                        if (url.contains("vk.com")) "https://$url" else "https://vk.com/$url"
                    }
                    platform.lowercase() == "github" -> "https://github.com/$url"
                    platform.lowercase() == "youtube" -> {
                        if (url.startsWith("@")) "https://youtube.com/$url" else "https://youtube.com/@$url"
                    }
                    else -> "https://$url"
                }
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uriString))
                context.startActivity(intent)
            } catch (_: Exception) {}
        },
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(10.dp)) {
            if (iconRes != 0) {
                Image(
                    painter = painterResource(id = iconRes),
                    contentDescription = platform,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text("🔗", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
