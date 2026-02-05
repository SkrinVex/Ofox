package su.SkrinVex.ofox.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Explore
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Главная", Icons.Default.Home)
    object Chats : Screen("chats", "Чаты", Icons.AutoMirrored.Filled.Chat)
    object Feed : Screen("feed", "Открытия", Icons.Default.Explore)
    object Settings : Screen("settings", "Параметры", Icons.Default.Settings)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Chats,
    Screen.Feed,
    Screen.Settings
)
