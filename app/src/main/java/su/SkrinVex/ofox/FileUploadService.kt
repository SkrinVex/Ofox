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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class FileUploadService : Service() {

    companion object {
        const val CHANNEL_ID = "file_upload"
        const val NOTIF_ID = 55
        const val ACTION_CANCEL_CURRENT = "su.SkrinVex.ofox.CANCEL_CURRENT"
        const val ACTION_CANCEL_ALL = "su.SkrinVex.ofox.CANCEL_ALL"
        // Оставляем старый ACTION_CANCEL как алиас для совместимости
        const val ACTION_CANCEL = ACTION_CANCEL_CURRENT

        const val EXTRA_CHAT_ID = "chatId"
        const val EXTRA_URI = "uri"
        const val EXTRA_REPLY_ID = "replyId"
        const val EXTRA_LOCAL_ID = "localId"

        sealed class UploadState {
            object Idle : UploadState()
            data class Uploading(
                val localId: Long, val chatId: Int, val fileName: String,
                val progress: Float, val current: Int, val total: Int
            ) : UploadState()
            data class Done(val localId: Long, val message: su.SkrinVex.ofox.data.Message) : UploadState()
            data class Error(val localId: Long, val reason: String) : UploadState()
            data class Cancelled(val localId: Long) : UploadState()
        }

        data class QueueItem(val chatId: Int, val uri: Uri, val replyId: Int?, val localId: Long)

        private val _state = MutableStateFlow<UploadState>(UploadState.Idle)
        val state: StateFlow<UploadState> = _state
        fun resetState() { _state.value = UploadState.Idle }

        // Очередь — видна снаружи для отображения размера
        val queue = LinkedBlockingQueue<QueueItem>()

        fun enqueue(ctx: Context, chatId: Int, uri: Uri, replyId: Int?, localId: Long) {
            queue.add(QueueItem(chatId, uri, replyId, localId))
            val intent = Intent(ctx, FileUploadService::class.java)
            ctx.startForegroundService(intent)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var workerJob: Job? = null
    private var currentJob: Job? = null
    private var currentLocalId = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL_CURRENT -> {
                currentJob?.cancel()
                _state.value = UploadState.Cancelled(currentLocalId)
                // Воркер подхватит следующий элемент сам
                return START_NOT_STICKY
            }
            ACTION_CANCEL_ALL -> {
                queue.clear()
                currentJob?.cancel()
                _state.value = UploadState.Cancelled(currentLocalId)
                workerJob?.cancel()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // Запускаем воркер если ещё не запущен
        if (workerJob == null || workerJob?.isActive == false) {
            startForeground(NOTIF_ID, buildProgressNotification("Подготовка…", 0, 0, true))
            workerJob = scope.launch { processQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun processQueue() {
        val repository = Repository(applicationContext)
        while (true) {
            val item = queue.poll() ?: break
            uploadItem(repository, item, queue.size)
        }
        stopSelf()
    }

    private suspend fun uploadItem(repository: Repository, item: QueueItem, remaining: Int) {
        val totalInBatch = remaining + 1 // текущий + оставшиеся
        currentLocalId = item.localId
        val uri = item.uri
        val chatId = item.chatId

        currentJob = scope.launch {
            try {
                val mimeType = contentResolver.getType(uri) ?: "application/octet-stream"
                val cursor = contentResolver.query(uri, arrayOf(
                    android.provider.OpenableColumns.DISPLAY_NAME,
                    android.provider.OpenableColumns.SIZE
                ), null, null, null)
                var fileName = "file"
                var fileSize = 0L
                cursor?.use { if (it.moveToFirst()) { fileName = it.getString(0) ?: "file"; fileSize = it.getLong(1) } }

                val doneCount = totalInBatch - remaining - 1
                _state.value = UploadState.Uploading(item.localId, chatId, fileName, 0f, doneCount + 1, totalInBatch)
                updateNotification(fileName, 0, doneCount + 1, totalInBatch, false)

                val info = repository.getChatFileUploadUrl(chatId, fileName, mimeType)
                if (info == null) {
                    _state.value = UploadState.Error(item.localId, "Не удалось получить ссылку")
                    notifyError(fileName, "Не удалось получить ссылку")
                    return@launch
                }

                val stream = contentResolver.openInputStream(uri)
                if (stream == null) {
                    _state.value = UploadState.Error(item.localId, "Не удалось открыть файл")
                    notifyError(fileName, "Не удалось открыть файл")
                    return@launch
                }
                val bytes = stream.readBytes(); stream.close()
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
                                sink.write(buf, 0, n); uploaded += n
                                val progress = (uploaded / total).coerceIn(0f, 0.99f)
                                _state.value = UploadState.Uploading(item.localId, chatId, fileName, progress, doneCount + 1, totalInBatch)
                                updateNotification(fileName, (progress * 100).toInt(), doneCount + 1, totalInBatch, false)
                            }
                        }
                    }
                }

                val response = client.newCall(Request.Builder().url(info.uploadUrl).put(requestBody).build()).execute()
                if (!response.isSuccessful) {
                    val msg = "Ошибка (${response.code})"
                    _state.value = UploadState.Error(item.localId, msg)
                    notifyError(fileName, msg)
                    return@launch
                }

                val sent = repository.sendFileMessage(chatId, info.key, fileName, fileSize, mimeType, item.replyId)
                if (sent != null) {
                    _state.value = UploadState.Done(item.localId, sent)
                    if (queue.isEmpty()) notifySuccess(fileName)
                } else {
                    val msg = "Файл загружен, но не удалось отправить"
                    _state.value = UploadState.Error(item.localId, msg)
                    notifyError(fileName, msg)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _state.value = UploadState.Cancelled(item.localId)
            } catch (e: Exception) {
                android.util.Log.e("FileUploadService", "Upload error", e)
                _state.value = UploadState.Error(item.localId, e.message ?: "Ошибка")
                notifyError("Файл", e.message ?: "Ошибка")
            }
        }
        // Ждём завершения текущего файла перед следующим
        currentJob?.join()
    }

    override fun onDestroy() {
        workerJob?.cancel(); currentJob?.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Загрузка файлов", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun cancelCurrentIntent(): PendingIntent {
        val i = Intent(this, FileUploadService::class.java).apply { action = ACTION_CANCEL_CURRENT }
        return PendingIntent.getService(this, 1, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun cancelAllIntent(): PendingIntent {
        val i = Intent(this, FileUploadService::class.java).apply { action = ACTION_CANCEL_ALL }
        return PendingIntent.getService(this, 2, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun buildProgressNotification(text: String, current: Int, total: Int, indeterminate: Boolean): android.app.Notification {
        val openIntent = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val title = if (total > 1) "Отправка файлов ($current/$total)" else "Отправка файла"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setProgress(100, if (indeterminate) 0 else (text.substringBefore("%").toIntOrNull() ?: 0), indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отменить", cancelCurrentIntent())
            .apply { if (total > 1) addAction(android.R.drawable.ic_delete, "Отменить все", cancelAllIntent()) }
            .build()
    }

    private fun updateNotification(fileName: String, pct: Int, current: Int, total: Int, indeterminate: Boolean) {
        val text = if (indeterminate) fileName else "$fileName  $pct%"
        val title = if (total > 1) "Отправка файлов ($current/$total)" else "Отправка файла"
        val openIntent = PendingIntent.getActivity(this, 0,
            packageManager.getLaunchIntentForPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setProgress(100, pct, indeterminate)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Отменить", cancelCurrentIntent())
            .apply { if (total > 1) addAction(android.R.drawable.ic_delete, "Отменить все", cancelAllIntent()) }
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notif)
    }

    private fun notifySuccess(fileName: String) {
        getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIF_ID)
            notify(NOTIF_ID + 1, NotificationCompat.Builder(this@FileUploadService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Файл отправлен")
                .setContentText(fileName)
                .setAutoCancel(true).build())
        }
    }

    private fun notifyError(fileName: String, reason: String) {
        getSystemService(NotificationManager::class.java).apply {
            cancel(NOTIF_ID)
            notify(NOTIF_ID + 1, NotificationCompat.Builder(this@FileUploadService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Ошибка отправки: $fileName")
                .setContentText(reason)
                .setAutoCancel(true).build())
        }
    }
}
