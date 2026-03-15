package com.duda.appvolumecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.duda.appvolumecontrol.core.OverlayPrefs

class BootStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START_SERVICES) return
        Log.i(TAG, "User initiated service start from boot notification")
        context.startForegroundService(Intent(context, PlaybackControllerService::class.java))
        val prefs = OverlayPrefs(context)
        if (prefs.isOverlayEnabled() && canDrawOverlays(context)) {
            val overlayIntent = Intent(context, VolumeOverlayService::class.java).apply {
                action = VolumeOverlayService.ACTION_START_WATCH
            }
            context.startForegroundService(overlayIntent)
        }
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    companion object {
        const val ACTION_START_SERVICES = "com.duda.appvolumecontrol.action.START_SERVICES"
        private const val TAG = "BootStartReceiver"
    }
}
