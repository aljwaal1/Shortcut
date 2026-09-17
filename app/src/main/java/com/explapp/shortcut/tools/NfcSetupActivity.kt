package com.explapp.shortcut.tools

import android.app.AlertDialog
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.explapp.shortcut.automation.routines.RoutineDispatcher
import com.explapp.shortcut.automation.routines.RoutineStore

class NfcSetupActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {
    private var adapter: NfcAdapter? = null
    private var selectedTool: ToolId? = null
    private var selectedRoutineId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        if (adapter == null) {
            Toast.makeText(this, local("NFC is not available on this phone", "NFC غير متوفر على هذا الهاتف"), Toast.LENGTH_LONG).show()
            finish()
            return
        }
        selectedRoutineId = intent.getStringExtra(EXTRA_ROUTINE_ID)?.takeIf { it.isNotBlank() }
        if (selectedRoutineId != null) {
            enableWriting()
            showTouchDialog()
        } else {
            chooseTool()
        }
    }

    private fun chooseTool() {
        val tools = ToolCatalog.all().filter { it.id != ToolId.NFC_TRIGGER }
        val ar = resources.configuration.locales[0].language == "ar"
        val labels = tools.map { if (ar) it.titleAr else it.titleEn }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(local("Choose what the NFC tag should run", "اختر ما الذي سيشغله وسم NFC"))
            .setItems(labels) { _, index ->
                selectedTool = tools[index].id
                enableWriting()
                showTouchDialog()
            }
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun showTouchDialog() {
        AlertDialog.Builder(this)
            .setTitle(local("Touch an NFC tag", "المس وسم NFC"))
            .setMessage(local("Keep the tag near the phone until writing finishes.", "قرّب الوسم من الهاتف حتى تنتهي الكتابة."))
            .setNegativeButton(local("Cancel", "إلغاء")) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun enableWriting() {
        adapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V,
            null,
        )
    }

    override fun onTagDiscovered(tag: Tag) {
        val uri = selectedRoutineId?.let { Uri.parse("shortcut://routine/$it") }
            ?: selectedTool?.let { Uri.parse("shortcut://tool/${it.name}") }
            ?: return
        val message = NdefMessage(arrayOf(NdefRecord.createUri(uri)))
        val success = runCatching {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                require(ndef.isWritable) { "Tag is read-only" }
                require(ndef.maxSize >= message.toByteArray().size) { "Tag is too small" }
                ndef.writeNdefMessage(message)
                ndef.close()
            } else {
                val formatable = NdefFormatable.get(tag) ?: error("Tag cannot be formatted")
                formatable.connect()
                formatable.format(message)
                formatable.close()
            }
        }.isSuccess

        runOnUiThread {
            Toast.makeText(
                this,
                if (success) local("NFC shortcut written", "تمت كتابة اختصار NFC") else local("Could not write this NFC tag", "تعذر كتابة وسم NFC"),
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    override fun onPause() {
        adapter?.disableReaderMode(this)
        super.onPause()
    }

    private fun local(en: String, ar: String): String =
        if (resources.configuration.locales[0].language == "ar") ar else en

    companion object { const val EXTRA_ROUTINE_ID = "routine_id" }
}

class NfcDispatchActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent.data
        when (data?.host) {
            "tool" -> {
                val tool = data.lastPathSegment?.let { runCatching { ToolId.valueOf(it) }.getOrNull() }
                if (tool != null) startActivity(ToolRouter.intent(this, tool))
            }
            "routine" -> {
                val id = data.lastPathSegment.orEmpty()
                val routine = RoutineStore(this).load().firstOrNull { it.id == id && it.isEnabled }
                if (routine != null) RoutineDispatcher(this).execute(routine, userInitiated = true)
            }
        }
        finish()
    }
}
