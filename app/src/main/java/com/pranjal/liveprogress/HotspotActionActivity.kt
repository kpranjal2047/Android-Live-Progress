package com.pranjal.liveprogress

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.TetheringManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast

/** User-initiated activity pending intents avoid notification broadcast trampolines. */
class HotspotActionActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val session = HotspotStopSession()
    private val timeout = Runnable { handleResult(HotspotStopSession.Result.TIMED_OUT) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action != ACTION_TURN_OFF) {
            handleResult(HotspotStopSession.Result.OPEN_SETTINGS, manualTurnOff = false)
            return
        }
        if (savedInstanceState != null) {
            handleResult(HotspotStopSession.Result.FAILED)
            return
        }
        val ui = SettingsUi(this)
        val root = ui.contentRoot()
        root.addView(ui.title(getString(R.string.system_hotspot)))
        root.addView(ui.info(getString(R.string.system_turning_off_hotspot)))
        setContentView(ui.scrollContent(root))
        handler.postDelayed(timeout, 5_000L)
        stopHotspot()
    }

    @SuppressLint("MissingPermission") // Public API may reject a system-owned request; use settings then.
    private fun stopHotspot() {
        try {
            getSystemService(TetheringManager::class.java).stopTethering(
                TetheringManager.TetheringRequest.Builder(TetheringManager.TETHERING_WIFI).build(),
                mainExecutor,
                object : TetheringManager.StopTetheringCallback {
                    override fun onStopTetheringSucceeded() {
                        handleResult(HotspotStopSession.Result.SUCCEEDED)
                    }
                    override fun onStopTetheringFailed(error: Int) { handleResult(HotspotStopSession.Result.FAILED) }
                }
            )
        } catch (_: RuntimeException) {
            handleResult(HotspotStopSession.Result.FAILED)
        }
    }

    private fun handleResult(result: HotspotStopSession.Result, manualTurnOff: Boolean = true) {
        when (session.accept(result)) {
            HotspotStopSession.Effect.NONE -> return
            HotspotStopSession.Effect.FINISH -> { handler.removeCallbacks(timeout); finish(); return }
            HotspotStopSession.Effect.OPEN_SETTINGS -> handler.removeCallbacks(timeout)
        }
        if (manualTurnOff) Toast.makeText(this, R.string.system_hotspot_turn_off_manually, Toast.LENGTH_LONG).show()
        for (action in SETTINGS_ACTIONS) {
            try {
                startActivity(Intent(action))
                break
            } catch (_: ActivityNotFoundException) {
                // Try the next system settings route on devices with different Settings apps.
            } catch (_: SecurityException) {
                // Some OEMs expose a private hotspot screen; use the public fallback.
            }
        }
        finish()
    }

    override fun onDestroy() {
        session.cancel()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val ACTION_TURN_OFF = "com.pranjal.liveprogress.TURN_OFF_HOTSPOT"
        val SETTINGS_ACTIONS = listOf("com.android.settings.WIFI_TETHER_SETTINGS",
            "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
    }
}
