# Android device validation

The test APK is separate from the customer APK. Its exported fixture activity posts synthetic notifications only; it is not included in normal builds. Use an idle Android 16 test phone with Live Notifications setup completed. The smoke runner briefly operates the torch, changes its notification enablement, and restores it to enabled/off; use fresh defaults when running the default-value assertions.

Build the target and companion with the same signing configuration:

```sh
./gradlew --init-script scripts/device-tests.gradle assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
python3 scripts/run_device_tests.py --serial DEVICE_SERIAL
```

The runner emits one PASS/FAIL line per case and a final summary. Use the Python wrapper so partial/crashed runs fail the command even when ADB returns zero. Any FAIL or instrumentation crash fails validation. It checks platform-built notification icons, artwork, original actions, refresh snapshots, known/unknown progress styles, visibility policy, and real torch callbacks/actions/dismissal/reconnect.

For real listener and screen-state tests, grant notification posting to the isolated fixture and launch it:

```sh
adb shell pm grant com.pranjal.liveprogress.test android.permission.POST_NOTIFICATIONS
adb shell am start -n com.pranjal.liveprogress.test/com.pranjal.liveprogress.NotificationFixtureActivity --es mode media
```

Fixture modes: `media`, `progress`, `cancel`. Boolean extras: `known`, `actions`, `alt` (alternate source icon and action label). Optional `channel` selects a fresh test channel. Cancel removes only companion notifications and its media session. These grants are test preparation; the production app continues to route user permission requests through setup pages.

The instrumentation `scenario` extra prepares `category-aod`, `category-aod-off`, `media-aod-off`, or `restore`; `channel` defaults to `fixture`. Scenario settings are deliberately persistent for subsequent physical screen transitions. Always run `restore` afterward; it removes synthetic companion category selections/observations and clears temporary diagnostics, restores the tested fresh defaults, and turns logging Off. Wait for the notification listener to reconnect and verify the source and expected mirror before accepting results. Scenario setup is not a UI test.

Manual coverage must include real AOD presentation, unlocked and awake-lock-screen transitions, original-channel restoration, permission grant/revocation, help/navigation, actual hotspot clients and OEM shutdown fallback. Automated policy/parser tests do not replace those checks.

After testing, cancel the fixture, restore app settings and device theme, turn off any test torch/hotspot, and uninstall `com.pranjal.liveprogress.test`. Do not uninstall or clear data for the customer app to clean up tests.

See [test-coverage.md](test-coverage.md) for the complete requirement-to-suite mapping and remaining physical checks.
