package com.explapp.shortcut.tools

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

object ToolOutputNamePolicy {
    fun sanitize(raw: String): String {
        val cleaned = raw
            .replace(Regex("""[\\/\u0000-\u001F\u007F]"""), "_")
            .replace("..", "_")
            .trim()
            .trim('.')
            .take(MAX_NAME_LENGTH)
        return cleaned.ifBlank { "Shortcut_output" }
    }

    private const val MAX_NAME_LENGTH = 120
}

class ToolOutputStore(private val context: Context) {
    fun create(name: String, mime: String, images: Boolean): Uri {
        val safeName = ToolOutputNamePolicy.sanitize(name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (images) "Pictures/Shortcut" else "Download/Shortcut")
            }
            val collection = if (images) {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            return requireNotNull(context.contentResolver.insert(collection, values))
        }

        val type = if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        val directory = File(context.getExternalFilesDir(type), "Shortcut").apply { mkdirs() }
        val file = File(directory, safeName)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
