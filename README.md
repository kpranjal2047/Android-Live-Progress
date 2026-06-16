<p align="center">
  <img src="docs/assets/live-progress-icon.svg" width="88" alt="Live Progress icon" />
</p>

<h1 align="center">Live Progress</h1>

<p align="center">
  Android 16 live notification mirrors for progress tasks and media playback.
</p>

<p align="center">
  <img alt="Android 16+" src="https://img.shields.io/badge/Android-16%2B-3DDC84?logo=android&logoColor=white" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white" />
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-f89820?logo=openjdk&logoColor=white" />
  <img alt="License" src="https://img.shields.io/badge/License-MIT-blue" />
</p>

<p align="center">
  <b>Status bar pills</b> • <b>ProgressStyle mirrors</b> • <b>AOD support</b> • <b>Optional Shizuku suppression</b>
</p>

---

## ✨ What Is Live Progress?

**Live Progress** mirrors eligible Android notifications into Android 16 promoted live notifications. It makes long-running tasks and media playback easier to glance at from the **status bar**, **lock screen**, and **always-on display**.

The app is designed around three principles:

| Principle | Meaning |
| :--- | :--- |
| 🧭 **Glanceable** | Progress percent, media time, and titles are visible without opening the notification shade. |
| 🧩 **Native** | Uses Android notification templates and `Notification.ProgressStyle`, not custom cloned `RemoteViews`. |
| 🔒 **Reversible** | Elevated behavior is optional, Shizuku-only, and restored when no longer needed. |

> Live Progress does **not** use root and does **not** make permanent elevated system changes.

---

## 🚀 Features

### 📊 Mirrored Live Progress

- Detects progress notifications from other apps.
- Ignores media notifications and existing Android live progress notifications.
- Mirrors title, text, subtext, app icon, large icon, color, timestamp, progress, content intent, and safe actions.
- Shows progress percentage as status-bar critical text.
- Keeps expanded notification content updated with current percent and progress state.

### 🎵 Media Live Updates

- Tracks media sessions from observed media notifications.
- Mirrors title, artist/album text, provider app, album art, controls, and playback progress.
- Supports status-bar text modes: **Title**, **Elapsed**, and **Remaining**.
- Keeps expanded media content updated with elapsed/total time when duration is known.
- Automatically hides media mirrors while any progress mirror is active.

### 🌓 AOD And Lock Screen

- Separate toggles for progress and media AOD mirrors.
- Lock screen mirrors are off by default for both progress and media.
- Screen-on lock screen mirrors use the normal non-alerting channel.
- Unlocked, Quick Settings, and AOD mirrors use silent low-priority channels.

---

## 👀 Visibility Matrix

| Surface | Progress Mirror | Media Mirror |
| :--- | :--- | :--- |
| 🔓 Unlocked, QS collapsed | Shows live mirror | Shows live mirror unless progress is active |
| ⚙️ Quick Settings expanded | Hidden only if QS-hide is enabled | Hidden only if QS-hide is enabled |
| 🔒 Screen-on lock screen | User setting, off by default | User setting, off by default |
| 🌙 AOD / screen off | User setting, on by default | User setting, on by default unless progress is active |

> When a progress mirror is active, media mirrors are hidden everywhere: status bar, QS, lock screen, and AOD.

---

## 🔐 Permissions

Live Progress asks for the minimum permission needed for the behavior you enable.

| Permission | Required? | Why |
| :--- | :---: | :--- |
| 🔔 Notifications | Required | Posts mirrored live notifications and test notifications. |
| ⭐ Promoted notification access | Required | Enables Android 16 live notification/status-bar behavior. |
| 👂 Notification listener | Required | Reads eligible progress and media notifications. |
| ⚙️ Accessibility service | Optional | Detects expanded Quick Settings so mirrors can be hidden while QS is open. |
| 🧰 Shizuku | Optional | Enables reversible original progress notification suppression when requested. |

Optional permission behavior:

- Skipping accessibility turns off **Hide mirrored notifications when Quick Settings is expanded**.
- Skipping Shizuku turns off **Hide original notification on lock screen**.
- If Shizuku is unavailable, original-notification suppression remains unchecked and disabled.

---

## 🎛️ Settings

### General

| Setting | Default | Notes |
| :--- | :---: | :--- |
| Hide mirrored notifications when Quick Settings is expanded | On | Requires accessibility only when enabled. |

### Progress Live Updates

| Setting | Default |
| :--- | :---: |
| Enable progress live updates | On |
| Show progress mirror on AOD | On |
| Show progress mirror on lock screen | Off |
| Hide original notification on lock screen | On internally, editable only when Shizuku support is available |

### Media Live Updates

| Setting | Default |
| :--- | :---: |
| Enable media live updates | On |
| Show media mirror on AOD | On |
| Show media mirror on lock screen | Off |
| Status bar text | Title |
| Scroll title in status bar | On, only editable in Title mode |

---

## 🧠 Behavior Notes

- Original progress notifications are not cancelled or dismissed as the normal hiding strategy, because destructive removal can stop future source updates.
- Original progress notification hiding works by temporarily adjusting the source notification channel lock-screen visibility when Shizuku and notification-assistant access allow it.
- Media original notifications are not suppressed in the current behavior.
- If Android or the source app prevents suppression, the mirror can still be shown and diagnostics report the limitation.
- Reinstalling under a new package name requires granting Android permissions again.

---

## 🧱 Code Overview

Live Progress is a small native Android Kotlin app. It does not use Compose, internet access, root support, or broad package-query permissions.

| Area | Responsibility |
| :--- | :--- |
| `NotificationMirrorService` | Reads active notifications, tracks progress candidates, and posts progress mirrors. |
| `MediaLiveController` | Tracks media notifications/sessions and posts media mirrors. |
| `MirrorNotificationBuilder` | Builds progress live notifications. |
| `MediaLiveNotificationBuilder` | Builds media live notifications. |
| `VisibilityState` | Tracks lock state, screen-off/AOD state, and Quick Settings expansion. |
| `QuickSettingsAccessibilityService` | Optional Quick Settings expansion detector. |
| `OriginalSuppressionController` | Best-effort reversible original progress notification suppression. |
| `PrivilegedAccess` | Shizuku-only temporary elevated setup and cleanup. |
| `MainActivity` | Permission setup, settings, test notification actions, and diagnostics. |

---

## 🛠️ Build From Source

### Requirements

- Android Studio
- Android SDK 36
- Java 21
- Android 16 / API 36 device or emulator for runtime testing

### Commands

```bash
# Build debug APK
./gradlew assembleDebug
```

```bash
# Run unit tests and lint
./gradlew testDebugUnitTest lintDebug
```

```bash
# Full verification
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/
```

---

## 📲 Manual Setup

After installing the APK:

1. Open **Live Progress**.
2. Grant notification posting permission.
3. Enable promoted/live notification access.
4. Enable notification listener access.
5. Optionally enable accessibility for Quick Settings hiding.
6. Optionally grant Shizuku permission for lock-screen original progress suppression.

Use **Post live notification test** inside the app to confirm Android live notification support.

---

## ⚠️ Limitations

- Android 16 / API 36+ only.
- Original notification hiding is best-effort and depends on Android system APIs, notification assistant access, Shizuku availability, and the source app notification channel.
- Custom notification layouts are not cloned.
- Multiple progress notifications can be tracked, but media mirrors are intentionally hidden while any progress mirror is active.

## 📄 License

This project is released under the [MIT License](LICENSE).

If you use this project or substantial parts of its code, acknowledgment or attribution to the original author is appreciated.
