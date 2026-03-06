package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.components.UserBadges
import su.SkrinVex.ofox.data.api.models.BadgeResponse

@Composable
fun UserProfileScreen(
    userId: Int,
    repository: Repository,
    onBack: () -> Unit,
    onEditProfile: () -> Unit = {},
    onPostClick: (Int) -> Unit = {}
) {
    var user by remember { mutableStateOf<su.SkrinVex.ofox.data.User?>(null) }
    var currentUser by remember { mutableStateOf<su.SkrinVex.ofox.data.User?>(null) }
    var userBadges by remember { mutableStateOf<List<BadgeResponse>>(emptyList()) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isMutualSubscription by remember { mutableStateOf(false) }
    var userPosts by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Post>()) }
    var subscribersCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val isOwnProfile = remember(currentUser, userId) { currentUser?.id == userId }
    
    LaunchedEffect(userId) {
        currentUser = repository.getCurrentUser()
        user = repository.getUserById(userId)
        userBadges = repository.getUserBadges(userId)
        isSubscribed = repository.isSubscribed(userId)
        isMutualSubscription = repository.isSubscribedToMe(userId)
        userPosts = repository.getPostsByUser(userId)
        subscribersCount = repository.getSubscribersCount(userId)
        android.util.Log.d("UserProfileScreen", "userId=$userId, isSubscribed=$isSubscribed, isMutualSubscription=$isMutualSubscription")
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Профиль",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile header
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user?.name?.firstOrNull()?.toString() ?: "?",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = user?.name ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (userBadges.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                UserBadges(badges = userBadges)
                            }
                        }
                        
                        if (!user?.bio.isNullOrEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = user?.bio ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${userPosts.size}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Постов",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$subscribersCount",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Подписчиков",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (isOwnProfile) {
                            Button(
                                onClick = onEditProfile,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = "Редактировать профиль",
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val newSubscribed = repository.toggleSubscription(userId)
                                        isSubscribed = newSubscribed
                                        subscribersCount = repository.getSubscribersCount(userId)
                                        isMutualSubscription = repository.isSubscribedToMe(userId)
                                        android.util.Log.d("UserProfileScreen", "After toggle: isSubscribed=$newSubscribed, isMutualSubscription=$isMutualSubscription")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed)
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else
                                        MaterialTheme.colorScheme.primary
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(
                                    text = if (isSubscribed) {
                                        if (isMutualSubscription) "Отписаться (взаимно)" else "Отписаться"
                                    } else {
                                        if (isMutualSubscription) "Подписаться в ответ" else "Подписаться"
                                    },
                                    color = if (isSubscribed)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
            
            // Posts section
            item {
                Text(
                    text = "Посты",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            items(userPosts) { post ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { 
                            android.util.Log.d("UserProfileScreen", "Post clicked: ${post.id}")
                            onPostClick(post.id) 
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = post.content,
                            style = MaterialTheme.typography.bodyMedium
                        )
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
