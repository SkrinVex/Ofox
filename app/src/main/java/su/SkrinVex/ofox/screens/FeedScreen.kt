package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import java.text.SimpleDateFormat
import java.util.*

data class TrendingTopic(val name: String, val posts: Int)

@Composable
fun FeedScreen(repository: Repository, navController: androidx.navigation.NavController? = null) {
    var discoveries by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Discovery>()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val fabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0
        }
    }
    
    val trendingTopics = listOf(
        TrendingTopic("Jetpack Compose", 156),
        TrendingTopic("Kotlin Multiplatform", 89),
        TrendingTopic("Android 15", 234),
        TrendingTopic("Flutter vs React Native", 67),
        TrendingTopic("AI в мобильной разработке", 123)
    )

    LaunchedEffect(Unit) {
        discoveries = repository.getAllDiscoveries()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        item {
            Text(
                text = "Открытия",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )
        }
        
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.TrendingUp,
                            contentDescription = "Тренды",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Сейчас обсуждают",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(trendingTopics) { topic ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier.clickable { }
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = topic.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${topic.posts} постов",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Text(
                text = "Вы участвуете",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        items(discoveries.filter { it.isJoined }) { discovery ->
            DiscoveryCard(
                discovery = discovery,
                onJoin = {
                    scope.launch {
                        repository.toggleJoinDiscovery(discovery)
                        discoveries = repository.getAllDiscoveries()
                    }
                },
                onDiscuss = {
                    navController?.navigate("discovery_discussion/${discovery.id}")
                }
            )
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Text(
                text = "Рекомендуем для тебя",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        items(discoveries.filter { !it.isJoined }) { discovery ->
            DiscoveryCard(
                discovery = discovery,
                onJoin = {
                    scope.launch {
                        repository.toggleJoinDiscovery(discovery)
                        discoveries = repository.getAllDiscoveries()
                    }
                },
                onDiscuss = {
                    navController?.navigate("discovery_discussion/${discovery.id}")
                }
            )
        }
    }
        
        ExtendedFloatingActionButton(
            onClick = { showCreateDialog = true },
            expanded = fabExpanded,
            icon = { Icon(Icons.Default.Add, contentDescription = "Создать") },
            text = { Text("Создать своё открытие") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        )
    }
    
    if (showCreateDialog) {
        CreateDiscoveryDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, category, color ->
                scope.launch {
                    repository.createDiscovery(title, description, category, color)
                    discoveries = repository.getAllDiscoveries()
                    showCreateDialog = false
                }
            }
        )
    }
}

@Composable
fun CreateDiscoveryDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Технологии") }
    var selectedColor by remember { mutableStateOf("FF4CAF50") }
    
    val categories = listOf(
        "Технологии" to "FF4CAF50",
        "Наука" to "FF2196F3",
        "Искусство" to "FFFF9800",
        "Спорт" to "FFF44336",
        "Образование" to "FF9C27B0",
        "Бизнес" to "FF00BCD4"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(16.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                Text(
                    text = "Создать открытие",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Категория",
                    style = MaterialTheme.typography.titleSmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(300.dp)
                ) {
                    items(categories.size) { index ->
                        val (category, color) = categories[index]
                        Card(
                            onClick = {
                                selectedCategory = category
                                selectedColor = color
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedCategory == category)
                                    Color(android.graphics.Color.parseColor("#$color"))
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    when(category) {
                                        "Технологии" -> Icons.Default.Computer
                                        "Наука" -> Icons.Default.Science
                                        "Искусство" -> Icons.Default.Palette
                                        "Спорт" -> Icons.Default.FitnessCenter
                                        "Образование" -> Icons.Default.School
                                        "Бизнес" -> Icons.Default.Business
                                        else -> Icons.Default.Category
                                    },
                                    contentDescription = null,
                                    tint = if (selectedCategory == category)
                                        Color.White
                                    else
                                        Color(android.graphics.Color.parseColor("#$color")),
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    category,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedCategory == category)
                                        Color.White
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
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
                            if (title.isNotBlank() && description.isNotBlank()) {
                                onCreate(title, description, selectedCategory, selectedColor)
                            }
                        },
                        enabled = title.isNotBlank() && description.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text("Создать")
                    }
                }
            }
        }
    }
}

@Composable
fun DiscoveryCard(
    discovery: su.SkrinVex.ofox.data.Discovery,
    onJoin: () -> Unit,
    onDiscuss: () -> Unit = {}
) {
    var showDetails by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor("#${discovery.colorHex}")))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = discovery.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${discovery.participants} участников",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = discovery.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = discovery.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (discovery.isJoined) {
                    Button(
                        onClick = onDiscuss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Открыть")
                    }
                } else {
                    Button(
                        onClick = onJoin,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Присоединиться")
                    }
                }
                
                OutlinedButton(
                    onClick = { showDetails = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Подробнее")
                }
            }
        }
    }

    if (showDetails) {
        DiscoveryDetailsDialog(
            discovery = discovery,
            onDismiss = { showDetails = false }
        )
    }
}

@Composable
fun DiscoveryDetailsDialog(
    discovery: su.SkrinVex.ofox.data.Discovery,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
    val createdDate = dateFormat.format(Date(System.currentTimeMillis() - (1..30).random() * 24 * 60 * 60 * 1000L))
    
    Dialog(onDismissRequest = onDismiss) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor("#${discovery.colorHex}")))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = discovery.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                DetailRow(icon = Icons.Default.Category, label = "Категория", value = discovery.category)
                DetailRow(icon = Icons.Default.People, label = "Участников", value = "${discovery.participants}")
                DetailRow(icon = Icons.Default.CalendarToday, label = "Создано", value = createdDate)
                DetailRow(icon = Icons.Default.Person, label = "Создатель", value = "Komari")
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Описание",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = discovery.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Закрыть")
                }
            }
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        )
    }
}
