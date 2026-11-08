# Update — 2026-10-22: explicit personal-account delivery request

The user explicitly opted in to person-to-person automatic screenshot sending by Telegram username.
The previous PREPARED_ONLY decision remains true of currently shipped Android builds, but is no
longer the intended final feature set. **No APK may claim personal delivery works until an
authenticated TDLib user session and recipient-resolution/photo-send backend are linked and
verified with an actual private Telegram chat.**

Implementation prerequisites:
1. Pin and audit the official TDLib Java/JNI distribution and Android ABI binaries (do not
   silently import unknown native code); ensure API version and reproducible build checks.
2. Implement TDLib authorization states, including phone, code, password, and logout; require
   the user's own Telegram app credentials/api_id and api_hash from a trustworthy source.
3. Encrypt on-device TDLib storage/session, never place authorization secrets in RoutineCodec,
   logs, cloud sync, or exported backups.
4. Resolve `@username` to a verified private chat. Confirm username and recipient before
   activating each scheduled automation.
5. Distinguish queued, accepted, confirmed, failed, and unknown delivery. Never blindly retry
   UNKNOWN, which can duplicate a personal message.
6. Explicit disclosure and informed opt-in for unattended sends, revocable by disabling
   the routine or logging out. Do not automate taps or abuse accessibility permissions.
7. E2E test with a real account and another consenting recipient on Android 8–16.
8. Preserve the existing bot transport as a separate explicit choice.

Existing steps: TelegramPersonalDeliveryPolicy and TelegramPersonalDeliveryCoordinator with
unit tests. **Missing:** JNI artifacts, TDLib client adapter, authorization UI, connection
to screenshot completion pipeline, persistent deduplication, device E2E verification.

---

# Telegram Auto-Send Feasibility — 2026-09-17

## Decision: PREPARED_ONLY for personal Telegram; BOT_ONLY for verifiable auto-send

Shortcut will keep personal Telegram messages in prepared-message mode in the normal Google Play build. A separate Telegram Bot API mode may be added for real automatic sends that are explicitly identified as bot messages.

## Evidence

- Telegram documents TDLib as a fully functional cross-platform Telegram client with Android support and Java/JNI bindings.
- TDLib handles networking, encryption and local storage; local data is encrypted with a user-provided key.
- A third-party Telegram client requires its own `api_id`/`api_hash` and full user authorization lifecycle.
- Telegram API Terms require user knowledge and consent for actions performed on the user's behalf and require third-party clients to disclose Telegram API use.
- Google Play treats authentication information as personal/sensitive user data and requires transparent disclosure, secure handling, and consent where appropriate.

## Android integration impact

TDLib is a native C++ library exposed to Java through JNI. Shipping it would add native binaries/ABI management, authentication state handling, encrypted session storage, network/background lifecycle management and a materially larger maintenance/security surface than the current local-first Shortcut app.

## Background sending

Although TDLib can technically submit `sendMessage` after authorization, a scheduler that sends personal messages silently would change Shortcut from a local utility into a Telegram client acting on a user's account. That requires a dedicated consent UX, privacy disclosures, session lifecycle, logout/delete-data controls, and extensive device/background verification.

This is not justified for the standard release while the same product goal can be served safely by notification + prepared-message handoff.

## Bot API

Telegram Bot API remains a viable future advanced mode because successful API responses can be verified. The UI must clearly state that the message is sent by the bot, not by the user's personal account. Bot tokens must be stored securely and excluded from portable backups by default.

## Forbidden implementation paths

- Accessibility-based Send-button automation
- reverse-engineered/private Telegram intents
- simulated taps
- hidden personal-account sends without explicit user knowledge/consent

## Revisit conditions

Revisit TDLib personal auto-send only if Shortcut intentionally adds an advanced Telegram-client module with explicit opt-in, authentication/session management, privacy policy coverage, secure storage, logout/delete-session controls, ABI/build support, and device verification across supported Android versions.
