package com.explapp.shortcut.automation.routines

import android.content.Context
import android.os.Build
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * TDLib personal user session. This is NOT Telegram Bot API.
 * No passwords, login codes, or API hashes are logged or saved here.
 * Authorization requires the user to enter their own credentials in the app.
 * All blocking calls must run off the UI thread.
 */
internal object TelegramTdlibSession : TelegramPersonalClient {
    @Volatile private var client: Client? = null
    @Volatile private var authState: TdApi.AuthorizationState? = null
    @Volatile private var currentApiId: Int = 0
    @Volatile private var currentApiHash: String = ""
    @Volatile private var appContext: Context? = null
    @Volatile private var sendError: String? = null
    private val delivered = ConcurrentHashMap.newKeySet<Long>()
    private val deliveryWaiters = ConcurrentHashMap<Long, CountDownLatch>()
    private val rejected = ConcurrentHashMap<Long, String>()

    fun status(): String = authState?.javaClass?.simpleName ?: "Not connected"
    fun lastError(): String? = sendError

    @Synchronized
    fun open(context: Context, apiId: Int, apiHash: String): Boolean {
        if (apiId <= 0 || apiHash.isBlank()) return false
        if (client != null) return true
        appContext = context.applicationContext
        currentApiId = apiId
        currentApiHash = apiHash
        return runCatching {
            System.loadLibrary("tdjson")
            client = Client.create({ update ->
                when (update) {
                    is TdApi.UpdateAuthorizationState -> onAuthorizationUpdate(update.authorizationState)
                    is TdApi.UpdateMessageSendSucceeded -> {
                        delivered.add(update.oldMessageId)
                        deliveryWaiters[update.oldMessageId]?.countDown()
                    }
                    is TdApi.UpdateMessageSendFailed -> {
                        rejected[update.oldMessageId] = "Telegram rejected outgoing photo"
                        deliveryWaiters[update.oldMessageId]?.countDown()
                    }
                    else -> Unit
                }
            }, null, null)
            true
        }.getOrElse {
            sendError = "TDLib initialization failed: ${it.javaClass.simpleName}"
            false
        }
    }

    private fun onAuthorizationUpdate(state: TdApi.AuthorizationState) {
        authState = state
        if (state is TdApi.AuthorizationStateWaitTdlibParameters) {
            val context = appContext ?: return
            val params = TdApi.SetTdlibParameters().apply {
                useTestDc = false
                databaseDirectory = File(context.filesDir, "telegram-personal/tdlib").apply { mkdirs() }.absolutePath
                filesDirectory = File(context.filesDir, "telegram-personal/files").apply { mkdirs() }.absolutePath
                useFileDatabase = true
                useChatInfoDatabase = true
                useMessageDatabase = true
                useSecretChats = false
                apiId = currentApiId
                apiHash = currentApiHash
                systemLanguageCode = Locale.getDefault().language
                deviceModel = Build.MODEL
                systemVersion = Build.VERSION.RELEASE
                applicationVersion = "Shortcut 0.9.10"
            }
            client?.send(params) { result ->
                if (result is TdApi.Error) sendError = "TDLib parameters: ${result.code} ${result.message}"
            }
        }
    }

    fun providePhone(phone: String) = submit(TdApi.SetAuthenticationPhoneNumber(phone.trim(), null))
    fun provideCode(code: String) = submit(TdApi.CheckAuthenticationCode(code.trim()))
    fun providePassword(password: String) = submit(TdApi.CheckAuthenticationPassword(password))
    fun logout() = submit(TdApi.LogOut())

    private fun submit(request: TdApi.Function<*>): Boolean {
        val c = client ?: return false
        c.send(request) { result ->
            if (result is TdApi.Error) sendError = "Telegram: ${result.code} ${result.message}"
            else sendError = null
        }
        return true
    }

    override fun isAuthorized() = authState is TdApi.AuthorizationStateReady

    private fun ask(request: TdApi.Function<*>, timeoutSeconds: Long = 20): TdApi.Object? {
        val c = client ?: return null
        val latch = CountDownLatch(1)
        val ref = AtomicReference<TdApi.Object?>()
        c.send(request) {
            ref.set(it)
            latch.countDown()
        }
        return if (latch.await(timeoutSeconds, TimeUnit.SECONDS)) ref.get() else null
    }

    override fun resolvePrivateRecipient(normalizedUsername: String): Long? {
        if (!isAuthorized()) return null
        val chat = ask(TdApi.SearchPublicChat(normalizedUsername.removePrefix("@"))) as? TdApi.Chat
            ?: return null
        if (chat.type !is TdApi.ChatTypePrivate) return null
        return chat.id
    }

    override fun sendPhoto(chatId: Long, file: File, caption: String, requestId: String): PersonalDeliveryResult {
        if (!isAuthorized()) return PersonalDeliveryResult.NeedsLogin
        if (!file.isFile || file.length() <= 0L) return PersonalDeliveryResult.Failed("Missing photo")
        // TDLib's initial Message result can still be pending: do not label it delivered.
        val photo = TdApi.InputMessagePhoto().apply {
            this.photo = TdApi.InputFileLocal(file.absolutePath)
            this.caption = TdApi.FormattedText(caption.take(1024), emptyArray())
        }
        val request = TdApi.SendMessage().apply {
            this.chatId = chatId
            this.inputMessageContent = photo
        }
        return when (val result = ask(request, 30)) {
            is TdApi.Error -> PersonalDeliveryResult.Failed("Telegram: ${result.code} ${result.message}")
            is TdApi.Message -> {
                val messageId = result.id
                if (delivered.remove(messageId)) return PersonalDeliveryResult.Confirmed
                rejected.remove(messageId)?.let { return PersonalDeliveryResult.Failed(it) }
                val latch = CountDownLatch(1)
                deliveryWaiters[messageId] = latch
                try {
                    if (delivered.remove(messageId)) return PersonalDeliveryResult.Confirmed
                    rejected.remove(messageId)?.let { return PersonalDeliveryResult.Failed(it) }
                    latch.await(25, TimeUnit.SECONDS)
                    if (delivered.remove(messageId)) PersonalDeliveryResult.Confirmed
                    else rejected.remove(messageId)?.let { PersonalDeliveryResult.Failed(it) }
                        ?: PersonalDeliveryResult.Uncertain("Awaiting Telegram delivery acknowledgment")
                } finally { deliveryWaiters.remove(messageId) }
            }
            else -> PersonalDeliveryResult.Uncertain("Telegram response not received")
        }
    }
}
