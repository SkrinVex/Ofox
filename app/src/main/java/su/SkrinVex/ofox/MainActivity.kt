package su.SkrinVex.ofox

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.navigation.Screen
import su.SkrinVex.ofox.navigation.bottomNavItems
import su.SkrinVex.ofox.screens.*
import su.SkrinVex.ofox.ui.theme.OfoxTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: Repository
    private val pendingDeepLink = mutableStateOf<DeepLinkData?>(null)

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Глобальная обработка ошибок
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("OFOX_CRASH", "Uncaught exception in thread ${thread.name}", throwable)
            throwable.printStackTrace()
        }
        
        repository = Repository(this)
        
        // Обработка deep link
        handleDeepLink(intent)
        
        // Синхронизация при запуске
        lifecycleScope.launch {
            try {
                if (repository.isLoggedIn()) {
                    repository.syncData()
                }
            } catch (e: Exception) {
                android.util.Log.e("OFOX", "Sync error", e)
            }
        }
        
        enableEdgeToEdge()
        setContent {
            OfoxTheme {
                var isAuthenticated by remember { mutableStateOf(repository.isLoggedIn()) }
                val deepLink by pendingDeepLink
                var currentBan by remember { mutableStateOf<su.SkrinVex.ofox.data.api.models.BanResponse?>(null) }
                var currentWarning by remember { mutableStateOf<su.SkrinVex.ofox.data.api.models.WarningResponse?>(null) }
                var hasUndeliveredWarnings by remember { mutableStateOf(false) }
                var deletedContent by remember { mutableStateOf<Pair<Int, Triple<String, Int, String>>?>(null) }
                val scope = rememberCoroutineScope()
                
                // Проверка бана при запуске
                LaunchedEffect(isAuthenticated) {
                    if (isAuthenticated) {
                        kotlinx.coroutines.delay(500)
                        currentBan = repository.getBan()
                        
                        val warnings = repository.getWarnings()
                        hasUndeliveredWarnings = warnings.isNotEmpty()
                        if (warnings.isNotEmpty()) {
                            val warning = warnings.first()
                            currentWarning = su.SkrinVex.ofox.data.api.models.WarningResponse(
                                id = warning.id,
                                reason = warning.reason,
                                warningNumber = warning.warningNumber,
                                totalWarnings = 3
                            )
                        }
                        
                        // Проверяем удаленный контент
                        val deletedContentList = repository.getDeletedContent()
                        android.util.Log.d("OFOX", "Deleted content: ${deletedContentList.size} items")
                        if (deletedContentList.isNotEmpty()) {
                            val content = deletedContentList.first()
                            android.util.Log.d("OFOX", "Showing deleted content: ${content.contentType} #${content.contentId}")
                            deletedContent = Pair(content.id, Triple(content.contentType, content.contentId, content.reason))
                        }
                    }
                }
                
                // WebSocket события для модерации
                val wsClient = remember { su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(this@MainActivity) }
                LaunchedEffect(wsClient.events) {
                    wsClient.events.collect { event ->
                        when (event) {
                            is su.SkrinVex.ofox.data.api.WSEvent.Warning -> {
                                currentWarning = su.SkrinVex.ofox.data.api.models.WarningResponse(
                                    id = event.id,
                                    reason = event.reason,
                                    warningNumber = event.warningNumber,
                                    totalWarnings = event.totalWarnings
                                )
                                hasUndeliveredWarnings = true
                            }
                            is su.SkrinVex.ofox.data.api.WSEvent.Ban -> {
                                currentBan = su.SkrinVex.ofox.data.api.models.BanResponse(
                                    reason = event.reason,
                                    expiresAt = event.expiresAt
                                )
                            }
                            is su.SkrinVex.ofox.data.api.WSEvent.ContentDeleted -> {
                                deletedContent = Pair(-1, Triple(event.contentType, event.contentId, event.reason))
                            }
                            else -> {}
                        }
                    }
                }
                
                // Диалог бана (блокирует все)
                currentBan?.let { ban ->
                    su.SkrinVex.ofox.components.BanDialog(
                        reason = ban.reason,
                        expiresAt = ban.expiresAt
                    )
                }
                
                // Диалог предупреждения
                currentWarning?.let { warning ->
                    su.SkrinVex.ofox.components.WarningDialog(
                        warningNumber = warning.warningNumber,
                        totalWarnings = warning.totalWarnings,
                        reason = warning.reason,
                        onDismiss = {
                            lifecycleScope.launch {
                                repository.markWarningDelivered(warning.id)
                                currentWarning = null
                                hasUndeliveredWarnings = false
                            }
                        }
                    )
                }
                
                // Диалог удаления контента
                deletedContent?.let { (dbId, data) ->
                    val (type, contentId, reason) = data
                    su.SkrinVex.ofox.components.ContentDeletedDialog(
                        contentType = type,
                        contentId = contentId,
                        reason = reason,
                        onDismiss = { 
                            if (dbId > 0) {
                                scope.launch {
                                    try {
                                        repository.markContentViewed(dbId)
                                    } catch (e: Exception) {
                                        android.util.Log.e("OFOX", "Error marking content viewed", e)
                                    }
                                }
                            }
                            deletedContent = null 
                        }
                    )
                }
                
                android.util.Log.d("OFOX", "Compose: isAuthenticated=$isAuthenticated, deepLink=$deepLink")
                
                if (!isAuthenticated) {
                    AuthScreen(
                        repository = repository,
                        onAuthSuccess = { isAuthenticated = true }
                    )
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    val initialRoute = remember {
                        when (deepLink) {
                            is DeepLinkData.Post -> Screen.Home.route
                            is DeepLinkData.Discovery -> Screen.Feed.route
                            else -> Screen.Home.route
                        }
                    }
                    
                    // Навигация при изменении deep link
                    LaunchedEffect(deepLink, pendingDeepLink.value) {
                        val link = pendingDeepLink.value ?: deepLink
                        link?.let {
                            android.util.Log.d("OFOX", "Deep link changed: $it, current: $currentRoute")
                            
                            when (it) {
                                is DeepLinkData.Post -> {
                                    if (currentRoute != Screen.Home.route) {
                                        kotlinx.coroutines.delay(300)
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    }
                                }
                                is DeepLinkData.Discovery -> {
                                    if (currentRoute != Screen.Feed.route) {
                                        kotlinx.coroutines.delay(300)
                                        navController.navigate(Screen.Feed.route) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    android.util.Log.d("OFOX", "Deep link: $deepLink, current route: $currentRoute, initial: $initialRoute")
                    
                    val shouldShowBottomBar = currentRoute in listOf(
                        Screen.Home.route,
                        Screen.Chats.route,
                        Screen.Feed.route,
                        Screen.Settings.route
                    )
                    
                    val chats by repository.chatsFlow.collectAsState(initial = emptyList())
                    val totalUnread = remember(chats) { chats.sumOf { it.unreadCount } }
                    
                    LaunchedEffect(Unit) {
                        repository.getAllChats()
                    }
                    
                    val wsClient = remember { su.SkrinVex.ofox.data.api.WebSocketClient.getInstance(this@MainActivity) }
                    LaunchedEffect(wsClient.events) {
                        wsClient.events.collect { event ->
                            android.util.Log.d("MainActivity", "WebSocket event received: $event")
                            when (event) {
                                is su.SkrinVex.ofox.data.api.WSEvent.NewMessage,
                                is su.SkrinVex.ofox.data.api.WSEvent.ChatUpdate -> {
                                    android.util.Log.d("MainActivity", "Reloading chats due to: $event")
                                    repository.getAllChats()
                                }
                                else -> {}
                            }
                        }
                    }
                    
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (shouldShowBottomBar) {
                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    bottomNavItems.forEach { screen ->
                                        NavigationBarItem(
                                            icon = { 
                                                BadgedBox(
                                                    badge = {
                                                        if (screen.route == Screen.Chats.route && totalUnread > 0) {
                                                            Badge {
                                                                Text(if (totalUnread > 9) "9+" else totalUnread.toString())
                                                            }
                                                        }
                                                        if (screen.route == Screen.Settings.route && hasUndeliveredWarnings) {
                                                            Badge(
                                                                containerColor = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        screen.icon, 
                                                        contentDescription = screen.title
                                                    )
                                                }
                                            },
                                            label = { Text(screen.title) },
                                            selected = currentRoute == screen.route,
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.startDestinationId)
                                                    launchSingleTop = true
                                                }
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = initialRoute,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Home.route) { 
                                val currentDeepLink by pendingDeepLink
                                val postId = (currentDeepLink ?: deepLink as? DeepLinkData.Post)?.let { 
                                    (it as? DeepLinkData.Post)?.postId 
                                }
                                
                                android.util.Log.d("OFOX", "HomeScreen composable: currentDeepLink=$currentDeepLink, deepLink=$deepLink, postId=$postId")
                                
                                HomeScreen(
                                    repository = repository, 
                                    navController = navController,
                                    highlightPostId = postId
                                )
                                
                                if (postId != null) {
                                    LaunchedEffect(postId) {
                                        android.util.Log.d("OFOX", "HomeScreen loaded with postId: $postId")
                                        kotlinx.coroutines.delay(5000)
                                        android.util.Log.d("OFOX", "Clearing pendingDeepLink")
                                        pendingDeepLink.value = null
                                    }
                                }
                            }
                            composable(Screen.Chats.route) { ChatsScreen(repository, navController) }
                            composable("chat/{chatId}") { backStackEntry ->
                                ChatDetailScreen(
                                    repository = repository,
                                    chatId = backStackEntry.arguments?.getString("chatId")?.toIntOrNull() ?: 0,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.Feed.route) { 
                                val currentDeepLink by pendingDeepLink
                                val discoveryId = (currentDeepLink as? DeepLinkData.Discovery)?.discoveryId
                                
                                FeedScreen(
                                    repository = repository, 
                                    navController = navController,
                                    highlightDiscoveryId = discoveryId
                                )
                                
                                if (discoveryId != null) {
                                    LaunchedEffect(discoveryId) {
                                        android.util.Log.d("OFOX", "FeedScreen loaded with discoveryId: $discoveryId")
                                        kotlinx.coroutines.delay(3000)
                                        pendingDeepLink.value = null
                                    }
                                }
                            }
                            composable(Screen.Settings.route) { 
                                SettingsScreen(
                                    repository = repository,
                                    navController = navController,
                                    onLogout = { isAuthenticated = false },
                                    hasUndeliveredWarnings = hasUndeliveredWarnings
                                ) 
                            }
                            composable("edit_profile") {
                                val currentUserId = repository.getCurrentUserId()
                                if (currentUserId != null) {
                                    UserProfileScreen(
                                        userId = currentUserId,
                                        repository = repository,
                                        onBack = { navController.popBackStack() },
                                        onEditProfile = { navController.navigate("edit_profile_form") },
                                        onPostClick = { postId ->
                                            android.util.Log.d("OFOX", "Profile: clicked post $postId")
                                            pendingDeepLink.value = DeepLinkData.Post(postId)
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                            }
                            composable("edit_profile_form") {
                                EditProfileScreen(repository) { 
                                    navController.popBackStack()
                                }
                            }
                            composable("user_profile/{userId}") { backStackEntry ->
                                val userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull()
                                if (userId != null) {
                                    UserProfileScreen(
                                        userId = userId,
                                        repository = repository,
                                        onBack = { navController.popBackStack() },
                                        onEditProfile = {},
                                        onPostClick = { postId ->
                                            android.util.Log.d("OFOX", "UserProfile: clicked post $postId")
                                            pendingDeepLink.value = DeepLinkData.Post(postId)
                                            navController.navigate(Screen.Home.route) {
                                                popUpTo(navController.graph.startDestinationId)
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                }
                            }
                            composable("customization") {
                                CustomizationScreen(
                                    onBack = { navController.popBackStack() },
                                    onThemeClick = { navController.navigate("theme") },
                                    onFontSizeClick = { navController.navigate("font_size") },
                                    onCornerRadiusClick = { navController.navigate("corner_radius") }
                                )
                            }
                            composable("theme") {
                                ThemeScreen { navController.popBackStack() }
                            }
                            composable("font_size") {
                                FontSizeScreen { navController.popBackStack() }
                            }
                            composable("corner_radius") {
                                CornerRadiusScreen { navController.popBackStack() }
                            }
                            composable("about") {
                                AboutScreen(
                                    repository = repository,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("user_profile/{userId}") { backStackEntry ->
                                UserProfileScreen(
                                    userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0,
                                    repository = repository,
                                    onBack = { navController.popBackStack() },
                                    onPostClick = { postId ->
                                        android.util.Log.d("OFOX", "UserProfile2: clicked post $postId")
                                        pendingDeepLink.value = DeepLinkData.Post(postId)
                                        navController.navigate(Screen.Home.route) {
                                            popUpTo(navController.graph.startDestinationId)
                                            launchSingleTop = true
                                        }
                                    }
                                )
                            }
                            composable("discovery_discussion/{discoveryId}") { backStackEntry ->
                                DiscoveryDiscussionScreen(
                                    discoveryId = backStackEntry.arguments?.getString("discoveryId")?.toIntOrNull() ?: 0,
                                    repository = repository,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToPost = { postId ->
                                        pendingDeepLink.value = DeepLinkData.Post(postId)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }
    
    private fun handleDeepLink(intent: Intent?) {
        intent?.data?.let { uri ->
            android.util.Log.d("OFOX", "Deep link received: $uri")
            android.util.Log.d("OFOX", "URI host: ${uri.host}, path: ${uri.path}, lastSegment: ${uri.lastPathSegment}")
            android.util.Log.d("OFOX", "Is logged in: ${repository.isLoggedIn()}")
            
            when {
                uri.host == "post" || uri.path?.contains("/post/") == true -> {
                    val postId = uri.lastPathSegment?.toIntOrNull()
                    android.util.Log.d("OFOX", "Post deep link: $postId")
                    if (postId != null) {
                        pendingDeepLink.value = DeepLinkData.Post(postId)
                        android.util.Log.d("OFOX", "Set pendingDeepLink to Post($postId)")
                    }
                }
                uri.host == "discovery" || uri.path?.contains("/discovery/") == true -> {
                    val discoveryId = uri.lastPathSegment?.toIntOrNull()
                    android.util.Log.d("OFOX", "Discovery deep link: $discoveryId")
                    if (discoveryId != null) {
                        pendingDeepLink.value = DeepLinkData.Discovery(discoveryId)
                        android.util.Log.d("OFOX", "Set pendingDeepLink to Discovery($discoveryId)")
                    }
                }
            }
        } ?: android.util.Log.d("OFOX", "No deep link data in intent")
    }
}

sealed class DeepLinkData {
    data class Post(val postId: Int) : DeepLinkData()
    data class Discovery(val discoveryId: Int) : DeepLinkData()
}