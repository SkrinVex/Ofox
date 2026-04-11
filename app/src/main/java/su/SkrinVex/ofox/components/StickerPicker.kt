package su.SkrinVex.ofox.components

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository
import su.SkrinVex.ofox.data.api.models.StickerItem
import su.SkrinVex.ofox.data.api.models.StickerPack
import su.SkrinVex.ofox.screens.StickerEditorActivity

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StickerPicker(
    repository: Repository,
    onStickerSelected: (StickerItem) -> Unit,
    onDismiss: () -> Unit,
    initialPackId: Int? = null
) {
    var recent by remember { mutableStateOf(listOf<StickerItem>()) }
    var packs by remember { mutableStateOf(listOf<StickerPack>()) }
    var selectedPackId by remember { mutableStateOf(initialPackId) }
    var isLoading by remember { mutableStateOf(true) }
    var showCatalog by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun reload(selectPackId: Int? = selectedPackId) {
        scope.launch {
            isLoading = true
            val data = repository.getStickers()
            recent = data.recent
            packs = data.packs
            // Если выбранный набор больше не существует — переключаемся на недавние
            selectedPackId = if (selectPackId != null && data.packs.none { it.id == selectPackId }) null
                            else selectPackId
            isLoading = false
        }
    }

    val editorLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uriStr = result.data?.getStringExtra(StickerEditorActivity.RESULT_STICKER_URI)
            if (uriStr != null) {
                scope.launch {
                    isLoading = true
                    val uploaded = repository.uploadSticker(Uri.parse(uriStr), context, selectedPackId?.takeIf { it > 0 })
                    if (uploaded == null) {
                        uploadError = "Не удалось загрузить стикер. Попробуйте снова."
                    }
                    reload()
                }
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: ""
            if (mime == "image/gif") {
                uploadError = "GIF не поддерживается для стикеров. Выберите PNG, JPEG или WebP."
            } else {
                editorLauncher.launch(StickerEditorActivity.createIntent(context, uri))
            }
        }
    }

    LaunchedEffect(Unit) { reload(initialPackId) }

    if (showCatalog) {
        StickerCatalog(
            repository = repository,
            installedPackIds = packs.filter { it.id > 0 }.map { it.id }.toSet(),
            onPackSelected = { packId ->
                scope.launch {
                    showCatalog = false
                    reload(packId)
                }
            },
            onDismiss = { showCatalog = false }
        )
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { Box(Modifier.padding(vertical = 8.dp)) {
            Box(Modifier.width(40.dp).height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                .align(Alignment.Center))
        }},
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
        ) {
            // Ошибка загрузки
            if (uploadError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(uploadError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                    TextButton(onClick = { uploadError = null }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("OK", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            // Сетка стикеров
            val currentStickers = if (selectedPackId == null) recent
                                   else packs.find { it.id == selectedPackId }?.stickers ?: emptyList()

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    currentStickers.isEmpty() -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            if (selectedPackId == null) "Нет недавних стикеров" else "Набор пуст",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        if (selectedPackId != null) {
                            TextButton(onClick = { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Добавить стикер") }
                        }
                    }
                    else -> {
                        val currentPack = packs.find { it.id == selectedPackId }
                        Column {
                            // Название набора
                            if (selectedPackId != null && currentPack != null) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        currentPack.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        "• ${currentPack.creator_name}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                    )
                                }
                            }
                            val currentUserId = remember { repository.getCurrentUserId() }
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(4),
                                contentPadding = PaddingValues(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(currentStickers, key = { it.id }) { sticker ->
                                    val packName = currentPack?.name ?: ""
                                    val isOwner = currentPack != null && currentPack.creator_id == currentUserId
                                    Box(modifier = Modifier.size(80.dp)) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(sticker.url)
                                                .diskCachePolicy(CachePolicy.ENABLED)
                                                .memoryCachePolicy(CachePolicy.ENABLED)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(MaterialTheme.shapes.small)
                                                .clickable { onStickerSelected(sticker.copy(pack_name = packName)) }
                                        )
                                        // Кнопка удаления — только для владельца набора
                                        if (isOwner && selectedPackId != null && selectedPackId!! > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .align(Alignment.TopEnd)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.error)
                                                    .clickable {
                                                        scope.launch {
                                                            repository.deleteSticker(sticker.id)
                                                            reload()
                                                        }
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete, null,
                                                    tint = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            // Нижняя панель: иконки наборов
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                verticalAlignment = Alignment.CenterVertically,
                contentPadding = PaddingValues(horizontal = 6.dp)
            ) {
                // Недавние
                item {
                    val sel = selectedPackId == null
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedPackId = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🕐", fontSize = 22.sp)
                    }
                }
                // Наборы
                items(packs) { pack ->
                    val sel = selectedPackId == pack.id
                    val preview = pack.stickers?.firstOrNull()?.url
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedPackId = pack.id },
                        contentAlignment = Alignment.Center
                    ) {
                        if (preview != null) {
                            AsyncImage(model = preview, contentDescription = pack.name, modifier = Modifier.size(30.dp))
                        } else {
                            Text(pack.name.take(1), fontSize = 18.sp)
                        }
                    }
                }
                // Разделитель
                item {
                    Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(24.dp).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)))
                }
                // Каталог
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Transparent)
                            .clickable { showCatalog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Explore, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(22.dp))
                    }
                }
                // Добавить стикер
                item {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { imagePicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StickerCatalog(
    repository: Repository,
    installedPackIds: Set<Int> = emptySet(),
    onPackSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var packs by remember { mutableStateOf(listOf<StickerPack>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showCreate by remember { mutableStateOf(false) }
    var newPackName by remember { mutableStateOf("") }
    // null = список, non-null = просмотр набора
    var viewingPack by remember { mutableStateOf<StickerPack?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentUserId = remember { repository.getCurrentUserId() }

    fun loadPacks() {
        scope.launch {
            val my = repository.getMyPacks()
            val public = repository.getPublicPacks()
            val myIds = my.map { it.id }.toSet()
            packs = my + public.filter { it.id !in myIds }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { loadPacks() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            if (viewingPack != null) {
                // ── Просмотр набора ──────────────────────────────────────────
                val pack = viewingPack!!
                val isInstalled = pack.id in installedPackIds || pack.creator_id == currentUserId
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewingPack = null }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                    Text(pack.name, style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (isInstalled) {
                        TextButton(onClick = {
                            scope.launch { onPackSelected(pack.id) }
                        }) { Text("Открыть") }
                        if (pack.creator_id == currentUserId) {
                            // Владелец — удалить набор полностью из БД
                            TextButton(onClick = {
                                scope.launch {
                                    repository.deletePack(pack.id)
                                    loadPacks()
                                    viewingPack = null
                                }
                            }) { Text("Удалить набор", color = MaterialTheme.colorScheme.error) }
                        } else {
                            // Не владелец — убрать из своих
                            TextButton(onClick = {
                                scope.launch {
                                    repository.uninstallPack(pack.id)
                                    loadPacks()
                                    viewingPack = null
                                }
                            }) { Text("Убрать", color = MaterialTheme.colorScheme.error) }
                        }
                    } else {
                        Button(onClick = {
                            scope.launch {
                                repository.installPack(pack.id)
                                onPackSelected(pack.id)
                            }
                        }) { Text("Добавить") }
                    }
                }
                HorizontalDivider()
                val stickers = pack.stickers ?: emptyList()
                if (stickers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Набор пуст", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(stickers, key = { it.id }) { sticker ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(sticker.url).crossfade(true).build(),
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.small)
                            )
                        }
                    }
                }
            } else {
                // ── Список наборов ───────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Каталог стикеров", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showCreate = !showCreate }) {
                        Text(if (showCreate) "Отмена" else "+ Создать набор")
                    }
                }

                androidx.compose.animation.AnimatedVisibility(visible = showCreate) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newPackName, onValueChange = { newPackName = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Название набора") },
                                singleLine = true, shape = MaterialTheme.shapes.medium
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showCreate = false; newPackName = "" },
                                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium
                                ) { Text("Отмена") }
                                Button(
                                    onClick = {
                                        if (newPackName.isNotBlank()) scope.launch {
                                            repository.createPack(newPackName.trim(), isPublic = true)
                                            newPackName = ""; showCreate = false
                                            loadPacks()
                                        }
                                    },
                                    enabled = newPackName.isNotBlank(),
                                    modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.medium
                                ) { Text("Создать") }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                if (isLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else if (packs.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("📦", fontSize = 48.sp)
                            Text("Наборов пока нет", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(packs, key = { it.id }) { pack ->
                            val isInstalled = pack.id in installedPackIds || pack.creator_id == currentUserId
                            Card(
                                modifier = Modifier.clickable {
                                    // Загружаем стикеры набора для просмотра
                                    scope.launch {
                                        val full = if (pack.stickers.isNullOrEmpty())
                                            repository.getPackBySlug(pack.slug) ?: pack
                                        else pack
                                        viewingPack = full
                                    }
                                },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isInstalled)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.surfaceVariant
                                ),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (pack.preview_url != null) {
                                        AsyncImage(
                                            model = pack.preview_url, contentDescription = null,
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surface),
                                            contentAlignment = Alignment.Center
                                        ) { Text("📦", fontSize = 24.sp) }
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pack.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${pack.sticker_count} стикеров • ${pack.creator_name}" +
                                                if (!pack.is_public) " • приватный" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    if (isInstalled) {
                                        Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    repository.installPack(pack.id)
                                                    onPackSelected(pack.id)
                                                }
                                            },
                                            shape = MaterialTheme.shapes.medium,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) { Text("Добавить") }
                                    }
                                }
                                if (pack.is_public) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                val shareText = "Набор стикеров «${pack.name}» в OFOX: https://api.skrinvex.su/ofox/stickers/preview/${pack.slug}"
                                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                    type = "text/plain"
                                                    putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                }
                                                context.startActivity(android.content.Intent.createChooser(intent, "Поделиться набором"))
                                            }
                                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Share, null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp))
                                        Text(
                                            "api.skrinvex.su/ofox/stickers/preview/${pack.slug}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
