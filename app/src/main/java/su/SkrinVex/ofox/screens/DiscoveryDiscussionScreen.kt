package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository

data class TopParticipant(val name: String, val postCount: Int)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DiscoveryDiscussionScreen(
    discoveryId: Int,
    repository: Repository,
    onBack: () -> Unit,
    onNavigateToPost: (Int) -> Unit = {},
    onNavigateToChat: (Int) -> Unit = {},
    onNavigateToAchievements: (Int) -> Unit = {}
) {
    var discovery by remember { mutableStateOf<su.SkrinVex.ofox.data.Discovery?>(null) }
    var posts by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Post>()) }
    var achievements by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.api.models.AchievementResponse>()) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showMenuSheet by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var userContribution by remember { mutableStateOf(0) }
    var currentUserId by remember { mutableStateOf<Int?>(null) }
    var isCreator by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val menuSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val infoSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    LaunchedEffect(discoveryId) {
        discovery = repository.getDiscoveryById(discoveryId)
        posts = repository.getPostsByDiscovery(discoveryId)
        userContribution = repository.getUserContributionToDiscovery(discoveryId)
        achievements = repository.getAchievements(discoveryId)
        currentUserId = repository.getCurrentUser()?.id
        isCreator = discovery?.creatorId == currentUserId
    }
    
    if (discovery == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Назад")
                }
                Text(
                    text = "Открытие",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { showMenuSheet = true }) {
                    Icon(Icons.Default.MoreVert, "Меню")
                }
            }
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(discovery?.colorHex ?: "#000000")))
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    discovery?.category ?: "",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                discovery?.title ?: "",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                discovery?.description ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                item {
                    Text("Статистика", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Участников", "${discovery?.participants ?: 0}", Icons.Default.People, Modifier.weight(1f))
                        StatCard("Постов", "${posts.size}", Icons.Default.Article, Modifier.weight(1f))
                    }
                }
                
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                val chatId = repository.getOrCreateDiscoveryChat(discoveryId)
                                chatId?.let { onNavigateToChat(it) }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Chat, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть чат")
                    }
                }
                
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard("Ваш вклад", "$userContribution", Icons.Default.Star, Modifier.weight(1f))
                    }
                }
                
                item {
                    Text("Достижения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                
                item {
                    // Стандартные достижения
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            AchievementItem("Первый участник", "Присоединились к открытию", discovery?.isJoined == true)
                            AchievementItem("Активист", "Создайте ${if (userContribution >= 1) userContribution else 1} ${if (userContribution >= 1) "постов" else "пост"}", userContribution >= 1)
                            AchievementItem("Лидер", "Создайте 5+ постов", userContribution >= 5)
                        }
                    }
                }
                
                // Кастомные достижения
                if (achievements.isNotEmpty()) {
                    item {
                        Text("Особые достижения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            achievements.forEach { achievement ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (achievement.is_earned)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (achievement.is_earned)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = achievement.icon ?: "🏆",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                achievement.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            achievement.description?.let {
                                                Text(
                                                    it,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                    maxLines = 1
                                                )
                                            }
                                            achievement.reward_value?.let {
                                                Text(
                                                    "🎁 $it",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                        if (achievement.is_earned) {
                                            Icon(
                                                Icons.Default.CheckCircle,
                                                null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (posts.isNotEmpty()) {
                    item {
                        Text("Последние посты", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    
                    items(posts.take(5)) { post ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPost(post.id) },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    post.authorName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    post.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showLeaveDialog) {
        Dialog(onDismissRequest = { showLeaveDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Покинуть открытие?",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Вы уверены, что хотите покинуть это открытие?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { showLeaveDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Отмена")
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    discovery?.let { repository.toggleJoinDiscovery(it) }
                                    onBack()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Покинуть")
                        }
                    }
                }
            }
        }
    }
    
    if (showMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMenuSheet = false },
            sheetState = menuSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Меню",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                MenuButton(
                    icon = Icons.Default.Info,
                    title = "Информация",
                    description = "Узнать больше об открытиях",
                    onClick = {
                        showMenuSheet = false
                        showInfoSheet = true
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                MenuButton(
                    icon = Icons.Default.Share,
                    title = "Поделиться",
                    description = "Пригласить друзей в открытие",
                    onClick = {
                        showMenuSheet = false
                        showShareSheet = true
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                MenuButton(
                    icon = Icons.Default.ExitToApp,
                    title = "Покинуть",
                    description = "Выйти из открытия",
                    onClick = {
                        showMenuSheet = false
                        showLeaveDialog = true
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
    
    if (showInfoSheet) {
        ModalBottomSheet(
            onDismissRequest = { showInfoSheet = false },
            sheetState = infoSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Что такое Открытия?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                InfoItem(
                    icon = Icons.Default.Explore,
                    title = "Открытия",
                    description = "Это тематические события, где участники объединяются вокруг общих интересов"
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                InfoItem(
                    icon = Icons.Default.TrendingUp,
                    title = "Вклад",
                    description = "Количество ваших постов в этом открытии. Публикуйте больше, чтобы увеличить вклад!"
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                InfoItem(
                    icon = Icons.Default.EmojiEvents,
                    title = "Достижения",
                    description = "Получайте награды за активность в открытии"
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { showInfoSheet = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Понятно")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    if (showShareSheet) {
        su.SkrinVex.ofox.components.ShareDiscoveryBottomSheet(
            discoveryId = discoveryId,
            onDismiss = { showShareSheet = false }
        )
    }
}

@Composable
fun InfoItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun MenuButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
    
}

@Composable
fun CreateAchievementDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("🏆") }
    var rewardValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое достижение", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Иконка (эмодзи)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rewardValue,
                    onValueChange = { rewardValue = it },
                    label = { Text("Награда") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(title, description, icon, rewardValue) },
                enabled = title.isNotBlank() && description.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun AchievementItem(title: String, description: String, unlocked: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (unlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                null,
                tint = if (unlocked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

