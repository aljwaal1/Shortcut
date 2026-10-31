# Shortcut v0.2 — Today Consolidated Design

## Goal
Deliver one coherent Android update that incorporates every product note agreed on September 16, 2026, without turning the app into a complicated power-user maze.

## Product principles
- Native Android only; no WebView.
- Arabic and English, automatic locale plus manual switch.
- Simple UI first. Do not add favorites, app categories, usage rankings, or extra filters to app search.
- General-purpose tools, not integrations tied to Spotify, Waze, Snapchat, or other specific entertainment/social apps.
- Standard APK must not register AccessibilityService.
- Use only Android-supported flows for Wi-Fi/Bluetooth/screenshot/background behavior.

## UI refresh
- More vivid Material 3 palette with a clean light/dark presentation.
- Cleaner cards, spacing, and hierarchy on Home/Templates/Tools.
- App picker: live name search, alphabetical list, app icon + name, tap to select.
- Keep flows short and direct.

## Tool groups
### Images
- Merge images horizontally or vertically.
- Images to PDF, including multiple images into one PDF.
- OCR from image and copy/share result.
- Convert image to JPEG with optional metadata removal.
- Show image information: dimensions, size, date, EXIF when available.
- Create GIF from multiple images.
- Compress/resize images.
- Crop image quickly.
- Copy/share latest photo.
- Share latest screenshot.

### PDF and files
- Extract/read text from PDF.
- ZIP files and unzip archives.
- Save a web page or URL to PDF using Android print flow where supported.
- Convert multiple links to PDFs through repeated print flow.

### Quick tools
- Generate QR code from text or URL.
- Clipboard tools for current clipboard content and locally saved snippets; no background clipboard surveillance.
- Screenshot capture through MediaProjection with explicit system consent, then save/share/OCR/PDF.
- Screenshot to OCR or search.
- Water Eject tone tool.

### Automations
- Car mode: prepare Wi-Fi and Bluetooth using Android-supported panels/requests, then open Maps.
- Parked-car location: save current location with explicit location permission, later open directions back to it.
- Battery threshold / charger connected or disconnected triggers.
- NFC trigger to launch a saved shortcut.
- App-open routines when Android-supported; do not re-introduce unrestricted AccessibilityService in Standard APK.
- Morning/sleep routines based on time/charging.
- Calendar-based reminder/notification helpers with explicit calendar permission or user-selected calendar integration.

## Search behavior
- Only one search field.
- Filter by app label as the user types.
- Case-insensitive and locale-friendly.
- Keep alphabetical order.
- Show icon + app name.
- Selecting an app immediately closes the picker.

## Safety/platform constraints
- Wi-Fi/Bluetooth toggles use supported settings panels or user-mediated flows on modern Android.
- Screenshot capture uses MediaProjection consent.
- Clipboard is read only when the app is foregrounded/user invokes the tool.
- File actions use Storage Access Framework/MediaStore.
- Imported advanced automations remain disabled until reviewed.

## Release target
- Build a testable v0.2 debug APK from CI after unit tests and Android compilation succeed.
