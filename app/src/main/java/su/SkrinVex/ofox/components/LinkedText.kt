package su.SkrinVex.ofox.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

private val URL_REGEX = Regex("""https?://[^\s<>"]+|www\.[^\s<>"]+""")
private val HASHTAG_REGEX = Regex("""#\w+""")

@Composable
fun LinkedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onHashtagClick: (String) -> Unit = {},
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    var pendingUrl by remember { mutableStateOf<String?>(null) }

    val annotated = remember(text, style.color, linkColor) {
        buildAnnotatedString {
            var last = 0
            val matches = (URL_REGEX.findAll(text) + HASHTAG_REGEX.findAll(text))
                .sortedBy { it.range.first }.toList()
            for (match in matches) {
                if (match.range.first < last) continue
                if (match.range.first > last)
                    withStyle(SpanStyle(color = style.color)) { append(text.substring(last, match.range.first)) }
                val isUrl = match.value.startsWith("http") || match.value.startsWith("www.")
                pushStringAnnotation(if (isUrl) "URL" else "HASHTAG", match.value)
                withStyle(SpanStyle(
                    color = linkColor,
                    fontWeight = if (isUrl) FontWeight.Normal else FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                )) { append(match.value) }
                pop()
                last = match.range.last + 1
            }
            if (last < text.length)
                withStyle(SpanStyle(color = style.color)) { append(text.substring(last)) }
        }
    }

    ClickableText(
        text = annotated, style = style, maxLines = maxLines,
        modifier = modifier, onTextLayout = onTextLayout,
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let {
                pendingUrl = it.item; return@ClickableText
            }
            annotated.getStringAnnotations("HASHTAG", offset, offset).firstOrNull()?.let {
                onHashtagClick(it.item)
            }
        }
    )

    pendingUrl?.let { url ->
        LinkConfirmDialog(url = url, onDismiss = { pendingUrl = null })
    }
}

@Composable
fun LinkConfirmDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text("Переход по ссылке", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text(
                    url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        val uri = if (url.startsWith("http")) Uri.parse(url) else Uri.parse("https://$url")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Перейти")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("link", url))
                        Toast.makeText(context, "Ссылка скопирована", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Копировать ссылку")
                }
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Отмена", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}
