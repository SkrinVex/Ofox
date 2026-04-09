package su.SkrinVex.ofox.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeedScreen(
    repository: Repository, 
    navController: androidx.navigation.NavController? = null,
    highlightDiscoveryId: Int? = null
) {
    var discoveries by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Discovery>()) }
    var filteredDiscoveries by remember { mutableStateOf(listOf<su.SkrinVex.ofox.data.Discovery>()) }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var discoveryDraftTitle by remember { mutableStateOf("") }
    var discoveryDraftDescription by remember { mutableStateOf("") }
    var discoveryDraftCategory by remember { mutableStateOf("Технологии") }
    var discoveryDraftColor by remember { mutableStateOf("FF4CAF50") }
    var isLoading by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }
    var localHighlightDiscoveryId by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
    val fabVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100 }
    }

    LaunchedEffect(highlightDiscoveryId) {
        if (highlightDiscoveryId != null && highlightDiscoveryId != localHighlightDiscoveryId) {
            localHighlightDiscoveryId = highlightDiscoveryId
            kotlinx.coroutines.delay(3000)
            localHighlightDiscoveryId = null
        }
    }

    fun loadDiscoveries() {
        scope.launch {
            isLoading = true
            discoveries = repository.getAllDiscoveries()
            filteredDiscoveries = discoveries
            isLoading = false
        }
    }
    fun filterDiscoveries(query: String) {
        filteredDiscoveries = if (query.isEmpty()) {
            discoveries
        } else {
            discoveries.filter { discovery ->
                discovery.title.contains(query, ignoreCase = true) ||
                discovery.description.contains(query, ignoreCase = true) ||
                discovery.category.contains(query, ignoreCase = true) ||
                levenshteinDistance(discovery.title.lowercase(), query.lowercase()) <= 2
            }
        }
    }

    LaunchedEffect(Unit) {
        if (discoveries.isEmpty() && isInitialized.not()) {
            loadDiscoveries()
            isInitialized = true
        }
    }
    
    LaunchedEffect(localHighlightDiscoveryId, filteredDiscoveries.size) {
        localHighlightDiscoveryId?.let { discoveryId ->
            if (filteredDiscoveries.isNotEmpty() && !isLoading) {
                android.util.Log.d("FeedScreen", "Trying to scroll to discovery $discoveryId")
                val index = filteredDiscoveries.indexOfFirst { it.id == discoveryId }
                android.util.Log.d("FeedScreen", "Discovery index: $index")
                if (index != -1) {
                    kotlinx.coroutines.delay(500)
                    listState.scrollToItem(index + 4)
                    android.util.Log.d("FeedScreen", "Scrolled to index ${index + 4}")
                }
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
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    filterDiscoveries(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск открытий...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { 
                            searchQuery = ""
                            filterDiscoveries("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }
        
        item {
            Text(
                text = "Вы участвуете",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        
        val joinedDiscoveries = filteredDiscoveries.filter { it.isJoined }
        
        if (joinedDiscoveries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Вы нигде не участвуете",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Присоединитесь к открытиям ниже или создайте своё",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(joinedDiscoveries) { discovery ->
                DiscoveryCard(
                    discovery = discovery,
                    onJoin = {
                        scope.launch {
                            repository.toggleJoinDiscovery(discovery)?.let { updated ->
                                discoveries = discoveries.map { if (it.id == updated.id) updated else it }
                                filterDiscoveries(searchQuery)
                            }
                        }
                    },
                    onDiscuss = {
                        navController?.navigate("discovery_discussion/${discovery.id}")
                    },
                    isHighlighted = localHighlightDiscoveryId == discovery.id
                )
            }
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
        
        val recommendedDiscoveries = filteredDiscoveries.filter { !it.isJoined }
        
        if (recommendedDiscoveries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Нет доступных открытий",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Создайте первое открытие для сообщества",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recommendedDiscoveries) { discovery ->
                DiscoveryCard(
                    discovery = discovery,
                    onJoin = {
                        scope.launch {
                            repository.toggleJoinDiscovery(discovery)?.let { updated ->
                                discoveries = discoveries.map { if (it.id == updated.id) updated else it }
                                filterDiscoveries(searchQuery)
                            }
                        }
                    },
                    onDiscuss = {
                        navController?.navigate("discovery_discussion/${discovery.id}")
                    },
                    isHighlighted = localHighlightDiscoveryId == discovery.id
                )
            }
        }
        }
        
        androidx.compose.animation.AnimatedVisibility(
            visible = fabVisible,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                expanded = fabExpanded,
                icon = { Icon(Icons.Default.Add, contentDescription = "Создать") },
                text = { Text("Создать открытие") },
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(end = 16.dp, bottom = 96.dp),
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    }
    
    if (showCreateDialog) {
        CreateDiscoveryDialog(
            initialTitle = discoveryDraftTitle,
            initialDescription = discoveryDraftDescription,
            initialCategory = discoveryDraftCategory,
            initialColor = discoveryDraftColor,
            onDismiss = { showCreateDialog = false },
            onDraftChange = { title, description, category, color ->
                discoveryDraftTitle = title
                discoveryDraftDescription = description
                discoveryDraftCategory = category
                discoveryDraftColor = color
            },
            onCreate = { title, description, category, color ->
                scope.launch {
                    val created = repository.createDiscovery(title, description, category, color)
                    showCreateDialog = false
                    discoveryDraftTitle = ""
                    discoveryDraftDescription = ""
                    discoveryDraftCategory = "Технологии"
                    discoveryDraftColor = "FF4CAF50"
                    if (created != null) {
                        loadDiscoveries()
                    }
                }
            }
        )
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CreateDiscoveryDialog(
    initialTitle: String = "",
    initialDescription: String = "",
    initialCategory: String = "Технологии",
    initialColor: String = "FF4CAF50",
    onDismiss: () -> Unit,
    onDraftChange: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onCreate: (String, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 3 })
    
    LaunchedEffect(title, description, selectedCategory, selectedColor) {
        onDraftChange(title, description, selectedCategory, selectedColor)
    }
    
    val categories = listOf(
        listOf("Технологии" to "FF4CAF50", "Наука" to "FF2196F3"),
        listOf("Искусство" to "FFFF9800", "Спорт" to "FFF44336"),
        listOf("Образование" to "FF9C27B0", "Бизнес" to "FF00BCD4")
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp)
            ) {
                Text(
                    text = "Создать открытие",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 500) title = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    supportingText = { Text("${title.length}/500") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 5000) description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = MaterialTheme.shapes.medium,
                    supportingText = { Text("${description.length}/5000") }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "Категория",
                    style = MaterialTheme.typography.titleSmall
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            categories[page].forEach { (category, color) ->
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
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.weight(1f)
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
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(pagerState.pageCount) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == index)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                        )
                    }
                }
            }
            
            HorizontalDivider()
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
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

@Composable
fun DiscoveryCard(
    discovery: su.SkrinVex.ofox.data.Discovery,
    onJoin: () -> Unit,
    onDiscuss: () -> Unit = {},
    isHighlighted: Boolean = false
) {
    var showDetails by remember { mutableStateOf(false) }
    var shouldHighlight by remember { mutableStateOf(false) }
    
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            android.util.Log.d("DiscoveryCard", "Highlighting discovery ${discovery.id}: ${discovery.title}")
            shouldHighlight = true
            kotlinx.coroutines.delay(2500)
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
            } else {
                MaterialTheme.colorScheme.surface
            }
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
                        .background(Color(android.graphics.Color.parseColor(discovery.colorHex)))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = discovery.category,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${discovery.participants} участников",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
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
    val createdDate = if (discovery.createdAt > 0) {
        dateFormat.format(Date(discovery.createdAt))
    } else {
        "Неизвестно"
    }
    
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
                            .background(Color(android.graphics.Color.parseColor(discovery.colorHex)))
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
                DetailRow(icon = Icons.Default.Person, label = "Создатель", value = discovery.creatorName)
                
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

fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length
    val dp = Array(len1 + 1) { IntArray(len2 + 1) }
    
    for (i in 0..len1) dp[i][0] = i
    for (j in 0..len2) dp[0][j] = j
    
    for (i in 1..len1) {
        for (j in 1..len2) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost
            )
        }
    }
    
    return dp[len1][len2]
}
