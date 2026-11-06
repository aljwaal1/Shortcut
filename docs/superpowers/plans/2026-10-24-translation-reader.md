# Shortcut Translation and PDF Reader Implementation Plan

**Goal:** Add offline Arabic-English translation, PDF text translation, speech, reading progress and automation access.

**Architecture:** Native Kotlin Activity backed by ML Kit Translate, PDFBox and Android TextToSpeech. Route from Tools and existing OPEN_TOOL automation action. Persist reading progress and translation history locally.

**Tasks:**
- [ ] Add and test pure text chunking/page navigation helpers.
- [ ] Implement bilingual translation and explicit model download with offline enforcement.
- [ ] Add PDF page selection, text extraction and translation.
- [ ] Add speech controls and reading progress persistence.
- [ ] Register tool routes for automation, then compile, lint and build APK.
