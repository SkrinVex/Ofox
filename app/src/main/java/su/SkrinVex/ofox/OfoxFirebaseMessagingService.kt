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
        val chatId = data["chatId"]?.toIntOrNull() ?: return
        val title = data["senderName"] ?: message.notification?.title ?: "Новое сообщение"
        val body = data["message"] ?: message.notification?.body ?: ""
        val avatarUrl = data["senderAvatarUrl"]

        // Не показываем если этот чат сейчас открыт
        if (ActiveChatTracker.activeChatId == chatId) {
            android.util.Log.d("FCM", "Skipping notification: chat $chatId is active")
            return
        }

        android.util.Log.d("FCM", "Showing notification for chat=$chatId, title=$title")

        CoroutineScope(Dispatchers.IO).launch {
            val avatarBitmap = avatarUrl?.let { loadCircleBitmap(it) }
            withContext(Dispatchers.Main) {
                showNotification(chatId, title, body, avatarBitmap)
            }
        }
    }

    private fun showNotification(chatId: Int, title: String, body: String, avatar: Bitmap?) {
        ensureNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_id", chatId)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, chatId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (avatar != null) {
            builder.setLargeIcon(avatar)
        }

        NotificationManagerCompat.from(this).notify(chatId, builder.build())
    }

    private fun loadCircleBitmap(url: String): Bitmap? {
        return try {
            val raw = android.graphics.BitmapFactory.decodeStream(URL(url).openStream()) ?: return null
            val size = minOf(raw.width, raw.height)
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            val src = Bitmap.createScaledBitmap(raw, size, size, true)
            canvas.drawBitmap(src, Rect(0, 0, size, size), Rect(0, 0, size, size), paint)
            output
        } catch (e: Exception) {
            android.util.Log.w("FCM", "Failed to load avatar: ${e.message}")
            null
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Чаты", NotificationManager.IMPORTANCE_HIGH)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "chats"
    }
}
