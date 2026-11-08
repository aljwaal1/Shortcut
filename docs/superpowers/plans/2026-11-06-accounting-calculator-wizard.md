# Accounting Calculator Wizard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the long-form calculator page with a sequential card wizard.
**Architecture:** Keep pure math in AccountingCalculators unchanged; implement screen state in AccountingCalculatorsActivity and a testable WizardState progression helper.
**Tech Stack:** Kotlin, Jetpack Compose Material3.
**Spec:** docs/superpowers/specs/2026-11-06-accounting-calculator-wizard-design.md

## Global Constraints
Retain all eight calculation modes; Arabic and English; no new dependencies; never touch main/stable branch.

## Review Focus
Empty input, decimal comma, non-finite input, back after entering values, two-input vs three-input calculators.

### Task 1: Wizard state and UI
**Files:** Modify AccountingCalculatorsActivity.kt; test with Android CI.
- [ ] Introduce selection/input/result stages and preserve values across Back/Next.
- [ ] Use a single responsive rounded card and numeric keyboard per question.
- [ ] Validate input before advancing; reuse existing formula calls.
- [ ] Add localized progress and final result with Edit/New buttons.
- [ ] Confirm all eight calculators are selectable.

### Task 2: Verification
- [ ] Run existing Android CI unit tests, lint, compile, APK assembly.
- [ ] Verify GitHub Actions green and report exact build artifact.
