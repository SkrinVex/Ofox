package su.SkrinVex.ofox

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import su.SkrinVex.ofox.data.Repository
import java.util.concurrent.TimeUnit

class FileUploadService : Service() {

    companion object {
        const val CHANNEL_ID = "file_upload"
        const val NOTIF_ID = 55
        const val ACTION_CANCEL = "su.SkrinVex.ofox.CANCEL_UPLOAD"

        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_URI = "uri"
        const val EXTRA_REPLY_ID = "replyId"
        const val EXTRA_LOCAL_ID = "localId"

        // Состояние загрузки — UI подписывается на это
        sealed class UploadState {
            object Idle : UploadState()
            data class Uploading(val localId: Long, val chatId: Int, val fileName: String, val progress: Float) : UploadState()
            data class Done(val localId: Long, val message: su.SkrinVex.ofox.data.Message) : UploadState()
            data class Error(val localId: Long, val reason: String) : UploadState()
            data class Cancelled(val localId: Long) : UploadState()
        }

        private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
        val state: StateFlow<UploadState> = _state
        fun resetState() { _state.value = UploadState.Idle }

        fun start(ctx: Context, chatId: Int, uri: Uri, replyId: Int?, localId: Long) {
            val intent = Intent(ctx, FileUploadService::class.java).apply {
                putExtra(EXTRA_CHAT_ID, chatId)
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_REPLY_ID, replyId ?: -1)
                putExtra(EXTRA_LOCAL_ID, localId)
            }
            ctx.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null
    private var currentLocalId = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            _state.value = UploadState.Cancelled(currentLocalId)
            stopSelf()
            return START_NOT_STICKY
        }

        val chatId = intent?.getIntExtra(EXTRA_CHAT_ID, -1) ?: -1
        val uriStr = intent?.getStringExtra(EXTRA_URI) ?: return START_NOT_STICKY
        val replyId = intent.getIntExtra(EXTRA_REPLY_ID, -1).takeIf { it != -1 }
        val localId = intent.getLongExtra(EXTRA_LOCAL_ID, System.currentTimeMillis())
        currentLocalId = localId
        val uri = Uri.parse(uriStr)

        startForeground(NOTIF_ID, buildProgressNotification("Подготовка…", 0, indeterminate = true))

        job = scope.launch {
            val repository = Repository(applicationContext)
            try {
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val cursor = contentResolver.query(uri, arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
                ), null, null, null)
                var fileName = "file"
                var fileSize = 0L
                cursor?.use {
                    if (it.moveToFirst()) {
                        fileName = it.getString(0) ?: "file"
                        fileSize = it.getLong(1)
                    }
                }

                _state.value = UploadState.Uploading(localId, chatId, fileName, 0f)
                updateNotification("Отправка: $fileName", 0, false)

                val info = repository.getChatFileUploadUrl(chatId, fileName, mimeType)
                if (info == null) {
                    _state.value = UploadState.Error(localId, "Не удалось получить ссылку для загрузки")
                    notifyError(fileName, "Не удалось получить ссылку")
                    stopSelf(); return@launch
                }

                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    _state.value = UploadState.Error(localId, "Не удалось открыть файл")
                    notifyError(fileName, "Не удалось открыть файл")
                    stopSelf(); return@launch
                }
                val bytes = stream.readBytes()
                stream.close()
                if (fileSize == 0L) fileSize = bytes.size.toLong()

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(300, TimeUnit.SECONDS)
                    .build()

                val total = bytes.size.toFloat()
                var uploaded = 0L
                val requestBody = object : RequestBody() {
                    override fun contentType() = mimeType.toMediaTypeOrNull()
                    override fun contentLength() = bytes.size.toLong()
                    override fun writeTo(sink: BufferedSink) {
                        val buf = ByteArray(65536)
                        bytes.inputStream().use { input ->
                            var n: Int
                            while (input.read(buf).also { n = it } != -1) {
                                sink.write(buf, 0, n)
                                uploaded += n
                                val progress = (uploaded / total).coerceIn(0f, 0.99f)
                                _state.value = UploadState.Uploading(localId, chatId, fileName, progress)
                                val pct = (progress * 100).toInt()
                                updateNotification("$fileName  $pct%", pct, false)
                            }
                        }
                    }
                }

                val response = client.newCall(
                    Request.Builder().url(info.uploadUrl).put(requestBody).build()
                ).execute()

                if (!response.isSuccessful) {
                    val msg = "Ошибка загрузки (${response.code})"
                    _state.value = UploadState.Error(localId, msg)
                    notifyError(fileName, msg)
                    stopSelf(); return@launch
                }

                val sent = repository.sendFileMessage(chatId, info.key, fileName, fileSize, mimeType, replyId)
                if (sent != null) {
                    _state.value = UploadState.Done(localId, sent)
                    notifySuccess(fileName)
                } else {
                    val msg = "Файл загружен, но не удалось отправить сообщение"
                    _state.value = UploadState.Error(localId, msg)
                    notifyError(fileName, msg)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = UploadState.Cancelled(localId)
            } catch (e: Exception) {
                android.util.Log.e("FileUploadService", "Upload error", e)
                val msg = e.message ?: "Неизвестная ошибка"
                _state.value = UploadState.Error(localId, msg)
                notifyError("Файл", msg)
            }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Загрузка файлов", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun cancelIntent(): PendingIntent {
        val i = Intent(this, FileUploadService::class.java).apply { action = ACTION_CANCEL }
        return PendingIntent.getService(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildProgressNotification(text: String, progress: Int, indeterminate: Boolean): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Отправка файла")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отменить", cancelIntent())
            .build()
    }

    private fun updateNotification(text: String, progress: Int, indeterminate: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildProgressNotification(text, progress, indeterminate))
    }

    private fun notifySuccess(fileName: String) {
        getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIF_ID)
            notify(NOTIF_ID + 1, NotificationCompat.Builder(this@FileUploadService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Файл отправлен")
                .setContentText(fileName)
                .setAutoCancel(true)
                .build())
        }
    }

    private fun notifyError(fileName: String, reason: String) {
        getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIF_ID)
            notify(NOTIF_ID + 1, NotificationCompat.Builder(this@FileUploadService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ошибка отправки: $fileName")
                .setContentText(reason)
                .setAutoCancel(true)
                .build())
        }
    }
}
