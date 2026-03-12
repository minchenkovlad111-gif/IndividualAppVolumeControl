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
import android.content.pm.ServiceInfo
import android.app.PendingIntent
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.HiddenApi
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.VolumeStore

class PlaybackControllerService : Service() {
    private val volumeStore by lazy { VolumeStore(this) }
    private val diagnosticsStore by lazy { DiagnosticsStore(this) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val lastApplied = mutableMapOf<String, Float>()
    private var lastNotifiedCount: Int = -1

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            safeApplyVolumes(configs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        HiddenApi.tryAllowHiddenApiAccess(this)
        val notification = buildNotification(-1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        safeApplyVolumes(audioManager.activePlaybackConfigurations)
    }

    override fun onDestroy() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REAPPLY -> safeApplyVolumes(audioManager.activePlaybackConfigurations)
            ACTION_DIAGNOSTICS_DUMP -> dumpActiveConfigs()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyVolumes(configs: List<AudioPlaybackConfiguration>) {
        diagnosticsStore.recordLastActive(configs.size)
        var appliedCount = 0
        val activeKeys = mutableSetOf<String>()
        val activeEntries = mutableSetOf<String>()
        for (config in configs) {
            val packageName = PlaybackUtils.getClientPackageName(config, packageManager) ?: continue
            val uid = PlaybackUtils.getClientUid(config)
            val userId = PlaybackUtils.getUserIdFromUid(uid)
            val volumeKey = VolumeStore.VolumeKey(packageName, userId)
            activeEntries.add(volumeKey.storageKey())
            val desiredVolume = volumeStore.getVolume(volumeKey) ?: 1f
            val key = "${volumeKey.storageKey()}:${config.hashCode()}"
            activeKeys.add(key)
            val current = lastApplied[key]
            if (current != null && current == desiredVolume) continue

            val applied = PlaybackUtils.applyVolumeToPlayer(config, desiredVolume)
            if (applied) {
                lastApplied[key] = desiredVolume
                appliedCount += 1
            } else {
                Log.w(TAG, "Failed to apply volume for $packageName")
            }
        }
        lastApplied.keys.retainAll(activeKeys)
        diagnosticsStore.recordLastApply(appliedCount)
        updateNotification(activeEntries.size)
    }

    private fun safeApplyVolumes(configs: List<AudioPlaybackConfiguration>) {
        try {
            applyVolumes(configs)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to apply volumes", t)
            diagnosticsStore.recordLastError(t.javaClass.simpleName + ": " + (t.message ?: "unknown"))
        }
    }

    private fun dumpActiveConfigs() {
        try {
            val configs = audioManager.activePlaybackConfigurations
            diagnosticsStore.recordLastDump(configs.size)
            val packages = configs.mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
                .distinct()
                .sorted()
            Log.i(TAG, "Diagnostics dump: active=${configs.size} packages=$packages")
        } catch (t: Throwable) {
            Log.e(TAG, "Diagnostics dump failed", t)
            diagnosticsStore.recordLastError(t.javaClass.simpleName + ": " + (t.message ?: "unknown"))
        }
    }

    private fun buildNotification(activeCount: Int): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "IndividualAppAudioVolumeControl",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Per-app volume control service"
            manager.createNotificationChannel(channel)
        }
        val detail = if (activeCount >= 0) "Active apps: $activeCount" else "Managing per-app volume"
        val overlayIntent = Intent(this, VolumeOverlayService::class.java).apply {
            action = VolumeOverlayService.ACTION_SHOW
        }
        val overlayPending = PendingIntent.getService(
            this,
            0,
            overlayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutable()
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("IndividualAppAudioVolumeControl")
            .setContentText(detail)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .addAction(Notification.Action.Builder(null, "Overlay", overlayPending).build())
            .build()
    }

    private fun pendingIntentImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun updateNotification(activeCount: Int) {
        if (activeCount == lastNotifiedCount) return
        lastNotifiedCount = activeCount
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(activeCount))
    }

    companion object {
        private const val TAG = "PlaybackController"
        private const val CHANNEL_ID = "app_volume_control"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_REAPPLY = "com.duda.appvolumecontrol.action.REAPPLY"
        const val ACTION_DIAGNOSTICS_DUMP = "com.duda.appvolumecontrol.action.DIAGNOSTICS_DUMP"

        fun requestReapply(context: Context) {
            val intent = Intent(context, PlaybackControllerService::class.java)
            intent.action = ACTION_REAPPLY
            context.startForegroundService(intent)
        }

        fun requestDiagnosticsDump(context: Context) {
            val intent = Intent(context, PlaybackControllerService::class.java)
            intent.action = ACTION_DIAGNOSTICS_DUMP
            context.startForegroundService(intent)
        }
    }
}
