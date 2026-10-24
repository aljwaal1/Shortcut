# Shortcut v0.2 Today Update Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the September 16 consolidated UI/search/tools/automation update as a testable Android APK.

**Architecture:** Keep the existing scheduling engine intact, split new utilities into focused `tools` classes/screens, and expose them through one Tools hub. Use Android system APIs and user-mediated flows where the platform restricts silent behavior.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android SDK 36, Storage Access Framework, MediaStore, PdfDocument, MediaProjection, ExifInterface, ML Kit text recognition, ZXing core.

**Spec:** `docs/superpowers/specs/2026-09-16-shortcut-v02-today-design.md`

## Global Constraints
- Native Android only; no WebView.
- Arabic/English and RTL/LTR.
- App search stays minimal: live name search, alphabetic order, icon/name, direct selection only.
- Standard APK must not register AccessibilityService.
- No app-specific Waze/Spotify/Snapchat integrations.
- Modern Android Wi-Fi/Bluetooth/screenshot restrictions must use supported system flows.

---

### Task 1: UI refresh and app picker search
**Files:** modify `ShortcutTheme.kt`, `CreateShortcutScreen.kt`, strings.
- [ ] Add a failing pure filter test for case-insensitive alphabetical app matching.
- [ ] Implement `AppSearch.filter`.
- [ ] Replace picker list with live search + icon/name rows.
- [ ] Refresh Material 3 color scheme and cards without adding extra filters.
- [ ] Run unit tests and Android compilation.

### Task 2: Tools hub and image/PDF/file utilities
**Files:** create `tools/ToolCatalog.kt`, `tools/ToolHubScreen.kt`, `tools/FileToolEngine.kt`; modify root navigation and Gradle dependencies.
- [ ] Add catalog tests verifying the approved September 16 tool set and four sections.
- [ ] Implement catalog and tool hub.
- [ ] Implement merge images, images→PDF, JPEG conversion with optional metadata stripping, image information/EXIF, resize/compress, crop handoff, latest photo/screenshot share, ZIP/unzip, URL print-to-PDF handoff.
- [ ] Add OCR/image text and PDF text extraction paths with explicit picker/result UI.
- [ ] Add QR generation/read entry points and GIF creation entry point.
- [ ] Verify file output uses user-selected destinations/MediaStore safely.

### Task 3: Quick tools and screenshot
**Files:** create `tools/QuickTools.kt`, screenshot activity/service helpers; manifest only for non-sensitive normal permissions.
- [ ] Implement foreground clipboard save/restore snippets without background monitoring.
- [ ] Implement MediaProjection screenshot consent and save/share handoff.
- [ ] Connect screenshot result to OCR/search/PDF actions.
- [ ] Implement Water Eject tone start/stop with bounded volume/frequency behavior.
- [ ] Test pure state/format helpers.

### Task 4: General automations and final build
**Files:** create focused automation helpers/receivers and modify templates/permission center.
- [ ] Car mode: Wi-Fi panel + Bluetooth request/settings + Maps launch.
- [ ] Parked-car save/open-directions flow with explicit location permission.
- [ ] Battery threshold and charger receiver rules stored locally.
- [ ] NFC intent/tag trigger entry point.
- [ ] App-open routines only where supported without Standard AccessibilityService; unsupported silent behavior must be represented as user-mediated actions, not fake automation.
- [ ] Morning/sleep scheduled templates and calendar reminder helper.
- [ ] Run `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:assembleDebug` in CI.
- [ ] Download the generated APK artifact only after the CI job succeeds.
