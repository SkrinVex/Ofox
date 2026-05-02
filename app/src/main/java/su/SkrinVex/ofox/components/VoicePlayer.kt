package su.SkrinVex.ofox.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import su.SkrinVex.ofox.OfoxApp
import su.SkrinVex.ofox.VoicePlayerService
import su.SkrinVex.ofox.data.Repository

@Composable
fun VoiceMessagePlayer(
    voiceKey: String,
    durationMs: Long,
    isFromMe: Boolean,
    repository: Repository,
    senderName: String = "",
    senderAvatarUrl: String = ""
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val globalState by VoicePlayerService.state.collectAsState()
    val isThisVoice = globalState.voiceKey == voiceKey
    val isPlaying = isThisVoice && globalState.isPlaying
    val isLoading = isThisVoice && globalState.isLoading
    val progress = if (isThisVoice) globalState.progress else 0f
    val positionMs = if (isThisVoice) globalState.positionMs else 0L
    val displayDuration = if (isThisVoice && globalState.durationMs > 0) globalState.durationMs else durationMs

    var playUrl by remember(voiceKey) { mutableStateOf<String?>(null) }

    val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    val trackColor = contentColor.copy(alpha = 0.3f)

    fun onPlayPause() {
        val svc = OfoxApp.voiceService
        if (isThisVoice && svc != null) {
            if (isPlaying) svc.pause() else svc.resume()
        } else {
            scope.launch(Dispatchers.IO) {
                val url = playUrl ?: repository.getVoicePlayUrl(voiceKey)?.also { playUrl = it } ?: return@launch
                OfoxApp.voiceService?.load(url, voiceKey, durationMs, senderName, senderAvatarUrl)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(min = 180.dp, max = 260.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = contentColor.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = contentColor)
                } else {
                    IconButton(onClick = ::onPlayPause, modifier = Modifier.fillMaxSize()) {
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

        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = progress,
                onValueChange = { v ->
                    if (isThisVoice) OfoxApp.voiceService?.seekTo((v * displayDuration).toLong())
                },
                modifier = Modifier.fillMaxWidth().height(20.dp),
                colors = SliderDefaults.colors(
                    thumbColor = contentColor,
                    activeTrackColor = contentColor,
                    inactiveTrackColor = trackColor
                )
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(if (isThisVoice) positionMs else 0L),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Text(
                    formatDuration(displayDuration),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}
