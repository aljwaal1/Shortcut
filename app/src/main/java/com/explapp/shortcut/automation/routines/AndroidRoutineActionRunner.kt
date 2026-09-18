package com.explapp.shortcut.automation.routines

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import com.explapp.shortcut.tools.ToolOutputStore
import java.io.File
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.messages.MessageDeepLinkFactory
import com.explapp.shortcut.tools.ScreenCaptureActivity
import com.explapp.shortcut.tools.PersistentScreenCaptureService
import com.explapp.shortcut.tools.ToolId
import com.explapp.shortcut.tools.ToolRouter
import com.explapp.shortcut.usage.AppUsageActivity

class AndroidRoutineActionRunner(
    private val context: Context,
    private val userInitiated: Boolean,
) : RoutineActionRunner {
    private val variables = mutableMapOf<String, String>()
    override fun run(action: RoutineAction): RoutineActionResult = when (action.type) {
        RoutineActionType.OPEN_APP -> openExternal(action, context.packageManager.getLaunchIntentForPackage(action.value))
        RoutineActionType.OPEN_APP_SCREENSHOT -> openAppScreenshot(action)
        RoutineActionType.TAKE_SCREENSHOT -> takeScreenshot(action)
        RoutineActionType.WAIT -> waitAction(action)
        RoutineActionType.OPEN_URL -> openExternal(action, Intent(Intent.ACTION_VIEW, Uri.parse(action.value)))
        RoutineActionType.OPEN_MAPS -> {
            val query = Uri.encode(action.value)
            openExternal(action, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
        }
        RoutineActionType.PREPARE_WHATSAPP -> preparedMessage(action, MessagePlatform.WHATSAPP)
        RoutineActionType.PREPARE_TELEGRAM -> preparedMessage(action, MessagePlatform.TELEGRAM)
        RoutineActionType.SEND_TELEGRAM_BOT -> sendTelegramBot(action)
        RoutineActionType.CUSTOM_SCRIPT -> runCustomScript(action)
        RoutineActionType.SET_VARIABLE -> setVariable(action)
        RoutineActionType.READ_CLIPBOARD -> readClipboard(action)
        RoutineActionType.COPY_TO_CLIPBOARD -> copyToClipboard(action)
        RoutineActionType.STOP_SHORTCUT -> RoutineActionResult.success(action)
        RoutineActionType.SAVE_TEXT_FILE -> saveTextFile(action)
        RoutineActionType.SHARE_TEXT -> shareText(action)
        RoutineActionType.SHARE_FILE -> shareFile(action)
        RoutineActionType.WEB_SEARCH -> webSearch(action)
        RoutineActionType.OPEN_TOOL -> {
            val tool = runCatching { ToolId.valueOf(action.value) }.getOrNull()
            if (tool != null) openExternal(action, ToolRouter.intent(context, tool))
            else RoutineActionResult.failure(action, local("Unknown built-in tool", "أداة داخلية غير معروفة"))
        }
        RoutineActionType.SHOW_NOTIFICATION -> {
            if (showNotification(local("Shortcut", "الاختصارات"), action.value, null)) RoutineActionResult.success(action)
            else RoutineActionResult.failure(action, local("Notification permission is required", "يلزم السماح بالإشعارات"))
        }
    }

    private fun waitAction(action: RoutineAction): RoutineActionResult {
        val delay = action.value.toLongOrNull()?.coerceIn(100L, 60_000L)
            ?: return RoutineActionResult.failure(action, local("Invalid wait time", "مدة الانتظار غير صالحة"))
        return runCatching {
            Thread.sleep(delay)
            RoutineActionResult.success(action)
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: local("Wait failed", "فشل الانتظار")) }
    }

    private fun takeScreenshot(action: RoutineAction): RoutineActionResult {
        val delayMs = action.value.toLongOrNull()?.coerceIn(500L, 10_000L) ?: 3_000L
        val workflowIntent = Intent(context, ScreenCaptureActivity::class.java)
            .putExtra(ScreenCaptureActivity.EXTRA_CAPTURE_DELAY_MS, delayMs)
            .putExtra(ScreenCaptureActivity.EXTRA_TELEGRAM_BOT_TOKEN, action.parameters["telegramBotToken"].orEmpty())
            .putExtra(ScreenCaptureActivity.EXTRA_TELEGRAM_CHAT_ID, action.parameters["telegramChatId"].orEmpty())
            .putExtra(ScreenCaptureActivity.EXTRA_TELEGRAM_CAPTION, resolve(action.parameters["telegramCaption"].orEmpty()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(workflowIntent)
            RoutineActionResult.prepared(action, local("Screen-capture consent required", "يلزم تأكيد إذن تصوير الشاشة"))
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
    }

    private fun sendTelegramBot(action: RoutineAction): RoutineActionResult {
        val token = resolve(action.parameters["botToken"].orEmpty())
        val chatId = resolve(action.parameters["chatId"].orEmpty())
        val text = resolve(action.secondaryValue)
        val attachment = resolve(action.parameters["attachment"].orEmpty())
        if (token.isBlank() || chatId.isBlank()) return RoutineActionResult.failure(action, local("Bot token and chat ID are required", "يلزم إدخال رمز البوت ومعرّف المحادثة"))
        var error: Throwable? = null
        val thread = Thread {
            val result = if (attachment.isNotBlank()) {
                TelegramBotSender().sendPhoto(token, chatId, text, java.io.File(attachment))
            } else {
                TelegramBotSender().sendText(token, chatId, text)
            }
            error = result.exceptionOrNull()
        }
        thread.start()
        thread.join(20_000L)
        return if (thread.isAlive) RoutineActionResult.failure(action, local("Telegram request timed out", "انتهت مهلة الاتصال بتيليجرام"))
        else error?.let { RoutineActionResult.failure(action, it.message ?: local("Telegram send failed", "فشل الإرسال إلى تيليجرام")) }
            ?: RoutineActionResult.success(action)
    }

    private fun setVariable(action: RoutineAction): RoutineActionResult {
        val name = action.value.trim()
        if (name.isBlank()) return RoutineActionResult.failure(action, local("Variable name is required", "يلزم إدخال اسم المتغير"))
        variables[name] = resolve(action.secondaryValue)
        variables["lastResult"] = variables[name].orEmpty()
        return RoutineActionResult.success(action)
    }

    private fun readClipboard(action: RoutineAction): RoutineActionResult {
        val name = action.value.trim()
        if (name.isBlank()) return RoutineActionResult.failure(action, "Variable name is required")
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
        variables[name] = text
        variables["lastResult"] = text
        return RoutineActionResult.success(action)
    }

    private fun copyToClipboard(action: RoutineAction): RoutineActionResult {
        val text = resolve(action.value)
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut", text))
        variables["lastResult"] = text
        return RoutineActionResult.success(action)
    }

    private fun saveTextFile(action: RoutineAction): RoutineActionResult {
        val text = resolve(action.value)
        val rawName = resolve(action.secondaryValue).ifBlank { "Shortcut_" + System.currentTimeMillis() + ".txt" }
        val fileName = if (rawName.endsWith(".txt", ignoreCase = true)) rawName else rawName + ".txt"
        return runCatching {
            val uri = ToolOutputStore(context).create(fileName, "text/plain", false)
            context.contentResolver.openOutputStream(uri).use { out ->
                requireNotNull(out).write(text.toByteArray(Charsets.UTF_8))
            }
            variables["lastFile"] = uri.toString()
            variables["lastResult"] = uri.toString()
            RoutineActionResult.success(action)
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: local("Could not save file", "تعذر حفظ الملف")) }
    }

    private fun shareText(action: RoutineAction): RoutineActionResult {
        val text = resolve(action.value)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (userInitiated) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, local("Share text", "مشاركة النص")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                RoutineActionResult.success(action)
            }.getOrElse { RoutineActionResult.failure(action, it.message ?: local("Could not open sharing", "تعذر فتح المشاركة")) }
        } else {
            if (showNotification(local("Text ready to share", "النص جاهز للمشاركة"), text, intent)) {
                RoutineActionResult.prepared(action, local("User action required", "يلزم إجراء من المستخدم"))
            } else RoutineActionResult.failure(action, local("Notification permission is required", "يلزم السماح بالإشعارات"))
        }
    }

    private fun shareFile(action: RoutineAction): RoutineActionResult {
        val raw = resolve(action.value).ifBlank { variables["lastFile"].orEmpty() }
        if (raw.isBlank()) return RoutineActionResult.failure(action, local("No file is available to share", "لا يوجد ملف متاح للمشاركة"))
        val uri = runCatching {
            val parsed = Uri.parse(raw)
            if (parsed.scheme == "content") parsed
            else FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(raw))
        }.getOrElse {
            return RoutineActionResult.failure(action, local("Could not access the file", "تعذر الوصول إلى الملف"))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = action.parameters["mime"].orEmpty().ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (userInitiated) {
            runCatching {
                context.startActivity(Intent.createChooser(intent, local("Share file", "مشاركة الملف")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                RoutineActionResult.success(action)
            }.getOrElse { RoutineActionResult.failure(action, it.message ?: local("Could not open sharing", "تعذر فتح المشاركة")) }
        } else {
            if (showNotification(local("File ready to share", "الملف جاهز للمشاركة"), local("Tap to choose where to share it", "اضغط لاختيار جهة المشاركة"), intent)) {
                RoutineActionResult.prepared(action, local("User action required", "يلزم إجراء من المستخدم"))
            } else RoutineActionResult.failure(action, local("Notification permission is required", "يلزم السماح بالإشعارات"))
        }
    }

    private fun webSearch(action: RoutineAction): RoutineActionResult {
        val query = resolve(action.value)
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
        return openExternal(action, intent)
    }

    private fun runCustomScript(action: RoutineAction): RoutineActionResult {
        val inputs = variables + mapOf(
            "input" to resolve(action.secondaryValue),
            "currentDate" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            "currentTime" to SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
        )
        val result = CustomScriptRunner().run(action.value, inputs)
        return result.fold(
            onSuccess = { output ->
                variables["lastResult"] = output
                RoutineActionResult.success(action)
            },
            onFailure = { RoutineActionResult.failure(action, it.message ?: local("Script failed", "فشل تنفيذ السكربت")) },
        )
    }

    private fun resolve(raw: String): String {
        val builtIns = mapOf(
            "currentDate" to SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
            "currentTime" to SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()),
        )
        return (builtIns + variables).entries.fold(raw) { acc, (key, value) ->
            acc.replace("{{$key}}", value)
        }
    }

    private fun openAppScreenshot(action: RoutineAction): RoutineActionResult {
        if (context.packageManager.getLaunchIntentForPackage(action.value) == null) {
            return RoutineActionResult.failure(action, local("Target app is unavailable", "التطبيق المطلوب غير متاح"))
        }
        val delayMs = action.secondaryValue.toLongOrNull()?.coerceIn(500L, 10_000L) ?: 3_000L
        val usePersistentSession = action.parameters["persistentCapture"].toBoolean()

        if (usePersistentSession) {
            if (PersistentScreenCaptureService.isSessionActive(context)) {
                val captureIntent = Intent(context, PersistentScreenCaptureService::class.java)
                    .setAction(PersistentScreenCaptureService.ACTION_CAPTURE)
                    .putExtra(PersistentScreenCaptureService.EXTRA_LAUNCH_PACKAGE, action.value)
                    .putExtra(PersistentScreenCaptureService.EXTRA_CAPTURE_DELAY_MS, delayMs)
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_BOT_TOKEN, action.parameters["telegramBotToken"].orEmpty())
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_CHAT_ID, action.parameters["telegramChatId"].orEmpty())
                    .putExtra(PersistentScreenCaptureService.EXTRA_TELEGRAM_CAPTION, resolve(action.parameters["telegramCaption"].orEmpty()))
                return runCatching {
                    ContextCompat.startForegroundService(context, captureIntent)
                    RoutineActionResult.success(action)
                }.getOrElse {
                    RoutineActionResult.failure(action, it.message ?: local("Persistent capture failed", "فشل التصوير المستمر"))
                }
            }

            val reactivate = Intent(context, ScreenCaptureActivity::class.java)
                .putExtra(ScreenCaptureActivity.EXTRA_PERSISTENT_START_ONLY, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return if (userInitiated) {
                runCatching {
                    context.startActivity(reactivate)
                    RoutineActionResult.prepared(action, local("Start the persistent capture session", "شغّل جلسة تصوير الشاشة المستمرة"))
                }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
            } else if (
                showNotification(
                    title = local("Screen-capture session needs approval", "جلسة تصوير الشاشة تحتاج موافقة"),
                    text = local("Tap once to reactivate persistent capture.", "اضغط مرة واحدة لإعادة تفعيل جلسة التصوير المستمرة."),
                    intent = reactivate,
                )
            ) {
                RoutineActionResult.prepared(action, local("Persistent capture session is inactive", "جلسة التصوير المستمرة غير نشطة"))
            } else {
                RoutineActionResult.failure(action, local("Notification permission is required", "يلزم السماح بالإشعارات"))
            }
        }

        val workflowIntent = Intent(context, ScreenCaptureActivity::class.java)
            .putExtra(ScreenCaptureActivity.EXTRA_LAUNCH_PACKAGE, action.value)
            .putExtra(ScreenCaptureActivity.EXTRA_CAPTURE_DELAY_MS, delayMs)
            .putExtra(ScreenCaptureActivity.EXTRA_TELEGRAM_BOT_TOKEN, action.parameters["telegramBotToken"].orEmpty())
            .putExtra(ScreenCaptureActivity.EXTRA_TELEGRAM_CHAT_ID, action.parameters["telegramChatId"].orEmpty())
            .putExtra(ScreenCaptureActivity.EXTRA_TELEGRAM_CAPTION, resolve(action.parameters["telegramCaption"].orEmpty()))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (userInitiated) {
            return runCatching {
                context.startActivity(workflowIntent)
                RoutineActionResult.success(action)
            }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
        }

        return if (
            showNotification(
                title = local("Open app and take screenshot", "فتح تطبيق والتقاط الشاشة"),
                text = local("Tap to approve screen capture and continue", "اضغط لتأكيد إذن تصوير الشاشة والمتابعة"),
                intent = workflowIntent,
            )
        ) {
            RoutineActionResult.prepared(action, local("Screen-capture consent required", "يلزم تأكيد إذن تصوير الشاشة"))
        } else {
            RoutineActionResult.failure(action, local("Notification permission is required", "يلزم السماح بالإشعارات"))
        }
    }

    private fun preparedMessage(action: RoutineAction, platform: MessagePlatform): RoutineActionResult {
        if (action.value.isBlank() || action.secondaryValue.isBlank()) return RoutineActionResult.failure(action, local("Recipient and message are required", "يلزم إدخال المستلم ونص الرسالة"))
        val uri = Uri.parse(MessageDeepLinkFactory.build(platform, action.value, action.secondaryValue))
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (userInitiated) openExternal(action, intent) else {
            if (showNotification(
                    title = if (platform == MessagePlatform.WHATSAPP) local("WhatsApp message ready", "رسالة واتساب جاهزة") else local("Telegram message ready", "رسالة تيليجرام جاهزة"),
                    text = action.secondaryValue,
                    intent = intent,
                )
            ) RoutineActionResult.prepared(action, local("User action required", "يلزم إجراء من المستخدم"))
            else RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun openExternal(action: RoutineAction, rawIntent: Intent?): RoutineActionResult {
        val intent = rawIntent ?: return RoutineActionResult.failure(action, local("Target is unavailable", "الوجهة غير متاحة"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!userInitiated) {
            return if (showNotification(local("Shortcut action ready", "إجراء الاختصار جاهز"), action.value.ifBlank { action.type.name }, intent)) {
                RoutineActionResult.prepared(action, "User action required")
            } else {
                RoutineActionResult.failure(action, "Notification permission is required")
            }
        }
        return runCatching {
            context.startActivity(intent)
            RoutineActionResult.success(action)
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
    }

    private fun local(en: String, ar: String): String =
        if (context.resources.configuration.locales[0].language == "ar") ar else en

    private fun showNotification(title: String, text: String, intent: Intent?): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, local("Automation routines", "اختصارات الأتمتة"), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle(title)
            .setContentText(text.take(120))
            .setAutoCancel(true)
        if (intent != null) {
            builder.setContentIntent(
                PendingIntent.getActivity(
                    context,
                    (intent.dataString ?: intent.component?.className.orEmpty()).hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        return runCatching {
            manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), builder.build())
            true
        }.getOrDefault(false)
    }

    companion object { private const val CHANNEL = "automation_routines" }
}
