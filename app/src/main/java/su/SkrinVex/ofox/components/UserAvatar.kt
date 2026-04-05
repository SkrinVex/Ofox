package su.SkrinVex.ofox.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest

@Composable
fun UserAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Используем полный URL (с ?v=) как ключ — при новом аватаре URL меняется и Coil перезагружает
    val url = avatarUrl?.takeIf { it.isNotBlank() }
    var loadFailed by remember(url) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        // Буква всегда под картинкой — нет мигания
        Text(
            text = name.firstOrNull()?.toString()?.uppercase() ?: "?",
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.4f).sp
        )

        if (url != null && !loadFailed) {
            android.util.Log.d("AVATAR", "AsyncImage loading url=$url")
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    // Ключ кэша = URL без query для диска, полный URL для памяти
                    .memoryCacheKey(url)
                    .diskCacheKey(url.substringBefore("?"))
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .crossfade(false) // без анимации — нет мигания
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    android.util.Log.d("AVATAR", "Coil state=$state")
                    if (state is AsyncImagePainter.State.Error) {
                        android.util.Log.e("AVATAR", "Coil error: ${state.result.throwable}")
                        loadFailed = true
                    }
                }
            )
        }
    }
}
