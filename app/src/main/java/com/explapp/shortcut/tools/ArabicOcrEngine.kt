package com.explapp.shortcut.tools

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Arabic OCR model is downloaded once; recognition then runs fully offline. */
internal object ArabicOcrEngine {
    private const val MODEL = "ara.traineddata"
    private const val MODEL_URL = "https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/ara.traineddata"
    private const val MIN_MODEL_BYTES = 200_000L
    private const val MAX_MODEL_BYTES = 10_000_000L

    private fun dataDir(context: Context): File = File(context.filesDir, "tesseract")
    private fun modelFile(context: Context): File = File(dataDir(context), "tessdata/$MODEL")

    fun available(context: Context): Boolean =
        modelFile(context).let { it.isFile && it.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES }

    /** Call from a background worker only. */
    fun download(context: Context) {
        if (available(context)) return
        val destination = modelFile(context)
        check(destination.parentFile?.mkdirs() == true || destination.parentFile?.isDirectory == true)
        val temp = File(destination.parentFile, "$MODEL.part")
        try {
            val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = false
            try {
                check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                    "OCR model download HTTP ${connection.responseCode}"
                }
                connection.inputStream.use { source ->
                    temp.outputStream().use { output -> source.copyTo(output) }
                }
            } finally {
                connection.disconnect()
            }
            check(temp.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES) {
                "Arabic OCR model download is incomplete"
            }
            check(temp.renameTo(destination)) { "Could not save Arabic OCR model" }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    /** Use a separate native instance for each request; caller owns bitmap lifetime. */
    fun recognize(context: Context, bitmap: Bitmap): String {
        check(available(context)) { "Arabic OCR model is not installed" }
        val api = TessBaseAPI()
        try {
            check(api.init(dataDir(context).absolutePath, "ara")) { "Could not initialize Arabic OCR" }
            api.setImage(bitmap)
            return api.getUTF8Text().orEmpty()
        } finally {
            api.recycle()
        }
    }
}
