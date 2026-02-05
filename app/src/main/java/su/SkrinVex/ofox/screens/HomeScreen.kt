package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import su.SkrinVex.ofox.components.CreativePostCard
import su.SkrinVex.ofox.components.ShareBottomSheet
import su.SkrinVex.ofox.components.PostMenuBottomSheet

data class CreativePost(
    val author: String,
    val content: String,
    val likes: Int,
    val comments: Int,
    val shares: Int,
    val time: String,
    val type: PostType = PostType.TEXT
)

enum class PostType { TEXT, POLL, QUOTE, MOOD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    var showShareMenu by remember { mutableStateOf(false) }
    var showPostMenu by remember { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<CreativePost?>(null) }
    
    val posts = listOf(
        CreativePost("Komari", "🌟 Сегодня особенный день! Запустил новый проект и чувствую невероятный прилив энергии. Иногда самые смелые решения приводят к лучшим результатам ✨", 42, 12, 8, "2 часа назад", PostType.MOOD),
        CreativePost("Елена", "Что лучше для изучения Android разработки?", 28, 15, 3, "4 часа назад", PostType.POLL),
        CreativePost("Андрей", "\"Код - это поэзия, которую понимают машины\" - неизвестный автор", 35, 8, 12, "6 часов назад", PostType.QUOTE),
        CreativePost("Ольга", "Делюсь своим опытом работы с Jetpack Compose. Эта технология действительно революционная! Создание UI стало намного проще и интуитивнее. Рекомендую всем Android разработчикам попробовать 🚀", 67, 23, 15, "8 часов назад"),
        CreativePost("Максим", "Сегодня настроение: продуктивность на максимум! 💪", 19, 5, 2, "10 часов назад", PostType.MOOD)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Простой заголовок без плюсика
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "K",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Привет, Komari! 👋",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Что нового сегодня?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
            }
        }
        
        // Posts без историй
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items(posts) { post ->
                CreativePostCard(
                    post = post,
                    onLike = { },
                    onComment = { },
                    onShare = { 
                        selectedPost = post
                        showShareMenu = true 
                    },
                    onMoreClick = {
                        selectedPost = post
                        showPostMenu = true
                    }
                )
            }
        }
    }
    
    // Share Menu
    if (showShareMenu && selectedPost != null) {
        ShareBottomSheet(
            onDismiss = { showShareMenu = false },
            onShare = { platform ->
                showShareMenu = false
            }
        )
    }
    
    // Post Menu
    if (showPostMenu && selectedPost != null) {
        PostMenuBottomSheet(
            onDismiss = { showPostMenu = false },
            onAction = { action ->
                showPostMenu = false
            }
        )
    }
}
