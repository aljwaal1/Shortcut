# Shortcut Android V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first production-shaped native Android version of Shortcut with local-first automation, Arabic/English UI, scheduled app launch, WhatsApp/Telegram preparation flows, boot/unlock triggers, templates, history, backup, permissions education, and non-intrusive ads.

**Architecture:** Kotlin + Jetpack Compose + Material 3, with a modular Gradle project. Automation is represented as `Trigger -> Conditions -> ordered Actions`; the engine is platform-agnostic where possible, while Android integrations live behind capability interfaces. Room stores automations/logs, DataStore stores preferences, AlarmManager/WorkManager schedule work, and Android-specific receivers/services bridge system events into the engine.

**Tech Stack:** Kotlin, Android Gradle Plugin, Jetpack Compose, Material 3, Navigation Compose, Room, DataStore, WorkManager, AlarmManager, kotlinx.serialization, Hilt, JUnit, AndroidX Test, Compose UI tests, Google Mobile Ads behind an abstraction.

**Spec:** `docs/superpowers/specs/2026-09-16-shortcut-android-design.md`

## Global Constraints

- Native Android app, not WebView.
- No account and no mandatory cloud backend.
- Local-first storage and execution.
- Arabic and English from day one, with automatic device-language detection and manual override.
- RTL support for Arabic, LTR for English.
- Standard Mode is default; Advanced Mode is optional and explicitly enabled.
- AccessibilityService must never be enabled silently and may only execute deterministic user-defined rules.
- Free app with Banner + contextual Interstitial ads; never show interstitials at startup, during permissions, during automation execution, or during sensitive messaging actions.
- Imported automations are validated and potentially sensitive imports remain disabled until reviewed.
- Never claim unsupported Android behavior; provide user-facing fallback when the OS blocks a capability.

---

## File Structure

Create these primary modules/packages:

- `app/` — application entry point, navigation, DI wiring.
- `core/model/` — pure Kotlin automation models and result types.
- `core/database/` — Room entities/DAO/database.
- `core/data/` — repositories and mappers.
- `core/designsystem/` — theme and reusable UI.
- `core/localization/` — locale preferences/helpers.
- `core/permissions/` — capability/permission inspection.
- `automation/engine/` — workflow executor.
- `automation/scheduler/` — AlarmManager/WorkManager scheduling and rescheduling.
- `automation/triggers/` — boot/unlock/time trigger adapters.
- `automation/actions/` — app launch, notification, deep-link, WhatsApp, Telegram, Maps actions.
- `automation/advanced/` — Advanced Mode disclosure/state and future Accessibility bridge.
- `feature/onboarding/` — first-run walkthrough.
- `feature/home/` — dashboard and automation list.
- `feature/builder-easy/` — three-step builder.
- `feature/templates/` — ready templates.
- `feature/history/` — execution log UI.
- `feature/settings/` — language/theme/permissions/advanced settings.
- `feature/support/` — contact/feedback/report/problem/about/privacy entry points.
- `feature/backup/` — JSON export/import.
- `monetization/ads/` — AdsManager abstraction and placement policy.

---

### Task 1: Bootstrap the Native Android Project

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/explapp/shortcut/ShortcutApplication.kt`
- Create: `app/src/main/java/com/explapp/shortcut/MainActivity.kt`
- Create: `app/src/androidTest/java/com/explapp/shortcut/SmokeTest.kt`

**Interfaces:**
- Produces: launchable Compose app and shared version catalog used by all later modules.

- [ ] **Step 1: Write a smoke test that expects the app package to launch**

```kotlin
@RunWith(AndroidJUnit4::class)
class SmokeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun appLaunches() {
        composeRule.onNodeWithTag("shortcut_root").assertExists()
    }
}
```

- [ ] **Step 2: Run the test and verify failure before the root UI exists**

Run: `./gradlew :app:connectedDebugAndroidTest`

Expected: FAIL because `MainActivity`/`shortcut_root` is not implemented.

- [ ] **Step 3: Implement the minimal Compose application shell**

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShortcutTheme {
                Box(Modifier.fillMaxSize().testTag("shortcut_root"))
            }
        }
    }
}
```

- [ ] **Step 4: Add Hilt application class and manifest wiring**

```kotlin
@HiltAndroidApp
class ShortcutApplication : Application()
```

Manifest must declare `.ShortcutApplication` and `.MainActivity` as the launcher.

- [ ] **Step 5: Run build and smoke test**

Run: `./gradlew clean :app:assembleDebug :app:connectedDebugAndroidTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "chore: bootstrap native Android app"
```

---

### Task 2: Design System, Localization, and App Preferences

**Files:**
- Create: `core/designsystem/.../ShortcutTheme.kt`
- Create: `core/localization/.../AppLocale.kt`
- Create: `core/localization/.../LocaleRepository.kt`
- Create: `core/localization/.../LocaleRepositoryImpl.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values-ar/strings.xml`
- Create: `feature/settings/.../AppearanceSettings.kt`
- Test: `core/localization/src/test/.../LocaleRepositoryTest.kt`

**Interfaces:**
- Produces: `LocaleRepository.observeLocale(): Flow<AppLocale>` and `setLocale(AppLocale)`.
- Produces: theme preference `SYSTEM | LIGHT | DARK`.

- [ ] **Step 1: Write locale persistence test**

```kotlin
@Test fun localeCanBeChangedFromSystemToArabic() = runTest {
    repository.setLocale(AppLocale.ARABIC)
    assertEquals(AppLocale.ARABIC, repository.observeLocale().first())
}
```

- [ ] **Step 2: Implement locale enum and DataStore-backed repository**

```kotlin
enum class AppLocale { SYSTEM, ARABIC, ENGLISH }

interface LocaleRepository {
    fun observeLocale(): Flow<AppLocale>
    suspend fun setLocale(locale: AppLocale)
}
```

- [ ] **Step 3: Add Arabic/English string resources and RTL support**

`AndroidManifest.xml` must set `android:supportsRtl="true"`.

Provide at least the navigation labels: Home, Templates, History, Settings, Create, Easy Builder, Advanced Builder, Permissions, Contact, Feedback, Report Problem.

- [ ] **Step 4: Add theme persistence and system/light/dark mapping**

- [ ] **Step 5: Run unit tests and app build**

Run: `./gradlew :core:localization:test :app:assembleDebug`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add localization and appearance settings"
```

---

### Task 3: Define Stable Automation Domain Models

**Files:**
- Create: `core/model/.../Automation.kt`
- Create: `core/model/.../Trigger.kt`
- Create: `core/model/.../Condition.kt`
- Create: `core/model/.../AutomationAction.kt`
- Create: `core/model/.../RepeatRule.kt`
- Create: `core/model/.../ExecutionResult.kt`
- Test: `core/model/src/test/.../AutomationSerializationTest.kt`

**Interfaces:**
- Produces: serializable domain types consumed by database, engine, scheduler, builder, and backup.

- [ ] **Step 1: Write round-trip serialization test**

```kotlin
@Test fun automationRoundTripsThroughJson() {
    val automation = Automation(
        id = "a1",
        name = "Open Maps",
        enabled = true,
        trigger = Trigger.Time("2026-09-17T07:30:00+03:00", RepeatRule.Daily),
        conditions = emptyList(),
        actions = listOf(AutomationAction.OpenApp("com.google.android.apps.maps"))
    )
    val json = Json.encodeToString(automation)
    assertEquals(automation, Json.decodeFromString<Automation>(json))
}
```

- [ ] **Step 2: Implement sealed trigger/action/result models**

```kotlin
@Serializable
sealed interface Trigger {
    @Serializable data class Time(val isoDateTime: String, val repeat: RepeatRule) : Trigger
    @Serializable data object BootCompleted : Trigger
    @Serializable data object UserUnlocked : Trigger
}

@Serializable
sealed interface AutomationAction {
    @Serializable data class OpenApp(val packageName: String) : AutomationAction
    @Serializable data class OpenUri(val uri: String) : AutomationAction
    @Serializable data class ShowNotification(val title: String, val body: String) : AutomationAction
    @Serializable data class Delay(val milliseconds: Long) : AutomationAction
    @Serializable data class RunShortcut(val automationId: String) : AutomationAction
}
```

`ExecutionResult` must include `Success`, `Failed(reason)`, `PermissionRequired(permission)`, `Unsupported(reason)`, `UserInteractionRequired(reason)`, `Skipped(reason)`.

- [ ] **Step 3: Implement repeat rules**

Include `Once`, `Daily`, `SelectedWeekdays(Set<DayOfWeek>)`, `Weekly`, `Monthly(dayOfMonth)`, and `Interval(amount, unit)`.

- [ ] **Step 4: Run tests**

Run: `./gradlew :core:model:test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: define automation domain model"
```

---

### Task 4: Room Persistence and Repositories

**Files:**
- Create: `core/database/.../AutomationEntity.kt`
- Create: `core/database/.../ExecutionLogEntity.kt`
- Create: `core/database/.../AutomationDao.kt`
- Create: `core/database/.../ExecutionLogDao.kt`
- Create: `core/database/.../ShortcutDatabase.kt`
- Create: `core/data/.../AutomationRepository.kt`
- Create: `core/data/.../ExecutionLogRepository.kt`
- Test: `core/database/src/androidTest/.../ShortcutDatabaseTest.kt`

**Interfaces:**
- Produces: `AutomationRepository.upsert`, `delete`, `get`, `observeAll`, `setEnabled`.
- Produces: `ExecutionLogRepository.append`, `observeRecent`, `getForAutomation`.

- [ ] **Step 1: Write Room CRUD instrumentation test**

```kotlin
@Test fun automationCanBeSavedAndRead() = runTest {
    dao.upsert(AutomationEntity("a1", "Open Maps", true, automationJson))
    assertEquals("Open Maps", dao.getById("a1")!!.name)
}
```

- [ ] **Step 2: Implement entities using JSON payload for polymorphic workflow data**

Keep indexed metadata (`id`, `name`, `enabled`, `updatedAt`) in columns and serialize the full workflow payload into a versioned JSON column.

- [ ] **Step 3: Implement repository mappers**

Repository must convert Room entities to/from `Automation` and return decoding errors as controlled failures rather than crashes.

- [ ] **Step 4: Run instrumentation tests**

Run: `./gradlew :core:database:connectedDebugAndroidTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: persist automations and execution logs"
```

---

### Task 5: Build the Automation Engine

**Files:**
- Create: `automation/engine/.../ActionExecutor.kt`
- Create: `automation/engine/.../ConditionEvaluator.kt`
- Create: `automation/engine/.../AutomationEngine.kt`
- Create: `automation/engine/.../ExecutionContext.kt`
- Test: `automation/engine/src/test/.../AutomationEngineTest.kt`

**Interfaces:**
- Consumes: `Automation`, `ExecutionResult`.
- Produces: `suspend fun AutomationEngine.execute(automation: Automation, triggerContext: TriggerContext): WorkflowResult`.
- Produces: registry `ActionExecutorRegistry.executorFor(action)`.

- [ ] **Step 1: Write test for ordered action execution**

```kotlin
@Test fun actionsRunInOrder() = runTest {
    val calls = mutableListOf<String>()
    val registry = fakeRegistry(calls)
    engine.execute(automationWith("one", "two"), TriggerContext.Manual)
    assertEquals(listOf("one", "two"), calls)
}
```

- [ ] **Step 2: Write failure-policy test**

When policy is `STOP`, action 2 must not run after action 1 fails. When policy is `CONTINUE`, action 2 must run.

- [ ] **Step 3: Implement the engine and structured step results**

Each executed action returns a `StepExecution` containing index, action type, startedAt, duration, result, and redacted diagnostic summary.

- [ ] **Step 4: Implement recursion guard for RunShortcut**

Reject cycles and max depth greater than 8 with `Failed("shortcut_recursion_limit")`.

- [ ] **Step 5: Run unit tests**

Run: `./gradlew :automation:engine:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add deterministic automation engine"
```

---

### Task 6: Scheduling and Repeat Calculation

**Files:**
- Create: `automation/scheduler/.../NextRunCalculator.kt`
- Create: `automation/scheduler/.../AutomationScheduler.kt`
- Create: `automation/scheduler/.../AlarmAutomationScheduler.kt`
- Create: `automation/scheduler/.../ScheduledAutomationReceiver.kt`
- Test: `automation/scheduler/src/test/.../NextRunCalculatorTest.kt`

**Interfaces:**
- Produces: `fun nextRun(after: ZonedDateTime, trigger: Trigger.Time): ZonedDateTime?`.
- Produces: `schedule(automation)`, `cancel(automationId)`, `rescheduleEnabled()`.

- [ ] **Step 1: Write repeat-rule tests**

Cover one-time, daily, selected weekdays, weekly, monthly day clipping, and interval recurrence across DST boundaries.

- [ ] **Step 2: Implement `NextRunCalculator` using `java.time`**

Never calculate recurrence using raw millisecond addition for calendar schedules.

- [ ] **Step 3: Implement AlarmManager scheduling**

Use exact alarms only when exact-alarm capability is available and required; otherwise use an inexact/fallback path and expose capability state to UI.

- [ ] **Step 4: Implement receiver -> engine execution -> next reschedule**

`ScheduledAutomationReceiver` must load the automation, verify it is still enabled, execute it, record the result, then schedule its next run.

- [ ] **Step 5: Run tests**

Run: `./gradlew :automation:scheduler:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: schedule recurring automations"
```

---

### Task 7: App Launch and Notification Fallback Actions

**Files:**
- Create: `automation/actions/.../OpenAppExecutor.kt`
- Create: `automation/actions/.../OpenUriExecutor.kt`
- Create: `automation/actions/.../NotificationFallback.kt`
- Create: `core/permissions/.../CapabilityChecker.kt`
- Test: `automation/actions/src/test/.../OpenAppExecutorTest.kt`

**Interfaces:**
- Produces: `OpenAppExecutor.execute(OpenApp, ExecutionContext)`.
- Produces: fallback notification with a `PendingIntent` that opens the target app.

- [ ] **Step 1: Write test for missing app**

Expected result: `Unsupported("app_not_installed")`.

- [ ] **Step 2: Write test for blocked background launch path**

Expected result: `UserInteractionRequired("background_launch_blocked")` and fallback notification is requested.

- [ ] **Step 3: Implement package resolution and launch intent**

Use PackageManager launch intent. Never assume a package has a launchable activity.

- [ ] **Step 4: Implement fallback notification channel and `Open now` action**

The notification must contain the app name and an explicit user action.

- [ ] **Step 5: Run tests/build**

Run: `./gradlew :automation:actions:test :app:assembleDebug`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: open apps with Android-safe fallback"
```

---

### Task 8: Boot/Unlock Triggers and Maps Template

**Files:**
- Create: `automation/triggers/.../BootReceiver.kt`
- Create: `automation/triggers/.../UserUnlockedReceiver.kt`
- Create: `automation/triggers/.../TriggerDispatcher.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `automation/triggers/src/test/.../TriggerDispatcherTest.kt`

**Interfaces:**
- Produces: trigger dispatch for `BootCompleted` and `UserUnlocked`.

- [ ] **Step 1: Write dispatcher test**

Given two enabled automations with `UserUnlocked`, both IDs are returned for execution; disabled ones are excluded.

- [ ] **Step 2: Implement receivers**

Register `BOOT_COMPLETED` and `USER_UNLOCKED`. On boot, reschedule time automations and dispatch boot automations. On unlock, dispatch unlock automations.

- [ ] **Step 3: Add Maps action preset**

Template action uses installed package discovery and should prefer Google Maps when present, otherwise let the user select any installed maps app.

- [ ] **Step 4: Test receiver integration where feasible**

Run unit tests and an emulator manual test using `adb shell am broadcast` for supported broadcasts.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add boot and unlock automation triggers"
```

---

### Task 9: Wi-Fi Capability Layer with Honest Fallbacks

**Files:**
- Create: `automation/actions/.../WifiCapability.kt`
- Create: `automation/actions/.../WifiPrepareExecutor.kt`
- Create: `core/permissions/.../WifiPermissionState.kt`
- Test: `automation/actions/src/test/.../WifiPrepareExecutorTest.kt`

**Interfaces:**
- Produces: capability result `DirectlyAvailable | UserConfirmationRequired | PermissionRequired | Unsupported`.

- [ ] **Step 1: Write tests for capability mapping by API/permission state**

Do not test hidden Android APIs; model the public capability decisions as pure Kotlin inputs.

- [ ] **Step 2: Implement a version-aware public-API strategy**

Use public Android network request/suggestion/settings flows. When silent enable/connect is not available, return `UserInteractionRequired` with a system settings/request intent.

- [ ] **Step 3: Add template composition**

`On unlock -> Prepare Wi-Fi -> Open Maps` must be generated as separate actions so users can delete/reorder either step.

- [ ] **Step 4: Run tests**

Run: `./gradlew :automation:actions:test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add .
git commit -m "feat: add Wi-Fi automation capability layer"
```

---

### Task 10: WhatsApp and Telegram Scheduled Workflows

**Files:**
- Create: `automation/actions/.../WhatsAppMessageExecutor.kt`
- Create: `automation/actions/.../TelegramMessageExecutor.kt`
- Extend: `core/model/.../AutomationAction.kt`
- Test: `automation/actions/src/test/.../MessagingActionTest.kt`

**Interfaces:**
- Produces actions `PrepareWhatsAppMessage(recipient, text)` and `PrepareTelegramMessage(target, text)`.

- [ ] **Step 1: Write URI/intent construction tests**

Test URL encoding for spaces, Arabic text, emoji, `&`, `?`, and newlines.

- [ ] **Step 2: Implement Standard Mode actions**

Standard Mode prepares and opens the appropriate messaging flow. It must never falsely report a message as sent when the user still needs to press Send.

- [ ] **Step 3: Add Advanced Mode capability placeholder only as an explicit state**

If advanced send is not implemented yet, return `Unsupported("advanced_message_send_not_available")`; do not simulate success.

- [ ] **Step 4: Add sensitive-log redaction**

Execution history records action type and recipient label but not full message content by default.

- [ ] **Step 5: Run tests**

Run: `./gradlew :automation:actions:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add WhatsApp and Telegram automation flows"
```

---

### Task 11: Easy Builder and Ready Templates

**Files:**
- Create: `feature/builder-easy/.../EasyBuilderScreen.kt`
- Create: `feature/builder-easy/.../EasyBuilderViewModel.kt`
- Create: `feature/templates/.../TemplateCatalog.kt`
- Create: `feature/templates/.../TemplatesScreen.kt`
- Create: `feature/templates/.../TemplateFactory.kt`
- Test: `feature/builder-easy/src/test/.../EasyBuilderViewModelTest.kt`
- Test: `feature/templates/src/test/.../TemplateFactoryTest.kt`

**Interfaces:**
- Produces: guided `When -> What -> Repeat` flow.
- Produces initial templates from the approved spec.

- [ ] **Step 1: Write builder state test**

A user selecting app + 07:30 + weekdays produces one enabled `Automation` with a time trigger and `OpenApp` action.

- [ ] **Step 2: Implement three-step builder state machine**

States: `WhenStep`, `ActionStep`, `RepeatStep`, `ReviewStep`.

- [ ] **Step 3: Implement template catalog**

Initial visible templates:
- Open app at a scheduled time.
- WhatsApp scheduled message.
- Telegram scheduled message.
- Shutdown reminder/system flow.
- On boot/unlock -> Wi-Fi -> Maps.
- Car mode.
- Work mode.
- Sleep mode.
- Battery 80% placeholder if trigger implementation is deferred.
- Bluetooth -> Spotify placeholder if trigger implementation is deferred.
- Open several apps in sequence.
- Custom reminder.
- One-tap shortcut.

Deferred templates must be clearly marked unavailable until their trigger exists; never create dead automations.

- [ ] **Step 4: Add installed-app picker**

Show app icon/name/package and searchable filtering.

- [ ] **Step 5: Run unit and Compose UI tests**

Run: `./gradlew :feature:builder-easy:test :feature:templates:test :app:connectedDebugAndroidTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add easy builder and starter templates"
```

---

### Task 12: Onboarding, Home, Navigation, and Permissions Center

**Files:**
- Create: `feature/onboarding/.../OnboardingScreen.kt`
- Create: `feature/home/.../HomeScreen.kt`
- Create: `core/permissions/.../PermissionsCenter.kt`
- Create: `feature/settings/.../PermissionsScreen.kt`
- Create: `app/.../ShortcutNavHost.kt`
- Test: `feature/onboarding/src/androidTest/.../OnboardingUiTest.kt`

**Interfaces:**
- Produces first-run onboarding and main navigation.

- [ ] **Step 1: Write onboarding UI test**

Verify first-run sequence contains feature explanation, messaging, connectivity, Advanced Mode, privacy, permissions, and Skip/Continue controls.

- [ ] **Step 2: Implement first-run persistence**

Onboarding appears once unless reopened from Settings.

- [ ] **Step 3: Implement Home dashboard**

Sections: My Shortcuts, Scheduled Automations, Ready Templates, Recent Activity, and a prominent Create button.

- [ ] **Step 4: Implement Permissions Center**

Each capability displays status, rationale, dependent features, and a button to open the relevant Android settings screen.

- [ ] **Step 5: Run UI tests**

Run: `./gradlew :app:connectedDebugAndroidTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add onboarding home and permissions center"
```

---

### Task 13: History, Backup/Restore, Support, and Settings

**Files:**
- Create: `feature/history/.../HistoryScreen.kt`
- Create: `feature/backup/.../BackupCodec.kt`
- Create: `feature/backup/.../BackupScreen.kt`
- Create: `feature/support/.../SupportScreen.kt`
- Create: `feature/settings/.../SettingsScreen.kt`
- Test: `feature/backup/src/test/.../BackupCodecTest.kt`

**Interfaces:**
- Produces versioned export format: `{ schemaVersion, exportedAt, automations[] }`.

- [ ] **Step 1: Write export/import round-trip test**

Verify Arabic names, repeat rules, app actions, and messaging actions survive export/import.

- [ ] **Step 2: Write malicious/invalid import tests**

Reject unknown schema versions, malformed JSON, oversized payloads, missing IDs, and invalid action data. Imported automations containing advanced/sensitive actions default to disabled.

- [ ] **Step 3: Implement History UI**

Display automation, trigger, timestamps, per-step result, duration, and redacted failure reason.

- [ ] **Step 4: Implement Support/Settings entries**

Include language, theme, advanced status, permissions, backup, onboarding, history retention, Contact us, Feedback, Report a problem, Privacy, About, version.

Contact/feedback/report actions should use explicit intents and fail gracefully if no handler exists.

- [ ] **Step 5: Run tests**

Run: `./gradlew :feature:backup:test :app:assembleDebug`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add history backup and support settings"
```

---

### Task 14: Advanced Mode Foundation

**Files:**
- Create: `automation/advanced/.../AdvancedModeRepository.kt`
- Create: `automation/advanced/.../AdvancedDisclosureScreen.kt`
- Create: `automation/advanced/.../ShortcutAccessibilityService.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `automation/advanced/src/test/.../AdvancedModeRepositoryTest.kt`

**Interfaces:**
- Produces explicit advanced-mode state and service capability status.

- [ ] **Step 1: Write consent-state tests**

Advanced Mode remains false until the user accepts disclosure; accepting disclosure alone does not imply Android Accessibility permission is enabled.

- [ ] **Step 2: Implement prominent disclosure flow**

Disclosure must explain what the service can observe/control and that only user-created deterministic rules are executed.

- [ ] **Step 3: Implement minimal AccessibilityService shell**

The first pass must not include generic autonomous clicking. Service exposes only a constrained command interface for future specifically-reviewed actions.

- [ ] **Step 4: Add settings status card**

Show `Advanced Mode: Off`, `Consent given`, and `Accessibility permission: On/Off` independently.

- [ ] **Step 5: Run tests and lint**

Run: `./gradlew :automation:advanced:test :app:lintDebug`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add advanced mode consent foundation"
```

---

### Task 15: Ads with Contextual Frequency Policy

**Files:**
- Create: `monetization/ads/.../AdsManager.kt`
- Create: `monetization/ads/.../AdsPolicy.kt`
- Create: `monetization/ads/.../GoogleMobileAdsManager.kt`
- Test: `monetization/ads/src/test/.../AdsPolicyTest.kt`

**Interfaces:**
- Produces: `shouldShowInterstitial(event, now): Boolean` and `markInterstitialShown(now)`.

- [ ] **Step 1: Write policy tests**

Assert interstitial is denied for startup, permission flows, running automation, and messaging execution; allowed only after configured logical completion events when cooldown has elapsed.

- [ ] **Step 2: Implement centralized policy**

Use events such as `AutomationSaved` and `TemplateFlowCompleted`. Keep cooldown and placement rules in one place.

- [ ] **Step 3: Add Banner host only to Home, Templates, History**

Do not render Banner in builders, onboarding, permission screens, or execution UI.

- [ ] **Step 4: Use test ad unit IDs in debug builds**

Release IDs must come from build configuration, never hard-coded into source control if they are environment-specific.

- [ ] **Step 5: Run tests**

Run: `./gradlew :monetization:ads:test :app:assembleDebug`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "feat: add non-intrusive ad policy"
```

---

### Task 16: End-to-End Verification and CI

**Files:**
- Create: `.github/workflows/android.yml`
- Create: `app/src/androidTest/.../ScheduledAppLaunchFlowTest.kt`
- Create: `app/src/androidTest/.../TemplateCreationFlowTest.kt`
- Create: `README.md`

**Interfaces:**
- Produces reproducible debug APK build and automated verification on pushes/PRs.

- [ ] **Step 1: Add end-to-end builder test**

Test: Home -> Create -> Easy Builder -> time -> installed test app -> repeat -> save -> automation visible on Home.

- [ ] **Step 2: Add template flow test**

Test: Templates -> On unlock Wi-Fi + Maps -> create -> automation contains separate Wi-Fi and OpenApp actions.

- [ ] **Step 3: Add CI workflow**

CI commands:

```bash
./gradlew test lintDebug assembleDebug
```

Run emulator tests in a separate Android emulator job where supported.

- [ ] **Step 4: Document supported vs fallback behaviors**

README must explicitly document limitations for background app launch, Wi-Fi, shutdown, and message sending so contributors do not reintroduce unsupported claims.

- [ ] **Step 5: Run full local verification**

```bash
./gradlew clean test lintDebug assembleDebug
```

Expected: all tasks PASS and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "ci: verify Shortcut Android v1"
```

---

## Acceptance Criteria for V1

V1 is complete only when all of the following are demonstrated on a real/emulated Android device:

1. App launches natively and works in Arabic RTL and English LTR.
2. User can use the app without an account.
3. User can create, enable, disable, edit, duplicate, and delete local automations.
4. Scheduled app launch works where Android permits and produces an immediate `Open now` fallback notification where background launch is blocked.
5. One-time and recurring schedules calculate the correct next occurrence.
6. WhatsApp and Telegram standard flows prepare the configured message without falsely claiming automatic send.
7. Boot/unlock automations execute and time automations are rescheduled after reboot.
8. `On unlock -> Wi-Fi preparation -> Maps` is available as a ready template with each action independently editable.
9. Permissions Center accurately reflects required capabilities.
10. History identifies success/failure per action and redacts sensitive content.
11. Export/import round-trips valid automations and disables sensitive imported workflows pending review.
12. Advanced Mode cannot be enabled silently and its Accessibility permission state is separately visible.
13. Banner appears only in approved low-interruption screens.
14. Interstitial ads follow contextual completion rules and cooldowns.
15. Full Gradle test/lint/build verification passes and a debug APK is produced.

## Self-Review Notes

- Spec coverage: all first-pass delivery areas are mapped to Tasks 1–16. NFC, geofence, broad notification triggers, battery/Bluetooth triggers, generic advanced clicking, and large action-catalog expansion remain intentionally outside V1 breadth until the engine and first vertical slices are stable.
- Placeholder scan: deferred trigger-based templates are explicitly marked unavailable rather than silently non-functional.
- Type consistency: `Automation`, `Trigger`, `AutomationAction`, `RepeatRule`, `ExecutionResult`, `AutomationEngine`, `AutomationScheduler`, and repository interfaces are defined before downstream tasks consume them.
