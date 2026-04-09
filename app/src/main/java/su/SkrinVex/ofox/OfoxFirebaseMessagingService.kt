package su.SkrinVex.ofox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import su.SkrinVex.ofox.data.api.ApiClient
import su.SkrinVex.ofox.utils.ActiveChatTracker
import java.net.URL

class OfoxFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiClient = ApiClient.getInstance(applicationContext)
                if (apiClient.getToken() != null) {
                    apiClient.api.updateFcmToken(mapOf("token" to token))
                }
            } catch (e: Exception) {
                android.util.Log.e("FCM", "Failed to update token", e)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return

        when (type) {
            "new_message" -> {
                val chatId = data["chatId"]?.toIntOrNull() ?: return
                if (ActiveChatTracker.activeChatId == chatId) return
                val title = data["senderName"] ?: "Новое сообщение"
                val body = data["message"] ?: ""
                val avatarUrl = data["senderAvatarUrl"]
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = avatarUrl?.let { loadCircleBitmap(it) }
                    withContext(Dispatchers.Main) {
                        showNotification(chatId, title, body, bitmap, CHANNEL_CHATS, chatId = chatId)
                    }
                }
            }
            "system_notification" -> {
                val title = data["title"] ?: "OFOX"
                val body = data["message"] ?: ""
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.Main) {
                        showNotification(System.currentTimeMillis().toInt(), title, body, null, CHANNEL_COMMENTS)
                    }
                }
            }
            "comment_reply" -> {
                val postId = data["postId"]?.toIntOrNull() ?: return
                val actorName = data["actorName"] ?: "Кто-то"
                val body = data["message"] ?: ""
                val avatarUrl = data["senderAvatarUrl"]
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = avatarUrl?.let { loadCircleBitmap(it) }
                    withContext(Dispatchers.Main) {
                        showNotification(
                            postId + 100000,
                            "$actorName ответил на ваш комментарий",
                            body, bitmap, CHANNEL_COMMENTS, postId = postId, notifType = "comment_reply"
                        )
                    }
                }
            }
            "post_comment" -> {
                val postId = data["postId"]?.toIntOrNull() ?: return
                val actorName = data["actorName"] ?: "Кто-то"
                val body = data["message"] ?: ""
                val avatarUrl = data["senderAvatarUrl"]
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = avatarUrl?.let { loadCircleBitmap(it) }
                    withContext(Dispatchers.Main) {
                        showNotification(
                            postId + 200000,
                            "$actorName прокомментировал ваш пост",
                            body, bitmap, CHANNEL_COMMENTS, postId = postId, notifType = "post_comment"
                        )
                    }
                }
            }
            "new_post" -> {
                val postId = data["postId"]?.toIntOrNull() ?: return
                val actorName = data["actorName"] ?: "Кто-то"
                val avatarUrl = data["senderAvatarUrl"]
                CoroutineScope(Dispatchers.IO).launch {
                    val bitmap = avatarUrl?.let { loadCircleBitmap(it) }
                    withContext(Dispatchers.Main) {
                        showNotification(
                            postId + 300000,
                            "$actorName опубликовал новый пост",
                            "", bitmap, CHANNEL_COMMENTS, postId = postId, notifType = "new_post"
                        )
                    }
                }
            }
        }
    }

    private fun showNotification(
        notifId: Int, title: String, body: String, avatar: Bitmap?,
        channelId: String, chatId: Int? = null, postId: Int? = null, notifType: String? = null
    ) {
        ensureChannels()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chat_id", it) }
            postId?.let { putExtra("post_id", it) }
            notifType?.let { putExtra("notif_type", it) }
        }
        val pi = PendingIntent.getActivity(this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val n = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .apply { avatar?.let { setLargeIcon(it) } }
            .build()

        NotificationManagerCompat.from(this).notify(notifId, n)
    }

    private fun loadCircleBitmap(url: String): Bitmap? = try {
        val raw = android.graphics.BitmapFactory.decodeStream(URL(url).openStream()) ?: return null
        val size = minOf(raw.width, raw.height)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(Bitmap.createScaledBitmap(raw, size, size, true), Rect(0,0,size,size), Rect(0,0,size,size), paint)
        out
    } catch (e: Exception) { null }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_CHATS, "Чаты", NotificationManager.IMPORTANCE_HIGH))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_COMMENTS, "Комментарии", NotificationManager.IMPORTANCE_HIGH))
        }
    }

    companion object {
        const val CHANNEL_CHATS = "chats"
        const val CHANNEL_COMMENTS = "comments"
    }
}
