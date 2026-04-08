package su.SkrinVex.ofox.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CustomizationScreen(onBack: () -> Unit, onThemeClick: () -> Unit, onFontSizeClick: () -> Unit, onCornerRadiusClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ofox_prefs", android.content.Context.MODE_PRIVATE) }
    var compactNav by remember { mutableStateOf(prefs.getBoolean("compact_nav", false)) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "Кастомизация",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CustomizationCard(
                title = "Цветовая палитра",
                description = "Выберите цветовую схему приложения",
                icon = Icons.Default.Palette,
                onClick = onThemeClick
            )
            
            CustomizationCard(
                title = "Размер шрифта",
                description = "Настройте размер текста",
                icon = Icons.Default.TextFields,
                onClick = onFontSizeClick
            )
            
            CustomizationCard(
                title = "Форма элементов",
                description = "Округлость карточек и кнопок",
                icon = Icons.Default.RoundedCorner,
                onClick = onCornerRadiusClick
            )

            // Компактное меню
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ViewCompact, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Компактное меню", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Скрыть подписи в панели навигации", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Switch(
                        checked = compactNav,
                        onCheckedChange = {
                            compactNav = it
                            prefs.edit().putBoolean("compact_nav", it).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomizationCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
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
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("ofox_prefs", android.content.Context.MODE_PRIVATE)
    var selectedTheme by remember { mutableStateOf(prefs.getString("theme", "Оранжевый") ?: "Оранжевый") }
    
    val themes = listOf(
        "Оранжевый" to Color(0xFFFF6B35),
        "Синий" to Color(0xFF2196F3),
        "Фиолетовый" to Color(0xFF9C27B0),
        "Зелёный" to Color(0xFF4CAF50),
        "Красный" to Color(0xFFF44336),
        "Розовый" to Color(0xFFE91E63),
        "Бирюзовый" to Color(0xFF00BCD4),
        "Янтарный" to Color(0xFFFF9800),
        "Лаймовый" to Color(0xFFCDDC39),
        "Индиго" to Color(0xFF3F51B5),
        "Коричневый" to Color(0xFF795548),
        "Серый" to Color(0xFF607D8B),
        "Малиновый" to Color(0xFFE91E63),
        "Морская волна" to Color(0xFF009688),
        "Золотой" to Color(0xFFFFD700)
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "Цветовая палитра",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(themes.size) { index ->
                val (name, color) = themes[index]
                Card(
                    onClick = {
                        selectedTheme = name
                        su.SkrinVex.ofox.ui.theme.CustomizationManager.saveTheme(context, name)
                        (context as? android.app.Activity)?.recreate()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTheme == name)
                            color.copy(alpha = 0.2f)
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.medium,
                    border = if (selectedTheme == name)
                        androidx.compose.foundation.BorderStroke(2.dp, color)
                    else null
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (selectedTheme == name) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun FontSizeScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("ofox_prefs", android.content.Context.MODE_PRIVATE)
    var selectedSize by remember { mutableStateOf(prefs.getString("font_size", "NORMAL") ?: "NORMAL") }
    
    val sizes = listOf(
        su.SkrinVex.ofox.ui.theme.FontSize.SMALL,
        su.SkrinVex.ofox.ui.theme.FontSize.NORMAL,
        su.SkrinVex.ofox.ui.theme.FontSize.LARGE,
        su.SkrinVex.ofox.ui.theme.FontSize.EXTRA_LARGE
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "Размер шрифта",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sizes.forEach { size ->
                Card(
                    onClick = {
                        selectedSize = size.name
                        su.SkrinVex.ofox.ui.theme.CustomizationManager.saveFontSize(context, size)
                        (context as? android.app.Activity)?.recreate()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedSize == size.name)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                size.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = MaterialTheme.typography.titleMedium.fontSize * size.scale),
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Пример текста",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize * size.scale),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (selectedSize == size.name) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CornerRadiusScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("ofox_prefs", android.content.Context.MODE_PRIVATE)
    var selectedRadius by remember { mutableStateOf(prefs.getString("corner_radius", "NORMAL") ?: "NORMAL") }
    
    val radii = listOf(
        su.SkrinVex.ofox.ui.theme.CornerRadius.SHARP,
        su.SkrinVex.ofox.ui.theme.CornerRadius.NORMAL,
        su.SkrinVex.ofox.ui.theme.CornerRadius.ROUNDED,
        su.SkrinVex.ofox.ui.theme.CornerRadius.EXTRA_ROUNDED
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Назад")
            }
            Text(
                "Форма элементов",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            radii.forEach { radius ->
                Card(
                    onClick = {
                        selectedRadius = radius.name
                        su.SkrinVex.ofox.ui.theme.CustomizationManager.saveCornerRadius(context, radius)
                        (context as? android.app.Activity)?.recreate()
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedRadius == radius.name)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(radius.value)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                radius.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Округлость: ${radius.value}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        if (selectedRadius == radius.name) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
