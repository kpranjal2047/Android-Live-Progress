![Live Progress app icon](docs/assets/live-progress-icon.svg)

# Live Progress

Live notifications for the updates you care about most on Android 16 and newer.

![Android 16+](https://img.shields.io/badge/Android-16%2B-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue)

**Progress at a glance** | **Media controls** | **Lock screen** | **Always-on display**

---

## What Live Progress Does

Live Progress turns supported notifications into easy-to-glance live updates. It keeps the original app notification active, so its information can continue updating normally.

- Track downloads, uploads, and other progress tasks.
- See media playback title, time, progress, and controls.
- Follow supported Uber trips, Domino's deliveries, and Where Is My Train updates.
- Keep selected notifications visible as live updates, even when an app does not provide a progress bar.
- Choose where updates appear: status bar, lock screen, and always-on display.

## Highlights

### Progress Updates

- Shows supported progress notifications as live updates.
- Shows a percentage only when the original notification supplies genuine progress.
- Keeps the expanded update current as the original notification changes.
- Supports multiple active progress updates.
- Can show supported Uber trip progress, including driver and vehicle artwork.
- Can show Domino's order progress, delivery time, and reached/delivered status.
- Can show Where Is My Train progress, route, destination, distance, schedule information, and expected reaching time.

### Media Updates

- Shows the currently active media playback as a live update.
- Lets you choose title, elapsed time, or remaining time for the status-bar text.
- Includes playback progress and available previous, play/pause, and next controls.
- Shows useful playback time on the always-on display.
- Automatically gives progress updates priority when both progress and media are active.

### Additional Live Notifications

Choose notification categories from your apps that should always appear as live updates.

- Browse categories grouped under each app.
- Turn on all categories for an app, or choose them individually.
- Choose lock-screen and always-on-display visibility for every selected category.
- Keep a selected live update after its original notification is dismissed, when needed.
- New categories remain off until you choose them, unless you enable automatic activation.
- Optionally refresh the category list to discover more notifications from installed apps.

## Where Updates Appear

| Place | What you control |
| --- | --- |
| Status bar | Short progress, delivery, train, or media text. |
| Quick Settings | Live updates can hide while Quick Settings is expanded. |
| Lock screen | Choose whether progress, media, and selected categories appear. |
| Always-on display | Choose whether progress, media, and selected categories appear while the screen is off. |
| Source app open | Optionally hide the duplicate status-bar update while using the original app. |

## Getting Started

1. Install and open **Live Progress**.
2. Follow the guided permission setup.
3. Choose whether to use progress updates, media updates, and additional live notifications.
4. Use **Post live notification test** to confirm the live presentation is available on your phone.

Some optional features request access only when you enable them. For example, hiding duplicates in expanded Quick Settings needs Accessibility access, and hiding an original notification on the lock screen or always-on display needs optional Shizuku access.

## Settings You Can Adjust

### General

- App language
- Hide updates while Quick Settings is expanded
- Hide the status-bar update while the original app is open

### Progress

- Enable progress live notifications
- Show updates on the lock screen
- Show updates on the always-on display
- Hide the original notification on the lock screen and always-on display, where supported

### Media

- Enable media live notifications
- Choose status-bar text
- Scroll long song titles
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

- Live Progress works on your phone and does not need an Internet connection.
- Your settings and enabled notification categories can be restored with Google Backup or a device transfer.
- Permission choices, temporary notification-hiding state, discovered-category history, first-run setup state, and developer logs are not restored.
- Live Progress does not use root.

## Notes

- Android 16 or newer is required.
- Live-update appearance can vary across phone brands and Android versions.
- Hiding an original notification is optional and may not work for every app or phone.
- Custom app notifications may change after the source app is updated. Live Progress will continue to show the original notification when a special live update cannot be created.

## License

Released under the [MIT License](LICENSE).

If you use this project or substantial parts of its code, acknowledgment of the original author is appreciated.
