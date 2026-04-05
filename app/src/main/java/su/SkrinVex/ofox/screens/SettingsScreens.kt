package su.SkrinVex.ofox.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.components.UserAvatar
import su.SkrinVex.ofox.data.Repository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(repository: Repository, onBack: () -> Unit) {
    var user by remember { mutableStateOf<su.SkrinVex.ofox.data.User?>(null) }
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var tg by remember { mutableStateOf("") }
    var vk by remember { mutableStateOf("") }
    var github by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var youtube by remember { mutableStateOf("") }
    var bannerColor by remember { mutableStateOf("#4CAF50") }
    var avatarUrl by remember { mutableStateOf<String?>(null) }
    var isUploadingAvatar by remember { mutableStateOf(false) }
    var avatarError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val colors = listOf("#4CAF50", "#2196F3", "#9C27B0", "#F44336", "#FF9800", "#607D8B", "#000000")

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            isUploadingAvatar = true
            avatarError = null
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: throw Exception("Не удалось открыть файл")
                val bytes = stream.readBytes()
                stream.close()
                if (bytes.size > 4 * 1024 * 1024) {
                    avatarError = "Файл слишком большой. Максимум 4 МБ"
                    isUploadingAvatar = false
                    return@launch
                }
                val result = repository.uploadAvatar(bytes)
                result.fold(
                    onSuccess = { url ->
                        android.util.Log.d("AVATAR", "Upload success, url=$url")
                        // Инвалидируем старый кэш
                        val imageLoader = coil.Coil.imageLoader(context)
                        avatarUrl?.let { oldUrl ->
                            imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(oldUrl))
                            imageLoader.diskCache?.remove(oldUrl.substringBefore("?"))
                        }
                        avatarUrl = url
                        android.util.Log.d("AVATAR", "avatarUrl set to=$avatarUrl")
                    },
                    onFailure = { e ->
                        avatarError = when {
                            e.message?.contains("413") == true || e.message?.contains("too large") == true ->
                                "Файл слишком большой. Максимум 4 МБ"
                            e.message?.contains("415") == true ->
                                "Неподдерживаемый формат. Используйте JPG, PNG или WebP"
                            else -> "Ошибка загрузки. Попробуйте снова"
                        }
                    }
                )
            } catch (e: Exception) {
                avatarError = "Ошибка: ${e.message}"
            }
            isUploadingAvatar = false
        }
    }

    LaunchedEffect(Unit) {
        user = repository.getCurrentUser()
        name = user?.name ?: ""
        bio = user?.bio ?: ""
        bannerColor = user?.bannerColor ?: "#4CAF50"
        avatarUrl = user?.avatarUrl?.takeIf { it.isNotBlank() }
        
        try {
            val json = org.json.JSONObject(user?.socialLinks ?: "{}")
            tg = if (json.has("tg")) json.getString("tg") else ""
            vk = if (json.has("vk")) json.getString("vk") else ""
            github = if (json.has("github")) json.getString("github") else ""
            website = if (json.has("website")) json.getString("website") else ""
            youtube = if (json.has("youtube")) json.getString("youtube") else ""
        } catch (e: Exception) {}
    }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text(
                "Редактирование профиля",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Основная информация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            // Аватар
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    UserAvatar(name = name.ifBlank { "?" }, avatarUrl = avatarUrl, size = 96.dp)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { imagePicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isUploadingAvatar) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Сменить фото",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            if (avatarError != null) {
                Text(avatarError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            if (!avatarUrl.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteAvatar()
                            avatarUrl = null
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Удалить фото", color = MaterialTheme.colorScheme.error)
                }
            }
            
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 50) name = it },
                label = { Text("Имя") },
                supportingText = { Text("${name.length}/50") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )

            OutlinedTextField(
                value = bio,
                onValueChange = { if (it.length <= 450) bio = it },
                label = { Text("О себе") },
                supportingText = { Text("${bio.length}/450") },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                minLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Цвет баннера", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                colors.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(color)))
                            .clickable { bannerColor = color }
                            .then(
                                if (bannerColor == color) {
                                    Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                } else Modifier
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text("Ссылки и соцсети", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Укажите ваш никнейм или ID", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

            SocialEditField("Telegram", tg, su.SkrinVex.ofox.R.drawable.ic_telegram, "Ваш @username") { if (it.length <= 50) tg = it }
            SocialEditField("VK", vk, su.SkrinVex.ofox.R.drawable.ic_vk, "ID или короткое имя") { if (it.length <= 50) vk = it }
            SocialEditField("GitHub", github, su.SkrinVex.ofox.R.drawable.ic_github, "Ваш username") { if (it.length <= 50) github = it }
            SocialEditFieldIcon("Личный сайт", website, Icons.Filled.Language, "Полная ссылка") { if (it.length <= 100) website = it }
            SocialEditField("YouTube", youtube, su.SkrinVex.ofox.R.drawable.ic_youtube, "Канал или @handle") { if (it.length <= 50) youtube = it }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    scope.launch {
                        val socialLinks = org.json.JSONObject().apply {
                            put("tg", tg)
                            put("vk", vk)
                            put("github", github)
                            put("website", website)
                            put("youtube", youtube)
                        }.toString()
                        
                        repository.updateUser(name, bio, socialLinks, bannerColor)
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Сохранить всё")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OldThemeScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("ofox_prefs", android.content.Context.MODE_PRIVATE)
    var selectedTheme by remember { mutableStateOf(prefs.getString("theme", "Orange") ?: "Orange") }

    val themes = listOf(
        "Orange" to androidx.compose.ui.graphics.Color(0xFFFF6B35),
        "Blue" to androidx.compose.ui.graphics.Color(0xFF2196F3),
        "Purple" to androidx.compose.ui.graphics.Color(0xFF9C27B0),
        "Green" to androidx.compose.ui.graphics.Color(0xFF4CAF50),
        "Red" to androidx.compose.ui.graphics.Color(0xFFF44336)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "Цветовая палитра",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            themes.forEach { (name, color) ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedTheme = name
                            prefs.edit().putString("theme", name).apply()
                            (context as? android.app.Activity)?.recreate()
                        },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedTheme == name)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(color)
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Text(
                            text = name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )

                        if (selectedTheme == name) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Выбрано",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(repository: Repository, onBack: () -> Unit) {
    var showAppInfo by remember { mutableStateOf(false) }
    var appInfoContent by remember { mutableStateOf("") }
    var appInfoTitle by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "О приложении",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    Text(
                        text = "OFOX",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Версия ${su.SkrinVex.ofox.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isLoading = "appInfo"
                            appInfoTitle = "Важная информация"
                            appInfoContent = repository.getAppInfo()
                            isLoading = null
                            showAppInfo = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Важная информация",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "О разработке и функциях",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (isLoading == "appInfo") {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isLoading = "rules"
                            appInfoTitle = "Правила Ofox"
                            appInfoContent = repository.getOfoxRules()
                            isLoading = null
                            showAppInfo = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Правила Ofox",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Правила использования платформы",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (isLoading == "rules") {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        scope.launch {
                            isLoading = "privacy"
                            appInfoTitle = "Политика конфиденциальности"
                            appInfoContent = repository.getPrivacyPolicy()
                            isLoading = null
                            showAppInfo = true
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Политика конфиденциальности",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Как мы обрабатываем ваши данные",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    if (isLoading == "privacy") {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

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
                    Text(
                        text = "Описание",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Социальная сеть для творческих людей. Делитесь идеями, находите единомышленников и создавайте вместе!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

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
                    Text(
                        text = "Разработчик",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "SkrinVex Team",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }

    if (showAppInfo) {
        ModalBottomSheet(
            onDismissRequest = { showAppInfo = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                MarkdownText(appInfoContent)
            }
        }
    }
}

@Composable
fun MarkdownText(markdown: String) {
    val lines = markdown.split("\n")
    
    lines.forEach { line ->
        val trimmed = line.trim()
        
        when {
            // Заголовки - проверяем от большего к меньшему
            trimmed.startsWith("### ") -> {
                Text(
                    text = trimmed.removePrefix("### "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )
            }
            trimmed.startsWith("## ") -> {
                Text(
                    text = trimmed.removePrefix("## "),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
            }
            trimmed.startsWith("# ") -> {
                Text(
                    text = trimmed.removePrefix("# "),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 12.dp)
                )
            }
            // Горизонтальная линия
            trimmed.matches(Regex("^-{3,}$")) || trimmed.matches(Regex("^\\*{3,}$")) -> {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                    thickness = 1.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            // Списки
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "• ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FormattedText(
                        text = trimmed.removePrefix("- ").removePrefix("* "),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Нумерованные списки
            trimmed.matches(Regex("^\\d+\\.\\s+.*")) -> {
                val number = trimmed.substringBefore(".").trim()
                val text = trimmed.substringAfter(". ").trim()
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Text(
                        text = "$number. ",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    FormattedText(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Обычный текст
            trimmed.isNotBlank() -> {
                FormattedText(
                    text = trimmed,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            // Пустая строка
            else -> {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun FormattedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
        val boldRegex = Regex("""\*\*(.+?)\*\*""")
        val allMatches = boldRegex.findAll(text).toList()
        
        if (allMatches.isEmpty()) {
            append(text)
        } else {
            var lastIndex = 0
            allMatches.forEach { match ->
                if (match.range.first > lastIndex) {
                    append(text.substring(lastIndex, match.range.first))
                }
                
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                append(match.groupValues[1])
                pop()
                
                lastIndex = match.range.last + 1
            }
            
            if (lastIndex < text.length) {
                append(text.substring(lastIndex))
            }
        }
    }
    
    Text(
        text = annotatedString,
        style = style,
        color = color,
        modifier = modifier
    )
}

@Composable
fun SocialEditField(label: String, value: String, iconRes: Int, placeholder: String = "", onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { 
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        singleLine = true
    )
}

@Composable
fun SocialEditFieldIcon(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, placeholder: String = "", onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { 
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        },
        singleLine = true
    )
}
