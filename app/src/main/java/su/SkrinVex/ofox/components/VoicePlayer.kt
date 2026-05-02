package su.SkrinVex.ofox.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.data.Repository

@Composable
fun VoiceMessagePlayer(
    voiceKey: String,
    durationMs: Long,
    isFromMe: Boolean,
    repository: Repository
) {
    var playUrl by remember(voiceKey) { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val trackColor = contentColor.copy(alpha = 0.3f)

    // Обновляем прогресс во время воспроизведения
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                val p = player
                if (p != null && p.isPlaying) {
                    progress = p.currentPosition.toFloat() / p.duration.toFloat()
                }
                delay(200)
            }
        }
    }

    DisposableEffect(voiceKey) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(min = 160.dp, max = 240.dp)
    ) {
        // Кнопка play/pause
        Surface(
            shape = CircleShape,
            color = contentColor.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                player?.pause()
                                isPlaying = false
                            } else {
                                scope.launch {
                                    if (playUrl == null) {
                                        isLoading = true
                                        playUrl = repository.getVoicePlayUrl(voiceKey)
                                        isLoading = false
                                    }
                                    val url = playUrl ?: return@launch
                                    if (player == null) {
                                        val mp = MediaPlayer()
                                        mp.setDataSource(url)
                                        mp.setOnCompletionListener {
                                            isPlaying = false
                                            progress = 0f
                                        }
                                        mp.prepare()
                                        player = mp
                                    }
                                    player?.start()
                                    isPlaying = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Прогресс-бар
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(MaterialTheme.shapes.small),
                color = contentColor,
                trackColor = trackColor
            )
            // Длительность
            val displayMs = if (isPlaying) (progress * durationMs).toLong() else durationMs
            Text(
                formatDuration(displayMs),
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}
