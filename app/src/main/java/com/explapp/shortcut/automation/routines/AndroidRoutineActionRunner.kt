package com.explapp.shortcut.automation.routines

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.explapp.shortcut.domain.MessagePlatform
import com.explapp.shortcut.messages.MessageDeepLinkFactory
import com.explapp.shortcut.tools.ScreenCaptureActivity
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
        RoutineActionType.OPEN_TOOL -> {
            if (action.value == "app_usage") openExternal(action, Intent(context, AppUsageActivity::class.java))
            else RoutineActionResult.failure(action, "Unknown tool: ${action.value}")
        }
        RoutineActionType.SHOW_NOTIFICATION -> {
            if (showNotification("Shortcut", action.value, null)) RoutineActionResult.success(action)
            else RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun waitAction(action: RoutineAction): RoutineActionResult {
        val delay = action.value.toLongOrNull()?.coerceIn(100L, 60_000L)
            ?: return RoutineActionResult.failure(action, "Invalid wait time")
        return runCatching {
            Thread.sleep(delay)
            RoutineActionResult.success(action)
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: "Wait failed") }
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
            RoutineActionResult.prepared(action, "Screen-capture consent required")
        }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
    }

    private fun sendTelegramBot(action: RoutineAction): RoutineActionResult {
        val token = resolve(action.parameters["botToken"].orEmpty())
        val chatId = resolve(action.parameters["chatId"].orEmpty())
        val text = resolve(action.secondaryValue)
        val attachment = resolve(action.parameters["attachment"].orEmpty())
        if (token.isBlank() || chatId.isBlank()) return RoutineActionResult.failure(action, "Bot token and chat ID are required")
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
        return if (thread.isAlive) RoutineActionResult.failure(action, "Telegram request timed out")
        else error?.let { RoutineActionResult.failure(action, it.message ?: "Telegram send failed") }
            ?: RoutineActionResult.success(action)
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
            onFailure = { RoutineActionResult.failure(action, it.message ?: "Script failed") },
        )
    }

    private fun resolve(raw: String): String = variables.entries.fold(raw) { acc, (key, value) ->
        acc.replace("{{$key}}", value)
    }

    private fun openAppScreenshot(action: RoutineAction): RoutineActionResult {
        if (context.packageManager.getLaunchIntentForPackage(action.value) == null) {
            return RoutineActionResult.failure(action, "Target app is unavailable")
        }
        val delayMs = action.secondaryValue.toLongOrNull()?.coerceIn(500L, 10_000L) ?: 2_000L
        val workflowIntent = Intent(context, ScreenCaptureActivity::class.java)
            .putExtra(ScreenCaptureActivity.EXTRA_LAUNCH_PACKAGE, action.value)
            .putExtra(ScreenCaptureActivity.EXTRA_CAPTURE_DELAY_MS, delayMs)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (userInitiated) {
            return runCatching {
                context.startActivity(workflowIntent)
                RoutineActionResult.success(action)
            }.getOrElse { RoutineActionResult.failure(action, it.message ?: it.javaClass.simpleName) }
        }

        return if (
            showNotification(
                title = "Open app + screenshot",
                text = "Tap to approve screen capture and continue",
                intent = workflowIntent,
            )
        ) {
            RoutineActionResult.prepared(action, "Screen-capture consent required")
        } else {
            RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun preparedMessage(action: RoutineAction, platform: MessagePlatform): RoutineActionResult {
        if (action.value.isBlank() || action.secondaryValue.isBlank()) return RoutineActionResult.failure(action, "Recipient and message are required")
        val uri = Uri.parse(MessageDeepLinkFactory.build(platform, action.value, action.secondaryValue))
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (userInitiated) openExternal(action, intent) else {
            if (showNotification(
                    title = if (platform == MessagePlatform.WHATSAPP) "WhatsApp message ready" else "Telegram message ready",
                    text = action.secondaryValue,
                    intent = intent,
                )
            ) RoutineActionResult.prepared(action, "User action required")
            else RoutineActionResult.failure(action, "Notification permission is required")
        }
    }

    private fun openExternal(action: RoutineAction, rawIntent: Intent?): RoutineActionResult {
        val intent = rawIntent ?: return RoutineActionResult.failure(action, "Target is unavailable")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!userInitiated) {
            return if (showNotification("Shortcut action ready", action.value.ifBlank { action.type.name }, intent)) {
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

    private fun showNotification(title: String, text: String, intent: Intent?): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Automation routines", NotificationManager.IMPORTANCE_DEFAULT))
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
