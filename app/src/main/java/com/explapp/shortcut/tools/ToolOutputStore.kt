package com.explapp.shortcut.tools

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

class ToolOutputStore(private val context: Context) {
    fun create(name: String, mime: String, images: Boolean): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
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
        return Uri.fromFile(File(directory, name))
    }
}
