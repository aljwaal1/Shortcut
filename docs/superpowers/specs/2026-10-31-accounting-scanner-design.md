# Accounting Tools & Shared Document Scanner — Design

**Approved 2026-10-31.** A single multi-page document scanner appears in both PDF tools and accountant tools. Both entries route to the same implementation and saved documents, not duplicate code.

## Scanner workflow
Capture or import consecutive pages; present thumbnails with reorder, delete/retake, rotate, crop and perspective correction; optional brightness/contrast grayscale enhancement; output a single PDF or split groups of PDFs. The user chooses output destination and reviews before saving. Reuse Android camera / Storage Access Framework / PdfDocument components where possible. Never save incomplete or blank files. Keep originals intact.

## Table to Excel
Photo -> OCR -> editable grid retaining rows/columns -> review and export XLSX. Require explicit user review of numeric fields before output, especially decimal separators and right-to-left text. Offer CSV fallback only if XLSX processing unavailable.

## Accountant tools
Depreciation (straight-line, declining balance), break-even units and currency, contribution margin, interest, margin/markup, ratios, discounts, ROI, NPV. Input validation with explanatory units and clear rounding. Offline by default. Other earlier tools remain unchanged.

## Navigation and visuals
One entry in the PDF section and one shortcut in accounting. Bright oval cards with 16dp+ internal padding, responsive text and accessibility. Accounting tool shortcuts may repeat entry points but must not duplicate implementations or records.

## Non-regressions
All current ToolCatalog IDs and templates remain; do not touch stable/v0.7.4 or main. New code only in feature/android-v1-phase1. Use test-first and CI, plus Android real-device verification for camera and file permissions.
