# Fresh-install validation — 2 November 2026

Device: OnePlus 9 Pro (LE2121), Android 16, connected through wireless ADB. Installed the existing privately signed debug APK after the user cleared data and uninstalled the app. No replacement signing key or package rename. The development APK uses version 1.2.0/code 4 through a temporary build override; repository `version.properties` remains 1.1.1/code 3.

## Automated results

| Check | Result |
| --- | --- |
| Debug build | Passed |
| Unit tests | 203 passed, zero failures/errors |
| Release-script tests | 19 passed |
| Lint | Passed |
| `git diff --check` | Passed |
| On-device smoke runner | 21 passed, repeated successfully after test-harness fixes |
| Pending release calculation | 1.2.0/code 4; only patch/minor records; no forced major-release restriction |

The device runner and synthetic notification activity live only in the test APK. See [device-testing.md](device-testing.md) for commands and scope. No production app behavior was changed during this validation task.

## Device observations

| Area | Result and evidence |
| --- | --- |
| Fresh setup | Existing Shizuku authorization was recognized without another prompt. Automatic setup listed only missing access and completed. Fresh visibility defaults were enabled for Progress, Media AOD, Torch, and Hotspot. |
| Mandatory listener access | Temporarily removing the app's listener access returned it to setup. Only the missing listener requirement was offered; granting through setup restored the service. |
| Missing optional access | Shizuku revocation disabled original-hiding controls without erasing their saved choice. Hotspot remained operational and cleared the previous count. Accessibility controls also correctly displayed off/disabled while access was missing and returned to on after restoration through the app's setup page. |
| Help | Granted-access Accessibility and Hotspot popups showed Close without Grant. Missing-access help offered a Grant action that opened the app's setup page first. Short prerequisite heading and one-line explanations observed. Labels/help remained readable and usable beside disabled switches. |
| Category page | App notifications naming, automatic-category setting below Show system apps, and refresh navigation verified. Missing Shizuku authorization routed refresh through setup, then resumed at the refresh confirmation. Refresh completed. |
| System status page | Two groups, each using Enable live notification and the combined visibility label; all defaults on. No footer text in the observed granted state. Hotspot help had no redundant authorization action. |
| Torch | Platform callback posted the card. Quick Settings did not remove it. Card content intent was absent. Turn off invoked the receiver and the callback confirmed torch off/card removal. Dismissal lasted until reactivation. Disable/re-enable and controller teardown/reconnect passed. |
| Hotspot | Real zero-client state showed Hotspot on. One external device produced 1 Device Connected. Revoking Shizuku cleared that count; restoring access recovered it. Disconnecting the device returned Hotspot on. Stable ID and BigTextStyle verified; no progress style. |
| Hotspot actions | Card tap opened system Wi-Fi hotspot settings. Turn off opened the approved settings fallback on this phone; direct system-owned hotspot shutdown was not permitted. Switching off in settings removed the card. The device's own automatic no-client shutdown also removed the card. |
| Quick Settings | With the global hiding preference enabled, the real media mirror disappeared under the panel while Hotspot remained. Torch behavior also passed the on-device controller test. |
| Media surfaces | Real synthetic media mirror present unlocked and screen-off, absent on awake lock screen. Original media controls remained on the awake lock screen. User explicitly confirmed Test media visible on physical AOD. |
| Category-enabled media | Dedicated Media disabled and synthetic app category enabled: category eligibility controlled AOD. Category AOD-off and dedicated Media AOD-off each left an active source without a mirror. Clean-channel screen-off suppression changed lock-screen visibility to SECRET (-1); waking the lock screen removed the mirror and restored the channel to its original default (-1000). |
| Media icons/artwork/actions | Platform tests verified original small icon, app-icon fallback, generic fallback, artwork, original action label/icon/pending intent, and no generated actions. Real source icon/action changes reached the mirror. Clicking the copied Updated action invoked the test source and removed source plus mirror. |
| Progress presentation | Platform tests covered known progress and unknown progress/duration/position. A real indeterminate download and unknown-duration media used BigTextStyle without a progress bar. Original actions and delete intents remained. Built-in live test notification had no added actions. |
| Appearance | Home page inspected in light/dark modes and at 150% text size. Labels wrapped and controls remained visible. Dark background was visually black and matches the explicit black palette. System status and category page structure inspected through UI hierarchy. |
| Short idle observation | After removing synthetic notifications and turning logging Off, the unchanged app process used 0.16 CPU seconds across 42.0 seconds wall time (about 0.38% of one core). This is a short CPU sample, not a battery-drain benchmark. |
| Data and cleanup | Customer app retained its new setup. Synthetic notifications canceled; companion APK removed; test category selections/observations and diagnostic logs cleared. Fresh defaults, logging Off, dark mode, and original 100% text size and 30-second screen timeout restored. |

## Test-harness issues resolved

The standalone companion initially lacked the target application's Kotlin runtime; its fixture activity was converted to Java. Scenario configuration initially exited before selected-category writes reliably completed; explicit persistence was added. Instrumentation restarts and screen-state timing produced stale test state, so the category restoration result was accepted only after a clean synthetic channel and a confirmed active source/listener. The final smoke run passed. These were test-harness issues, not confirmed production app defects.

## Coverage limits and remaining work

These are not claimed as hardware passes:

- Two or more simultaneous hotspot clients and real cross-band deduplication: only one external device was available. Parser tests cover zero/one/multiple, duplicate identifiers, historical data, and malformed/ambiguous dumps.
- Shizuku actually uninstalled or server stopped while the page is open: authorization loss/recovery was exercised, but uninstall/stop was not. Android rejected the reversible package-hide simulation with MANAGE_USERS permission denial; Shizuku was left installed. Availability/setup policy has unit coverage.
- Forced privileged-read stalls/timeouts, output-size overflow, and overlapping-read stress on the device: bounded-read code reviewed; stale/generation/failure behavior covered by unit tests. No artificial service-level stall was injected.
- Full physical on/off visibility matrix for every Progress, Torch, Hotspot, and app-category switch on every surface. Shared policies have automated coverage; real media/category-media transitions and system-card coexistence were exercised as described above.
- Original-channel suppression recovery across abrupt process death remains distinct from the successfully tested normal screen-wake restoration.
- Long-duration battery drain, thermal behavior, overnight AOD, and multi-hour reconnect soak. A short idle CPU observation is not a battery-life benchmark.
- Spoken TalkBack navigation and visual review of every translation/RTL layout. Resource parity/format tests passed; English light/dark and large-text layouts were inspected.
- Fresh-install manual rejection paths for notification posting and promoted-notification access: setup policy tests cover them, but this phone's complete fresh setup used the authorized automatic route.
- Successful direct hotspot shutdown on an OEM that permits it, and fallback timeout rather than immediate rejection. This phone validated the rejection/settings path.

No raw Wi-Fi client diagnostics, client identifiers, credentials, or personal notification content are included in this report.
