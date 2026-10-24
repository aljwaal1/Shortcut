package com.explapp.shortcut.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.translate.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.util.Locale

/** Native counterpart of Aidict's offline translation and PDF-reader's reading experience. */
class TranslationReaderActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private lateinit var sourceInput: EditText
    private lateinit var translatedText: TextView
    private lateinit var status: TextView
    private lateinit var pageLabel: TextView
    private lateinit var languageButton: Button
    private var sourceEnglish = true
    private var pdfUri: Uri? = null
    private var page = 0
    private var pageCount = 0
    private var translator: Translator? = null
    private var tts: TextToSpeech? = null
    private var speechReady = false
    private var modelsReady = false
    private var generation = 0

    private val pdfPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            pdfUri = uri
            val key = uri.toString()
            page = preferences().getInt("page_$key", 0)
            readPdfPage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        tts = TextToSpeech(this, this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(24))
        }
        val scroll = ScrollView(this).apply { fillViewport = true; addView(root) }
        setContentView(scroll)

        root.addView(TextView(this).apply {
            text = label("Offline translation and PDF reading", "الترجمة المحلية وقراءة PDF")
            textSize = 23f
            setPadding(0, 0, 0, dp(10))
        })
        status = TextView(this).apply { textSize = 15f; setPadding(0, 0, 0, dp(12)) }
        root.addView(status)
        showStatus(label("Download Arabic/English models once before offline use.", "نزّل نموذجي العربية والإنجليزية مرة واحدة قبل العمل دون إنترنت."))

        languageButton = button(root, "English → Arabic", "إنجليزي ← عربي") {
            sourceEnglish = !sourceEnglish
            modelsReady = false
            translator?.close()
            translator = null
            languageButton.text = label(
                if (sourceEnglish) "English → Arabic" else "Arabic → English",
                if (sourceEnglish) "إنجليزي ← عربي" else "عربي ← إنجليزي",
            )
            showStatus(label("Direction changed. Confirm models are installed.", "تم تغيير الاتجاه. تأكد من تنزيل النماذج."))
        }
        button(root, "Download / check offline languages", "تنزيل / فحص لغات الترجمة") { prepareModels() }
        button(root, "Choose PDF from phone", "اختيار ملف PDF من الهاتف") { pdfPicker.launch(arrayOf("application/pdf")) }
        pageLabel = TextView(this).apply { gravity = Gravity.CENTER; textSize = 16f }
        root.addView(pageLabel)
        button(root, "Previous page", "الصفحة السابقة") { changePage(-1) }
        button(root, "Next page", "الصفحة التالية") { changePage(1) }

        sourceInput = EditText(this).apply {
            hint = label("Enter text or select a PDF page", "اكتب النص أو اختر صفحة PDF")
            minLines = 5
            maxLines = 14
            gravity = Gravity.TOP
            setTextIsSelectable(true)
        }
        root.addView(sourceInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        button(root, "Translate text / current page", "ترجمة النص / الصفحة الحالية") { translate() }
        translatedText = TextView(this).apply {
            textSize = 17f
            setTextIsSelectable(true)
            setPadding(dp(8), dp(14), dp(8), dp(14))
            text = label("Translation will appear here", "ستظهر الترجمة هنا")
        }
        root.addView(translatedText)
        button(root, "Read original aloud", "قراءة النص الأصلي صوتيًا") { speak(sourceInput.text.toString(), original = true) }
        button(root, "Read translation aloud", "قراءة الترجمة صوتيًا") { speak(translatedText.text.toString(), original = false) }
        button(root, "Stop reading", "إيقاف القراءة") { tts?.stop() }
        button(root, "Copy translation", "نسخ الترجمة") {
            val clip = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("translation", translatedText.text))
            showStatus(label("Copied", "تم النسخ"))
        }
        button(root, "Share translation", "مشاركة الترجمة") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, translatedText.text.toString())
            }, label("Share", "مشاركة")))
        }
        pageLabel.text = ""
        intent.getStringExtra(EXTRA_INITIAL_TEXT)?.takeIf { it.isNotBlank() }?.let(sourceInput::setText)
    }

    private fun prepareModels() {
        showStatus(label("Checking / downloading languages…", "جارٍ فحص / تنزيل اللغات…"))
        modelsReady = false
        translator?.close()
        translator = Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(if (sourceEnglish) TranslateLanguage.ENGLISH else TranslateLanguage.ARABIC)
            .setTargetLanguage(if (sourceEnglish) TranslateLanguage.ARABIC else TranslateLanguage.ENGLISH)
            .build())
        val active = translator ?: return
        active.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener {
                if (active === translator && !isDestroyed) {
                    modelsReady = true
                    showStatus(label("Languages ready. Translation now works offline.", "اللغات جاهزة. الترجمة تعمل الآن دون إنترنت."))
                }
            }.addOnFailureListener {
                if (active === translator && !isDestroyed) showStatus(label("Model download failed: ", "فشل تنزيل النموذج: ") + it.localizedMessage)
            }
    }

    private fun translate() {
        val input = sourceInput.text.toString().trim()
        if (input.isBlank()) return showStatus(label("Enter some text first.", "أدخل نصًا أولًا."))
        if (!modelsReady) return showStatus(label("Download / check the models first.", "نزّل النماذج أو تحقق منها أولًا."))
        val active = translator ?: return
        val segments = TranslationReading.chunks(input, 450)
        val results = ArrayList<String>(segments.size)
        val thisGeneration = ++generation
        showStatus(label("Translating locally…", "جارٍ الترجمة محليًا…"))
        fun next(index: Int) {
            if (thisGeneration != generation || isDestroyed) return
            if (index == segments.size) {
                translatedText.text = results.joinToString("\n")
                preferences().edit().putString("translation_${pdfUri}_${page}", translatedText.text.toString()).apply()
                showStatus(label("Translation complete", "اكتملت الترجمة"))
                return
            }
            active.translate(segments[index]).addOnSuccessListener { translated ->
                results += translated
                next(index + 1)
            }.addOnFailureListener {
                if (thisGeneration == generation && !isDestroyed) showStatus(label("Translation failed: ", "فشلت الترجمة: ") + it.localizedMessage)
            }
        }
        next(0)
    }

    private fun changePage(delta: Int) {
        if (pdfUri == null || pageCount <= 0) return
        preferences().edit().putInt("offset_${pdfUri}_${page}", sourceInput.selectionStart.coerceAtLeast(0)).apply()
        page = TranslationReading.pageIndex(page + delta, pageCount)
        readPdfPage()
    }

    private fun readPdfPage() {
        val uri = pdfUri ?: return
        val requestedPage = page
        val thisGeneration = ++generation
        showStatus(label("Opening PDF page…", "جارٍ فتح صفحة PDF…"))
        Thread {
            val result = runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { pdf ->
                        val count = pdf.numberOfPages
                        require(count > 0) { "PDF has no pages" }
                        val actual = TranslationReading.pageIndex(requestedPage, count)
                        val text = PDFTextStripper().apply {
                            startPage = actual + 1
                            endPage = actual + 1
                            sortByPosition = true
                        }.getText(pdf)
                        Triple(count, actual, text)
                    }
                } ?: error("Cannot open selected PDF")
            }
            runOnUiThread {
                if (thisGeneration != generation || isDestroyed) return@runOnUiThread
                result.onSuccess { (count, actual, text) ->
                    pageCount = count
                    page = actual
                    sourceInput.setText(text)
                    val savedOffset = preferences().getInt("offset_${pdfUri}_${page}", 0)
                    sourceInput.setSelection(savedOffset.coerceIn(0, sourceInput.length()))
                    pageLabel.text = label("Page ${actual + 1} / $count", "الصفحة ${actual + 1} من $count")
                    translatedText.text = preferences().getString("translation_${pdfUri}_${page}", "").orEmpty()
                    preferences().edit().putInt("page_$uri", actual).apply()
                    showStatus(if (text.isBlank()) label("No extractable text. Scanned pages need OCR.", "لا يوجد نص قابل للاستخراج؛ الصفحات المصوّرة تحتاج OCR.") else label("Page ready", "الصفحة جاهزة"))
                }.onFailure { showStatus(label("PDF error: ", "خطأ في PDF: ") + it.localizedMessage) }
            }
        }.start()
    }

    private fun speak(text: String, original: Boolean) {
        if (!speechReady) return showStatus(label("Voice engine not ready", "محرك النطق غير جاهز"))
        if (text.isBlank()) return
        val useEnglish = if (original) sourceEnglish else !sourceEnglish
        val locale = if (useEnglish) Locale.ENGLISH else Locale("ar")
        if (tts?.setLanguage(locale) == TextToSpeech.LANG_MISSING_DATA) {
            showStatus(label("Install this voice in Android's TTS settings.", "ثبّت هذا الصوت من إعدادات النطق في أندرويد."))
            return
        }
        TranslationReading.chunks(text, 3000).forEachIndexed { index, part ->
            tts?.speak(part, if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "shortcut_reader_$index")
        }
    }

    override fun onInit(statusCode: Int) { speechReady = statusCode == TextToSpeech.SUCCESS }

    override fun onPause() {
        pdfUri?.let { uri ->
            preferences().edit()
                .putInt("page_$uri", page)
                .putInt("offset_${uri}_${page}", sourceInput.selectionStart.coerceAtLeast(0))
                .apply()
        }
        super.onPause()
    }

    override fun onDestroy() {
        generation++
        translator?.close()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun preferences() = getSharedPreferences("shortcut_translation_reader", MODE_PRIVATE)
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun label(en: String, ar: String): String = if (Locale.getDefault().language == "ar") ar else en
    private fun showStatus(message: String) { status.text = message }
    private fun button(parent: LinearLayout, en: String, ar: String, action: () -> Unit): Button = Button(this).also {
        it.text = label(en, ar)
        it.setOnClickListener { _ -> action() }
        parent.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    companion object { const val EXTRA_INITIAL_TEXT = "initial_text" }
}
