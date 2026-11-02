![Live Notifications app icon](docs/assets/live-progress-icon.svg)

# Live Notifications

Live notifications for the updates you care about most on Android 16 and newer.

![Android 16+](https://img.shields.io/badge/Android-16%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

**Progress at a glance** | **Media controls** | **Lock screen** | **Always-on display**

---

## What Live Notifications Does

Live Notifications turns supported notifications into easy-to-glance live updates. It keeps the original app notification active, so its information can continue updating normally.

- Track downloads, uploads, and other progress tasks.
- See media playback title, time, progress, and controls.
- Follow supported Uber trips, Domino's deliveries, and Where Is My Train updates.
- Keep selected notifications visible as live updates, even when an app does not provide a progress bar.
- Choose where updates appear: status bar, lock screen, and always-on display.
- Status-bar text longer than seven characters scrolls automatically for all live notifications.

## Highlights

### Progress Updates

- Shows supported progress notifications as live updates.
- Shows a percentage only when the original notification supplies genuine progress.
- Keeps the expanded update current as the original notification changes.
- Supports multiple active progress updates.
- Follow supported Uber trips with arrival time in the status bar, destination text, a progress bar without percentage text, and available driver and vehicle artwork.
- Can show Domino's order progress, delivery time, and reached/delivered status.
- Shows Where Is My Train reaching time in the header, train number and name in the title, and current status, distance labeled as “km remaining,” and ahead/behind information below the title.
- Shows time until train arrival in the status-bar pill, such as `18 min` or `1h 12m`; clears expired or stale estimates and shows `Arrived` only when the source confirms the destination.
- Reads train details from their positions in the notification, keeping destination names and arrival times separate from shortened train names and last-update times.

### Media Updates

- Shows the currently active media playback as a live update.
- Lets you choose title, elapsed time, or remaining time for the status-bar text.
- Includes playback progress and available previous, play/pause, and next controls.
- Shows useful playback time on the always-on display.
- Automatically gives progress updates priority when both progress and media are active.

### Additional Live Notifications

Open **App notifications** to choose notification categories from your apps that should always appear as live updates. Automatic activation of new categories is configured below **Show system apps** on that page.

- Browse categories grouped under each app.
- Turn on all categories for an app, or choose them individually.
- Use one switch per category to show updates on both the lock screen and always-on display, or neither.
- Keep a selected live update after its original notification is dismissed, when needed.
- New categories remain off until you choose them, unless you enable automatic activation.
- Optionally refresh the category list to discover more notifications from installed apps.

### System Status

Torch and Hotspot each show a separate live notification while active. Both are enabled by default; their lock-screen/AOD switches start on.

- Torch shows **Torch on**.
- Hotspot shows **Hotspot on**, or the number of connected devices when available through optional Shizuku access. Failed or timed-out count reads clear the previous count and return to **Hotspot on**.
- Use **Enable live notification** in each group under **Additional Live Notifications → System status**.
- The Hotspot info popup briefly explains that Shizuku is needed for device counts; it does not ask again when access is already granted.
- Media updates use the original app’s notification icon and buttons. Live notifications add no Dismiss button; normal swipe dismissal remains available where Android permits it.

## Where Updates Appear

| Place | What you control |
| --- | --- |
| Status bar | Short progress, delivery, train, or media text. |
| Quick Settings | App notification mirrors can hide while Quick Settings is expanded. Torch and Hotspot stay visible. |
| Lock screen and always-on display | Combined switches for Progress, each app category, and each system state. Media uses “Show on AOD” and never mirrors on the awake lock screen. |
| Source app open | Optionally hide the duplicate status-bar update while using the original app. |

## Getting Started

Download the latest APK from [GitHub Releases](https://github.com/kpranjal2047/Android-Live-Progress/releases/latest). Each release includes its version and a summary of what changed. The same version appears in the app's About section.

1. Install and open **Live Notifications**.
2. Follow the guided permission setup. Each step explains the features it enables; access already granted is skipped.
3. Choose whether to use progress updates, media updates, and additional live notifications.
4. Use **Post live notification test** to confirm the live presentation is available on your phone.

Accessibility access enables hiding duplicate updates while Quick Settings or the source app is open. Optional Shizuku access can grant missing setup permissions, hide original notifications on the lock screen/AOD, discover app notification categories, and read hotspot device counts where supported. You can set up manually without Shizuku; Hotspot still shows its on/off state when counts are unavailable.

## Settings You Can Adjust

### General

- App language (including train and delivery countdowns, distance labels, and arrival messages)
- Hide updates while Quick Settings is expanded
- Hide the status-bar update while the original app is open

### Progress

- Enable progress live notifications
- Show updates on the lock screen and always-on display together
- Hide the original notification on the lock screen and always-on display, where supported

### Media

- Enable media live notifications
- Choose status-bar text
- Show updates on the lock screen and always-on display

### Additional Live Notifications

- Choose notification categories
- Automatically enable newly discovered categories
- Set lock-screen, always-on-display, original-notification, and keep-after-dismiss behavior for each category

### Developer

- Enable Normal or Verbose logs when troubleshooting
- Choose how long logs are retained
- View and export logs using Android's file picker

## Privacy And Backup

- Live Notifications works on your phone and does not need an Internet connection.
- Your settings and enabled notification categories can be restored with Google Backup or a device transfer.
- Permission choices, temporary notification-hiding state, discovered-category history, first-run setup state, and developer logs are not restored.
- Live Notifications does not use root.

## Notes

- Android 16 or newer is required.
- Live-update appearance can vary across phone brands and Android versions.
- Hiding an original notification is optional and may not work for every app or phone.
- Custom app notifications may change after the source app is updated. Live Notifications will continue to show the original notification when a special live update cannot be created.

## License

Released under the [MIT License](LICENSE).

If you use this project or substantial parts of its code, acknowledgment of the original author is appreciated.

Settings that need optional accessibility or Shizuku access have an info icon. Mandatory notification permissions are handled during setup. Tap it for a one-line prerequisite under a short “Prerequisites” heading, without changing the setting. System status keeps all Hotspot access explanations inside its info popup, with no footer text. Shizuku authorization is offered there only when running without permission.

Progress lock-screen/AOD visibility and Media AOD visibility are enabled by default. Existing saved choices are preserved; media mirrors remain hidden on the awake lock screen.

If accessibility or Shizuku access required by a setting is missing, its switch appears off and disabled without changing your saved choice. Restoring access restores that choice. The info popup opens the app’s corresponding setup page. Hotspot stays available without Shizuku; only connected-device counts depend on it.

When Shizuku is not installed, its setup page is skipped and info popups show no Shizuku grant button. Installing Shizuku makes setup available again.

Disabled settings keep their labels and info icons readable; only the switch is greyed out.

Refreshing notification categories opens Shizuku setup first when access is missing, then returns to refresh confirmation after access is granted. The refresh button is hidden when Shizuku is not installed. Permission requests always start from the app’s setup pages.

Dark mode uses true-black page backgrounds for AMOLED screens, with readable dark cards and controls. Background work is event-driven where possible: Hotspot device counts are polled only while its card is visible and Shizuku access is available, and accessibility scanning follows the enabled hiding settings. Live scrolling and notification updates keep their existing behavior.

Live notifications show a progress bar only when progress is known. Unknown progress or media duration/position uses a text-only live card, retaining its icons and actions.

Torch, Hotspot, and other cards without known progress use text notification layouts, so Android does not add a default progress bar.

Torch and Hotspot live cards include **Turn off**. Tapping the Hotspot card opens system hotspot settings; tapping the Torch card does nothing. If Android refuses direct hotspot control, Turn off opens hotspot settings so you can stop it there. No new permissions are requested by these actions.
