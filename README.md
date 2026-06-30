<p align="center">
  <img src="docs/assets/live-progress-icon.svg" width="88" alt="Live Progress icon" />
</p>

<h1 align="center">Live Progress</h1>

<p align="center">
  Beautiful Android 16 live updates for progress tasks and media playback.
</p>

<p align="center">
  <img alt="Android 16+" src="https://img.shields.io/badge/Android-16%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-f89820?logo=openjdk&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue" />
</p>

<p align="center">
  <b>Status bar pills</b> • <b>Lock screen mirrors</b> • <b>AOD updates</b> • <b>Media progress</b> • <b>System colors</b>
</p>

---

## ✨ What It Does

**Live Progress** brings Android 16 live notifications to apps that show useful progress or media notifications.

It helps you glance at:

- 📊 Download, upload, and other real progress notifications
- 🎵 Current media playback with title, time, and controls
- 🌙 Always-on display updates while the screen is off
- 🔒 Optional lock screen mirrors
- 🟢 Status bar pills with short progress or media text

The app keeps the original notification active so updates continue normally.

---

## 🚀 Highlights

### 📊 Progress Mirrors

- Shows eligible progress notifications as Android 16 live updates.
- Displays progress percentage in the status bar when the original notification provides real progress.
- Keeps expanded mirror content updated in real time.
- Supports multiple progress notifications.
- Can optionally show progress mirrors on the lock screen and AOD.
- Hides the mirror while the source app is open, when foreground detection is available.

### 🎵 Media Live Updates

- Shows media playback as a live update when enabled.
- Supports status bar text modes: **Title**, **Elapsed**, and **Remaining**.
- Shows media progress and playback actions.
- Can show media progress on AOD.
- Hides the mirror while the media app is open, when foreground detection is available.
- Automatically hides media mirrors when a progress mirror is active.

### 🧩 Notification Category Picker

Some apps use useful custom notification categories without exposing real Android progress. Live Progress lets you open a dedicated **Notification categories** page from Progress settings and choose which observed categories should always mirror as live updates.

- Categories appear after Live Progress has observed notifications from those apps.
- Selected categories mirror as indeterminate live updates when the original has no real progress.
- If a selected category later exposes real progress, the mirror shows that real progress.
- Media notifications still use the media mirror path and are not converted into progress mirrors.
- Live Progress does not guess percentages from notification text.

### 🎨 Native Look

- Uses Android’s system color palette.
- Supports light and dark mode.
- Uses a Material 3 Expressive-inspired settings screen.
- Avoids custom cloned notification layouts so Android can promote mirrors properly.

---

## 👀 Where Mirrors Appear

| Surface | Progress | Media |
| :--- | :--- | :--- |
| 🔓 Unlocked | Shows live mirror | Shows live mirror unless progress is active |
| 📱 Source app open | Hidden when foreground detection is available | Hidden when foreground detection is available |
| ⚙️ Quick Settings expanded | Can hide mirror | Can hide mirror |
| 🔒 Lock screen | Optional | Optional |
| 🌙 AOD / screen off | Optional | Optional unless progress is active |

When a progress mirror is active, media mirrors are hidden everywhere so progress gets priority.

---

## 🔐 Permissions

Live Progress asks for permissions only when needed.

| Permission | Why |
| :--- | :--- |
| 🔔 Notifications | Posts mirrored live updates and test notifications. |
| ⭐ Live notification access | Enables Android 16 promoted/live notification behavior. |
| 👂 Notification listener | Reads eligible notifications from other apps. |
| ⚙️ Accessibility service | Optional. Detects expanded Quick Settings and foreground apps so mirrors can hide when they should. |
| 🧰 Shizuku | Optional. Helps hide original progress notifications on the lock screen when enabled. |

Live Progress does **not** use root.

---

## 📲 Setup

1. Install and open **Live Progress**.
2. Follow the startup permission pages.
3. Enable the features you want from the main settings screen.
4. Use **Post live notification test** to confirm live updates are working.

Optional setup pages can be skipped. Skipping an optional permission turns off the feature that needs it.

---

## 🎛️ Main Settings

### General

- Language
- Hide mirrored notifications when Quick Settings is expanded

### Progress

- Enable progress live updates
- Notification categories
- Show progress mirror on AOD
- Show progress mirror on lock screen
- Hide original notification on lock screen, when supported

### Media

- Enable media live updates
- Show media mirror on AOD
- Show media mirror on lock screen
- Status bar text mode
- Scroll title in status bar

---

## ⚠️ Notes And Limits

- Android 16 / API 36+ is required.
- Some manufacturers may customize live notification behavior.
- Hiding mirrors while the source app is open requires the optional accessibility service.
- Original notification hiding is best-effort and may not work for every app or device.
- Custom notification layouts are not copied exactly.
- User-selected notification categories mirror as indeterminate updates unless the original notification exposes real progress.

---

## 🛠️ Build From Source

Requirements:

- Android Studio
- Android SDK 36
- Java 21
- Android 16 / API 36 device or emulator for runtime testing

Commands:

```bash
./gradlew assembleDebug
```

```bash
./gradlew testDebugUnitTest lintDebug
```

```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/
```

---

## 📝 Documentation Rule

When behavior, supported apps, settings, permissions, build steps, or limitations change, update this README or the relevant docs in the same change.

---

## 📄 License

This project is released under the [MIT License](LICENSE).

If you use this project or substantial parts of its code, acknowledgment or attribution to the original author is appreciated.
