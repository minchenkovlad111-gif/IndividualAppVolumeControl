package com.duda.appvolumecontrol.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.OverlayPrefs

class VolumeKeyAccessibilityService : AccessibilityService() {
    private val overlayPrefs by lazy { OverlayPrefs(this) }
    private val diagnosticsStore by lazy { DiagnosticsStore(this) }
    private var lastStartErrorToastAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we only care about key events.
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        val keyCode = event.keyCode
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false
        }
        if (!overlayPrefs.isOverlayEnabled()) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            val intent = Intent(this, VolumeOverlayService::class.java).apply {
                action = VolumeOverlayService.ACTION_SHOW
                putExtra(
                    VolumeOverlayService.EXTRA_SHOW_REASON,
                    VolumeOverlayService.SHOW_REASON_ACCESSIBILITY
                )
            }
            try {
                startService(intent)
            } catch (t: Throwable) {
                diagnosticsStore.recordLastError(
                    "Overlay start failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
                )
                val now = SystemClock.elapsedRealtime()
                if (now - lastStartErrorToastAt > 5000L) {
                    lastStartErrorToastAt = now
                    Toast.makeText(
                        this,
                        "Overlay start failed; open the app and enable the overlay",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        return false
    }
}
