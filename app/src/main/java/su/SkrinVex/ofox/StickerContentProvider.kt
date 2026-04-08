package su.SkrinVex.ofox

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Content Provider для поддержки отправки стикеров через gboard и другие клавиатуры.
 * Клавиатуры запрашивают стикер по URI вида:
 * content://su.SkrinVex.ofox.stickers/<encoded_sticker_url>
 */
class StickerContentProvider : ContentProvider() {

    override fun onCreate() = true

    override fun getType(uri: Uri): String = "image/webp"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val context = context ?: return null
        // uri.lastPathSegment содержит URL стикера (закодированный)
        val stickerUrl = Uri.decode(uri.lastPathSegment) ?: return null

        return try {
            // Кешируем стикер локально
            val cacheDir = File(context.cacheDir, "sticker_share")
            cacheDir.mkdirs()
            val file = File(cacheDir, "${stickerUrl.hashCode()}.webp")

            if (!file.exists()) {
                val bytes = URL(stickerUrl).readBytes()
                FileOutputStream(file).use { it.write(bytes) }
            }

            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Exception) {
            android.util.Log.e("StickerProvider", "openFile error", e)
            null
        }
    }

    // Не используется, но обязательно для ContentProvider
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?) = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
