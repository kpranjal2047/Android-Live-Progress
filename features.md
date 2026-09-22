# Live Progress Feature Reference

This document is a detailed implementation and product reference for Live Progress. It describes the behavior currently implemented in the Android project, including where Android or OEM behavior can limit an outcome.

## 1. Product scope

Live Progress is an Android 16+ companion app that observes eligible notifications and republishes them as Android live notifications. It focuses on three sources of live information:

1. Native determinate progress notifications, such as downloads and uploads.
2. Active media sessions and their media notifications.
3. User-selected notification categories that should remain visible as live notifications even when they do not publish native progress.

The app uses Android's `Notification.ProgressStyle` and promoted ongoing notification APIs. It does not modify source applications, clone arbitrary custom layouts into the live notification, or require a system modification framework.

## 2. Platform and installation requirements

### 2.1 Android version

- Minimum SDK: Android 16 / API 36.
- Target SDK: Android 16 / API 36.
- The app intentionally does not support earlier Android releases.
- Java and Kotlin are compiled for Java 21.

### 2.2 Required Android access

The normal notification mirror path requires the following Android access:

- App notifications permission, so Live Progress can post its own mirrors.
- Promoted notification / live notification access, so Android can promote eligible mirrors to the status-bar live presentation.
- Notification listener access, so the app can observe eligible source notifications and their changes.

Accessibility access is optional. It is required only for features that need to know whether Quick Settings is expanded or whether a source app is in the foreground.

Shizuku access is optional. It is used for explicit elevated features, such as temporary original-notification suppression and full installed-app notification-category discovery. Root access is not supported or attempted.

### 2.3 First-run setup

- If Shizuku is running but permission has not been granted, the setup flow presents Shizuku first.
- A user can skip Shizuku and continue through Android's manual permission pages.
- With Shizuku permission, the app can offer to grant the missing required accesses automatically. Each access is verified after the attempt; unsuccessful items fall back to the corresponding Android settings page.
- Automatic setup preserves other enabled accessibility services when adding Live Progress's accessibility service.
- If Shizuku is granted during the first onboarding flow, the app can automatically run one installed-app notification-category refresh before showing the main settings screen.
- Skipping or lacking Shizuku on that first run does not schedule a later automatic refresh. Later refreshes are user initiated.

## 3. User-facing settings

The main screen is organized into General, Progress Live Notifications, Media Live Notifications, Additional Live Notifications, Developer, and an inline About area.

### 3.1 General

- **Language**: selects Automatic or an app-specific language.
- **Hide mirrored notifications when Quick Settings is expanded**: hides mirrors while the expanded Quick Settings panel is visible. This setting needs accessibility access; enabling it without access starts the optional accessibility setup.
- **Hide status-bar pill when source app is in foreground**: suppresses the mirror/pill when the app that generated the source notification is foregrounded. This also relies on accessibility access.

### 3.2 Progress Live Notifications

- **Enable progress live notifications**: the master switch for native progress mirrors and supported custom progress sources.
- **Show live notifications on lock screen**: enables a Progress mirror while the device is locked with the screen on.
- **Show live notifications on AOD**: enables a Progress mirror while the screen is off / Always On Display is active.
- **Hide original notification on lock screen and AOD**: requests temporary source-notification suppression while a Progress mirror is active on those surfaces. The setting is only selectable when Shizuku is available and authorized.
- **Notification categories**: opens the separate Additional Live Notifications category-management screen.

Defaults for Progress are enabled, lock-screen mirroring off, AOD mirroring on, and original suppression preference on. The suppression control is shown unchecked and disabled whenever Shizuku is unavailable or unauthorized, because the preference alone cannot reliably suppress an original notification.

### 3.3 Media Live Notifications

- **Enable media live notifications**: master switch for media-session mirrors.
- **Status-bar text**: selects Title, Elapsed, or Remaining for the short critical text while the device is unlocked.
- **Scroll title in status bar**: enables scrolling for title mode; it is editable only when Title is selected.
- **Show live notifications on lock screen**: enables a Media mirror on the lock screen.
- **Show live notifications on AOD**: enables a Media mirror while the screen is off.

Defaults for Media are enabled, Title text, scrolling title enabled, lock-screen mirroring off, and AOD mirroring on.

### 3.4 Additional Live Notifications

Additional Live Notifications is independent from the Progress and Media master switches. It provides an opt-in fallback for notification categories the user wants mirrored even without native progress.

- **Notification categories** opens a dedicated page rather than rendering a potentially long list on the main screen.
- **Enable new notification categories automatically** is off by default. When off, newly observed categories are listed but disabled. When on, new categories are enabled with the current Progress visibility defaults.

### 3.5 Developer

- **Logging level**: Off, Normal, or Verbose. The default is Off.
- **Clear logs**: On app exit, After 1 hour, After 6 hours, After 1 day, or After 7 days. The default is After 1 hour.
- **Logs** opens the log viewer and file export action.

### 3.6 About

The inline About area displays the app icon, Live Progress name, version, author Kumar Pranjal, an end-user description, MIT license information, an attribution request, and a link to the source repository.

## 4. Progress notification mirroring

### 4.1 Standard Android progress notifications

Live Progress automatically accepts source notifications that expose valid Android progress extras. A qualifying source has determinate or indeterminate progress through the platform notification fields, rather than progress guessed from text.

For a standard candidate, the mirror copies or safely maps:

- title, body text, and subtext;
- source application label;
- source small notification icon when Android permits it;
- source large icon when available and safe;
- notification color;
- timestamp and content intent;
- determinate progress maximum/current values or indeterminate progress state;
- compatible source actions; and
- a source-specific visual payload identifier when custom artwork affects the mirror.

The mirror is ongoing and requests Android promoted-notification treatment. A determinate source produces a percentage critical text only when the source actually exposes a numeric progress signal. The app never derives a percentage from keywords or arbitrary notification text.

### 4.2 Unsupported or excluded sources

The standard Progress path excludes:

- media notifications, which use the Media pipeline;
- notifications already using Android live/progress presentation, to avoid mirroring a live notification again;
- source notifications that do not expose genuine native progress, unless their category has been explicitly enabled under Additional Live Notifications;
- unsafe actions, such as action types that require unsupported remote input or authentication.

### 4.3 Progress visibility rules

When the Progress master setting is the reason a source is mirrored:

| Device state | Progress mirror behavior |
| --- | --- |
| Unlocked, Quick Settings collapsed | Mirror is posted and can provide percentage critical text. |
| Unlocked, Quick Settings expanded | Mirror is hidden only when the global Quick Settings hide setting is enabled. |
| Locked, screen on | Mirror is posted only when Progress lock-screen visibility is enabled. |
| Screen off / AOD | Mirror is posted only when Progress AOD visibility is enabled. |
| Source app foreground | Mirror/pill is hidden only when the foreground-source setting is enabled and accessibility can identify the foreground source. |

If the source is visible through Additional Live Notifications instead, its per-category settings control lock screen, AOD, and original suppression instead of the Progress-level values.

### 4.4 Priority and alert behavior

- Mirrors shown on a lock-screen or AOD surface use the default-importance mirror channel.
- Mirrors posted while unlocked use a low-importance, silent channel.
- All mirror channels disable sound, vibration, and badges.
- A channel/priority change is part of the mirror snapshot, so changing the visibility context causes a necessary repost.

This separation keeps unlocked shade/status presentation quiet while allowing Android to treat a permitted lock-screen mirror as a normal-importance ongoing notification.

### 4.5 Original source suppression

Live Progress does not cancel, snooze, or dismiss the original notification as its normal hiding strategy. Source cancellation can stop future progress updates.

When the user enables Progress original suppression and Shizuku is authorized, the app attempts reversible visual suppression of the source category on lock screen and AOD:

1. It obtains temporary notification-assistant capability through the user-approved Shizuku bridge.
2. It inspects the source category and temporarily changes the lock-screen visibility to `SECRET` where Android permits it.
3. It keeps the original source notification active, so future updates still reach the listener.
4. It restores the source category visibility and releases temporary elevated state when the mirror no longer needs suppression.

Suppression is best effort. Android, OEM customizations, assistant ownership, source-category restrictions, or shell command behavior can prevent it. A failure leaves the mirror available where allowed and records a diagnostic event when logging is enabled.

## 5. Supported custom Progress sources

Custom source support is intentionally isolated and fails closed when an app changes its layout.

### 5.1 Uber trip notifications

Package: `com.ubercab`.

When Progress live notifications are enabled, the Uber extractor reads a recognized expanded rich notification layout outside the notification-listener callback. It can extract:

- expanded title and subtitle;
- visual progress-bar fraction;
- driver and vehicle artwork; and
- source context used to keep mirror updates tied to the original source key.

The visual bar fraction maps to clamped determinate progress from 0 to 1000. Driver and vehicle imagery are composed into a square large image for the live mirror. The standard Android template still supplies the mirror structure, source icon, actions, and progress bar.

If the recognized custom layout cannot be rendered or does not produce valid progress, the source does not get a guessed Progress mirror. It can still appear only through an enabled Additional Live Notifications category. A genuine native Android Progress notification from Uber continues to use the normal standard-progress path.

Uber extraction is coalesced by source key, runs away from the listener callback, verifies the source is still active before posting, and never routes an Uber trip notification into Media handling.

### 5.2 Domino's delivery notifications

Package: `com.Dominos`.

For a recognized `live_notification_layout`, the Domino's extractor reads the rendered custom layout to obtain:

- notification title;
- notification message;
- determinate progress-bar maximum and current value; and
- a delivery ETA from clear relative-duration or labeled-arrival wording.

The source must expose valid determinate visual progress. The expanded mirror preserves the title/message and progress bar. The short critical text prioritizes the live delivery countdown, for example `18 min`, rather than the calculated percent. A minute-boundary scheduler updates only when the displayed minute changes.

For terminal delivery states:

- **Reached** is shown as localized critical text when a reliable reached state is present.
- **Delivered** is shown as localized critical text when a reliable delivered state is present.
- The expanded live notification does not show a percentage for reached or delivered terminal cases.

When no reliable time or terminal state can be extracted, the critical text falls back to the determinate percentage. The extractor never estimates a delivery deadline from the progress bar. A changed source notification immediately replaces the current deadline; a newer notification without a readable delivery time clears the old deadline.

### 5.3 Where Is My Train notifications

Package: `com.whereismytrain.android`.

The WIMT extractor reads the current custom notification representation and uses its rendered route imagery to obtain information that normal extras do not expose reliably. It can derive:

- train header, including the train number and route/name;
- current state, such as departure status or current location;
- schedule state, including delay or ahead-of-schedule status;
- destination;
- remaining distance;
- ETA; and
- visual route-progress fraction.

The expanded live notification uses a three-line hierarchy designed for Android's `ProgressStyle` template:

1. Header/subtext: train number and route/name.
2. Main title: current status plus delay/ahead-of-schedule state.
3. Content: destination, remaining distance, and `Reaching <time>`.

The status-bar critical text uses the compact reaching time. It scrolls through a seven-character window when necessary, using the same efficient scheduling approach as media title scrolling. ETA does not become a progress percentage. A train that is ahead of schedule is represented as schedule status alongside the current train state.

Optical character recognition is used only as a text fallback for route imagery. It does not infer progress or manufacture missing ETA data. Failed layout/OCR extraction falls back only to an enabled Additional Live Notifications category.

## 6. Media live notifications

### 6.1 Media source tracking

Media is handled by a dedicated pipeline rather than the notification-progress classifier. The app uses `MediaSessionManager` active sessions and media-controller callbacks to follow metadata, playback state, position, duration, actions, artwork, and source intents.

The controller caches viable media-session candidates and refreshes the active-session list when sessions change, a source changes, or the selected controller no longer produces valid state. It does not scan all active sessions every second.

### 6.2 Media mirror content

When visible, a Media mirror can include:

- source application icon as small icon;
- title;
- artist and album/provider text where available;
- source content intent;
- album art while appropriate for the current surface;
- determinate playback progress when duration is known;
- Previous, Play/Pause, and Next actions when supported by the session; and
- a dismiss action.

Playback actions are delivered through the app's non-exported `MediaControlReceiver`. The receiver controls the selected media session; it is not exposed to other apps.

### 6.3 Media text

While unlocked, the chosen media pill mode controls the short critical text:

- **Title**: track title, optionally scrolling in seven-character windows.
- **Elapsed**: elapsed playback time.
- **Remaining**: remaining playback time.

Unknown values are hidden instead of represented by placeholder ellipses. Known text that overflows uses ellipsis or controlled scrolling as appropriate.

On AOD, media text always uses elapsed/total duration when duration is known. It is independent of the status-bar pill setting. Album art is intentionally omitted from AOD mirrors.

### 6.4 Media visibility rules

| Device state | Media mirror behavior |
| --- | --- |
| Unlocked, Quick Settings collapsed | Mirror is posted and supplies configured critical text. |
| Unlocked, Quick Settings expanded | Mirror is hidden only when the global Quick Settings hide setting is enabled. |
| Locked, screen on | Mirror is posted only when Media lock-screen visibility is enabled. |
| Screen off / AOD | Mirror is posted only when Media AOD visibility is enabled. |
| Progress mirror active | Media mirror is hidden everywhere, including status bar, lock screen, and AOD. |
| Source app foreground | Mirror/pill is hidden only when the foreground-source setting is enabled and detectable. |

The global Progress-priority rule avoids competing media and progress live presentations. As soon as no Progress candidate remains active, the Media controller reevaluates and can post its mirror again.

### 6.5 Efficient media updates

- No periodic media tick runs while the mirror is hidden.
- A one-second update is used only when visible elapsed/remaining/AOD time must change.
- A 500 ms update is used only for enabled title scrolling.
- Media notification work is coalesced so that only one build runs at a time and newer requests replace stale queued work.
- A snapshot prevents reposting an unchanged media notification.
- Album-art decoding and repeated action/content-intent reconstruction are avoided when source content has not changed.

## 7. Additional Live Notifications

### 7.1 Purpose

Some valuable notifications do not publish native progress and are not media sessions. Additional Live Notifications gives the user an explicit opt-in way to mirror those notifications as indeterminate live notifications. It never fabricates a percentage for these mirrors.

### 7.2 Notification-category discovery

The app records notification categories it observes through the notification listener. An observed entry contains:

- application package;
- Android user identifier derived from the UID;
- notification category identifier;
- application label;
- category label, with the raw identifier as fallback;
- last-seen time; and
- source/system-app metadata used for presentation and filtering.

The application-user-category key avoids collisions for the same package installed under different Android users or work profiles.

Without Shizuku, the page lists categories the listener has already observed. With an authorized Shizuku bridge, a user can request a complete installed-app scan. The scan uses temporary assistant access to inspect installed applications' categories.

### 7.3 Category page

The separate Additional Live Notifications page provides:

- grouped applications with their icons and category rows;
- an application-level toggle that enables or disables all observed categories for that app;
- a per-category main toggle;
- compact per-category settings shown only when that category is enabled;
- **Turn all on** and **Turn all off** actions;
- a system-app show/hide toggle; and
- an optional Shizuku-powered refresh action.

The category page loads icons lazily and caches them. It maintains scroll position while settings change, avoiding a return to the top and minimizing work for large category lists.

### 7.4 Per-category settings

An enabled category exposes these behavior controls:

- **Show on lock screen**.
- **Show on AOD**.
- **Hide original notification**.
- **Keep live notification after original is dismissed**.

When a category is first enabled, its lock-screen, AOD, and original-suppression values copy the current Progress settings. Keep-after-dismiss defaults to off. Auto-enabled new categories use the same Progress-derived defaults.

The Hide original control is unavailable and unselected when Shizuku-backed suppression is unavailable.

### 7.5 Routing precedence

The app applies a deliberate precedence order:

1. A true Progress notification uses Progress settings when Progress live notifications are enabled.
2. A Media source uses Media settings when Media live notifications are enabled.
3. An enabled Additional category is used only when the normal Progress or Media path is unavailable or disabled.
4. A non-progress/non-media enabled category creates an indeterminate ProgressStyle mirror using that category's settings.

Selecting a category never overrides the normal Progress or Media policy when that normal policy is active. It is an independent fallback layer.

### 7.6 Retained additional mirrors

For Additional-forced, ProgressStyle mirrors only, the user can keep the last mirror after the original is dismissed. The retained mirror:

- keeps its last known title, text, and progress state;
- obeys the category's lock screen, AOD, and original-suppression settings;
- updates/replaces when a later source notification arrives for the same source/category; and
- is removed when the user taps Dismiss, disables the category, changes settings so it is ineligible, reloads/restarts the notification service, or a new matching source replaces it.

Normal Progress and Media mirrors continue to disappear when their source disappears. Retention does not survive an app/service restart.

### 7.7 Full Shizuku refresh

When Shizuku is available, the page exposes **Refresh notification categories with Shizuku**.

- If Shizuku is running but permission is missing, tapping requests Shizuku permission and does not begin scanning.
- Once authorized, a confirmation warns that scanning can take several minutes and cannot be cancelled after it begins.
- The user sees a full-page preparation/progress state instead of the category list.
- Progress is reported as installed applications inspected over total installed applications, with a determinate percentage.
- Back navigation is consumed during the active scan; the scan has no cancel control.
- Completion, partial success, and failure restore the regular category page with a resulting status.
- Temporary notification-assistant state is released on every terminal path.

## 8. Generic image-text extraction

Live Progress includes optional, offline generic OCR for certain eligible source notifications whose textual extras are incomplete. It uses on-device ML Kit text recognition.

- OCR runs off the notification-listener callback on a single background worker.
- Requests are coalesced by source key and version so obsolete work is discarded.
- It is limited to eligible non-media sources where text is missing and either native Progress or an enabled Additional category justifies a mirror.
- OCR contributes text only. It never estimates percentage, progress, ETA, or state from arbitrary imagery.
- WIMT reuses this OCR capability for route visual text.
- Extraction errors are diagnostic-only and do not crash the listener or block the Additional-category fallback.

## 9. Mirror interaction behavior

### 9.1 Open source

When a source notification provides a safe content intent, tapping the mirror opens the source application's original destination.

### 9.2 Source actions

Compatible source actions are copied conservatively, with unsafe remote-input or authentication-dependent actions excluded. The number of copied actions is bounded to keep the native live presentation usable.

### 9.3 Dismiss

Mirrored live notifications include an explicit dismiss action. This gives the user a way to remove an ongoing mirror even when Android's lock-screen live-notification interaction does not make an ongoing notification dismissible in the usual way.

The dismiss action affects the mirror. It does not dismiss the original source notification.

## 10. Quick Settings, lock screen, AOD, and foreground awareness

### 10.1 Visibility state tracking

`VisibilityState` tracks screen on/off, user presence, and locked state through Android broadcasts. It treats screen-off as locked and performs delayed reconciliation after an unlock to avoid stale lock-state transitions.

### 10.2 Quick Settings

The optional accessibility service watches System UI state to identify expanded Quick Settings. It throttles recursive accessibility scans and uses event/window hints before doing expensive traversal. If the service is not enabled, expanded Quick Settings cannot be detected, and mirrors remain visible instead of being hidden speculatively.

### 10.3 Foreground source application

The same optional accessibility capability can identify when a source application is foregrounded. When the setting is enabled, a mirror from that foreground source is hidden to avoid duplicating information inside the source app.

## 11. Notification channels created by Live Progress

Live Progress creates separate, non-alerting channels for its own mirrors:

- Mirrored live progress, default importance.
- Mirrored live progress, low importance.
- Media live notifications, default importance.
- Media live notifications, low importance.

All channels disable sound, vibration, and badges. Default importance is used for lock-screen/AOD presentation; low importance is used for unlocked presentation. Android owns final user-visible channel controls in App info.

## 12. Background operation and recovery

The app does not run a permanent foreground service. Android binds the notification listener while access remains enabled.

The app registers a boot/package-replacement receiver to:

- create mirror channels;
- register visibility tracking;
- request listener and assistant rebinds; and
- schedule a constrained refresh of active notifications.

Startup refresh is throttled so an immediate listener connection does not result in redundant active-notification work. The app attempts stale temporary-suppression cleanup after startup.

OEM background restrictions can still affect Android service delivery. Behavior is best on devices that keep notification-listener services enabled and unrestricted.

## 13. Diagnostics and log export

### 13.1 Log format and storage

Logs are newest first and use the format:

```text
[yyyy-MM-dd HH:mm:ss] [log_type] message
```

Common types include `listener`, `mirror`, `media`, `visibility`, `suppression`, `background`, and `privileged_setup`.

There is no fixed entry-count cap. The selected retention policy controls removal.

### 13.2 Levels

- **Off**: stores no new diagnostic events.
- **Normal**: stores settings changes, setup events, major state transitions, errors, and suppression outcomes. Repeated equivalent messages are deduplicated.
- **Verbose**: stores Normal events plus useful high-frequency debug information, including mirror update reasoning, session work, parser results, retry/fallback decisions, and scheduling details.

### 13.3 Retention

- Timed retention removes old events when logs are written, when the app opens, and when the Logs page opens.
- On app exit clears logs after all Live Progress activities are no longer visible, with a short grace interval so moving between app pages does not falsely count as exit.
- Background notification services do not keep the UI alive for the purpose of the On app exit policy.

### 13.4 Export

The Logs page exports through Android's system file-create picker (`ACTION_CREATE_DOCUMENT`). The user chooses a folder and file name. The app does not directly write into Downloads or another user folder without that selection.

## 14. Localization and accessibility

### 14.1 Languages

The app supports Automatic plus these app-specific languages:

- English
- Hindi
- Spanish
- French
- Arabic
- Bengali
- Portuguese
- Russian
- Urdu
- Japanese
- German
- Italian
- Indonesian
- Turkish
- Vietnamese
- Korean
- Thai
- Persian
- Malay
- Tamil

Language names in the selector use native-language names. Automatic clears the app-specific locale and follows the system locale. App-specific selection uses Android's native `LocaleManager.applicationLocales` API and recreates the UI immediately.

### 14.2 RTL and dynamic appearance

- The manifest enables RTL support.
- Arabic, Urdu, and Persian receive RTL-safe layouts and translations.
- The UI follows system light/dark mode.
- It derives its palette from Android system colors rather than a fixed green brand color.
- The interface uses native Android views with Material 3 Expressive-inspired spacing, cards, rows, dropdowns, and clear switch states.

## 15. Data handling, backup, and privacy boundaries

### 15.1 Local data

The app stores preferences locally, including display settings, language, developer settings, enabled Additional-category selections, and selected category behavior.

It does not require Internet permission, a cloud account, broad installed-package visibility, or `QUERY_ALL_PACKAGES`.

### 15.2 Android backup and device transfer

Android cloud backup and device transfer include normal shared preferences. The app explicitly excludes:

- diagnostics/logs;
- temporary privileged state;
- temporary original-suppression state;
- the full observed notification-category inventory; and
- first-run onboarding state.

Only enabled Additional-category selections are persisted for backup, rather than every notification category ever observed. After restore, the listener can rebuild the observed list from newly seen notifications or a Shizuku refresh.

Cloud backup requires encryption capability as declared in `data_extraction_rules.xml`.

### 15.3 Elevated-operation scope

Shizuku commands run only after the user grants Shizuku authorization. Original-notification suppression is temporary and restored after it is no longer needed. No root binary, `su` command, or root fallback exists in the application.

## 16. Performance and battery practices

The app reduces notification and CPU churn through:

- progress and media snapshots that skip unchanged `notify` calls;
- global progress candidate tracking rather than repeated full recalculation;
- media-controller callbacks and active-session change listeners instead of per-second active-session scans;
- coalesced media builds that preserve only the newest request;
- conditional time updates only when visible text needs a clock tick;
- 500 ms updates only for deliberate scrolling text;
- no recurring tick while a Media mirror is hidden;
- cached/lazy image and icon work where possible;
- off-main-thread custom RemoteViews rendering and OCR;
- stale-version checks before publishing background extraction results;
- Quick Settings accessibility throttling; and
- diagnostic deduplication and logging-level control.

Internal battery counters can report media scans, posts, skipped reposts, progress posts, progress skipped reposts, and skipped startup refreshes when diagnostics are enabled.

## 17. Testing coverage and verification targets

The project contains focused unit tests for:

- notification classification and category selection;
- Progress math and snapshots;
- Media visibility, text formatting, scheduling, build coalescing, and session refresh choices;
- Additional-category serialization, defaults, bulk toggles, and precedence;
- setup-flow and first-run refresh decisions;
- Shizuku category-refresh state and cleanup;
- Uber custom progress routing and visual mapping;
- Domino's ETA parsing, terminal delivery status, and routing;
- WIMT parsing, route progress, text formatting, and routing;
- OCR eligibility and text extraction behavior; and
- mirror priority decisions.

Standard project verification is:

```text
./gradlew --no-daemon --console=plain --no-watch-fs assembleDebug testDebugUnitTest lintDebug
```

Manual verification on Android 16 remains important because promoted/live-notification presentation, AOD behavior, lock screen layout, notification assistant behavior, and OEM service policy are ultimately controlled by the installed system image.

## 18. Important limits

- Android and OEM System UI decide whether a requested promoted notification is rendered as a live pill/card.
- Other apps' custom RemoteViews cannot be cloned wholesale into a promoted live notification; Live Progress uses the native ProgressStyle template for eligibility and stability.
- Original-notification hiding is best effort and can fail even with authorized Shizuku.
- The app cannot reliably highlight or blink its own row inside Android's system settings screens.
- Android notification listener/media/accessibility behavior can be restricted by OEM battery management.
- Custom extractors for Uber, Domino's, and WIMT depend on their source layouts and safely fall back when those layouts change.
- A notification-category scan can take several minutes on a device with many installed apps, and the confirmed scan intentionally cannot be cancelled from the category page.
