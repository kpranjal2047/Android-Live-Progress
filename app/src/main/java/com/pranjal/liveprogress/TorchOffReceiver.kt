package com.pranjal.liveprogress

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.util.concurrent.atomic.AtomicBoolean

class TorchOffReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val activeIds = SystemStatusController.current?.activeTorchCameraIds()
        if (activeIds != null && activeIds.isEmpty()) return
        if (!busy.compareAndSet(false, true)) return
        val pending = goAsync()
        val appContext = context.applicationContext
        Thread({
            try {
                val cameras = appContext.getSystemService(CameraManager::class.java)
                val flashIds = activeIds ?: cameras.cameraIdList.filter {
                    cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }
                var failed = flashIds.isEmpty()
                for (id in flashIds) {
                    if (runCatching { cameras.setTorchMode(id, false) }.isFailure) failed = true
                }
                if (failed) showFailure(appContext)
                // The torch observer removes the card only after the device confirms it is off.
            } catch (_: Exception) {
                showFailure(appContext)
            } finally {
                busy.set(false)
                pending.finish()
            }
        }, "turn-off-torch").start()
    }

    private fun showFailure(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, R.string.system_torch_turn_off_failed, Toast.LENGTH_LONG).show()
        }
    }

    companion object { private val busy = AtomicBoolean(false) }
}
