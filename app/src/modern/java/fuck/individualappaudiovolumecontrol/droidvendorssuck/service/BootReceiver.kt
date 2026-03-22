package fuck.individualappaudiovolumecontrol.droidvendorssuck.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.provider.Settings
import android.util.Log
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.OverlayPrefs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.R

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Log.w(TAG, "Skipping boot start on Android 14+; posting start notification instead")
                    postStartNotification(context)
                    return
                }
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

    private fun postStartNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Service start",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "Tap to start the audio service after boot."
            manager.createNotificationChannel(channel)
        }
        val startIntent = Intent(context, BootStartReceiver::class.java).apply {
            action = BootStartReceiver.ACTION_START_SERVICES
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val startPending = PendingIntent.getBroadcast(context, 0, startIntent, flags)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Start audio control")
            .setContentText("Tap to enable per-app volume control after boot.")
            .setContentIntent(startPending)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "BootReceiver"
        private const val CHANNEL_ID = "boot_start"
        private const val NOTIFICATION_ID = 9001
    }
}
