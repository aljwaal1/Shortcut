package com.explapp.shortcut.automation.routines

import android.content.Context
import android.os.Build
import android.os.SystemClock
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
    @Volatile private var resendAvailableAtMs: Long = 0L
    private val delivered = ConcurrentHashMap.newKeySet<Long>()
    private val deliveryWaiters = ConcurrentHashMap<Long, CountDownLatch>()
    private val rejected = ConcurrentHashMap<Long, String>()

    fun status(): String = authState?.javaClass?.simpleName ?: "Not connected"

    /** Initialize only from an explicitly opted-in, Keystore-encrypted session. */
    fun reconnect(context: Context): Boolean {
        if (client != null) return true
        val (id, hash) = TelegramPersonalCredentialVault.load(context) ?: return false
        return open(context, id, hash)
    }
    fun lastError(): String? = sendError

    /** Respect Telegram's server-specified resend timeout, not a UI-only timer. */
    fun codeResendRemainingSeconds(): Int {
        val state = authState
        if (state !is TdApi.AuthorizationStateWaitCode && state !is TdApi.AuthorizationStateWaitEmailCode) return 0
        val remaining = resendAvailableAtMs - SystemClock.elapsedRealtime()
        return if (remaining <= 0L) 0 else ((remaining + 999L) / 1000L).toInt()
    }

    fun canResendCode(): Boolean = when (val state = authState) {
        is TdApi.AuthorizationStateWaitCode -> state.codeInfo.nextType != null && codeResendRemainingSeconds() == 0
        is TdApi.AuthorizationStateWaitEmailCode -> codeResendRemainingSeconds() == 0
        else -> false
    }

    fun nextCodeDelivery(): String? = (authState as? TdApi.AuthorizationStateWaitCode)
        ?.codeInfo?.nextType?.javaClass?.simpleName?.removePrefix("AuthenticationCodeType")

    fun currentCodeDelivery(): String? = (authState as? TdApi.AuthorizationStateWaitCode)
        ?.codeInfo?.type?.javaClass?.simpleName?.removePrefix("AuthenticationCodeType")

    /** Must be called off the main thread; confirms the TDLib server response. */
    fun resendAuthenticationCode(): Boolean {
        if (!canResendCode()) {
            sendError = "Telegram does not allow resending yet, or no alternative method is available."
            return false
        }
        // Block repeat taps while the server handles the request.
        resendAvailableAtMs = SystemClock.elapsedRealtime() + 10_000L
        return when (val response = ask(TdApi.ResendAuthenticationCode(), 25)) {
            is TdApi.Ok -> {
                sendError = null
                true
            }
            is TdApi.Error -> {
                sendError = "Telegram resend: ${response.code} ${response.message}"
                false
            }
            else -> {
                sendError = "Telegram did not confirm the resend request. Check your active Telegram devices."
                false
            }
        }
    }

    @Synchronized
    fun open(context: Context, apiId: Int, apiHash: String): Boolean {
        if (apiId <= 0 || apiHash.isBlank()) return false
        if (client != null) return true
        appContext = context.applicationContext
        currentApiId = apiId
        currentApiHash = apiHash
        return runCatching {
            // org.drinkless.tdlib.Client loads its paired tdjni JNI library.
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
            TelegramPersonalCredentialVault.save(context.applicationContext, apiId, apiHash)
            true
        }.getOrElse {
            sendError = "TDLib initialization failed: ${it.javaClass.simpleName}"
            false
        }
    }

    private fun onAuthorizationUpdate(state: TdApi.AuthorizationState) {
        authState = state
        if (state is TdApi.AuthorizationStateWaitCode) {
            resendAvailableAtMs = SystemClock.elapsedRealtime() + state.codeInfo.timeout.coerceAtLeast(0) * 1000L
        } else if (state is TdApi.AuthorizationStateWaitEmailCode) {
            resendAvailableAtMs = SystemClock.elapsedRealtime()
        }
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
    fun logout(): Boolean {
        appContext?.let { TelegramPersonalCredentialVault.clear(it) }
        return submit(TdApi.LogOut())
    }

    private fun submit(request: TdApi.Function<*>): Boolean {
        val c = client ?: return false
        c.send(request) { result ->
            if (result is TdApi.Error) sendError = "Telegram: ${result.code} ${result.message}"
            else sendError = null
        }
        return true
    }

    override fun isAuthorized() = authState is TdApi.AuthorizationStateReady

    /** Allow an encrypted, previously authorized session time to reconnect on a worker thread. */
    fun waitForAuthorization(timeoutMs: Long = 12_000L): Boolean {
        val deadline = android.os.SystemClock.elapsedRealtime() + timeoutMs
        while (!isAuthorized() && android.os.SystemClock.elapsedRealtime() < deadline) {
            val state = authState
            if (state is TdApi.AuthorizationStateWaitPhoneNumber ||
                state is TdApi.AuthorizationStateWaitCode ||
                state is TdApi.AuthorizationStateWaitPassword ||
                state is TdApi.AuthorizationStateWaitEmailAddress ||
                state is TdApi.AuthorizationStateWaitEmailCode) return false
            Thread.sleep(200)
        }
        return isAuthorized()
    }

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
        // Send screenshot as a Telegram image file. This TDLib build expects InputPhoto
        // rather than InputFile for InputMessagePhoto, so use the supported document
        // transport to avoid an invalid photo object or a falsely successful send.
        val photo = TdApi.InputMessageDocument().apply {
            this.document = TdApi.InputDocument().apply {
                this.document = TdApi.InputFileLocal(file.absolutePath)
                this.disableContentTypeDetection = false
            }
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
