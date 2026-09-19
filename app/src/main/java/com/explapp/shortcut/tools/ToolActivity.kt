package com.explapp.shortcut.tools

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max

class ToolActivity : AppCompatActivity() {
    private lateinit var tool: ToolId
    private var mergeVertical = false
    private var pendingScreenshotOcr = false
    private var waterTrack: AudioTrack? = null
    private var pendingShareLatestScreenshot = false

    private val imagePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult finish()
        when (tool) {
            ToolId.IMAGE_OCR -> ocr(uri)
            ToolId.IMAGE_TO_JPEG -> convertToJpeg(uri)
            ToolId.IMAGE_INFO -> showImageInfo(uri)
            ToolId.IMAGE_RESIZE_COMPRESS -> compressResize(uri)
            ToolId.IMAGE_CROP -> editImage(uri)
            else -> finish()
        }
    }

    private val multiImagePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult finish()
        when (tool) {
            ToolId.MERGE_IMAGES -> mergeImages(uris, mergeVertical)
            ToolId.IMAGES_TO_PDF -> imagesToPdf(uris)
            ToolId.GIF_CREATE -> imagesToGif(uris)
            else -> finish()
        }
    }

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult finish()
        extractPdfText(uri)
    }

    private val filesPicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult finish()
        zipFiles(uris)
    }

    private val zipPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri ?: return@registerForActivityResult finish()
        unzip(uri)
    }

    private val screenCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult finish()
        ScreenCaptureHelper.captureOnce(this, result.resultCode, data) { bitmap ->
            when {
                bitmap == null -> {
                    toast(local("Screenshot cancelled", "تم إلغاء لقطة الشاشة"))
                    finish()
                }
                pendingScreenshotOcr -> ocrBitmap(bitmap)
                else -> {
                    runCatching {
                        saveBitmap(bitmap, "Screenshot_${System.currentTimeMillis()}.png", "image/png", Bitmap.CompressFormat.PNG, 100)
                    }.onSuccess { toast(local("Screenshot saved", "تم حفظ لقطة الشاشة")) }
                        .onFailure(::showError)
                    bitmap.recycle()
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tool = runCatching { ToolId.valueOf(intent.getStringExtra(EXTRA_TOOL).orEmpty()) }.getOrElse {
            finish(); return
        }
        route()
    }

    override fun onDestroy() {
        runCatching { waterTrack?.stop() }
        waterTrack?.release()
        waterTrack = null
        super.onDestroy()
    }

    private fun route() {
        when (tool) {
            ToolId.MERGE_IMAGES -> AlertDialog.Builder(this)
                .setTitle(local("Merge direction", "اتجاه الدمج"))
                .setItems(arrayOf(local("Horizontal", "أفقي"), local("Vertical", "عمودي"))) { _, which ->
                    mergeVertical = which == 1
                    multiImagePicker.launch("image/*")
                }
                .setOnCancelListener { finish() }
                .show()

            ToolId.IMAGES_TO_PDF, ToolId.GIF_CREATE -> multiImagePicker.launch("image/*")
            ToolId.IMAGE_OCR, ToolId.IMAGE_TO_JPEG, ToolId.IMAGE_INFO,
            ToolId.IMAGE_RESIZE_COMPRESS, ToolId.IMAGE_CROP -> imagePicker.launch("image/*")
            ToolId.PDF_TEXT -> pdfPicker.launch("application/pdf")
            ToolId.ZIP_FILES -> filesPicker.launch(arrayOf("*/*"))
            ToolId.UNZIP_FILES -> zipPicker.launch("application/zip")
            ToolId.QR_CREATE -> promptQr()
            ToolId.CLIPBOARD -> clipboardTools()
            ToolId.SCREENSHOT_CAPTURE -> requestScreenshot(false)
            ToolId.SCREENSHOT_OCR_SEARCH -> requestScreenshot(true)
            ToolId.CAR_MODE -> carMode()
            ToolId.PARKED_CAR -> parkedCar()
            ToolId.CALENDAR_REMINDERS -> calendarReminder()
            ToolId.WATER_EJECT -> waterEject()
            ToolId.LATEST_PHOTO -> shareLatest(false)
            ToolId.LATEST_SCREENSHOT -> shareLatest(true)
            ToolId.URL_TO_PDF -> promptUrl(false)
            ToolId.MULTI_URL_TO_PDF -> promptUrl(true)
            ToolId.BATTERY_CHARGER -> batteryTool()
            ToolId.NFC_TRIGGER -> systemTool(
                local("NFC", "NFC"),
                local("Open NFC settings. Shortcut can use NFC as a trigger without Accessibility access.", "افتح إعدادات NFC. يمكن استخدام NFC كمحفز للاختصارات دون صلاحية إمكانية الوصول."),
                Settings.ACTION_NFC_SETTINGS,
            )
            ToolId.APP_OPEN_ROUTINE -> systemTool(
                local("When an app opens", "عند فتح تطبيق"),
                local("Android requires Usage Access for reliable app-open detection in the Standard build.", "يتطلب Android صلاحية الوصول إلى الاستخدام لاكتشاف فتح التطبيقات بشكل موثوق في النسخة العادية."),
                Settings.ACTION_USAGE_ACCESS_SETTINGS,
            )
            ToolId.MY_AUTOMATIONS -> finish()
            ToolId.MORNING_SLEEP -> systemTool(
                local("Morning / sleep", "روتين الصباح / النوم"),
                local("Use Android alarms together with Shortcut scheduling for morning and sleep routines.", "استخدم منبهات Android مع جدولة الاختصارات لروتين الصباح والنوم."),
                AlarmClock.ACTION_SHOW_ALARMS,
            )
        }
    }

    private fun mergeImages(uris: List<Uri>, vertical: Boolean) {
        runCatching {
            val bitmaps = uris.map { loadScaledBitmap(it, 1600) }
            val width = if (vertical) bitmaps.maxOf { it.width } else bitmaps.sumOf { it.width }
            val height = if (vertical) bitmaps.sumOf { it.height } else bitmaps.maxOf { it.height }
            require(width > 0 && height > 0 && width.toLong() * height <= 40_000_000L) { "Result is too large" }
            val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            var x = 0f
            var y = 0f
            bitmaps.forEach { bitmap ->
                canvas.drawBitmap(bitmap, x, y, null)
                if (vertical) y += bitmap.height else x += bitmap.width
            }
            saveBitmap(result, "Merged_${System.currentTimeMillis()}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92)
            bitmaps.forEach(Bitmap::recycle)
            result.recycle()
        }.onSuccess { toast(local("Merged image saved", "تم حفظ الصورة المدمجة")) }
            .onFailure(::showError)
        finish()
    }

    private fun imagesToPdf(uris: List<Uri>) {
        runCatching {
            val pdf = PdfDocument()
            uris.forEachIndexed { index, uri ->
                val bitmap = loadScaledBitmap(uri, 1800)
                val page = pdf.startPage(PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create())
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdf.finishPage(page)
                bitmap.recycle()
            }
            val bytes = ByteArrayOutputStream().use { out ->
                pdf.writeTo(out)
                pdf.close()
                out.toByteArray()
            }
            saveBytes(bytes, "Images_${System.currentTimeMillis()}.pdf", "application/pdf")
        }.onSuccess { toast(local("PDF saved", "تم حفظ PDF")) }
            .onFailure(::showError)
        finish()
    }

    private fun ocr(uri: Uri) {
        runCatching { InputImage.fromFilePath(this, uri) }
            .onSuccess { image ->
                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    .process(image)
                    .addOnSuccessListener { showTextResult(it.text) }
                    .addOnFailureListener(::showError)
            }.onFailure(::showError)
    }

    private fun ocrBitmap(bitmap: Bitmap) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener {
                bitmap.recycle()
                showTextResult(it.text, allowSearch = true)
            }
            .addOnFailureListener {
                bitmap.recycle()
                showError(it)
            }
    }

    private fun extractPdfText(uri: Uri) {
        runCatching {
            PDFBoxResourceLoader.init(applicationContext)
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                PDDocument.load(input).use { document -> PDFTextStripper().getText(document) }
            }
        }.onSuccess(::showTextResult)
            .onFailure(::showError)
    }

    private fun convertToJpeg(uri: Uri) {
        runCatching {
            val bitmap = loadScaledBitmap(uri, 2400)
            saveBitmap(bitmap, "Converted_${System.currentTimeMillis()}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 92)
            bitmap.recycle()
        }.onSuccess { toast(local("JPEG saved without copied metadata", "تم حفظ JPEG دون نسخ بيانات metadata")) }
            .onFailure(::showError)
        finish()
    }

    private fun showImageInfo(uri: Uri) {
        runCatching {
            val nameSize = queryNameAndSize(uri)
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            val exifText = contentResolver.openInputStream(uri).use { input ->
                if (input == null) "" else runCatching {
                    val exif = ExifInterface(input)
                    listOfNotNull(
                        exif.getAttribute(ExifInterface.TAG_MAKE)?.let { "Make: $it" },
                        exif.getAttribute(ExifInterface.TAG_MODEL)?.let { "Model: $it" },
                        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { "Date: $it" },
                        exif.getAttribute(ExifInterface.TAG_ORIENTATION)?.let { "Orientation: $it" },
                    ).joinToString("\n")
                }.getOrDefault("")
            }
            "${nameSize.first}\n${opts.outWidth} × ${opts.outHeight}\n${nameSize.second} bytes" +
                if (exifText.isBlank()) "" else "\n$exifText"
        }.onSuccess(::showTextResult)
            .onFailure(::showError)
    }

    private fun compressResize(uri: Uri) {
        runCatching {
            val bitmap = loadScaledBitmap(uri, 1600)
            saveBitmap(bitmap, "Compressed_${System.currentTimeMillis()}.jpg", "image/jpeg", Bitmap.CompressFormat.JPEG, 82)
            bitmap.recycle()
        }.onSuccess { toast(local("Compressed image saved", "تم حفظ الصورة المضغوطة")) }
            .onFailure(::showError)
        finish()
    }

    private fun editImage(uri: Uri) {
        val intent = Intent(Intent.ACTION_EDIT)
            .setDataAndType(uri, "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        runCatching { startActivity(Intent.createChooser(intent, local("Crop / edit image", "قص / تعديل الصورة"))) }
            .onFailure(::showError)
        finish()
    }

    private fun zipFiles(uris: List<Uri>) {
        runCatching {
            val bytes = ByteArrayOutputStream()
            ZipOutputStream(bytes).use { zip ->
                uris.forEachIndexed { index, uri ->
                    val name = queryNameAndSize(uri).first.ifBlank { "file_$index" }
                    zip.putNextEntry(ZipEntry(name))
                    contentResolver.openInputStream(uri).use { input -> requireNotNull(input).copyTo(zip) }
                    zip.closeEntry()
                }
            }
            saveBytes(bytes.toByteArray(), "Shortcut_${System.currentTimeMillis()}.zip", "application/zip")
        }.onSuccess { toast(local("ZIP saved", "تم حفظ ZIP")) }
            .onFailure(::showError)
        finish()
    }

    private fun unzip(uri: Uri) {
        runCatching {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Unzipped_${System.currentTimeMillis()}")
                .apply { mkdirs() }
            contentResolver.openInputStream(uri).use { input ->
                ZipInputStream(requireNotNull(input)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val output = File(dir, entry.name).canonicalFile
                        require(output.path.startsWith(dir.canonicalPath)) { "Unsafe ZIP entry" }
                        if (entry.isDirectory) {
                            output.mkdirs()
                        } else {
                            output.parentFile?.mkdirs()
                            FileOutputStream(output).use { zip.copyTo(it) }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            dir.absolutePath
        }.onSuccess { showTextResult(local("Unzipped to:\n$it", "تم فك الملفات إلى:\n$it")) }
            .onFailure(::showError)
    }

    private fun promptQr() {
        val input = EditText(this).apply { hint = local("Text or URL", "نص أو رابط") }
        AlertDialog.Builder(this)
            .setTitle(local("Create QR code", "إنشاء QR Code"))
            .setView(input)
            .setPositiveButton(local("Create", "إنشاء")) { _, _ -> createQr(input.text.toString()) }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun createQr(text: String) {
        runCatching {
            require(text.isNotBlank())
            val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 900, 900)
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
            for (x in 0 until matrix.width) {
                for (y in 0 until matrix.height) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            saveBitmap(bitmap, "QR_${System.currentTimeMillis()}.png", "image/png", Bitmap.CompressFormat.PNG, 100)
            bitmap.recycle()
        }.onSuccess { toast(local("QR code saved", "تم حفظ QR Code")) }
            .onFailure(::showError)
        finish()
    }

    private fun clipboardTools() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        val prefs = getSharedPreferences("clipboard_tools", MODE_PRIVATE)
        val saved = prefs.getString("saved", "").orEmpty()
        val message = buildString {
            append(local("Current:\n", "الحالي:\n"))
            append(current.ifBlank { "—" })
            if (saved.isNotBlank()) {
                append("\n\n")
                append(local("Saved:\n", "المحفوظ:\n"))
                append(saved)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(local("Clipboard tools", "أدوات الحافظة"))
            .setMessage(message)
            .setPositiveButton(local("Save current", "حفظ الحالي")) { _, _ ->
                prefs.edit().putString("saved", current).apply()
                finish()
            }
            .setNeutralButton(local("Copy saved", "نسخ المحفوظ")) { _, _ ->
                if (saved.isNotBlank()) clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut", saved))
                finish()
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun requestScreenshot(ocr: Boolean) {
        pendingScreenshotOcr = ocr
        screenCapture.launch(getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
    }

    private fun carMode() {
        AlertDialog.Builder(this)
            .setTitle(local("Car mode", "وضع السيارة"))
            .setMessage(local(
                "Use Android's Wi‑Fi and Bluetooth controls, then open Maps.",
                "استخدم أدوات Android لتشغيل Wi‑Fi وBluetooth ثم افتح الخرائط.",
            ))
            .setPositiveButton("Wi‑Fi") { _, _ ->
                val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    Settings.Panel.ACTION_INTERNET_CONNECTIVITY
                } else {
                    Settings.ACTION_WIFI_SETTINGS
                }
                runCatching { startActivity(Intent(action)) }
            }
            .setNeutralButton("Bluetooth") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            }
            .setNegativeButton(local("Open Maps", "فتح الخرائط")) { _, _ -> openMaps(null) }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun parkedCar() {
        val prefs = getSharedPreferences("parked_car", MODE_PRIVATE)
        val lat = prefs.getString("lat", null)?.toDoubleOrNull()
        val lon = prefs.getString("lon", null)?.toDoubleOrNull()
        AlertDialog.Builder(this)
            .setTitle(local("Parked car", "السيارة المركونة"))
            .setPositiveButton(local("Save current location", "حفظ الموقع الحالي")) { _, _ -> saveCurrentLocation() }
            .apply {
                if (lat != null && lon != null) {
                    setNeutralButton(local("Directions", "الاتجاهات")) { _, _ -> openMaps(lat to lon) }
                }
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun saveCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION)
            return
        }
        val manager = getSystemService(android.location.LocationManager::class.java)
        val location = manager.getProviders(true)
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (location == null) {
            toast(local("No recent location available. Turn on location and try again.", "لا يوجد موقع حديث. شغّل الموقع وحاول مرة أخرى."))
        } else {
            getSharedPreferences("parked_car", MODE_PRIVATE).edit()
                .putString("lat", location.latitude.toString())
                .putString("lon", location.longitude.toString())
                .apply()
            toast(local("Parked location saved", "تم حفظ موقع السيارة"))
        }
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_LOCATION -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) saveCurrentLocation() else finish()
            REQUEST_MEDIA -> if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) shareLatest(pendingShareLatestScreenshot) else finish()
        }
    }

    private fun calendarReminder() {
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, local("Shortcut reminder", "تذكير الاختصارات"))
        runCatching { startActivity(intent) }.onFailure(::showError)
        finish()
    }

    private fun waterEject() {
        AlertDialog.Builder(this)
            .setTitle("Water Eject")
            .setMessage(local(
                "Play a low-frequency tone for 10 seconds. Keep volume at a comfortable level.",
                "تشغيل نغمة منخفضة التردد لمدة 10 ثوانٍ. أبقِ مستوى الصوت مريحًا.",
            ))
            .setPositiveButton(local("Start", "تشغيل")) { _, _ -> playWaterTone() }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun playWaterTone() {
        val sampleRate = 44_100
        val count = sampleRate * 10
        val frequency = 165.0
        val samples = ShortArray(count) { i ->
            (kotlin.math.sin(2.0 * Math.PI * i * frequency / sampleRate) * Short.MAX_VALUE * 0.35).toInt().toShort()
        }
        waterTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { track ->
                track.write(samples, 0, samples.size)
                track.play()
            }
        toast(local("Water Eject started", "بدأ تشغيل طرد الماء"))
        android.os.Handler(mainLooper).postDelayed({ finish() }, 10_500)
    }

    private fun imagesToGif(uris: List<Uri>) {
        runCatching {
            val frames = uris.take(20).map { loadScaledBitmap(it, 720) }
            val width = frames.maxOf { it.width }
            val height = frames.maxOf { it.height }
            val out = ByteArrayOutputStream()
            val encoder = GifEncoder(out, width, height, 0)
            val options = ImageOptions().apply { setDelay(350, TimeUnit.MILLISECONDS) }
            frames.forEach { frame ->
                val normalized = if (frame.width == width && frame.height == height) {
                    frame
                } else {
                    Bitmap.createScaledBitmap(frame, width, height, true)
                }
                encoder.addImage(bitmapPixels(normalized), options)
                if (normalized !== frame) normalized.recycle()
            }
            encoder.finishEncoding()
            frames.forEach(Bitmap::recycle)
            saveBytes(out.toByteArray(), "Animated_${System.currentTimeMillis()}.gif", "image/gif")
        }.onSuccess { toast(local("GIF saved", "تم حفظ GIF")) }
            .onFailure(::showError)
        finish()
    }

    private fun bitmapPixels(bitmap: Bitmap): Array<IntArray> {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return Array(bitmap.width) { x -> IntArray(bitmap.height) { y -> pixels[y * bitmap.width + x] } }
    }

    private fun shareLatest(screenshotOnly: Boolean) {
        pendingShareLatestScreenshot = screenshotOnly
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(permission), REQUEST_MEDIA)
            return
        }
        runCatching {
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME)
            val selection = if (screenshotOnly) "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" else null
            val args = if (screenshotOnly) arrayOf("%Screenshot%") else null
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { cursor ->
                require(cursor.moveToFirst()) { "No image found" }
                Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0).toString())
            } ?: error("No image found")
        }.onSuccess { uri ->
            val share = Intent(Intent.ACTION_SEND)
                .setType("image/*")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivity(Intent.createChooser(share, null))
            finish()
        }.onFailure(::showError)
    }

    private fun promptUrl(multiple: Boolean) {
        val input = EditText(this).apply {
            hint = if (multiple) local("One URL per line", "رابط في كل سطر") else "https://"
        }
        AlertDialog.Builder(this)
            .setTitle(local("URL to PDF", "الرابط إلى PDF"))
            .setView(input)
            .setMessage(local(
                "Shortcut opens the page in your browser. Choose Print → Save as PDF.",
                "يفتح الاختصار الصفحة في المتصفح. اختر طباعة ← حفظ كـ PDF.",
            ))
            .setPositiveButton(local("Open", "فتح")) { _, _ ->
                input.text.toString().lineSequence().map(String::trim).firstOrNull { it.isNotBlank() }?.let { url ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }
                finish()
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun batteryTool() {
        val level = getSystemService(android.os.BatteryManager::class.java)
            .getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        showTextResult(local(
            "Current battery: $level%\nUse the battery automation setup to choose a threshold or charger action.",
            "البطارية الحالية: $level%\nاستخدم إعداد أتمتة البطارية لاختيار النسبة أو إجراء الشاحن.",
        ))
    }

    private fun systemTool(title: String, message: String, action: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(local("Open settings", "فتح الإعدادات")) { _, _ ->
                runCatching { startActivity(Intent(action)) }
                finish()
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun openMaps(point: Pair<Double, Double>?) {
        val uri = if (point == null) {
            Uri.parse("geo:0,0?q=")
        } else {
            Uri.parse("geo:${point.first},${point.second}?q=${point.first},${point.second}")
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure(::showError)
        finish()
    }

    private fun showTextResult(text: String, allowSearch: Boolean = false) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val builder = AlertDialog.Builder(this)
            .setTitle(local("Result", "النتيجة"))
            .setMessage(text.ifBlank { local("No text found", "لم يتم العثور على نص") })
            .setPositiveButton(local("Copy", "نسخ")) { _, _ ->
                clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut", text))
                finish()
            }
            .setNegativeButton(local("Close", "إغلاق")) { _, _ -> finish() }
        if (allowSearch && text.isNotBlank()) {
            builder.setNeutralButton(local("Search", "بحث")) { _, _ ->
                val url = "https://www.google.com/search?q=${Uri.encode(text.take(500))}"
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                finish()
            }
        }
        builder.setOnCancelListener { finish() }.show()
    }

    private fun queryNameAndSize(uri: Uri): Pair<String, Long> {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0).orEmpty() to c.getLong(1)
        }
        return "file" to -1L
    }

    private fun loadScaledBitmap(uri: Uri, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension * 2) sample *= 2
        val decoded = contentResolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("Unable to decode image")
        val scale = maxDimension.toFloat() / max(decoded.width, decoded.height)
        return if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            decoded.recycle()
            scaled
        } else decoded
    }

    private fun saveBitmap(
        bitmap: Bitmap,
        name: String,
        mime: String,
        format: Bitmap.CompressFormat,
        quality: Int,
    ): Uri {
        val uri = createOutputUri(name, mime, images = true)
        contentResolver.openOutputStream(uri).use { out ->
            requireNotNull(out)
            check(bitmap.compress(format, quality, out))
        }
        return uri
    }

    private fun saveBytes(bytes: ByteArray, name: String, mime: String): Uri {
        val uri = createOutputUri(name, mime, images = mime.startsWith("image/"))
        contentResolver.openOutputStream(uri).use { out -> requireNotNull(out).write(bytes) }
        return uri
    }

    private fun createOutputUri(name: String, mime: String, images: Boolean): Uri {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (images) "Pictures/Shortcut" else "Download/Shortcut")
            }
            val collection = if (images) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            return requireNotNull(contentResolver.insert(collection, values))
        }

        val dir = File(
            getExternalFilesDir(if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS),
            "Shortcut",
        ).apply { mkdirs() }
        val file = File(dir, name)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
        }
        val collection = if (images) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
        return requireNotNull(contentResolver.insert(collection, values))
    }

    private fun showError(t: Throwable) {
        AlertDialog.Builder(this)
            .setTitle(local("Could not complete action", "تعذر تنفيذ العملية"))
            .setMessage(t.message ?: t.javaClass.simpleName)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object {
        const val EXTRA_TOOL = "tool"
        private const val REQUEST_LOCATION = 3001
        private const val REQUEST_MEDIA = 3002
    }
}
