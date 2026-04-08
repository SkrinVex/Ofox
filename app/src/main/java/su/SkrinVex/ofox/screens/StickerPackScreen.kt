package su.SkrinVex.ofox.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.data.api.models.StickerPack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerPackScreen(
    slug: String,
    repository: Repository,
    onBack: () -> Unit
) {
    var pack by remember { mutableStateOf<StickerPack?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isInstalled by remember { mutableStateOf(false) }
    var isInstalling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(slug) {
        pack = repository.getPackBySlug(slug)
        if (pack != null) {
            val userPacks = repository.getStickers().packs
            isInstalled = userPacks.any { it.id == pack!!.id }
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (pack != null) "Стикеры — ${pack!!.name}" else "Набор стикеров",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (pack != null && !isInstalled) {
                        Button(
                            onClick = {
                                scope.launch {
                                    isInstalling = true
                                    repository.installPack(pack!!.id)
                                    isInstalled = true
                                    isInstalling = false
                                }
                            },
                            enabled = !isInstalling,
                            modifier = Modifier.padding(end = 8.dp)
                        ) { Text(if (isInstalling) "..." else "Добавить") }
                    } else if (isInstalled) {
                        Text(
                            "✓ Установлен",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                pack == null -> Text(
                    "Набор не найден",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                pack!!.stickers.isNullOrEmpty() -> Text(
                    "В наборе нет стикеров",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                else -> {
                    Column {
                        if (pack!!.description.isNotBlank()) {
                            Text(
                                pack!!.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        Text(
                            "${pack!!.stickers!!.size} стикеров • ${pack!!.creator_name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(pack!!.stickers!!) { sticker ->
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(sticker.url)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
