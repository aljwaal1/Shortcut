package com.explapp.shortcut.tools

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

object ToolResultActions {
    fun show(activity: Activity, outputs: List<Uri>, mime: String) {
        if (outputs.isEmpty()) {
            Toast.makeText(activity, local(activity, "No output was created", "لم يتم إنشاء ملف ناتج"), Toast.LENGTH_LONG).show()
            activity.finish()
            return
        }
        AlertDialog.Builder(activity)
            .setTitle(local(activity, "Operation complete", "اكتملت العملية"))
            .setMessage(local(activity, "${outputs.size} output file(s) created.", "تم إنشاء ${outputs.size} ملف ناتج."))
            .setPositiveButton(local(activity, "Share", "مشاركة")) { _, _ -> share(activity, outputs, mime) }
            .setNeutralButton(local(activity, "Open", "فتح")) { _, _ -> open(activity, outputs.first(), mime) }
            .setNegativeButton(local(activity, "Done", "تم")) { _, _ -> activity.finish() }
            .setOnCancelListener { activity.finish() }
            .show()
    }

    fun share(activity: Activity, outputs: List<Uri>, mime: String) {
        val uris = outputs.mapNotNull { deliverableUri(activity, it) }
        if (uris.isEmpty()) {
            Toast.makeText(activity, local(activity, "Could not share the generated file", "تعذر مشاركة الملف الناتج"), Toast.LENGTH_LONG).show()
            return
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
                clipData = ClipData.newRawUri("Shortcut output", uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = ClipData.newRawUri("Shortcut output", uris.first()).also { clip ->
                    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
            }
        }.apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }

        runCatching {
            activity.startActivity(Intent.createChooser(intent, local(activity, "Share result", "مشاركة النتيجة")))
        }.onFailure {
            Toast.makeText(activity, local(activity, "No app can share this file", "لا يوجد تطبيق لمشاركة هذا الملف"), Toast.LENGTH_LONG).show()
        }
    }

    fun open(activity: Activity, output: Uri, mime: String) {
        val uri = deliverableUri(activity, output)
        if (uri == null) {
            Toast.makeText(activity, local(activity, "Could not open the generated file", "تعذر فتح الملف الناتج"), Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("Shortcut output", uri)
            })
        }.onFailure {
            Toast.makeText(activity, local(activity, "No app can open this file", "لا يوجد تطبيق لفتح هذا الملف"), Toast.LENGTH_LONG).show()
        }
    }

    private fun deliverableUri(activity: Activity, uri: Uri): Uri? = when (OutputUriPolicy.deliveryFor(uri.scheme)) {
        OutputUriDelivery.DIRECT_CONTENT -> uri
        OutputUriDelivery.FILE_PROVIDER -> {
            val path = uri.path ?: return null
            runCatching {
                FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", File(path))
            }.getOrNull()
        }
        OutputUriDelivery.UNSUPPORTED -> null
    }

    private fun local(activity: Activity, en: String, ar: String): String =
        if (activity.resources.configuration.locales[0].language == "ar") ar else en
}
