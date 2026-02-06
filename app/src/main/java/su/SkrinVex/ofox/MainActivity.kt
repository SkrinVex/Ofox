package su.SkrinVex.ofox

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = Repository(this)
        
        lifecycleScope.launch {
            repository.initializeSampleData()
        }
        
        enableEdgeToEdge()
        setContent {
            OfoxTheme {
                var isAuthenticated by remember { mutableStateOf(repository.isLoggedIn()) }
                
                if (!isAuthenticated) {
                    AuthScreen(
                        repository = repository,
                        onAuthSuccess = { isAuthenticated = true }
                    )
                } else {
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    
                    val shouldShowBottomBar = currentRoute in listOf(
                        Screen.Home.route,
                        Screen.Chats.route,
                        Screen.Feed.route,
                        Screen.Settings.route
                    )
                    
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
                                                Icon(
                                                    screen.icon, 
                                                    contentDescription = screen.title
                                                ) 
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
                            startDestination = Screen.Home.route,
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable(Screen.Home.route) { HomeScreen(repository, navController) }
                            composable(Screen.Chats.route) { ChatsScreen(repository, navController) }
                            composable("chat/{chatId}") { backStackEntry ->
                                ChatDetailScreen(
                                    repository = repository,
                                    chatId = backStackEntry.arguments?.getString("chatId")?.toIntOrNull() ?: 0,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(Screen.Feed.route) { FeedScreen(repository, navController) }
                            composable(Screen.Settings.route) { 
                                SettingsScreen(
                                    repository = repository,
                                    navController = navController,
                                    onLogout = { isAuthenticated = false }
                                ) 
                            }
                            composable("edit_profile") {
                                EditProfileScreen(repository) { navController.popBackStack() }
                            }
                            composable("customization") {
                                CustomizationScreen(
                                    onBack = { navController.popBackStack() },
                                    onThemeClick = { navController.navigate("theme") }
                                )
                            }
                            composable("theme") {
                                ThemeScreen { navController.popBackStack() }
                            }
                            composable("about") {
                                AboutScreen { navController.popBackStack() }
                            }
                            composable("user_profile/{userId}") { backStackEntry ->
                                UserProfileScreen(
                                    userId = backStackEntry.arguments?.getString("userId")?.toIntOrNull() ?: 0,
                                    repository = repository,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable("discovery_discussion/{discoveryId}") { backStackEntry ->
                                DiscoveryDiscussionScreen(
                                    discoveryId = backStackEntry.arguments?.getString("discoveryId")?.toIntOrNull() ?: 0,
                                    repository = repository,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}