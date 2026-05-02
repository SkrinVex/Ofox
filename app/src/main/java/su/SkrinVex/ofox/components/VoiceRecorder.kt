package su.SkrinVex.ofox.components

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import su.SkrinVex.ofox.data.Repository
import java.io.File

private const val MIME = "audio/mp4"
private const val EXT  = "m4a"

private enum class RecordState { IDLE, RECORDING, LOCKED, UPLOADING }

@Composable
fun VoiceMessageButton(
    repository: Repository,
    chatId: Int,
    onVoiceSent: (key: String, durationMs: Long) -> Unit,
    onRecordingStateChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf(RecordState.IDLE) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<File?>(null) }
    var recordStart by remember { mutableStateOf(0L) }
    var elapsed by remember { mutableStateOf(0L) }
    var lockProgress by remember { mutableStateOf(0f) }
    var showWarningDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doStartRecording(context) { rec, file ->
            recorder = rec; recordFile = file
            recordStart = System.currentTimeMillis()
            state = RecordState.RECORDING
        }
    }

    LaunchedEffect(state) {
        onRecordingStateChange(state == RecordState.RECORDING || state == RecordState.LOCKED)
        if (state == RecordState.RECORDING || state == RecordState.LOCKED) {
            while (state == RecordState.RECORDING || state == RecordState.LOCKED) {
                elapsed = (System.currentTimeMillis() - recordStart) / 1000
                delay(500)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse)
    )

    fun stopAndSend() {
        val duration = System.currentTimeMillis() - recordStart
        val file = recordFile
        val rec = recorder
        try { rec?.stop(); rec?.release() } catch (_: Exception) {}
        recorder = null; recordFile = null; lockProgress = 0f
        state = RecordState.UPLOADING
        if (file != null && duration > 300) {
            scope.launch {
                val key = uploadVoice(repository, chatId, file) { uploadProgress = it }
                file.delete()
                state = RecordState.IDLE
                if (key != null) onVoiceSent(key, duration)
            }
        } else {
            file?.delete(); state = RecordState.IDLE
        }
    }

    fun cancelRecording() {
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        recorder = null; recordFile?.delete(); recordFile = null
        lockProgress = 0f; state = RecordState.IDLE
    }

    fun tryStartRecording() {
        val hasPerm = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!hasPerm) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!repository.isVoiceWarningShown()) {
            showWarningDialog = true
        } else {
            doStartRecording(context) { rec, file ->
                recorder = rec; recordFile = file
                recordStart = System.currentTimeMillis()
                state = RecordState.RECORDING
                lockProgress = 0f
            }
        }
    }

    // Диалог-предупреждение об автоудалении голосовых (нельзя закрыть без подтверждения)
    if (showWarningDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Mic, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Голосовые сообщения",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Голосовые сообщения автоматически удаляются через 7 дней после отправки в целях экономии места на серверах Ofox.\n\nЕсли сообщение важно — зажмите на него и выберите «Скачать», чтобы сохранить на устройство.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            repository.setVoiceWarningShown()
                            showWarningDialog = false
                            doStartRecording(context) { rec, file ->
                                recorder = rec; recordFile = file
                                recordStart = System.currentTimeMillis()
                                state = RecordState.RECORDING
                                lockProgress = 0f
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Понятно, записать")
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showWarningDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Отмена")
                    }
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        // Таймер слева от кнопки
        if (state == RecordState.RECORDING || state == RecordState.LOCKED) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
                Text(
                    formatDuration(elapsed * 1000),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state == RecordState.RECORDING) {
                    Text(
                        "↑ для фиксации",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Замок над кнопкой при свайпе вверх
            if (state == RecordState.RECORDING && lockProgress > 0.05f) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (lockProgress >= 1f) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp).size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Lock, null,
                            tint = if (lockProgress >= 1f) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = lockProgress),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            when (state) {
                RecordState.UPLOADING -> {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { uploadProgress },
                            modifier = Modifier.size(34.dp),
                            strokeWidth = 3.dp
                        )
                    }
                }
                RecordState.LOCKED -> {
                    // Зафиксирована — кнопка отправки
                    Box(
                        modifier = Modifier.size(48.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = { stopAndSend() }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(22.dp))
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (state == RecordState.RECORDING) pulse else 1f)
                            .clip(CircleShape)
                            .background(
                                if (state == RecordState.RECORDING) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                            )
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val down = event.changes.firstOrNull() ?: continue
                                        if (!down.pressed) continue
                                        down.consume()

                                        val hasPerm = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                                                android.content.pm.PackageManager.PERMISSION_GRANTED
                                        if (!hasPerm) {
                                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            continue
                                        }

                                        if (!repository.isVoiceWarningShown()) {
                                            showWarningDialog = true
                                            continue
                                        }

                                        doStartRecording(context) { rec, file ->
                                            recorder = rec; recordFile = file
                                            recordStart = System.currentTimeMillis()
                                            state = RecordState.RECORDING
                                            lockProgress = 0f
                                        }

                                        val startY = down.position.y
                                        while (true) {
                                            val moveEvent = awaitPointerEvent(PointerEventPass.Main)
                                            val ptr = moveEvent.changes.firstOrNull() ?: break
                                            if (!ptr.pressed) {
                                                ptr.consume()
                                                if (state == RecordState.RECORDING) stopAndSend()
                                                break
                                            }
                                            ptr.consume()
                                            val dy = startY - ptr.position.y
                                            if (dy > 0f && state == RecordState.RECORDING) {
                                                lockProgress = (dy / 120f).coerceIn(0f, 1f)
                                                if (lockProgress.compareTo(1f) >= 0) {
                                                    state = RecordState.LOCKED
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Mic, "Голосовое", tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

private fun doStartRecording(context: Context, onStarted: (MediaRecorder, File) -> Unit) {
    val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.$EXT")
    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context)
              else @Suppress("DEPRECATION") MediaRecorder()
    try {
        rec.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(64000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        onStarted(rec, file)
    } catch (e: Exception) {
        android.util.Log.e("VoiceRecorder", "Start failed", e)
        try { rec.release() } catch (_: Exception) {}
        file.delete()
    }
}

private suspend fun uploadVoice(
    repository: Repository,
    chatId: Int,
    file: File,
    onProgress: (Float) -> Unit
): String? = withContext(Dispatchers.IO) {
    try {
        val urlResponse = repository.getVoiceUploadUrl(chatId) ?: return@withContext null
        val client = OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val requestBody = object : RequestBody() {
            override fun contentType() = MIME.toMediaType()
            override fun contentLength() = file.length()
            override fun writeTo(sink: okio.BufferedSink) {
                val total = file.length().toFloat()
                var uploaded = 0L
                file.inputStream().use { input ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        sink.write(buf, 0, n)
                        uploaded += n
                        onProgress((uploaded / total).coerceIn(0f, 0.99f))
                    }
                }
                onProgress(1f)
            }
        }
        val response = OkHttpClient().newCall(
            Request.Builder().url(urlResponse.uploadUrl).put(requestBody).build()
        ).execute()
        android.util.Log.d("Voice", "Upload: ${response.code}")
        if (response.isSuccessful) urlResponse.key else null
    } catch (e: Exception) {
        android.util.Log.e("Voice", "Upload failed", e)
        null
    }
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
