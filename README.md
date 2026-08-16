![Live Progress icon](docs/assets/live-progress-icon.svg)

# Live Progress

Beautiful Android 16 live notifications for progress tasks and media playback.

![Android 16+](https://img.shields.io/badge/Android-16%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

**Status bar pills** • **Lock screen mirrors** • **AOD updates** • **Media progress** • **System colors**

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

- Uses an adaptive launcher icon that supports Android themed icons.

### 📊 Progress Mirrors

- Shows eligible progress notifications as Android 16 live notifications.
- Supports eligible Uber trip notifications with live trip progress and driver/car artwork.
- Displays progress percentage in the status bar when the original notification provides real progress.
- Keeps expanded mirror content updated in real time.
- Supports multiple progress notifications.
- Can optionally show progress mirrors on the lock screen and AOD.
- Includes a Dismiss action for live mirrors that cannot be swiped away on the lock screen.
- Lets you choose whether the status bar pill hides while the source app is open.

### 🎵 Media Live Notifications

- Shows media playback as a live notification when enabled.
- Supports status bar text modes: **Title**, **Elapsed**, and **Remaining**.
- Shows media progress and playback actions.
- Can show media progress on AOD.
- Includes a Dismiss action for the mirrored media notification.
- Uses the same source-app status bar pill setting as progress mirrors.
- Automatically hides media mirrors when a progress mirror is active.

### 🧩 Additional Live Notifications

Some apps use useful notification categories without exposing real Android progress. Live Progress lets you open **Notification categories** under **Additional Live Notifications** and choose which observed categories should mirror as live notifications.

- Categories appear after Live Progress has observed notifications from those apps.
- Apps are grouped clearly, with notification categories listed underneath.
- Selected categories mirror as indeterminate live notifications when the original has no real progress.
- If a selected category later exposes real progress, the mirror shows that real progress.
- A Shizuku refresh option can find notification categories from installed apps.
- System app categories are hidden by default and can be shown from the categories page.
- Progress and media notifications keep their normal behavior when their main live notification settings are enabled.
- Each selected category shows compact controls for lock screen visibility, AOD, original notification hiding where supported, and keeping the live mirror after the original is dismissed.
- New categories stay off by default unless automatic enabling is turned on.
- Live Progress does not guess percentages from notification text.

### 🎨 Native Look

- Uses Android’s system color palette.
- Supports light and dark mode.
- Uses a Material 3 Expressive-inspired settings screen.

---

## 👀 Where Mirrors Appear

- 🔓 **Unlocked:** progress and media mirrors can show live status bar pills.
- 📱 **Source app open:** mirrors can hide when foreground detection is available.
- ⚙️ **Quick Settings expanded:** mirrors can hide when the optional setting is enabled.
- 🔒 **Lock screen:** progress and media mirrors are optional.
- 🌙 **AOD / screen off:** progress and media mirrors are optional, with progress taking priority.

When a progress mirror is active, media mirrors are hidden everywhere so progress gets priority.

---

## 🔐 Permissions

Live Progress asks for permissions only when needed.

- 🔔 **Notifications:** posts mirrored live notifications and test notifications.
- ⭐ **Live notification access:** enables Android 16 promoted/live notification behavior.
- 👂 **Notification listener:** reads eligible notifications from other apps.
- ⚙️ **Accessibility service:** optional. Detects expanded Quick Settings and foreground apps so mirrors can hide when they should.
- 🧰 **Shizuku:** optional. Helps hide original progress notifications on the lock screen and AOD when enabled.

Live Progress does **not** use root.

---

## 📲 Setup

1. Install and open **Live Progress**.
2. Follow the startup permission pages.
3. Enable the features you want from the main settings screen.
4. Use **Post live notification test** to confirm live notifications are working.

Optional setup pages can be skipped. Skipping an optional permission turns off the feature that needs it.

Your Live Progress settings and selected notification categories can be restored with Google Backup or a device transfer. Permissions, temporary notification-hiding state, and developer logs are not restored.

---

## 🎛️ Main Settings

### General

- Language
- Hide mirrored notifications when Quick Settings is expanded
- Hide the status bar pill while the source app is open

### Progress

- Enable progress live notifications
- Show live notifications on lock screen
- Show live notifications on AOD
- Hide original notification on lock screen and AOD, when supported

### Media

- Enable media live notifications
- Show live notifications on lock screen
- Show live notifications on AOD
- Status bar text mode
- Scroll title in status bar

### Additional Live Notifications

- Notification categories
- Enable new notification categories automatically
- Per-category lock screen, AOD, original notification, and keep-after-dismiss controls

### Developer

- Logging level
- Verbose logging for detailed troubleshooting traces
- Clear logs timing
- Logs page with smooth long-log scrolling and file export using the system file picker

### About

- Inline app card with version, license, attribution note, and source-code link

---

## ⚠️ Notes And Limits

- Android 16 / API 36+ is required.
- Some manufacturers may customize live notification behavior.
- Hiding mirrors while the source app is open requires the optional accessibility service.
- Original notification hiding is best-effort and may not work for every app or device.
- Custom notification layouts are not copied exactly.
- User-selected notification categories mirror as indeterminate updates unless the original notification exposes real progress.

---

## 🔏 Private Local Builds

Maintainer builds can use a private signing key by copying `keystore.properties.example` to `keystore.properties` and filling in local key details. The real `keystore.properties` file and key files are ignored by Git.

Only builds signed with the same private key can upgrade an existing installed copy, so public builds from this repository cannot replace the maintainer-signed app.

---

## 📄 License

This project is released under the [MIT License](LICENSE).

If you use this project or substantial parts of its code, acknowledgment or attribution to the original author is appreciated.
