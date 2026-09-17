# Shortcut Messaging + Automation Core Design

## Scope

This specification covers the first implementation package after Shortcut v0.4.2 on branch `feature/android-v1-phase1`.

The package focuses on two goals:

1. Make scheduled messaging behavior accurate, reliable, and honest on modern Android.
2. Upgrade scheduled tasks from create/delete-only flows into editable, pausable, testable automations without duplicating stored or scheduled tasks.

This package does **not** add Accessibility-based automation, WebView, cloud accounts, or generic multi-step routines yet.

## Global constraints

- Native Android only: Kotlin + Jetpack Compose + Material 3.
- No WebView.
- Local-first / offline-first for all core app data.
- No app account or cloud dependency for the normal Shortcut experience.
- Arabic and English, including RTL/LTR.
- Permissions requested only when the user invokes a feature that needs them.
- No Accessibility Service in the normal release path.
- `minSdk = 26`.
- `targetSdk = 36`.
- Existing media tools, App Usage Time behavior, and the two existing usage charts must remain unchanged.
- Existing execution history remains capped at 200 entries unless a migration requires otherwise.
- A successful build is not sufficient evidence of correct runtime behavior; scheduling and messaging flows require behavior-level tests and device verification.

## Current behavior and root cause

The current scheduled-message flow is:

`AndroidMessageScheduler` -> `AlarmManager` -> `ScheduledMessageReceiver` -> deep link -> external messaging app.

`ScheduledMessageReceiver` currently builds an `ACTION_VIEW` deep link and attempts to launch it from a `BroadcastReceiver`. The deep-link factory builds:

- WhatsApp: `https://wa.me/<number>?text=<encoded text>`
- Telegram: `https://t.me/<username>?text=<encoded text>`

The existing `MessageCapabilities` model already declares both platforms as:

- prepared-message capable
- not auto-send capable

Therefore the present implementation does not automatically send either personal WhatsApp or personal Telegram messages. It prepares content and relies on the external application and user interaction.

A second reliability concern is background activity launch restrictions on modern Android. Launching another app directly from a broadcast receiver is not a dependable user-facing delivery mechanism. The default safe path should use a notification that the user can tap.

## Messaging product model

Shortcut will distinguish three concepts explicitly.

### 1. Personal WhatsApp prepared message

Supported behavior:

- schedule locally
- show a notification at the scheduled time
- tapping the notification opens WhatsApp with the intended chat/message prepared when possible
- the app must never claim the message was sent automatically

Execution history terminology:

- `Prepared` when the notification/deep-link handoff is created successfully
- `Failed` when the app cannot build or present the handoff
- never `Sent` unless Shortcut has a verifiable delivery API response

### 2. Personal Telegram prepared message

Same baseline behavior as WhatsApp:

- schedule locally
- notify at the scheduled time
- tap to open Telegram with prepared content when supported
- never claim automatic send

### 3. Telegram Auto Send

This is an optional advanced mode and must be isolated from the normal local-first messaging path.

The design allows two official Telegram mechanisms, but they must not be conflated:

#### Bot API mode

- real automatic sending
- message is sent by a Telegram bot, not by the user's personal Telegram account
- requires network access
- requires bot token and chat identifier
- credentials must be stored locally using Android secure storage
- the UI must clearly identify this mode as bot-based

#### TDLib / MTProto personal-account mode

This is a feasibility-gated feature, not a mandatory implementation in this package.

Before production integration, implementation must prove all of the following:

- supported current Android integration path
- acceptable binary size and ABI impact
- safe local credential/session handling
- login/OTP/2FA state handling
- Google Play compatibility
- clear privacy disclosure
- reliable scheduled-send behavior under Android background limits
- no requirement for Accessibility Service

If any of these conditions is not satisfied, TDLib remains disabled and Telegram personal messages stay in prepared-message mode.

No unofficial UI automation, private intents, simulated button presses, or reverse-engineered send hooks may be used.

## Scheduled-task identity

The current request-code generation depends on mutable task fields such as name, time, recipient, or package name. That makes editing fragile because changing those fields can prevent cancellation of the old `PendingIntent`.

Both `ScheduledAppShortcut` and `ScheduledMessage` must gain a stable immutable identifier.

Proposed field:

```kotlin
val id: String
```

The ID is generated once when the task is created and preserved through edits, duplication creates a new ID, and scheduler request codes are derived from this stable ID.

This stable identity is the foundation for correct edit/cancel/replace semantics.

## Domain model changes

### ScheduledAppShortcut

Add:

- stable `id`
- `isEnabled`
- created/updated metadata only if required by the current storage design

Required behavior:

- editing preserves `id`
- duplicating creates a new `id`
- disabling cancels the current alarm but does not delete the item
- re-enabling schedules the next valid run

### ScheduledMessage

Add the same identity and enabled-state behavior.

The model may also gain an explicit delivery mode enum so UI and history are unambiguous.

Proposed concept:

```kotlin
enum class MessageDeliveryMode {
    PREPARED,
    TELEGRAM_BOT_AUTO,
    TELEGRAM_PERSONAL_AUTO,
}
```

`TELEGRAM_PERSONAL_AUTO` must remain unavailable unless the TDLib feasibility gate passes.

## Edit behavior

Every scheduled app shortcut and scheduled message receives an Edit action.

Opening Edit must reuse the creation screen with all current values prefilled.

Saving an edit must execute this exact semantic sequence:

1. load the original entity by stable ID
2. cancel the currently scheduled alarm using the stable ID
3. replace the stored entity with the edited entity while preserving the same ID
4. schedule the edited entity if it remains enabled
5. ensure only one stored item exists with that ID
6. ensure only one matching scheduled alarm exists

The UI must not append a second entity during editing.

## Pause / Resume

Each scheduled task has a visible enabled switch or equivalent action.

Pause:

- keep task in local storage
- cancel its alarm
- mark `isEnabled = false`
- show that it is paused

Resume:

- mark enabled
- compute the next future run
- schedule exactly one alarm

## Run now

Each task gets a `Run now` action.

For app launches:

- execute the same launch path used by scheduled execution, subject to Android runtime restrictions

For prepared messages:

- create the same user-visible prepared-message handoff immediately

For Telegram bot auto-send, if implemented:

- perform a real send and record the verified API result

`Run now` does not alter the next scheduled occurrence unless the task's repeat semantics explicitly require it.

## Duplicate

Duplicate creates a new entity with:

- new stable ID
- copied user-configurable fields
- optionally a derived display name such as "Copy of ..." / localized equivalent
- no reuse of the source alarm identity

The duplicate must be independently editable and cancellable.

## Next run

The task list and task detail view should display the next scheduled run for enabled tasks.

Requirements:

- derived using the existing `NextRunCalculator`
- never display a past time
- paused tasks display a paused state instead of a next-run timestamp
- one-time tasks that have completed are removed or marked completed according to the existing product behavior

## Execution history semantics

Execution history should distinguish outcomes more precisely.

Minimum statuses:

- `SUCCESS` for actions Shortcut can verify as executed
- `PREPARED` for handoff-based personal WhatsApp/Telegram messages
- `FAILED` for an execution failure

If the current enum cannot support this without a migration, the UI wording must at minimum avoid calling a prepared message "sent" or "successfully sent".

The history entry should preserve:

- task name
- task type
- timestamp
- duration
- result
- failure reason when relevant

## Notification-first messaging execution

For scheduled personal WhatsApp and Telegram messages, the default scheduled execution should no longer rely on forcing an external activity to the foreground from the receiver.

Preferred flow:

`AlarmManager` -> `ScheduledMessageReceiver` -> notification -> user tap -> prepared-message deep link

The notification should contain localized text such as:

- English: `Message ready — tap to open WhatsApp`
- Arabic: `الرسالة جاهزة — اضغط لفتح واتساب`

or the Telegram equivalent.

If notification permission is required on the current Android version, it is requested only when the user first enables a feature that relies on scheduled message notifications.

## Restore after reboot

`RestoreSchedulesReceiver` must restore only enabled tasks.

Restoration must not create duplicates. Stable IDs are used to reconstruct the same alarm identity.

## Storage and migration

Existing v0.4.2 users may already have stored `ScheduledAppShortcut` and `ScheduledMessage` records without stable IDs or enabled flags.

The codecs/stores must support migration:

- legacy item without ID -> assign a new stable ID once during decode/migration
- legacy item without enabled flag -> default to enabled
- preserve existing names, schedule, repeat settings, recipients, text, and package names
- write back in the new format after successful load/update when practical

Migration must be covered by unit tests.

## UI changes

Task cards and/or details must support:

- Edit
- Pause / Resume
- Run now
- Duplicate
- Delete
- Next run

The UI should remain compact. Destructive actions should not visually dominate the card.

Create and Edit should share the same builder UI and validation logic rather than diverging into separate screens.

## Telegram feasibility spike

Before TDLib integration, perform a focused feasibility investigation against current official Telegram and Android documentation.

The spike output must record:

- official dependency/integration method
- minimum Android/API requirements
- supported ABIs
- estimated binary-size impact
- authentication lifecycle
- local session-storage requirements
- background execution considerations
- whether scheduled sending can be completed reliably without UI automation
- Google Play/privacy implications

The spike must end with one of three explicit conclusions:

1. `GO` — safe to implement personal Telegram auto-send via TDLib
2. `BOT_ONLY` — only Telegram Bot API auto-send should ship
3. `PREPARED_ONLY` — keep personal Telegram as prepared-message handoff

No production TDLib code is added until the spike conclusion is `GO`.

## Testing strategy

All feature work follows TDD.

Required unit tests include at minimum:

- editing preserves stable task ID
- replacing an edited task leaves exactly one stored item
- scheduler cancel uses stable identity rather than mutable fields
- editing then saving cancels old schedule and creates only one new schedule
- duplicate creates a new ID
- pause cancels schedule but retains item
- resume creates one schedule
- reboot restore ignores paused tasks
- legacy codec migration creates stable IDs
- prepared WhatsApp result is not reported as sent
- prepared Telegram result is not reported as sent
- next-run display logic returns a future occurrence

Where Android framework behavior cannot be unit-tested directly, use instrumented tests or small boundary abstractions so scheduler interactions can be asserted deterministically.

## Device verification

Before calling the package complete, verify on at least:

- one Android 13+ device/emulator
- one Android 15/16-compatible environment if available

Manual verification scenarios:

- create a scheduled app launch
- edit its time/name and verify only the new schedule fires
- pause and confirm it does not fire
- resume and confirm it fires once
- duplicate and verify both tasks behave independently
- schedule WhatsApp prepared message and verify notification + tap flow
- schedule Telegram prepared message and verify notification + tap flow
- reboot and verify enabled schedules restore once

## Out of scope for this package

These approved ideas remain planned for later packages:

- generic multi-step automation engine
- new triggers such as Wi-Fi/Bluetooth/NFC/device-unlock/app-open
- widgets
- Quick Settings tiles
- command palette
- expanded templates
- backup/restore improvements beyond any migration required here
- batch improvements to image tools
- broader home-screen redesign

They should be implemented only after the Messaging + Automation Core package is stable.
