package com.duda.appvolumecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.duda.appvolumecontrol.core.OverlayPrefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                context.startForegroundService(Intent(context, PlaybackControllerService::class.java))
                val prefs = OverlayPrefs(context)
                if (prefs.isOverlayEnabled() && canDrawOverlays(context)) {
                    val overlayIntent = Intent(context, VolumeOverlayService::class.java).apply {
                        action = VolumeOverlayService.ACTION_START_WATCH
                    }
                    context.startForegroundService(overlayIntent)
                }
            }
        }
    }

    private fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
}
