# Shortcut 0.4 Design: Usage, History, Favorites, and Media Quality

Date: 2026-09-16
Branch: `feature/android-v1-phase1`
Status: Approved direction, implementation pending plan

## Goal

Improve Shortcut as a polished daily utility without turning it into a heavy platform. The release should strengthen four areas already present in the app:

1. Advanced app-usage statistics.
2. Persistent task execution history.
3. Favorite and recent tools.
4. Higher-quality image/PDF workflows.

All user data remains local. No account, cloud service, or Accessibility service is introduced.

## 1. Advanced app-usage statistics

Replace the current dialog-based usage screen with a dedicated Compose screen.

### Behavior

- Keep a persistent ON/OFF toggle for Shortcut's usage-tracking feature.
- Request Android Usage Access only when the user turns tracking on and access is missing.
- Do not request Usage Access when the app starts.
- If access is revoked while tracking is enabled, show a clear `Usage access required` state rather than silently returning empty data.
- Shortcut does not claim to stop Android's own system usage collection when the toggle is OFF; OFF means Shortcut stops reading/presenting/storing its own derived usage data.

### Dashboard

Show:

- Total app usage today.
- Yesterday's total and a comparison versus today.
- Seven daily totals for the last 7 calendar days.
- Top 5 apps today with app icon, app label, duration, and percentage of today's total.
- A full ranked list below the top 5.
- A compact status indicator for `Tracking enabled`, `Tracking disabled`, or `Permission required`.

Use `UsageStatsManager` and the existing aggregator foundation. Prefer calendar-day boundaries rather than rolling 24-hour windows for daily comparisons.

### Accuracy wording

Label the aggregate as `App usage` / `استخدام التطبيقات`, not `Screen time`, because Android multi-window and system usage accounting can make it different from exact interactive-screen time.

## 2. Persistent task execution history

Expand the current `TaskExecutionReporter`, which stores only the latest result, into a bounded local history.

### Stored fields

Each record stores:

- Task name.
- Execution status: success or failure.
- Optional failure reason.
- Start timestamp.
- Finish timestamp.
- Derived duration.

### Retention

- Keep the newest 200 records.
- Drop the oldest records automatically when the limit is exceeded.
- Store locally using the current lightweight persistence approach; do not add Room in this release.

### UI

The History tab shows:

- All / Success / Failure filters.
- Newest first.
- Status icon and color.
- Task name.
- Date/time.
- Duration.
- Failure reason when present.
- Clear-history action with confirmation.

The Home screen continues to show a compact latest-result card.

## 3. Favorite and recent tools

Add lightweight tool personalization to the existing tool catalog.

### Favorites

- Each tool card exposes a favorite toggle.
- Favorites are stored locally by stable `ToolId`.
- Favoriting does not require permissions.

### Recent tools

- Record a tool as recent when the user actually opens it.
- Keep the 8 most recent unique tools.
- Opening the same tool again moves it to the front instead of creating duplicates.

### Home and Tool Hub

- Home shows a `Favorites` quick row when at least one exists.
- Home shows `Recent tools` when recent items exist.
- Tool Hub adds compact Favorites and Recent sections above the normal categories.
- The full categorized tool list remains unchanged below them.

## 4. Image and PDF workflow quality

This release improves existing media workflows without adding a full document editor.

### Shared operation pattern

For applicable long-running operations:

- Show selected input count before starting.
- Show a progress indicator while processing.
- Allow cancellation when the underlying operation can be safely stopped between items.
- On completion, show clear actions for Share, Open, or Done when Android supports them.
- On failure, show a concise user-facing error and preserve the original inputs.

### Batch support

Add multi-file handling where it fits the current tools:

- JPEG conversion: multiple images.
- Resize/compress: multiple images.
- Images to PDF: already multi-image; add clearer preview/order summary and progress.
- GIF creation: multiple images with clearer selected-frame count.
- Merge images: multiple images with clearer order summary before processing.

Crop remains single-image because batch cropping without a per-image crop decision would be misleading.

### Preview and ordering

Before merge, GIF, or Images-to-PDF:

- Show the selected file count.
- Show the current order.
- Allow re-selection if the order is wrong.

A drag-and-drop reorder UI is not required in this release; Android picker order plus re-selection is sufficient.

### Output handling

- Continue using Storage Access Framework / MediaStore / app-owned output as appropriate.
- Do not reintroduce broad storage permissions.
- Preserve EXIF orientation normalization already added to image decoding.

## Architecture

Keep these concerns isolated:

- `usage/`: calculation and usage-dashboard models/repository/UI.
- `execution/`: result history persistence and query/filter logic.
- `tools/`: favorite/recent tool preferences and Tool Hub integration.
- media-processing code: shared progress/cancellation/output result model used by existing image/PDF activities where practical.

Use SharedPreferences/JSON for the bounded local lists in this release. Room is intentionally deferred because record counts are capped and query needs are simple.

## Permissions

Permission behavior remains incremental and contextual:

- Usage Access: only when enabling usage statistics.
- Notifications: only when scheduling a feature that needs result notifications.
- Exact alarms: only when a scheduled task requires them.
- Location: only for parked-car behavior.
- MediaProjection: only when taking a screenshot.
- Image/file tools use system file pickers whenever possible rather than broad media/storage permission.

No Accessibility service is added.

## Error handling

- Missing special access should produce a permission-required state with one direct action to open the corresponding Android settings.
- Empty usage data is different from missing permission and should be presented as `No usage data yet`.
- A canceled media job should be shown as canceled, not failed.
- Media operations should not overwrite original files.
- History persistence failures should not prevent the task itself from completing.

## Testing

Use TDD for behavior changes.

Required unit coverage:

- Daily usage totals and yesterday comparison.
- Top-app percentages.
- Seven-day bucketing across calendar boundaries.
- Execution-history append, newest-first order, filtering, and 200-record retention.
- Favorites add/remove.
- Recents uniqueness, ordering, and 8-item cap.
- Media batch progress accounting and cancellation state where extracted into pure logic.

Final verification must run the existing strict command:

`gradle :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug --stacktrace`

Only the APK produced from the final verified head commit is distributed.

## Out of scope for this release

- Cloud sync or accounts.
- Room database migration.
- Full PDF page editor with arbitrary page manipulation.
- Background usage alerts or app limits.
- Accessibility-based automation.
- Silent personal Telegram/WhatsApp sending.
- Drag-and-drop media reorder UI.
