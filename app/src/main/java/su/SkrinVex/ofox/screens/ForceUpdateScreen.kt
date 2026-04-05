package su.SkrinVex.ofox.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val APK_URL = "https://files.skrinvex.su/projects/ofox/app-release.apk"
private const val APK_FILENAME = "ofox-update.apk"

private val funnyHints = listOf(
    "Не сворачивайте Ofox, мы стараемся...",
    "Скачиваем что-то хорошее для вас 🦊",
    "Новая версия уже почти у вас в руках",
    "Готовим свежие фичи к запуску...",
    "Осталось совсем чуть-чуть, честно!",
    "Лиса бежит со всех лап 🏃",
    "Упаковываем обновление с любовью",
    "Почти готово, не уходите далеко",
    "Загружаем улучшения и исправления",
    "Скоро всё будет ещё лучше ✨"
)

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    data class Done(val file: File) : DownloadState()
    data class Error(val message: String, val code: Int? = null) : DownloadState()
    data class ReadyToInstall(val file: File) : DownloadState()
}

@Composable
fun ForceUpdateScreen(message: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var hintIndex by remember { mutableStateOf(0) }

    // Меняем подсказки каждые 3 секунды во время загрузки
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Downloading) {
            while (true) {
                delay(3000)
                hintIndex = (hintIndex + 1) % funnyHints.size
            }
        }
    }

    // Проверяем при возврате в приложение — есть ли уже скачанный файл
    LaunchedEffect(Unit) {
        val existing = getApkFile(context)
        if (existing.exists()) {
            downloadState = DownloadState.ReadyToInstall(existing)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp)
            )

            Text(
                "Требуется обновление",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            when (val state = downloadState) {
                is DownloadState.Idle -> {
                    Button(
                        onClick = {
                            scope.launch {
                                downloadApk(context, onProgress = { progress ->
                                    downloadState = DownloadState.Downloading(progress)
                                }, onDone = { file ->
                                    downloadState = DownloadState.Done(file)
                                }, onError = { msg, code ->
                                    downloadState = DownloadState.Error(msg, code)
                                })
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Скачать обновление")
                    }
                }

                is DownloadState.Downloading -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "Загрузка...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )

                        Text(
                            funnyHints[hintIndex],
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is DownloadState.Done -> {
                    LaunchedEffect(state) {
                        installApk(context, state.file)
                        // После возврата из установщика — показываем кнопку
                        delay(1000)
                        downloadState = DownloadState.ReadyToInstall(state.file)
                    }
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        "Открываем установщик...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is DownloadState.ReadyToInstall -> {
                    Text(
                        "Файл загружен. Нажмите чтобы установить.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { installApk(context, state.file) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Установить")
                    }
                }

                is DownloadState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "Ошибка загрузки",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                state.message + if (state.code != null) " (код: ${state.code})" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                    Button(
                        onClick = { downloadState = DownloadState.Idle },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Попробовать снова")
                    }
                }
            }
        }
    }
}

private fun getApkFile(context: Context): File =
    File(context.getExternalFilesDir("Downloads"), APK_FILENAME)

private suspend fun downloadApk(
    context: Context,
    onProgress: (Float) -> Unit,
    onDone: (File) -> Unit,
    onError: (String, Int?) -> Unit
) = withContext(Dispatchers.IO) {
    try {
        val url = URL(APK_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            withContext(Dispatchers.Main) { onError("Сервер недоступен", responseCode) }
            return@withContext
        }

        val totalBytes = connection.contentLengthLong
        val file = getApkFile(context)
        file.parentFile?.mkdirs()

        connection.inputStream.use { input ->
            file.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var downloaded = 0L
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloaded += bytes
                    if (totalBytes > 0) {
                        val progress = downloaded.toFloat() / totalBytes
                        withContext(Dispatchers.Main) { onProgress(progress) }
                    }
                }
            }
        }

        withContext(Dispatchers.Main) { onDone(file) }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) { onError(e.message ?: "Неизвестная ошибка", null) }
    }
}

private fun installApk(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}
