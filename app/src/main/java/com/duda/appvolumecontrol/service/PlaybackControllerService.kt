package com.duda.appvolumecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.duda.appvolumecontrol.core.HiddenApi
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.VolumeStore

class PlaybackControllerService : Service() {
    private val volumeStore by lazy { VolumeStore(this) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val lastApplied = mutableMapOf<String, Float>()

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            applyVolumes(configs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        HiddenApi.tryAllowHiddenApiAccess(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        applyVolumes(audioManager.activePlaybackConfigurations)
    }

    override fun onDestroy() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REAPPLY) {
            applyVolumes(audioManager.activePlaybackConfigurations)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyVolumes(configs: List<AudioPlaybackConfiguration>) {
        for (config in configs) {
            val packageName = PlaybackUtils.getClientPackageName(config, packageManager) ?: continue
            val desiredVolume = volumeStore.getVolume(packageName) ?: 1f
            val key = "${packageName}:${config.hashCode()}"
            val current = lastApplied[key]
            if (current != null && current == desiredVolume) continue

            val applied = PlaybackUtils.applyVolumeToPlayer(config, desiredVolume)
            if (applied) {
                lastApplied[key] = desiredVolume
            } else {
                Log.w(TAG, "Failed to apply volume for $packageName")
            }
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "App Volume Control",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("App Volume Control")
            .setContentText("Managing per-app volume")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    companion object {
        private const val TAG = "PlaybackController"
        private const val CHANNEL_ID = "app_volume_control"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_REAPPLY = "com.duda.appvolumecontrol.action.REAPPLY"

        fun requestReapply(context: Context) {
            val intent = Intent(context, PlaybackControllerService::class.java)
            intent.action = ACTION_REAPPLY
            context.startForegroundService(intent)
        }
    }
}
