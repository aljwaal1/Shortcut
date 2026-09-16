# Shortcut for Android — Product & Architecture Design

Date: 2026-09-16
Repository: aljwaal1/Shortcut
Status: Approved direction, implementation pending plan

## 1. Product goal

Build a native Android automation app inspired by the ease and breadth of Apple Shortcuts, while respecting Android platform limitations and Google Play policies. The app must be useful to ordinary users through simple templates and an Easy Builder, while also offering an Advanced Mode for deterministic user-defined automation.

Core principles:
- Native Android app, not WebView.
- No account and no mandatory cloud backend.
- Local-first storage and execution.
- Arabic and English from day one, with automatic device-language detection and manual override.
- RTL support for Arabic, LTR for English.
- Simple onboarding and permissions education.
- Large catalog of triggers, conditions, actions, and ready-made templates.
- Rule-based automation created explicitly by the user.
- Free app with non-intrusive ads.

## 2. Recommended implementation approach

Use a modular Android architecture in Kotlin with Jetpack Compose and Material 3.

The automation model is:

Trigger -> optional Conditions -> ordered Actions

Each automation is stored locally and can be enabled, disabled, duplicated, exported, imported, tested, and inspected through an execution log.

Two execution modes:

### Standard mode
Uses normal Android APIs and permissions. This is the default mode and should stay as compatible with Google Play policy as possible.

### Advanced mode
Optional, explicitly enabled by the user. It may use additional capabilities such as AccessibilityService only for deterministic, user-defined rules and after prominent disclosure/consent. The app must never autonomously invent, plan, or execute actions outside the user's configured workflow.

## 3. Main user experience

### First-run onboarding
The first installation opens a short visual walkthrough:
1. What the app can automate.
2. Scheduled app launching.
3. Scheduled WhatsApp and Telegram workflows.
4. Device and connectivity automation.
5. Standard vs Advanced Mode.
6. Privacy: automations are stored locally and no account is required.
7. Permissions center: explain each permission before opening Android settings.

The user can skip onboarding and reopen it later from Settings.

### Home screen
The home screen should stay simple despite the app's power:
- My Shortcuts
- Scheduled Automations
- Ready Templates
- Recent Activity
- Large + Create button

Create offers:
- Easy Builder
- Advanced Builder

## 4. Easy Builder

A guided three-part flow:
1. When?
2. What should happen?
3. Repeat?

The builder should use plain language and hide technical details.

Examples:
- At 07:30 -> open Google Maps -> every weekday.
- At 20:00 -> prepare/send a WhatsApp message -> every day.
- When phone becomes available after boot/unlock -> prepare Wi-Fi connectivity -> open Maps.

## 5. Advanced Builder

A block-based ordered workflow editor supporting:
- Trigger
- Conditions
- Actions
- If / Else
- AND / OR
- Wait / Delay
- Repeat / Loop
- Run another shortcut
- Stop shortcut
- Variables
- Results from previous actions
- Error handling behavior

Each block must be independently testable and display required permissions.

## 6. Ready-made templates

Templates are a first-class part of the product. Each template must support one-time execution or repeat rules where meaningful.

Initial templates:

### Scheduled app launch
- Select any installed app.
- Choose date/time.
- One time or repeat.
- Repeat options: daily, selected weekdays, weekly, monthly, or custom interval where Android scheduling permits.
- Optional fallback notification with Open now if Android blocks a background activity launch.

### Scheduled WhatsApp message
Standard mode:
- Choose recipient/contact where supported.
- Compose message.
- Choose date/time and repeat.
- Open the target WhatsApp flow with the message prepared when direct background sending is not permitted.

Advanced mode:
- Additional deterministic automation may be enabled only with explicit user consent and only where technically and policy compliant.

### Scheduled Telegram message
- Choose date/time and repetition.
- Support app-opening/deep-link preparation.
- Where the user explicitly configures a bot/API integration, allow a separate supported path for sending through that integration.

### Scheduled device shutdown
Because ordinary third-party Android apps cannot reliably power off a normal consumer device through public APIs, this template should expose capability-dependent behavior:
- Standard devices: remind the user or open the appropriate system flow where possible.
- Advanced/managed/root-capable environments: expose stronger options only when capability is detected and clearly explained.

Never pretend full shutdown is available when the OS blocks it.

### On boot / unlock automation
Trigger choices:
- Boot completed.
- User unlocked after boot.
- User becomes present after keyguard dismissal, where supported.

Example template:
- On boot/unlock -> prepare Wi-Fi connectivity -> inspect available/known networks within Android limits -> connect or prompt using supported system APIs -> open Maps.

Every step must be independently optional. The user may instead configure:
- On unlock -> open Maps only.
- On unlock -> run another shortcut.
- On boot -> show reminder.

### Additional initial templates
- Car mode.
- Work mode.
- Sleep mode.
- Battery reaches 80%.
- Connect Bluetooth -> open Spotify.
- Connect to specific Wi-Fi -> run shortcut.
- Open several apps in sequence.
- Custom reminder/notification.
- One-tap home-screen shortcut.

## 7. Trigger catalog

Initial trigger families:
- Date/time.
- Scheduled repetition.
- Boot completed.
- User unlocked / present.
- Battery level.
- Charging connected/disconnected.
- Wi-Fi state/network availability within OS limits.
- Bluetooth device connected/disconnected.
- Notification received from selected apps, where Notification Access is granted.
- App-related events where Android APIs and permissions allow reliable detection.
- Home-screen widget/shortcut button.
- NFC later.
- Location/geofence later with explicit permission.

## 8. Action catalog

Initial action families:

### Apps
- Open app.
- Open URI/deep link.
- Open multiple apps in sequence.
- Check whether app is installed.
- Launch another shortcut.

### Messaging and sharing
- WhatsApp workflow.
- Telegram workflow.
- SMS compose/send where Android permissions and policy permit.
- Share text/file.
- Copy text.

### Device and connectivity
- Wi-Fi-related supported actions.
- Bluetooth-related supported actions.
- Volume control.
- Ringer/vibration actions where permitted.
- Brightness where permitted.
- Do Not Disturb with policy access.
- Screen-orientation related action where supported.

### Notifications and user prompts
- Show notification.
- Show confirmation.
- Ask for text input.
- Present a choice list.

### Web and data
- Open URL.
- HTTP GET/POST.
- Headers/body.
- Parse JSON.
- Extract values.
- Webhook.

### Files and text
- Create/read text file within app-accessible or user-selected storage.
- Copy/move/rename where Storage Access Framework permits.
- Text concatenate/replace/split.
- Regex for advanced users.

### Logic
- If/Else.
- AND/OR.
- Delay.
- Repeat.
- Variables.
- Stop shortcut.

## 9. Wi-Fi behavior and platform limits

Wi-Fi scanning and direct network control are heavily restricted on modern Android versions. The app should therefore:
- Request only necessary permissions with clear disclosure.
- Use supported Wi-Fi APIs for the running Android version.
- Respect scan throttling.
- Prefer Android system network suggestion/request flows when required.
- Never claim that the app can silently enable, scan, and connect in all Android versions if the platform disallows it.
- Provide a graceful prompt-based fallback.

## 10. Scheduled execution and background restrictions

Use Android scheduling primitives according to the required precision:
- WorkManager for deferrable/background work.
- AlarmManager for user-requested precise schedules when appropriate.
- Exact-alarm special access only for features that genuinely need exact timing.

Android may restrict launching another app directly from the background. Therefore Scheduled App Launch must have a two-path strategy:
1. Direct launch when the OS permits the context.
2. Immediate high-priority user-facing notification with Open now when direct launch is blocked.

Do not rely on unsupported hacks as the standard execution path.

## 11. Accessibility / Advanced Mode policy design

AccessibilityService is never enabled silently.

Before enabling Advanced Mode:
- Show prominent disclosure.
- Explain exactly what the service can observe/control.
- Obtain explicit consent.
- Open the Android Accessibility settings for the user to enable it.

Automation must remain deterministic and driven by static human-defined rules such as: If trigger X occurs, perform action Y.

Advanced mode must not independently decide whom to message, what to click, or what automation to create.

## 12. Local data model

All core data stays on-device.

Entities:
- Automation
- Trigger
- Condition
- Action
- Schedule
- Template instance
- Execution log
- Permission/capability state
- App settings

Recommended local persistence:
- Room database for structured automation data and logs.
- DataStore for lightweight settings.

No account is required.

## 13. Backup and portability

Users can:
- Export one shortcut.
- Export all shortcuts.
- Import from a local file.

Export should use a versioned JSON-based format so future app versions can migrate older backups safely.

## 14. Languages

Version 1:
- Arabic.
- English.

Behavior:
- Detect device/app locale on first run.
- Arabic uses full RTL layout.
- English uses LTR.
- Language can be changed manually in Settings without creating a new account or deleting automations.

String resources must be externalized from the start so additional languages can be added later.

## 15. Ads

The app is free and ad-supported.

### Banner
Allowed in low-interruption areas such as:
- Home.
- Templates.
- Execution history.

Do not place banner ads where they obstruct the workflow builder or permission education.

### Interstitial
Use the agreed Contextual model:
- Only after a logical completion point, such as saving an automation or leaving a template flow.
- Never at app startup.
- Never while granting permissions.
- Never during an automation execution.
- Never during a sensitive messaging action.
- Enforce cooldown/frequency limits so repeated navigation does not create repeated interstitials.

Ad delivery logic should be isolated behind an AdsManager abstraction so placement and frequency can be changed centrally.

## 16. Settings and support

Settings should include:
- Language.
- Theme: system/light/dark.
- Standard/Advanced Mode status.
- Permissions Center.
- Backup/export/import.
- Reopen onboarding.
- Execution history and log retention.
- Contact us.
- Send feedback/suggestion.
- Report a problem.
- Privacy policy.
- About app.
- App version.

## 17. Permissions Center

Create a dedicated screen showing each optional capability as:
- Available / unavailable.
- Permission granted / not granted.
- Why it is needed.
- Which templates/actions use it.
- Button to open the appropriate Android permission/settings screen.

Examples:
- Notifications.
- Exact alarm access.
- Notification access.
- Accessibility advanced mode.
- Location/Wi-Fi permissions where needed.
- Do Not Disturb policy access.

No permission should be requested before the user understands the feature that needs it.

## 18. Execution history and diagnostics

Every automation run records:
- Automation name/id.
- Start time.
- Trigger.
- Actions attempted.
- Success/failure/skipped state.
- Failure reason.
- Duration.

Users should be able to see exactly which step failed and why.

Sensitive content such as message text, credentials, API headers, or tokens should be redacted from logs by default.

## 19. Search and discovery

Because the action catalog will grow, both builders must provide command search.

Examples:
- Search 'WhatsApp'.
- Search 'Bluetooth'.
- Search 'open app'.

Results should include the action name, category, short explanation, and required permissions.

## 20. Architecture boundaries

Suggested modules/packages:
- app
- core:model
- core:data
- core:database
- core:designsystem
- core:localization
- core:permissions
- automation:engine
- automation:scheduler
- automation:triggers
- automation:actions
- automation:advanced
- feature:onboarding
- feature:home
- feature:builder-easy
- feature:builder-advanced
- feature:templates
- feature:history
- feature:settings
- feature:support
- feature:backup
- monetization:ads

Each trigger/action implements a stable interface so new capabilities can be added without rewriting the engine.

## 21. Error handling

Every action returns a structured result:
- Success.
- Failed with reason.
- Permission required.
- Unsupported on this Android version/device.
- User interaction required.
- Skipped.

The engine should support configurable behavior for failures:
- Stop workflow.
- Continue to next action.
- Notify user.

## 22. Security and privacy

- Local-first data.
- No mandatory account.
- No silent accessibility enablement.
- No hidden automation.
- Redact sensitive log data.
- API secrets stored using Android security facilities where applicable.
- Imported shortcut files must be validated before activation.
- Potentially sensitive imported automations should open disabled until the user reviews permissions/actions.

## 23. Testing strategy

Testing must include:
- Unit tests for automation engine logic.
- Unit tests for scheduling/repeat-rule calculations.
- Room migration tests.
- Serialization/import-export tests.
- Instrumented tests for permissions and Android integration paths where feasible.
- UI tests for Easy Builder and onboarding.
- Tests on multiple Android API levels, especially around background activity launch, exact alarms, boot behavior, Wi-Fi APIs, and notification/accessibility flows.

## 24. Initial delivery order

Implementation should establish the engine before breadth:
1. Native project foundation, localization, navigation, design system.
2. Local data model and Room/DataStore.
3. Automation engine interfaces.
4. Scheduler and boot rescheduling.
5. Easy Builder.
6. Scheduled App Launch end-to-end.
7. Templates system.
8. WhatsApp/Telegram preparation flows.
9. Boot/unlock + Maps template.
10. Wi-Fi capability layer and fallbacks.
11. Advanced Mode infrastructure and disclosures.
12. History/diagnostics.
13. Backup/import/export.
14. Ads integration with contextual interstitial policy.
15. Support/settings/polish.

## 25. Explicit non-goals for the first implementation pass

To avoid building unreliable or policy-risky behavior before the core engine is stable, the first pass should not promise:
- Universal silent shutdown of normal Android phones.
- Universal silent enabling/connecting of Wi-Fi across all Android versions.
- Universal background opening of arbitrary apps when Android blocks it.
- Fully autonomous Accessibility workflows that are not directly defined by the user.

Where Android restricts a capability, the app must expose the limitation honestly and provide the best supported fallback.
