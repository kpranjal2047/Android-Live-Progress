package com.pranjal.liveprogress

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.*

/** On-device smoke checks without external test dependencies. Run on an idle, unlocked test phone. */
class DeviceSmokeInstrumentation : Instrumentation() {
    private val results = mutableListOf<String>()
    private var scenario: String? = null
    private var scenarioChannel = "fixture"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); scenario = arguments?.getString("scenario"); scenarioChannel = arguments?.getString("channel") ?: "fixture"; start() }
    private fun checkCase(name: String, block: () -> Unit) {
        try { block(); results += "PASS $name" } catch (e: Throwable) { results += "FAIL $name: ${e.javaClass.simpleName}: ${e.message}" }
        sendStatus(0, Bundle().apply { putString("stream", results.last() + "\n") })
    }
    private fun expect(value: Boolean, message: String) { check(value) { message } }
    private fun awaitCondition(block: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 6_000
        while (!block()) { check(SystemClock.elapsedRealtime() < end) { "Condition timed out" }; Thread.sleep(100) }
    }
    override fun onStart() {
        val c = targetContext
        if (scenario != null) {
            runOnMainSync {
                val key = NotificationCategoryKey("com.pranjal.liveprogress.test", 0, scenarioChannel)
                val categories = NotificationCategoryPreferences(c)
                when (scenario) {
                    "category-aod" -> {
                        MediaPreferences(c).enabled = false
                        categories.updateSettings(key) { NotificationCategorySettings(true, true, true) }
                    }
                    "category-aod-off" -> categories.updateSettings(key) { it.copy(showOnLockScreen = false) }
                    "media-aod-off" -> { MediaPreferences(c).enabled = true; MediaPreferences(c).showOnAod = false }
                    "restore" -> {
                        MediaPreferences(c).enabled = true
                        MediaPreferences(c).showOnAod = true
                        VisibilityPreferences(c).hideMirrorsWhenQuickSettingsExpanded = true
                        categories.snapshot().settingsByKey.keys.filter { it.packageName == "com.pranjal.liveprogress.test" }.forEach {
                            categories.updateSettings(it) { NotificationCategorySettings() }
                        }
                        val observed = c.getSharedPreferences("live_progress_notification_categories", 0)
                        for ((name, value) in observed.all) {
                            if (value is Set<*>) {
                                val clean = value.filterIsInstance<String>().filterNot { it.startsWith("com.pranjal.liveprogress.test\u001F") }.toSet()
                                observed.edit().putStringSet(name, clean).commit()
                            }
                        }
                        DeveloperPreferences(c).loggingLevel = DiagnosticLoggingLevel.OFF
                        AppDiagnostics.clear(c)
                    }
                    else -> error("Unknown scenario")
                }
            }
            // Flush apply() writes before instrumentation terminates the test process.
            c.getSharedPreferences("live_progress_media_preferences", 0).edit().commit()
            c.getSharedPreferences("live_progress_visibility_preferences", 0).edit().commit()
            c.getSharedPreferences("live_progress_selected_notification_categories", 0).edit().commit()
            c.getSharedPreferences("live_progress_developer_preferences", 0).edit().commit()
            c.getSharedPreferences("android_live_progress_diagnostics", 0).edit().commit()
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "Configured $scenario\n") })
            return
        }
        val nm = c.getSystemService(NotificationManager::class.java)
        val icon = Icon.createWithResource(c, R.drawable.ic_system_torch)
        val intent = PendingIntent.getActivity(c, 9901, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val action = Notification.Action.Builder(icon, "Test source action", intent).build()
        val original = OriginalNotificationSource("device-fixture", c.packageName, Process.myUid(), Process.myUserHandle(), "fixture", "Fixture")
        val source = MediaNotificationSource(original, icon, intent, listOf(action))
        val art = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val state = MediaState("Device test", "Fixture artist", "Fixture album", art, null, true, 120_000, 10_000, c.packageName)
        fun media(s: MediaState = state, src: MediaNotificationSource? = source) = MediaLiveNotificationBuilder.build(c, s, src, "Test", false)
        fun snapshot(src: MediaNotificationSource?) = MediaNotificationSnapshot.from(state, src, "Fixture", "Test", false, true, MirrorPriorityMode.DEFAULT)
        checkCase("fresh visibility defaults") {
            expect(ProgressPreferences(c).showOnLockScreen && MediaPreferences(c).showOnAod, "Progress/media visibility default")
            SystemStatusKind.entries.forEach { expect(SystemStatusPreferences(c).enabled(it) && SystemStatusPreferences(c).showOnLockScreen(it), "System status default") }
        }
        checkCase("setup permissions granted") {
            val access = SetupAccessStateReader.read(c)
            expect(nm.areNotificationsEnabled() && access.notificationListenerReady && access.accessibilityEnabled, "Missing setup permission")
        }
        checkCase("media source small icon") { expect(media().smallIcon.resId == icon.resId, "Source icon replaced") }
        checkCase("media app icon fallback") { expect(media(src = source.copy(smallIcon = null)).smallIcon.loadDrawable(c) != null, "App fallback missing") }
        checkCase("media generic icon fallback") {
            val n = media(state.copy(packageName = "invalid.fixture.package"), null)
            expect(n.smallIcon.loadDrawable(c) != null, "Generic fallback missing")
        }
        checkCase("media artwork retained") { expect(media().getLargeIcon() != null, "Artwork lost") }
        checkCase("media original actions preserved") {
            val actions = media().actions
            expect(actions.size == 1 && actions[0].title == action.title && actions[0].actionIntent == intent && actions[0].getIcon().resId == icon.resId, "Action changed")
        }
        checkCase("media no generated actions") { expect(media(src = source.copy(actions = emptyList())).actions.isNullOrEmpty(), "Generated actions present") }
        checkCase("media icon-only refresh") { expect(snapshot(source) != snapshot(source.copy(smallIcon = Icon.createWithResource(c, R.drawable.ic_system_hotspot))), "Icon change ignored") }
        checkCase("media action-only refresh") { expect(snapshot(source) != snapshot(source.copy(actions = emptyList())), "Action change ignored") }
        checkCase("media unknown duration has no progress style") { expect(media(state.copy(durationMs = 0)).extras.getString(Notification.EXTRA_TEMPLATE) == Notification.BigTextStyle::class.java.name, "Unknown duration has progress") }
        checkCase("media unknown position has no progress style") { expect(media(state.copy(positionMs = -1)).extras.getString(Notification.EXTRA_TEMPLATE) == Notification.BigTextStyle::class.java.name, "Unknown position has progress") }
        checkCase("media known progress uses progress style") { expect(media().extras.getString(Notification.EXTRA_TEMPLATE) == Notification.ProgressStyle::class.java.name, "Known progress absent") }
        fun candidate(progress: ProgressInfo) = MirrorCandidate("fixture", c.packageName, 9902, null, Process.myUid(), Process.myUserHandle(), "fixture", 9902, "Fixture", "Test progress", "Test", null, intent, icon, null, 0, 0, false, listOf(action), progress, displaySettings = MirrorCandidateDisplaySettings(MirrorCandidateSource.PROGRESS, true, false))
        checkCase("progress unknown value has no progress style") {
            val n = MirrorNotificationBuilder.build(c, candidate(ProgressInfo(0, 0, true)))
            expect(n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.BigTextStyle::class.java.name, "Unknown progress bar")
            expect(n.actions.size == 1 && n.actions[0].actionIntent == intent && n.deleteIntent != null, "Original action or swipe callback changed")
        }
        checkCase("progress known value retains progress and only source actions") {
            val n = MirrorNotificationBuilder.build(c, candidate(ProgressInfo(20, 100, false)))
            expect(n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.ProgressStyle::class.java.name && n.actions.size == 1, "Known progress/actions incorrect")
        }
        checkCase("media visibility surface matrix") {
            for (aod in listOf(false, true)) for (locked in listOf(false, true)) for (off in listOf(false, true)) {
                val d = MediaVisibilityPolicy.decide(true, true, locked, off, false, progressMirrorActive = false, showOnAod = aod, hideOriginalNotification = true)
                expect(d.showMirror == (if (off) aod else !locked), "Wrong visibility for lock=$locked off=$off aod=$aod")
                if (locked && !off) expect(!d.suppressOriginal, "Original remains suppressed on awake lockscreen")
            }
        }
        checkCase("help stays clickable without changing disabled or enabled toggles") {
            runOnMainSync {
                for (enabled in listOf(false, true)) {
                    var helpClicks = 0
                    var changes = 0
                    val row = SettingsUi(c).settingToggle("Test prerequisite", false, enabled,
                        onInfo = { helpClicks++ }, onChanged = { changes++ }) as android.view.ViewGroup
                    val help = row.getChildAt(1)
                    expect(help.isEnabled && help.isFocusable && !help.contentDescription.isNullOrBlank(), "Help inaccessible")
                    help.performClick()
                    expect(helpClicks == 1 && changes == 0, "Help changed toggle")
                    expect(row.alpha == 1f && row.getChildAt(0).alpha == 1f, "Whole row dimmed")
                    row.performClick()
                    expect(changes == if (enabled) 1 else 0, "Disabled row changed preference")
                }
            }
        }
        checkCase("existing disabled visibility choices survive preference reconstruction") {
            val progress = ProgressPreferences(c)
            val media = MediaPreferences(c)
            val oldProgress = progress.showOnLockScreen
            val oldMedia = media.showOnAod
            try {
                progress.showOnLockScreen = false; media.showOnAod = false
                expect(!ProgressPreferences(c).showOnLockScreen && !MediaPreferences(c).showOnAod, "Saved choice reset to default")
            } finally { progress.showOnLockScreen = oldProgress; media.showOnAod = oldMedia }
        }
        checkCase("media replacement action label and intent refresh without changing action count") {
            val replacementIntent = PendingIntent.getActivity(c, 9903, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val replacement = Notification.Action.Builder(icon, "Changed label", replacementIntent).build()
            val changed = source.copy(actions = listOf(replacement))
            expect(snapshot(source) != snapshot(changed), "Replacement action ignored")
            val n = media(src = changed)
            expect(n.actions.single().title == "Changed label" && n.actions.single().actionIntent == replacementIntent, "Replacement action not preserved")
        }
        var controller: SystemStatusController? = null
        val camera = c.getSystemService(CameraManager::class.java)
        var cameraId: String? = null
        try {
            runOnMainSync { controller = SystemStatusController(c).also { it.start() } }
            cameraId = camera.cameraIdList.first { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            checkCase("torch callback posts card and Quick Settings does not hide it") {
                camera.setTorchMode(cameraId, true)
                awaitCondition { nm.activeNotifications.any { it.id == 3101 } }
                runOnMainSync { VisibilityState.setQuickSettingsExpanded(c, true); controller!!.refresh() }
                Thread.sleep(500)
                val n = nm.activeNotifications.first { it.id == 3101 }.notification
                expect(n.contentIntent == null, "Torch card has tap action")
                expect(n.extras.getString(Notification.EXTRA_TEXT) == "Torch on", "Torch text mismatch")
                expect(n.extras.getString(Notification.EXTRA_TEMPLATE) == Notification.BigTextStyle::class.java.name, "Torch has progress style")
                expect(n.actions.size == 1 && n.actions[0].title.toString() == "Turn off", "Missing Turn off")
            }
            checkCase("torch notification Turn off action") {
                nm.activeNotifications.first { it.id == 3101 }.notification.actions[0].actionIntent.send()
                awaitCondition { nm.activeNotifications.none { it.id == 3101 } }
                var active = true
                runOnMainSync { active = controller!!.activeTorchCameraIds().isNotEmpty() }
                expect(!active, "Torch remains active")
            }
            checkCase("torch dismissal persists until next activation") {
                camera.setTorchMode(cameraId, true)
                awaitCondition { nm.activeNotifications.any { it.id == 3101 } }
                nm.activeNotifications.first { it.id == 3101 }.notification.deleteIntent.send()
                awaitCondition { nm.activeNotifications.none { it.id == 3101 } }
                runOnMainSync { controller!!.refresh() }
                expect(nm.activeNotifications.none { it.id == 3101 }, "Dismissed card returned")
                camera.setTorchMode(cameraId, false); Thread.sleep(400); camera.setTorchMode(cameraId, true)
                awaitCondition { nm.activeNotifications.any { it.id == 3101 } }
            }
            checkCase("torch setting disables and restores active card") {
                runOnMainSync { SystemStatusPreferences(c).setEnabled(SystemStatusKind.TORCH, false) }
                awaitCondition { nm.activeNotifications.none { it.id == 3101 } }
                runOnMainSync { SystemStatusPreferences(c).setEnabled(SystemStatusKind.TORCH, true) }
                awaitCondition { nm.activeNotifications.any { it.id == 3101 } }
            }
            checkCase("controller teardown removes cards; reconnect restores active torch") {
                runOnMainSync { controller!!.destroy() }
                awaitCondition { nm.activeNotifications.none { it.id == 3101 } }
                runOnMainSync { controller = SystemStatusController(c).also { it.start() } }
                awaitCondition { nm.activeNotifications.any { it.id == 3101 } }
            }
        } catch (e: Throwable) { results += "FAIL system-state harness: ${e.message}" }
        finally {
            cameraId?.let { runCatching { camera.setTorchMode(it, false) } }
            runOnMainSync { controller?.destroy(); VisibilityState.setQuickSettingsExpanded(c, false); SystemStatusPreferences(c).setEnabled(SystemStatusKind.TORCH, true) }
        }
        finish(if (results.any { it.startsWith("FAIL") }) 0 else Activity.RESULT_OK, Bundle().apply { putString("stream", results.joinToString("\n") + "\nDEVICE_TESTS_RESULT: ${if (results.any { it.startsWith("FAIL") }) "FAIL" else "PASS"} cases=${results.size}\n") })
    }
}
