# Shortcut — current approved test build

**Build identity:** Shortcut v0.9.13 (versionCode 45)

**Status:** Current user-designated approved development build, pending on-device acceptance checks. This does not imply publication to Google Play.

**Source branch:** `feature/android-v1-phase1`

**Official test artifact:** `Shortcut-Approved-v0.9.13-b45` from the latest successful Android CI run for this branch.

**APK file inside the artifact:** `Shortcut-Approved-v0.9.13-b45.apk` (debug signed; Android may reject an in-place update over an APK signed with a different key).

## What identifies this build

- Updated tool-card layouts and simplified translation, PDF and automation screens.
- GIF encoding fix for non-square images, with portrait/landscape regression tests and a unified GIF creation screen.
- Editable OCR result screen, PDF file-backed page reading and improved floating capture entry points.
- Screenshot and video-control settings grouped under Settings.

## Avoid mixing versions

1. Use **versionName 0.9.13** and **versionCode 45** together when reporting problems.
2. Download from the named artifact on this branch, not from an older run or the historical preview release workflow.
3. Preserve older releases for traceability; do not promote them as the current test build.
4. Do not merge to `main` or claim production-ready publication without explicit user approval and on-device verification.
