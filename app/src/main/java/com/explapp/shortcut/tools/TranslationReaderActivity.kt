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
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
            setPadding(dp(16), dp(18), dp(16), dp(30))
            setBackgroundColor(android.graphics.Color.rgb(246, 248, 253))
        }
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })

        val hero = panel(root, android.graphics.Color.rgb(41, 52, 119))
        hero.addView(TextView(this).apply {
            text = label("Translate & Read", "الترجمة والقراءة")
            textSize = 25f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        hero.addView(TextView(this).apply {
            text = label("Text and PDF pages • Arabic ↔ English • Offline", "النصوص وصفحات PDF • عربي ↔ إنجليزي • دون إنترنت")
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(224, 230, 255))
            setPadding(0, dp(6), 0, 0)
        })

        val controls = panel(root)
        controls.addView(sectionTitle(label("Languages", "اللغات")))
        languageButton = button(controls, "English → Arabic  ⇄", "إنجليزي ← عربي  ⇄") {
            sourceEnglish = !sourceEnglish
            generation++
            modelsReady = false
            translator?.close()
            translator = null
            languageButton.text = label(
                if (sourceEnglish) "English → Arabic  ⇄" else "Arabic → English  ⇄",
                if (sourceEnglish) "إنجليزي ← عربي  ⇄" else "عربي ← إنجليزي  ⇄",
            )
            showStatus(label("Direction changed. Check the offline model.", "تغير اتجاه الترجمة؛ تحقّق من النموذج المحلي."))
        }
        button(controls, "Check / download offline model", "فحص / تنزيل نموذج الترجمة المحلي") { prepareModels() }
        button(controls, "Download Arabic OCR model", "تنزيل نموذج قراءة الصور العربية") { prepareArabicOcr() }
        status = TextView(this).apply {
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(59, 74, 104))
            setPadding(dp(4), dp(8), dp(4), dp(3))
        }
        controls.addView(status)
        showStatus(label("Check the model once; then translate without Internet.", "افحص النموذج مرة واحدة ثم ترجم دون إنترنت."))

        val inputCard = panel(root)
        inputCard.addView(sectionTitle(label("Original text", "النص الأصلي")))
        sourceInput = EditText(this).apply {
            hint = label("Write, paste, or open a PDF page…", "اكتب أو الصق نصًا أو افتح صفحة PDF…")
            minLines = 5
            maxLines = 14
            textSize = 17f
            gravity = Gravity.TOP
            setTextIsSelectable(true)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(android.graphics.Color.rgb(247, 249, 255), 16)
        }
        inputCard.addView(sourceInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        button(inputCard, "Translate now", "ترجم الآن") { translate() }

        val pdfCard = panel(root)
        pdfCard.addView(sectionTitle(label("PDF page reader", "قارئ صفحات PDF")))
        button(pdfCard, "Open PDF from phone", "فتح ملف PDF من الهاتف") { pdfPicker.launch(arrayOf("application/pdf")) }
        pageLabel = TextView(this).apply {
            text = label("No PDF selected", "لم يتم اختيار ملف PDF")
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(android.graphics.Color.rgb(74, 84, 110))
            setPadding(0, dp(8), 0, dp(4))
        }
        pdfCard.addView(pageLabel)
        val pageActions = horizontalRow(pdfCard)
        button(pageActions, "◀ Previous", "◀ السابقة") { changePage(-1) }
        button(pageActions, "Next ▶", "التالية ▶") { changePage(1) }

        val resultCard = panel(root)
        resultCard.addView(sectionTitle(label("Translation", "الترجمة")))
        translatedText = TextView(this).apply {
            textSize = 17f
            setTextIsSelectable(true)
            setTextColor(android.graphics.Color.rgb(32, 44, 72))
            setPadding(dp(14), dp(18), dp(14), dp(18))
            text = label("Translation will appear here", "ستظهر الترجمة هنا")
            minHeight = dp(110)
            background = rounded(android.graphics.Color.rgb(247, 249, 255), 16)
        }
        resultCard.addView(translatedText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val resultActions = horizontalRow(resultCard)
        button(resultActions, "Copy", "نسخ") {
            val clip = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            clip.setPrimaryClip(ClipData.newPlainText("translation", translatedText.text))
            showStatus(label("Copied", "تم النسخ"))
        }
        button(resultActions, "Share", "مشاركة") {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, translatedText.text.toString())
            }, label("Share", "مشاركة")))
        }
        val speechActions = horizontalRow(resultCard)
        button(speechActions, "Listen: original", "نطق الأصل") { speak(sourceInput.text.toString(), original = true) }
        button(speechActions, "Listen: result", "نطق الترجمة") { speak(translatedText.text.toString(), original = false) }
        button(resultCard, "Stop speaking", "إيقاف النطق") { tts?.stop() }

        intent.getStringExtra(EXTRA_INITIAL_TEXT)?.takeIf { it.isNotBlank() }?.let(sourceInput::setText)
    }

    private fun rounded(color: Int, radius: Int) = android.graphics.drawable.GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun panel(parent: LinearLayout, color: Int = android.graphics.Color.WHITE): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = rounded(color, 22)
            elevation = dp(2).toFloat()
            parent.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(14) })
        }

    private fun horizontalRow(parent: LinearLayout): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            parent.addView(this, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ))
        }

    private fun sectionTitle(title: String): TextView = TextView(this).apply {
        text = title
        textSize = 17f
        setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(android.graphics.Color.rgb(41, 52, 119))
        setPadding(0, 0, 0, dp(12))
    }

    private fun prepareArabicOcr() {
        if (ArabicOcrEngine.available(this)) {
            showStatus(label("Arabic OCR is ready for offline use.", "نموذج قراءة الصور العربية جاهز دون إنترنت."))
            return
        }
        showStatus(label("Downloading Arabic OCR once…", "جارٍ تنزيل نموذج OCR العربي لأول مرة…"))
        Thread {
            val result = runCatching { ArabicOcrEngine.download(applicationContext) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                result.onSuccess { showStatus(label("Arabic OCR is installed.", "تم تثبيت نموذج OCR العربي.")) }
                    .onFailure { showStatus(label("Arabic OCR download failed: ", "فشل تنزيل OCR العربي: ") + it.localizedMessage) }
            }
        }.start()
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
                    if (text.isBlank()) {
                        showStatus(label("Scanned page: reading text with OCR…", "صفحة مصوّرة: جارٍ التعرف على النص…"))
                        recognizeScannedPage(uri, actual, thisGeneration)
                    } else {
                        showStatus(label("Page ready", "الصفحة جاهزة"))
                    }
                }.onFailure { showStatus(label("PDF error: ", "خطأ في PDF: ") + it.localizedMessage) }
            }
        }.start()
    }

    private fun recognizeScannedPage(uri: Uri, pageIndex: Int, request: Int) {
        Thread {
            val rendered = runCatching {
                contentResolver.openInputStream(uri)?.use { stream ->
                    PDDocument.load(stream).use { pdf ->
                        PDFRenderer(pdf).renderImageWithDPI(pageIndex, 120f)
                    }
                } ?: error("Cannot open scanned page")
            }
            rendered.onFailure { error ->
                runOnUiThread {
                    if (request == generation && !isDestroyed) {
                        showStatus(label("OCR failed: ", "فشل التعرف على النص: ") + error.localizedMessage)
                    }
                }
            }
            rendered.onSuccess { bitmap ->
                if (!sourceEnglish) {
                    val result = runCatching { ArabicOcrEngine.recognize(applicationContext, bitmap) }
                    bitmap.recycle()
                    runOnUiThread {
                        if (request != generation || isDestroyed) return@runOnUiThread
                        result.onSuccess { extracted ->
                            sourceInput.setText(extracted)
                            showStatus(if (extracted.isBlank()) {
                                label("No Arabic text found in this page.", "لم يُعثر على نص عربي واضح في الصفحة.")
                            } else label("Arabic OCR complete. Translate when ready.", "اكتمل استخراج النص العربي. يمكنك ترجمته الآن."))
                        }.onFailure { error ->
                            showStatus(if (!ArabicOcrEngine.available(this)) {
                                label("Download the Arabic OCR model first.", "نزّل نموذج OCR العربي أولًا من قسم اللغات.")
                            } else label("Arabic OCR failed: ", "فشل OCR العربي: ") + error.localizedMessage)
                        }
                    }
                } else {
                    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { result ->
                            if (request == generation && !isDestroyed) {
                                sourceInput.setText(result.text)
                                showStatus(if (result.text.isBlank()) {
                                    label("No readable text found.", "لم يُعثر على نص واضح.")
                                } else label("Scanned text extracted. You can translate it now.", "تم استخراج النص المصوّر، ويمكن ترجمته الآن."))
                            }
                        }
                        .addOnFailureListener { error ->
                            if (request == generation && !isDestroyed) {
                                showStatus(label("OCR failed: ", "فشل التعرف على النص: ") + error.localizedMessage)
                            }
                        }
                        .addOnCompleteListener {
                            bitmap.recycle()
                            recognizer.close()
                        }
                }
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
        it.isAllCaps = false
        it.textSize = 14f
        it.setTextColor(android.graphics.Color.WHITE)
        it.background = rounded(android.graphics.Color.rgb(77, 77, 222), 14)
        it.setOnClickListener { _ -> action() }
        val horizontal = parent.orientation == LinearLayout.HORIZONTAL
        parent.addView(it, LinearLayout.LayoutParams(
            if (horizontal) 0 else ViewGroup.LayoutParams.MATCH_PARENT,
            dp(48),
            if (horizontal) 1f else 0f,
        ).apply {
            topMargin = dp(8)
            if (horizontal) { marginStart = dp(3); marginEnd = dp(3) }
        })
    }

    companion object { const val EXTRA_INITIAL_TEXT = "initial_text" }
}
