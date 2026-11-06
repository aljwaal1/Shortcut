# Shortcut Cards and Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Consistent colorful oval cards and simplified four-area navigation without removing functionality.

**Architecture:** Reuse Jetpack Compose existing views and preserve ToolRouter, ToolCatalog, templates, saved data and activities. Introduce shared visual styling for cards, then adjust top-level navigation/entry points while keeping existing action handlers.

**Tech Stack:** Android Kotlin, Jetpack Compose, JUnit, GitHub Actions.

**Spec:** docs/superpowers/specs/2026-10-31-shortcut-navigation-cards-design.md

## Global Constraints
- Work only on feature/android-v1-phase1.
- Do not remove any tool, automation, template, route or stored data.
- Arabic/English, dark theme, small phones and large fonts all supported.

## Review Focus
- Long Arabic titles wrap without clipping on narrow screens.
- English labels remain readable with increased system text scaling.
- Every ToolCatalog entry remains reachable.
- Back navigation exits nested destinations predictably.
- Saved automations and templates retain working entry points.

---

### Task 1: Preserve navigation inventory
**Files:** Modify app/src/test/java/com/explapp/shortcut/ui/VisualIdentityTest.kt; inspect ShortcutApp.kt and ToolHubScreen.kt.
- [ ] Add failing tests that inventory all ToolCatalog sections and verify no missing category.
- [ ] Verify red with Gradle test.
- [ ] Implement minimal stable navigation destination model (Home/Tools/Automation/More) and mapping for current entries.
- [ ] Verify green and commit.

### Task 2: Shared adaptive cards
**Files:** Modify VisualComponents.kt, VisualIdentity.kt, HomeToolQuickRows.kt, ToolHubScreen.kt; test VisualIdentityTest.kt.
- [ ] Add failing tests for section palette/contrast and card style tokens.
- [ ] Verify red.
- [ ] Implement rounded 24–30dp adaptive card styling, text wrap, icon surfaces, consistent heights and touch targets.
- [ ] Verify green and commit.

### Task 3: Simplify navigation
**Files:** Modify ShortcutApp.kt and relevant screen entry points; create navigation test under app/src/test/java/com/explapp/shortcut/ui/.
- [ ] Add failing tests mapping each destination to one primary section and retained secondary shortcuts.
- [ ] Verify red.
- [ ] Implement four visible destinations; group templates and existing automation management under Automation; move history/settings/permissions under More; keep actions and persistence untouched.
- [ ] Verify green and commit.

### Task 4: Regression and APK
- [ ] Run complete unit tests, lint, compile and debug APK workflow.
- [ ] Review feature inventory and verify no deletion in diff.
- [ ] Report workflow URL and device-only UI checks that remain.
