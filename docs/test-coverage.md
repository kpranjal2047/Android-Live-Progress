# Regression coverage

Every area in the Live Notifications change checklist is tracked below. JVM tests validate deterministic behavior; Android tests validate framework objects and callbacks; physical tests validate System UI/OEM behavior. A unit-test pass is not a claim that a physical display or privileged Android service was exercised.

## Automated commands

```sh
python3 -m unittest discover -s scripts/tests -v
./gradlew --no-daemon --console=plain --no-watch-fs --init-script scripts/device-tests.gradle assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
git diff --check
```

The Checks workflow runs the first two commands, including compilation of the device suite. On a prepared Android 16 test device, follow [device-testing.md](device-testing.md), then run:

```sh
python3 scripts/run_device_tests.py --serial DEVICE_SERIAL
```

The Python runner returns a failing exit status for failed cases, missing completion, partial results, a crashed process, an ADB error, or a timeout. An ADB exit code of zero alone does not establish success. Device tests are opt-in because they require setup access and briefly operate the torch.

## Requirement mapping

| Requested behavior | Automated coverage | Physical coverage boundary |
| --- | --- | --- |
| Combined lock-screen/AOD visibility; independent Progress, categories, Torch and Hotspot | `SystemStatusSessionTest`, `VisibilityRegressionMatrixTest`, `MirrorVisibilityPolicyTest`, `NotificationCategoryPreferencesTest`, device saved-value/default checks | OEM visual layout and full on/off surface matrix |
| Media AOD only; no awake-lock-screen mirror or original suppression | `MediaVisibilityPolicyTest`, full visibility matrices, device builder tests; persistent category/AOD fixture scenarios | Real wake transitions and original channel restoration |
| System cards remain under Quick Settings | System policy has no QS input; device controller posts Torch with QS expanded | Hotspot/system shade presentation |
| Torch transitions, stable card, inert content tap, Turn off, dismissal, enablement, reconnect | Device smoke runner operates camera callbacks and notification pending intents; session tests cover dismissal and independent states | Unavailable camera/OEM behavior |
| Hotspot zero/one/multiple clients, current-only parsing, deduplication, unknown fallback | `HotspotClientCountParserTest`: current fields/map, empty/malformed maps, duplicates across bands, history, local-only/stopped managers, ambiguous totals, overflow | Actual vendor diagnostic formats and multiple external clients |
| Diagnostic timeout, size bound, failures and cleanup | `BoundedProcessReaderTest`: successful boundary, overflow, nonzero exit, broken stream, stalled read canceled by watchdog, child cleanup | Real privileged service refusing to terminate; OEM diagnostic limits |
| Poll eligibility, no overlap, stale count clearing, revoke/regrant, reconnect | `HotspotPollSessionTest`: read ownership survives eligibility changes; stale generation rejected; failure clears count; repeated transitions | Long-running service/reconnect stress and real five-second cadence |
| Hotspot Turn off success/failure/timeout and duplicate/late callbacks | `HotspotStopSessionTest` exercises success, settings fallback, every callback ordering, activity teardown | Real stop permission, Settings intent resolution, locked-phone authentication |
| Media icons, fallback icons, artwork, source actions, absence of generated playback/Dismiss buttons | Device builder tests; `MediaNotificationSnapshotTest`; device icon-only, action-count, replacement-label/intent refresh checks | Loading arbitrary third-party assets and original app action behavior |
| Unknown progress has no bar; known progress preserved | `ProgressMathTest`, `MediaProgressMapperTest`, device BigTextStyle/ProgressStyle assertions for progress and media | Actual System UI rendering; built-in test notification UI |
| Required permissions gate setup; already granted permissions skipped | `SetupFlowPolicyTest`, `SetupRegressionMatrixTest` (all 128 access combinations), `EnabledServiceComponentsTest` | Android denial prompts, automatic-grant failures, manual settings navigation |
| Accessibility/Shizuku loss disables presentation without overwriting preference | `PrerequisiteTogglePolicyTest`, setup matrix repeated grant/loss cycles, `ShizukuAccessPolicyTest` | Live service/permission observation and saved-choice restoration |
| Shizuku absent/stopped/granted; no redundant grant action or setup when absent | `ShizukuAccessPolicyTest`; `CategoryRefreshPolicyTest`; setup policies | Installed-package events, stopped server, real popup updates |
| Hotspot remains usable without Shizuku | Independent system session eligibility; parser/read failures yield unknown; physical revocation fixture procedure | Real absent/stopped/grant-loss state |
| Info is independent and accessible; only switch disabled | Device `SettingsUi` check: help remains enabled/focusable/labeled, clicks do not toggle; disabled row cannot mutate preferences; label/row undimmed | Spoken TalkBack, all font sizes and input methods |
| App notifications navigation, auto-enable setting, refresh through setup, no System status footer | `NotificationCategoryPreferencesTest`, `CategoryRefreshPolicyTest`, `FirstRunCategoryRefreshPolicyTest`; documented UI checklist | Row placement, page navigation, footer absence in all service states |
| Localized branding, short popup strings and notification wording | `NotificationLocalizationTest` resource/placeholder parity; English platform assertions | Translation quality, RTL, all visual layouts |
| Battery/AMOLED behavior | `MediaUpdateSchedulerTest`, `MediaSessionRefreshPolicyTest`, `MediaBuildCoalescerTest`, `PillTextScrollerTest`, poll lifecycle tests; theme/layout inspection procedure | Overnight battery, thermal/AOD behavior, screen burn-in prevention, CPU/wakeup measurement |
| No migration/package/signing/version reset; 1.x release from 1.1.1 | Device saved-value reconstruction checks; release-script suite and release calculation | Signed upgrade/install on a user's existing installation |
| Original-channel restoration after abrupt process death | Recorded in manual regression checklist | Not claimed as automated: requires controlled process death while a real channel is suppressed |

## Current verification

The expanded suite passes **227 JVM tests** and **23 Python tests**. Debug build, Android test APK compilation, lint, and diff checks pass. The Android suite now contains **24 checks**; its three newly added checks are compiled, not yet claimed as executed on a phone. The earlier 21-check device run and manual observations remain recorded in [validation-2026-11-02.md](validation-2026-11-02.md).

Remaining physical checks are explicitly listed in that report. They cannot be replaced by mocked assertions or silently counted as unit-test coverage.
