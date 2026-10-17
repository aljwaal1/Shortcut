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
