# Shortcut accounting and media quality implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand mobile-first accounting calculators, improve GIF fidelity and large-PDF resilience, and streamline ready-made automation entry without changing main.

**Architecture:** Keep the existing Kotlin and Compose screens and pure formula library. Reuse existing output storage and PDFBox while adding bounded-memory document input. Preserve old advanced automation builder behind an explicit action.

**Tech Stack:** Android Kotlin, Compose Material 3, PDFBox Android, gifencoder, JUnit, GitHub Actions.

**Spec:** Approved in conversation, 2026-11-06: retain admired accounting cards and guided entry; add financial tools; improve other cards, GIF and large-PDF processing; simplify ready templates.

## Global Constraints
- Work only on `feature/android-v1-phase1`, not `main`.
- Keep existing tools and mobile RTL/localized behavior.
- Deliver a directly downloadable APK after verified CI build; do not claim one if unavailable.

## Review Focus
- Invalid and zero-denominator inputs must fail clearly.
- Large PDFs must not force unbounded byte-array accumulation.
- Mixed portrait/landscape GIF frames must maintain aspect ratio.
- Existing advanced automation editing must remain accessible.
- Small phones and Arabic labels must remain legible.

### Task 1: Expand accounting formulas and wizard cards
**Files:** `AccountingCalculators.kt`, `AccountingCalculatorsTest.kt`, `AccountingCalculatorsActivity.kt`
- [ ] Add failing tests for liquidity, inventory, markup, net-profit margin, safety margin and reorder-point calculations; run `./gradlew testDebugUnitTest` to confirm failures.
- [ ] Implement validated pure formulas and expose each through the existing guided card wizard.
- [ ] Run unit tests and commit.

### Task 2: Improve GIF quality with one active pipeline
**Files:** `ImageBatchActivity.kt`, targeted tests
- [ ] Add failing tests for quality presets and output dimensions.
- [ ] Provide quality configuration, higher output-resolution mode, safe frame limits and on-device streaming.
- [ ] Run unit tests and commit.

### Task 3: Harden large PDF handling
**Files:** `PdfSplitActivity.kt`, `ToolActivity.kt`, targeted tests
- [ ] Add failing tests for bounded input-size handling and validation.
- [ ] Use temporary random-access backing instead of whole-document stream accumulation where supported, and avoid full document text materialization.
- [ ] Run unit tests and commit.

### Task 4: Template and tool cards UX
**Files:** `MyAutomationsActivity.kt`, `ToolHubScreen.kt` and relevant tests
- [ ] Add test coverage for quick/advanced template routing.
- [ ] Keep template configuration independent of generic builder by default; provide advanced-edit path, and improve tool card spacing and color.
- [ ] Run unit tests and commit.

### Task 5: Verify delivery
- [ ] Run tests and build locally or through GitHub Actions.
- [ ] Confirm branch, workflow status, artifact and APK link before handoff.
