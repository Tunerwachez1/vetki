package dev.vetki.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.LruCache
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

data class StoredAttachment(val uri: String, val name: String, val mime: String)

class AttachmentStore(private val context: Context) {
    private val directory = File(context.filesDir, "attachments").apply { mkdirs() }
    private val cache =
        object : LruCache<String, Bitmap>(12 * 1024) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
        }

    fun file(uri: String): File {
        require(uri.startsWith("attachment://")) { "Неизвестный формат вложения" }
        val name = uri.removePrefix("attachment://")
        require(name.matches(Regex("[a-f0-9-]{36}(\\.[a-zA-Z0-9]{1,10})?"))) {
            "Некорректное имя вложения"
        }
        return File(directory, name)
    }

    fun copyFrom(source: Uri): StoredAttachment {
        val mime = context.contentResolver.getType(source) ?: "application/octet-stream"
        val display =
            context.contentResolver
                .query(source, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Файл"
        val extension =
            MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.takeIf {
                it.matches(Regex("[a-zA-Z0-9]{1,10}"))
            }
        val uri = "attachment://${UUID.randomUUID()}${extension?.let { ".$it" }.orEmpty()}"
        val target = file(uri)
        try {
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var size = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        require(size <= MAX_FILE) { "Выбери файл до 8 МБ" }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Не удалось открыть файл")
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        return StoredAttachment(uri, display, mime)
    }

    fun externalUri(uri: String): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file(uri))

    fun thumbnail(uri: String): Bitmap? {
        cache.get(uri)?.let {
            return it
        }
        return runCatching {
                val path = file(uri).absolutePath
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val scale =
                    generateSequence(1) { it * 2 }
                        .first { bounds.outWidth / it <= 600 && bounds.outHeight / it <= 600 }
                BitmapFactory.decodeFile(
                        path,
                        BitmapFactory.Options().apply { inSampleSize = scale },
                    )
                    ?.also { cache.put(uri, it) }
            }
            .getOrNull()
    }

    companion object {
        const val MAX_FILE = 8 * 1024 * 1024
    }
}
