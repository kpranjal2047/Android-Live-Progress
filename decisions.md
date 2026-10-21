# Live Progress Engineering Decisions

This document records the current product and engineering decisions in Live Progress. Each entry states the choice, why it was made, and the practical consequence. It is intended to make future changes deliberate rather than accidental.

## 1. Product boundary

### Android 16+ only

**Decision:** Support Android 16 / API 36 and newer only.

**Why:** Promoted ongoing/live notification behavior is the core product. Supporting older APIs would require separate, weaker implementations and feature flags that could not deliver the same result.

**Consequence:** The project can use Android 16 platform APIs directly, keeps a smaller compatibility surface, and does not offer a downgraded pre-Android-16 experience.

### Companion app, not a System UI modification

**Decision:** Build an ordinary installable Android app using notification listener, media session, accessibility, and optional Shizuku access. Do not use LSPosed, SystemUI hooks, or a modified ROM.

**Why:** A standard APK is broadly installable and reviewable. System modification approaches require a different trust model, root/system integration, and device-specific maintenance.

**Consequence:** Some System UI outcomes, especially exact original-notification suppression and AOD rendering, remain best effort and OEM-dependent.

### Native ProgressStyle template instead of cloned custom RemoteViews

**Decision:** Rebuild source information in Android's native `Notification.ProgressStyle` template rather than copying an app's custom `RemoteViews` layout.

**Why:** Custom layouts can disqualify a mirror from promoted/live-notification presentation and cannot be trusted to render safely in another app's process. The native template gives Android a consistent, eligible structure.

**Consequence:** Source notification layouts are represented semantically, not pixel-for-pixel. Supported custom sources get targeted extraction for their meaningful data and art.

## 2. Notification eligibility and routing

### Real progress only for automatic Progress mirrors

**Decision:** Automatically mirror only notifications with genuine Android determinate/indeterminate progress extras, plus explicitly supported custom visual-progress sources.

**Why:** Keyword-based percentage guesses are misleading and can produce a status-bar percentage that does not correspond to source state.

**Consequence:** Non-progress notifications are never automatically treated as Progress mirrors. They require an enabled Additional Live Notifications category.

### No estimated percentage from text

**Decision:** Never estimate a percentage from title/body keywords, OCR text, ETA text, or arbitrary progress wording.

**Why:** The percentage is a status-bar critical signal and should be accurate enough to be trusted.

**Consequence:** Indeterminate mirrors do not show a fabricated percentage. Custom visual bars may provide a determinate value only when their rendered fraction can be read reliably.

### Separate Progress and Media pipelines

**Decision:** Route Progress and Media through separate controllers.

**Why:** Media state is session-driven and needs playback callbacks, timeline calculation, and transport actions. Progress notifications are notification-driven and need source-notification lifecycle handling. Combining both would make filtering and scheduling fragile.

**Consequence:** Media notifications are excluded from the generic Progress classifier, while media-specific data/actions remain available.

### Progress wins over Media

**Decision:** Hide all Media mirrors when at least one eligible Progress mirror is active.

**Why:** Android live surfaces and status-bar space are scarce. A real task-progress mirror takes priority over a simultaneous media mirror, avoiding competing pills and confusing AOD output.

**Consequence:** Media reappears only after no active Progress candidate remains. The policy is global, not package-specific.

### Additional categories are fallback, not an override

**Decision:** An enabled Additional Live Notifications category is used only when normal Progress or Media behavior is unavailable or its master toggle is disabled.

**Why:** Users expect native Progress and Media settings to remain authoritative for their own notification types. A category selection should add coverage, not silently replace established behavior.

**Consequence:** A notification matching both a normal path and Additional selection uses normal Progress/Media visibility, priority, pill text, and lifecycle rules.

### Additional non-progress notifications are indeterminate

**Decision:** A selected non-progress/non-media category becomes an indeterminate ProgressStyle mirror.

**Why:** This makes the notification eligible for the same live presentation without pretending it has a measurable completion value.

**Consequence:** The mirror can display text, icon, intent, and safe actions, but no invented percentage.

### Separate custom extractors and fail-closed behavior

**Decision:** Keep Uber, Domino's, and WIMT support in dedicated extractors that only accept recognized layouts and valid results.

**Why:** These apps publish custom layouts whose implementation may change at any release. Generic parsing would be brittle and could misrepresent unrelated notification states.

**Consequence:** A changed or malformed source layout produces no special mirror. The notification can still use an enabled Additional category, and genuine native progress remains eligible through the standard path.

## 3. Custom source decisions

### Uber visual progress

**Decision:** For a recognized Uber trip layout, derive determinate progress from the rendered visual bar and compose driver/car art into a large image.

**Why:** Uber's useful trip progress may not be available through native progress extras. The rendered layout carries the state users see in the original notification.

**Consequence:** Uber produces a richer Progress mirror when extraction succeeds. The source small icon and live-notification frame remain native, while driver/vehicle imagery becomes optional large artwork.

### Current Uber trip layout

**Decision:** Recognize the current `ub__ooa_layout_rider_trip_expanded` layout alongside the existing rich layout. Scope shared text IDs to their header/body containers, use the current driver/vehicle IDs, and read only visible progress elements. Use Uber's expanded edge-distance resource for the visual-bar calculation; accept the dedicated simple progress widget only with a valid determinate range.

**Why:** On-device inspection showed that Uber replaced the outer layout and text/artwork IDs while retaining a padding-based progress bar. Header and body share the same child ID, and hidden decorative progress widgets contain unrelated zero values. A global text lookup or arbitrary progress-widget search would capture incorrect data.

**Consequence:** The existing Progress controls remain unchanged. Missing artwork does not prevent valid text/progress from being mirrored. PIN and vehicle registration are not copied into the mirror. Unknown custom layouts fail closed to the opt-in Additional path rather than using placeholder extras. Diagnostic failure reasons identify layout/read failures without storing trip text.

### Uber ETA instead of percentage text

**Decision:** Keep Uber's determinate progress bar, disable appended percentage text, and use a source-header ETA for short critical text. Accept valid clock times and English hour/minute durations; use an explicit empty override if ETA cannot be read.

**Why:** Arrival time is more useful than trip percentage for this notification. An empty override prevents the generic builder from falling back to percentage. Restricting extraction to the trip header avoids interpreting destination numbers as an ETA.

**Consequence:** The current `Drop-off at 4:12 PM` header produces a `4:12 PM` pill. Source updates replace that value; no ETA is inferred from bar progress and no independent countdown is introduced. Longer ETA strings scroll using the existing shared formatter. Other progress-notification types keep their existing percentage behavior.

### Uber does not fall back to raw extras for a failed custom trip layout

**Decision:** When a notification is recognized as Uber's custom trip layout but cannot be extracted, do not reinterpret raw extras as progress.

**Why:** Those fields may be incomplete or unrelated to actual trip advancement. Treating them as progress reintroduces the inaccurate-data problem.

**Consequence:** Only a genuinely native Uber progress notification uses standard Progress handling. Otherwise the enabled Additional-category fallback is the only automatic alternative.

### Domino's countdown comes from delivery wording, not bar progress

**Decision:** Use clear relative-time or labeled-arrival text to establish a Domino's delivery deadline; never derive delivery time from the progress bar.

**Why:** Order-stage completion and actual arrival time are different quantities. A visual order bar cannot reliably predict delivery minutes.

**Consequence:** The Domino's pill shows remaining minutes only when the source text supports it. It reverts to actual progress percentage when no reliable delivery time exists.

### Domino's terminal critical text

**Decision:** Show localized `Reached` or `Delivered` critical text for terminal Domino's states, and omit percentage from the expanded terminal mirror.

**Why:** A completion percentage is less useful once delivery has reached a terminal state. The state label is clearer and shorter.

**Consequence:** Users see the outcome rather than stale or artificial `100%` text.

### WIMT line hierarchy

**Decision:** Map WIMT arrival ETA to `Reaching <ETA>` subtext, train number/name to title, and current status, remaining distance, and ahead/behind information to content text. Label the distance as `<distance> remaining` so its meaning is explicit. Omit missing fields and on-time schedule text.

**Why:** Android's promoted ProgressStyle card has a stable header, title, and content hierarchy. Long custom layouts cannot be copied, and newline-heavy content is frequently clipped by System UI.

**Consequence:** Arrival time is visible in the header and the train identity gets the prominent title placement. Operational details share one middle-dot-separated content line. Missing ETA leaves the header empty; destination remains extracted but is not displayed in the content line.

### WIMT time until arrival in the status pill

**Decision:** Show compact time until arrival in the train pill while keeping `Reaching <ETA>` in the header. Use ceiling minutes, compact hours/minutes, and `<1 min` below one minute. Only a fresh source status that explicitly names the complete destination as arrived/reached may produce `Arrived`.

**Why:** Time until arrival answers the immediate travel question and uses compact localized units. Longer translations use the shared scroller. Delay/ahead variance remains contextual information. A passed prediction is not proof of arrival.

**Consequence:** Date the clocks against the source post and last-update time, convert to a fixed monotonic deadline, and reuse the visible-only countdown scheduler. Clear the pill at ETA expiry or 30 minutes after the source update. Require a valid update clock. Later same-day ETAs are accepted; an earlier ETA clock wraps to the next day only when it is more than 12 hours behind the update clock. Resolve midnight relative to the source snapshot so expired ETAs cannot become tomorrow's arrival. Fresh source updates replace the snapshot; display ticks never refresh its age or repeat OCR. Delivery countdown behavior remains unchanged.

### WIMT fields follow the route-card layout

**Decision:** Preserve OCR element bounds and parse the recognized route image using normalized row/column regions. Keep numeric train number, arbitrary train name, last-update time, source, destination, current status, distance, ETA, and schedule status separate. Validate 12-hour clock ranges and normalize distance spacing. Prefer a complete station label from the two destination positions; never infer stations from train-name wording or ETA from header time. Use direct collapsed status/schedule text before image text. Missing ETA explicitly suppresses percentage fallback in the pill.

**Why:** OCR block order can differ from visual row order, and the train header may be truncated while station labels remain complete. The header clock is last update, not arrival. Row and column positions carry the field semantics even when names contain numbers, punctuation, or multiple dashes.

**Consequence:** OCR remains enabled and runs once per route image. Its geometry drives field assignment for the supported layout, while bitmap colors still drive progress. Incomplete recognition leaves fields absent instead of guessing. Presentation is independent of parsing; source and update time are available separately in the extracted data.

### OCR is generic text recovery only

**Decision:** Provide generic on-device OCR for eligible image-heavy notifications, but restrict its output to missing text recovery.

**Why:** OCR can safely improve title/body context where a source used graphics, but interpreting text as progress or state without source-specific rules would be unreliable.

**Consequence:** OCR never creates a percentage, ETA, or progress value by itself. WIMT uses it for route text within its specialized, validated parser.

## 4. Source lifecycle and suppression

### Preserve the original source notification

**Decision:** Do not normally cancel, snooze, or dismiss an original source notification to hide it.

**Why:** Cancellation can stop future updates, break a media session's lifecycle, and make a live mirror stale.

**Consequence:** The source remains active while the mirror follows its updates. Hiding is treated as a visual-suppression concern, not a lifecycle action.

### Shizuku-only elevated access

**Decision:** Use Shizuku for elevated commands. Remove all root and `su` fallback paths.

**Why:** Root behavior is device-specific, increases risk, and conflicts with the desired standard-installable trust model. Shizuku provides explicit user-mediated shell access.

**Consequence:** Elevated functions are unavailable without Shizuku. The app never probes for or attempts root access.

### Temporary notification-assistant state

**Decision:** Acquire notification-assistant capability only for the duration needed to inspect/suppress original notification categories, then restore prior assistant state.

**Why:** Assistant access can be sensitive and persistent. The app should not occupy that role permanently for a short-lived visual operation.

**Consequence:** Suppression setup is more complex and can fail on OEM variants, but it avoids a permanent elevated system change for normal mirror use.

### Suppress by temporary lock-screen category visibility

**Decision:** When allowed, temporarily set the source notification category's lock-screen visibility to `SECRET` instead of cancelling the source.

**Why:** This is a non-destructive way to keep the source alive while trying to prevent duplicate lock-screen/AOD display.

**Consequence:** The effect can be category-wide for the source category during the temporary period, is best effort, and must be restored carefully. The app logs failure rather than performing destructive fallback cancellation.

### Suppression control disabled when impossible

**Decision:** Show original-suppression settings as unselected and non-editable when Shizuku is unavailable or not authorized.

**Why:** Presenting an enabled control that cannot work creates a false guarantee.

**Consequence:** Users can see why the setting is unavailable and are only asked for Shizuku when they choose a feature that needs it.

### Retained mirrors only for Additional ProgressStyle fallback

**Decision:** Allow a category-controlled mirror to remain after its source is dismissed only for Additional-forced ProgressStyle mirrors.

**Why:** The feature is intended for user-chosen persistent reminders. Applying it to normal Progress or Media can preserve stale task/media state and violates their expected source-following lifecycle.

**Consequence:** Retention is explicit, runtime-only, and does not survive a service restart. A new source for the same key/category replaces the retained presentation.

### Explicit mirror dismiss action

**Decision:** Include an in-mirror dismiss action.

**Why:** Ongoing/live notifications are not always normally dismissible from the lock screen. The user needs a reliable way to remove a mirror without altering the original source.

**Consequence:** Dismiss removes the mirror only. It does not attempt to cancel the origin app's notification.

## 5. Surface visibility and importance

### Lock screen and AOD are separate settings

**Decision:** Give Progress, Media, and Additional categories independent controls for lock-screen and AOD visibility.

**Why:** Users commonly want quiet AOD awareness but not a full lock-screen live notification, or the inverse. Android treats screen-off/AOD and screen-on lock states differently.

**Consequence:** Surface rules must be evaluated for every mirror state transition, rather than using a single generic locked flag.

### Screen off is treated as locked

**Decision:** Model screen-off/AOD as a locked state while retaining a distinct `screenOff` dimension.

**Why:** It matches Android's privacy/visibility semantics while preserving enough detail to apply the separate AOD switch.

**Consequence:** AOD rules work consistently without conflating them with a screen-on lock screen.

### Low importance while unlocked, default importance while locked/AOD

**Decision:** Post unlocked mirrors through silent low-importance channels and lock-screen/AOD mirrors through default-importance channels. Disable sound, vibration, and badge behavior on both.

**Why:** Unlocked mirrors mainly support the small live/status presentation and should not draw attention in the shade. A permitted lock-screen mirror needs normal Android prominence without audible alerting.

**Consequence:** Context changes may require a repost to a different channel. Channel selection is included in mirror snapshots to ensure such transitions are not deduplicated incorrectly.

### Global Quick Settings hiding

**Decision:** Use one global setting for hiding all mirrors while Quick Settings is expanded.

**Why:** Quick Settings is a system-wide surface where duplicate mirrors are distracting. A single rule is easier to understand than separate per-pipeline settings.

**Consequence:** Accessibility becomes optional but required when this setting is enabled. If accessibility is unavailable, the app leaves mirrors visible rather than guessing panel state.

### Foreground-source pill hiding

**Decision:** Let users hide a mirror while its own source app is foregrounded.

**Why:** The source app often already presents richer current information, making the duplicate live pill unnecessary.

**Consequence:** This remains optional and depends on accessibility-derived foreground information. It is not enforced when detection is unavailable.

### Progress priority hides Media everywhere

**Decision:** Treat an active Progress candidate as a global Media visibility blocker on all surfaces.

**Why:** This provides deterministic competition handling, including AOD where two information streams are especially cluttered.

**Consequence:** Media is not merely stripped of critical text; it is hidden entirely until Progress is inactive.

## 6. Media decisions

### Use MediaSessionManager instead of notification parsing

**Decision:** Obtain media metadata and controls from active `MediaController` instances.

**Why:** Media-session APIs provide canonical playback state, timeline, transport controls, metadata, and content intents. Parsing a media notification would be less reliable and would lose transport semantics.

**Consequence:** The app tracks active sessions and callbacks separately from notification classification.

### Preserve core media controls

**Decision:** Include previous, play/pause, and next where the selected session exposes them.

**Why:** A live media mirror should be operational, not only informational.

**Consequence:** Actions are routed through a non-exported receiver to the current session, and unavailable controls are omitted.

### AOD uses elapsed/total, not status-bar preference

**Decision:** AOD media text always uses elapsed/total duration when duration is known.

**Why:** The AOD needs a stable playback context independent of whether the user prefers title, elapsed, or remaining in the status bar.

**Consequence:** Changing the media pill mode does not change AOD time format.

### No album art on AOD

**Decision:** Omit album art from AOD media mirrors.

**Why:** AOD needs concise, low-power information and artwork does not add enough value to justify visual clutter or display work.

**Consequence:** Album art remains useful on other visible mirror surfaces but is intentionally absent on AOD.

### Short scrolling window

**Decision:** Automatically scroll every critical-text value longer than seven user-visible characters in a seven-character window at 500 ms intervals. Remove the Media scrolling preference and toggle. Apply this rule to Progress, Media, and Additional mirrors, with pauses at the start and end and Unicode-safe boundaries.

**Why:** Android status-pill width is constrained. Short deterministic windows make long titles/ETAs readable without assuming a particular device width.

**Consequence:** Short and exactly-seven-character text stays static. Long titles, time values, localized statuses, and train ETAs share one formatter. Scrolling stops while hidden; paused media can still scroll its title. Complete text remains separate from displayed windows, and the same computed frame is used by the builder and deduplication snapshot. Changing source/title/status or mode restarts the animation, while changing countdown/time digits preserves its phase. Previously saved toggle values are ignored without migration.

## 7. Additional-category discovery decisions

### Notification categories rather than broad application scanning by default

**Decision:** Build the default list from notifications actually observed by the listener.

**Why:** It avoids broad package visibility, reflects categories that matter to the user, and removes the need for installed-app scanning during normal use.

**Consequence:** A category may not appear until its app posts a notification, unless the user invokes the optional Shizuku refresh.

### Shizuku refresh is explicit and non-cancellable after confirmation

**Decision:** A full installed-app category refresh needs an explicit warning/confirmation and has no in-page cancel option after it starts.

**Why:** Scanning can take several minutes and relies on temporary assistant setup. Mid-operation cancellation creates cleanup and partial-state complexity that could leave a misleading list.

**Consequence:** The page replaces the category list with preparation/progress UI, consumes Back, and always runs terminal cleanup. Android Home, process death, and force-stop remain outside the app's control.

### Group category settings by application

**Decision:** Present categories hierarchically under application rows with app-level enable/disable controls.

**Why:** A large flat list is hard to scan and makes it expensive to opt into all categories for one app.

**Consequence:** App-level selection changes only its observed categories. Partial state remains visible through row styling/status rather than silently assuming all are enabled.

### User ID belongs in the category key

**Decision:** Key category data by package, Android user identifier, and platform category ID.

**Why:** A package name alone is not globally unique across primary, work, or secondary Android users. UID-derived user separation prevents duplicate/mismatched configuration.

**Consequence:** The list can distinguish the same package across profiles while still resolving readable app labels.

### Auto-enable is off by default

**Decision:** New notification categories are visible but disabled unless the user enables automatic category activation.

**Why:** Mirroring every new category can be noisy and may expose unexpected notifications to live surfaces.

**Consequence:** Users opt in deliberately. Users who prefer broad coverage can turn on auto-enable, which applies Progress-derived visibility defaults to newly discovered categories.

### Copy Progress settings when enabling a category

**Decision:** Snapshot the current Progress lock-screen, AOD, and original-suppression choices into a category at the moment it becomes enabled.

**Why:** Progress represents the application's default live-notification posture. Copying gives an intuitive starting point while allowing categories to diverge later.

**Consequence:** Later Progress setting changes do not silently rewrite individual category behavior.

### System-app filtering is presentation-only

**Decision:** Keep system categories observable but allow the user to hide/show them in the category page.

**Why:** Some users need system categories, while most want a shorter app-focused list. Omitting them entirely would make the scanner incomplete.

**Consequence:** The underlying data remains available while the user controls visual noise.

### Lazy icon loading and stable scroll position

**Decision:** Load app icons lazily/cached and retain page position across category setting changes.

**Why:** Rendering every application icon synchronously and recreating the list after every toggle can create ANRs, slow scrolling, and jarring jumps to the top.

**Consequence:** The category page stays responsive for large device inventories, though an icon may appear after its text row initially renders.

## 8. Permission and setup decisions

### Shizuku first when available

**Decision:** In onboarding, request Shizuku permission before other missing accesses when Shizuku is present.

**Why:** One approved Shizuku connection can offer automatic setup and first-run category discovery, reducing repeated detours through Android settings.

**Consequence:** Users who do not want elevated access can skip it and use manual setup without being blocked.

### Automatic setup remains optional

**Decision:** After Shizuku is granted, present `Grant automatically` and `Grant manually` rather than silently changing Android settings.

**Why:** Notification listener, accessibility, promoted notifications, and app-notification access may be persistent system settings. The user should explicitly choose automation.

**Consequence:** Automatic setup can be convenient, but manual setup remains transparent and available. Each automatic result is verified and failures have a manual fallback.

### Accessibility is feature-gated, not universally required

**Decision:** Do not block basic app use on accessibility. Ask for it only when the user enables Quick Settings hiding or foreground-source pill hiding.

**Why:** Accessibility is more sensitive than basic notification observation and is not necessary for all users.

**Consequence:** Without accessibility, the app cannot reliably identify expanded Quick Settings or foreground-source status and leaves mirrors visible under those conditions.

### No in-main-screen Shizuku grant button

**Decision:** Do not expose a generic Shizuku authorization button in ordinary main settings.

**Why:** Shizuku should be requested in context, only when the user selects a feature that requires it or starts a refresh.

**Consequence:** The UI avoids prompting for elevated access without a clear user goal.

### Direct system settings pages, no fake highlighting

**Decision:** Open Android's relevant settings pages for manual permissions but do not attempt to animate/highlight Live Progress's row inside System Settings.

**Why:** Public Android APIs do not provide a reliable way for a third-party app to blink or highlight its own service row in system settings.

**Consequence:** The user receives contextual instructions and direct navigation, but final row selection remains controlled by System Settings.

## 9. Performance and reliability decisions

### Snapshot deduplication before notify

**Decision:** Build lightweight snapshots for Progress and Media mirrors and skip `NotificationManager.notify` when visible payload is unchanged.

**Why:** Frequent reposts churn System UI, consume CPU, and can cause visual instability without delivering new information.

**Consequence:** Snapshots include meaningful fields such as progress, text, actions, icons, visual payload key, visibility mode, and channel/priority so legitimate changes still post.

### Coalesce asynchronous media and custom-source work

**Decision:** Allow one media build at a time and retain only the latest pending request; apply stale-version checks to custom extraction results.

**Why:** Playback and notifications can emit bursty updates. Processing every queued intermediate state wastes work and can publish stale data.

**Consequence:** The UI converges on the newest state with less work. Extractors verify their source is still active before posting.

### Event-driven media session refresh

**Decision:** Use active-session change listeners and controller callbacks instead of calling `getActiveSessions` every second.

**Why:** Session scans are comparatively expensive and unnecessary when an active controller remains valid.

**Consequence:** The app rescans only for session-list changes, source changes, or invalid controller state.

### Schedule ticks only for visible time/text changes

**Decision:** Run recurring updates only when elapsed/remaining/AOD clocks or scrolling text are currently visible.

**Why:** Background ticking while hidden cannot change user-visible output and drains battery.

**Consequence:** Short title text alone does not require recurring updates; visible clocks still receive time updates, and long critical text receives scrolling updates. Hidden mirrors have no periodic update loop.

### Off-callback custom rendering/OCR

**Decision:** Render RemoteViews and run OCR outside notification-listener callbacks.

**Why:** Listener callbacks must remain responsive; rendering/OCR can be slow and failures should not interfere with normal notification observation.

**Consequence:** Work is serialized/coalesced in background, with diagnostics on failure and normal fallback behavior preserved.

### Throttled Quick Settings inspection

**Decision:** Throttle accessibility scans and prefer event/window clues before recursive System UI traversal.

**Why:** Accessibility tree traversal can be expensive during panel animations and frequent state updates.

**Consequence:** QS hiding remains responsive while reducing repeated hierarchy scans.

### Start-up refresh throttling

**Decision:** Skip a delayed active-notification refresh if the listener is disconnected or a fresh listener connection already did equivalent work.

**Why:** Opening the app should not cause duplicate scans for active source notifications.

**Consequence:** Existing notifications can still be picked up after startup without redundant work.

## 10. Data, backup, and logging decisions

### Back up settings, not transient inventories or logs

**Decision:** Include normal shared preferences in Android cloud backup/device transfer but exclude logs, temporary elevated/suppression state, full observed categories, and first-run flow state.

**Why:** Users benefit from restoring deliberate choices, while transient inventories and diagnostic history can be stale, bulky, or privacy-sensitive.

**Consequence:** Enabled Additional-category choices are retained, but the complete observed-category list is rebuilt after restore.

### Cloud backup requires encryption capability

**Decision:** Use Android data extraction rules with encrypted cloud backup capability required.

**Why:** Preferences can reveal a user's notification-category choices and application behavior.

**Consequence:** Backup participation follows Android's encryption-capable backup environment.

### Logs off by default

**Decision:** Default diagnostic logging to Off.

**Why:** Background logging has storage, privacy, and performance cost. Most users do not need internal event history during ordinary use.

**Consequence:** A user or tester must choose Normal or Verbose before new logs are saved.

### Verbose means useful debugging detail

**Decision:** Make Verbose retain Normal events plus detailed state, parser, scheduling, retry, scan, and mirror reasoning that can aid debugging.

**Why:** Debugging notification behavior often requires context that a concise error alone cannot provide.

**Consequence:** Verbose may produce substantially more records; retention remains user controlled.

### Time-based retention instead of entry-count cap

**Decision:** Do not cap logs by count. Prune based on a selected duration or app exit.

**Why:** A fixed count can discard the most relevant period during high-volume debugging. A time window is easier for users to reason about.

**Consequence:** Very verbose activity can create many entries within the chosen retention window.

### System document picker for export

**Decision:** Export logs through Android's file-create document picker.

**Why:** The user should choose the destination and file name; direct Downloads writes require unnecessary storage assumptions and can surprise users.

**Consequence:** No broad storage permission or hard-coded export folder is needed.

## 11. UI and accessibility decisions

### Native views, not Compose

**Decision:** Use native Android views for all screens.

**Why:** The project already uses view-based settings and notification-service integration. Introducing Compose would add a second UI architecture without solving a product need.

**Consequence:** Styling and state updates remain consistent with existing code and dependencies stay smaller.

### System-derived color palette

**Decision:** Derive UI color from Android system colors instead of a fixed green theme.

**Why:** Live Progress is a system-adjacent utility. It should feel integrated with the user's wallpaper/theme and maintain contrast in light/dark modes.

**Consequence:** Appearance varies intentionally by device theme; UI controls must be checked against system colors for off-state visibility.

### Material 3 Expressive-inspired layout

**Decision:** Use clear sections, restrained cards, familiar switches, dropdowns, compact rows, and system color styling inspired by Material 3 Expressive principles.

**Why:** The app has dense operational settings. The design needs strong hierarchy without marketing-style decoration or nested card clutter.

**Consequence:** Functionality remains the primary interface focus and control states must remain visible in both system themes.

### Inline About section

**Decision:** Keep Smartspacer-inspired About content inline on the main page rather than a separate About Activity.

**Why:** The information is short and belongs with settings. An extra navigation page adds friction without offering a richer workflow.

**Consequence:** Version, author, MIT/license note, and source link appear in the final main-page section.

### App-specific locale API

**Decision:** Use `LocaleManager.applicationLocales`, with an empty locale list for Automatic.

**Why:** Android 16 supplies a native per-app locale mechanism; no AppCompat compatibility layer is needed.

**Consequence:** The language changes immediately by recreating the activity, remains visible to Android's per-app language settings, and falls back to system locale in Automatic mode.

### Native language names in the selector

**Decision:** Display language options in their native-language forms.

**Why:** It helps users identify their language independently of the currently selected UI locale.

**Consequence:** The list mixes scripts intentionally and needs stable layout/RTL support.

### Full localization of visible static content

**Decision:** Localize UI labels, setup explanations, notifications emitted by Live Progress, channel labels, actions, diagnostics empty states, and About content. Leave raw exception text, shell output, package names, identifiers, and historical stored log messages unchanged.

**Why:** User-facing content should be readable in the selected language; machine-generated/debug data must remain exact for diagnosis.

**Consequence:** Locale resource keys and formatting arguments must stay synchronized; unit tests enforce parity across all supported locales. Resolve countdown, distance, schedule, arrival, and missing-title wording through resources at render time. Preserve raw train fields and delivery states so retained notifications can be translated again after a language change. Invalidate posted snapshots and in-flight media builds on language changes. Source app wording and parser recognition rules remain separate from output localization, while old log records may remain in the language used when they were written.

## 12. Dependency and security decisions

### Minimal dependency set

**Decision:** Avoid Compose, AndroidX Media libraries, image-loading libraries, foreground-service dependencies, Internet permission, and broad package-query declarations.

**Why:** The application needs a small, auditable footprint for a notification utility. Platform APIs cover the core requirements.

**Consequence:** Some UI/media conveniences are implemented with platform code, while the dependency graph stays focused on Activity KTX, Shizuku, and on-device ML Kit OCR.

### Scoped package visibility

**Decision:** Declare only known package queries needed by special custom extractors: Uber, Domino's, and Where Is My Train.

**Why:** Broad package visibility is unnecessary for listener-observed categories and conflicts with least-privilege principles.

**Consequence:** Special-source availability can be checked without `QUERY_ALL_PACKAGES`; general category discovery remains listener/Shizuku based.

### Non-exported internal receivers and activities

**Decision:** Keep mirror-dismiss, media-control, Logs, and category-selection components non-exported unless Android requires an exported service binding.

**Why:** These components are internal control surfaces and should not accept arbitrary third-party invocations.

**Consequence:** The manifest exposes only the activities/services Android must discover, with protected binding permissions where applicable.

### Adaptive icon and monochrome support

**Decision:** Use a modern adaptive app icon with a monochrome layer designed for Android themed icon treatment.

**Why:** Android launchers and System UI may mask or recolor icons. A minimal, complete vector construction works across current icon shapes and themed modes.

**Consequence:** The application icon is not reused as a mirror fallback where that would misrepresent the source notification; mirror icons prefer source notification assets or a neutral internal fallback.

## 13. Documentation and verification decisions

### README remains customer-facing

**Decision:** Keep `README.md` focused on user capabilities, supported notification types, required setup, and high-level limitations.

**Why:** A public repository's first page should help users decide whether the app meets their need, rather than document every implementation detail or historical bug fix.

**Consequence:** This `features.md` and `decisions.md` hold the deeper technical reference while the README stays approachable.

### Documentation follows source changes

**Decision:** Update documentation whenever a product capability, behavior, or supported source changes.

**Why:** Notification behavior has platform-dependent subtleties; stale documentation is especially harmful for permission and lock-screen expectations.

**Consequence:** Code changes should include corresponding README/reference updates and tests when behavior changes.

### Unit tests plus device verification

**Decision:** Test deterministic policy, parsing, snapshot, preferences, and scheduling logic with unit tests; verify actual live rendering on Android 16 devices.

**Why:** Android System UI controls final promoted/live behavior, AOD layout, and some permission/suppression outcomes, which unit tests cannot fully simulate.

**Consequence:** A green build proves implementation consistency but not every OEM's live-notification presentation. Device testing remains a release requirement for surface-specific changes.

### Standard verification command

**Decision:** Use assemble, unit test, and lint together for full project verification.

**Why:** Kotlin compilation, resource processing, unit behavior, and Android lint catch different failure classes.

**Consequence:** The normal release-quality check is:

```text
./gradlew --no-daemon --console=plain --no-watch-fs assembleDebug testDebugUnitTest lintDebug
```

## 14. Release automation decisions

**Decision:** Use reviewed per-change records instead of inferring versions from commit wording or sending code to an AI service. Agent/contributor instructions require classification from the actual request and diff.

**Why:** GitHub cannot read the development chat, and a diff alone does not reliably identify compatibility breaks. Records make the intended impact explicit and testable while permitting ordinary commit messages.

**Decision:** Maintain a single version file consumed by Gradle and the existing home-screen `BuildConfig.VERSION_NAME` display. Increment the integer code once per release and choose the highest pending semantic impact.

**Why:** APK metadata, displayed version, tag, and release must agree. Independent version codes ensure Android upgrade ordering without fragile semantic-number arithmetic.

**Decision:** Publish only after tests, lint, signed assembly, and certificate/version validation. Push release metadata and tag atomically, reject concurrent changes, and recover unfinished drafts before calculating a new version.

**Why:** Failed builds must not consume published versions; failed uploads must not create duplicate releases or rewrite an existing binary. Keeping publishing in one workflow also avoids relying on token-created tags to trigger other workflows.

**Decision:** Look up releases using the GitHub CLI's draft-aware command and fail on authentication/network errors. The published-release REST tag endpoint does not find drafts, so using it would strand the first upload and break retry recovery.

**Decision:** Generate one non-overwritten permanent key and use it for owner debug/release builds and trusted GitHub releases. Private material is ignored locally, uploaded only as encrypted Actions secrets, excluded from pull requests/artifacts, and removed from runners afterward.

**Why:** Stable signatures permit future upgrades while preventing unrelated public builders from replacing the owner's APK. A release must fail rather than silently switch to a debug key. Existing differently signed device installs require explicit one-time replacement, never automatic data deletion.

## 15. Known tradeoffs accepted by design

1. Original-notification suppression is not guaranteed because it depends on Android/OEM assistant and channel behavior; the app favors non-destructive source continuity over forced cancellation.
2. Custom app support is layout-sensitive; safe failure and an explicit Additional-category fallback are preferred over permissive but inaccurate parsing.
3. Media is deliberately hidden while Progress is active to prioritize one live state over multiple simultaneous pills.
4. Shizuku-enabled automation can create persistent Android permission/service state only after explicit user approval; ordinary suppression state is restored when no longer needed.
5. Accessibility is optional, so QS/foreground awareness may be unavailable by user choice.
6. Category discovery favors least privilege and observed data; a complete scan is deliberately opt-in, slow, and non-cancellable after confirmation.
7. AOD and live-notification final appearance are owned by Android/OEM System UI, not guaranteed by an app-level API request.
