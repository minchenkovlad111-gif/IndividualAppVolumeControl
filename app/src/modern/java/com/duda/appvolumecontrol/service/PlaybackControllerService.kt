@file:Suppress("DEPRECATION")

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
import android.widget.RemoteViews
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import com.duda.appvolumecontrol.R
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.HiddenApi
import com.duda.appvolumecontrol.core.GeofenceRuleManager
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.RulesEngine
import com.duda.appvolumecontrol.core.VolumeStore
import com.duda.appvolumecontrol.core.LocationRuleStore
import com.duda.appvolumecontrol.core.LocationRuleManager
import com.duda.appvolumecontrol.core.AudioSettingsPrefs

@Suppress("DEPRECATION")
class PlaybackControllerService : Service() {
    private val volumeStore by lazy { VolumeStore(this) }
    private val diagnosticsStore by lazy { DiagnosticsStore(this) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val audioSettings by lazy { AudioSettingsPrefs(this) }
    private val locationRuleStore by lazy { LocationRuleStore(this) }
    private val locationRuleManager by lazy { LocationRuleManager(this) }
    private val geofenceRuleManager by lazy { GeofenceRuleManager(this) }
    private val lastApplied = mutableMapOf<String, Float>()
    private var lastNotifiedCount: Int = -1
    private var lastNotificationEntries: List<NotificationEntry> = emptyList()
    private var lastNotificationSignature: String? = null
    private val lastPausedKeys = mutableSetOf<String>()
    private var callMuteActive: Boolean = false
    private var telephonyCallback: TelephonyCallback? = null
    private var phoneStateListener: PhoneStateListener? = null

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            safeApplyVolumes(configs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        HiddenApi.tryAllowHiddenApiAccess(this)
        RulesEngine.setLocationIdProvider { locationRuleStore.getCurrentLocationId() }
        locationRuleManager.start()
        geofenceRuleManager.syncGeofences()
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
        setupCallStateListener()
        safeApplyVolumes(audioManager.activePlaybackConfigurations)
    }

    override fun onDestroy() {
        locationRuleManager.stop()
        teardownCallStateListener()
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REAPPLY -> safeApplyVolumes(audioManager.activePlaybackConfigurations)
            ACTION_DIAGNOSTICS_DUMP -> dumpActiveConfigs()
            ACTION_LOCATION_REFRESH -> locationRuleManager.start()
            ACTION_LOCATION_SYNC -> {
                locationRuleManager.syncBackgroundUpdates()
                geofenceRuleManager.syncGeofences()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun applyVolumes(configs: List<AudioPlaybackConfiguration>) {
        diagnosticsStore.recordLastActive(configs.size)
        var appliedCount = 0
        var routeAttempted = 0
        var routeApplied = 0
        var routeMissing = 0
        var routeFailed = 0
        val routeDetails = mutableListOf<String>()
        val activeKeys = mutableSetOf<String>()
        val activeEntries = mutableSetOf<String>()
        val activeVolumeKeys = mutableSetOf<VolumeStore.VolumeKey>()
        val activeConfigs = mutableListOf<ActiveConfig>()
        for (config in configs) {
            val packageName = PlaybackUtils.getClientPackageName(config, packageManager) ?: continue
            val uid = PlaybackUtils.getClientUid(config)
            val userId = PlaybackUtils.getUserIdFromUid(uid)
            val volumeKey = VolumeStore.VolumeKey(packageName, userId)
            activeEntries.add(volumeKey.storageKey())
            activeVolumeKeys.add(volumeKey)
            activeConfigs.add(ActiveConfig(config, volumeKey, packageName))
        }
        val ruleEffects = RulesEngine.computeEffects(
            activeVolumeKeys,
            volumeStore.listRuleGroups(),
            volumeStore.listRules(),
        )
        val pauseTargets = ruleEffects.pauseTargets
        val pauseAppliedKeys = mutableSetOf<String>()
        for (entry in activeConfigs) {
            val volumeKey = entry.volumeKey
            val packageName = entry.packageName
            val desiredVolume = if (callMuteActive) {
                0f
            } else {
                ruleEffects.volumeOverrides[volumeKey] ?: (volumeStore.getVolume(volumeKey) ?: 1f)
            }
            val muteBehavior = volumeStore.getMuteBehavior(volumeKey)
            val outputDevice = volumeStore.getOutputDevice(volumeKey)
            val key = "${volumeKey.storageKey()}:${entry.config.hashCode()}"
            activeKeys.add(key)
            val current = lastApplied[key]
            if (current != null && current == desiredVolume) continue

            val applied = PlaybackUtils.applyVolumeToPlayer(entry.config, desiredVolume)
            if (applied) {
                lastApplied[key] = desiredVolume
                appliedCount += 1
            } else {
                Log.w(TAG, "Failed to apply volume for $packageName")
            }

            val shouldPause = pauseTargets.contains(volumeKey) ||
                (desiredVolume <= 0f && muteBehavior == VolumeStore.MuteBehavior.MUTE_AND_PAUSE)
            if (shouldPause) {
                pauseAppliedKeys.add(key)
                if (!lastPausedKeys.contains(key)) {
                    val paused = PlaybackUtils.pausePlayer(entry.config)
                    if (paused) {
                        lastPausedKeys.add(key)
                    } else {
                        Log.w(TAG, "Failed to pause player for $packageName")
                    }
                }
            }
            val resumeOverride = volumeStore.getAutoResumeOverride(volumeKey)
            val resumeAllowed = resumeOverride ?: audioSettings.isAutoResumeEnabled()
            if (!shouldPause && lastPausedKeys.contains(key) && resumeAllowed) {
                val resumed = PlaybackUtils.playPlayer(entry.config)
                if (resumed) {
                    lastPausedKeys.remove(key)
                } else {
                    Log.w(TAG, "Failed to resume player for $packageName")
                }
            }

            if (outputDevice != null) {
                routeAttempted += 1
                val matchAddress = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    outputDevice.address
                } else {
                    null
                }
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val deviceInfo = outputs.firstOrNull { info ->
                        if (info.type != outputDevice.type) return@firstOrNull false
                        if (matchAddress.isNullOrBlank()) return@firstOrNull true
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) {
                            return@firstOrNull false
                        }
                        info.address == matchAddress
                    }
                if (deviceInfo != null) {
                    val routed = PlaybackUtils.applyPreferredDeviceToPlayer(entry.config, deviceInfo)
                    if (!routed) {
                        routeFailed += 1
                        routeDetails.add("${volumeKey.storageKey()}|${formatOutputDevice(outputDevice)}|failed")
                        Log.w(TAG, "Failed to apply preferred output device for $packageName")
                    } else {
                        routeApplied += 1
                        routeDetails.add("${volumeKey.storageKey()}|${formatOutputDevice(outputDevice)}|ok")
                    }
                } else {
                    routeMissing += 1
                    val sameTypeCount = outputs.count { it.type == outputDevice.type }
                    routeDetails.add(
                        "${volumeKey.storageKey()}|${formatOutputDevice(outputDevice)}|" +
                            "missing(total=${outputs.size},type=$sameTypeCount)"
                    )
                    Log.w(TAG, "Preferred output device not found for $packageName")
                }
            }
        }
        lastApplied.keys.retainAll(activeKeys)
        lastPausedKeys.retainAll(pauseAppliedKeys)
        diagnosticsStore.recordLastApply(appliedCount)
        if (routeAttempted > 0) {
            val detailBlob = truncateDetails(routeDetails, MAX_ROUTE_DETAIL_CHARS)
            diagnosticsStore.recordLastRouting(
                attempted = routeAttempted,
                applied = routeApplied,
                missing = routeMissing,
                failed = routeFailed,
                details = detailBlob
            )
        }
        lastNotificationEntries = buildNotificationEntries(activeVolumeKeys)
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
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("IndividualAppAudioVolumeControl")
            .setContentText(detail)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Overlay", overlayPending).build())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notification.setCustomBigContentView(buildBigRemoteViews())
            notification.setStyle(Notification.DecoratedCustomViewStyle())
        }
        return notification.build()
    }

    private fun pendingIntentImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun updateNotification(activeCount: Int) {
        val signature = buildNotificationSignature()
        if (activeCount == lastNotifiedCount && signature == lastNotificationSignature) return
        lastNotifiedCount = activeCount
        lastNotificationSignature = signature
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(activeCount))
    }

    private fun buildNotificationSignature(): String {
        if (lastNotificationEntries.isEmpty()) return ""
        return lastNotificationEntries.joinToString("|") { entry ->
            val userPart = entry.userId?.toString() ?: "x"
            "${entry.packageName}@$userPart:${entry.percent}"
        }
    }

    private data class ActiveConfig(
        val config: AudioPlaybackConfiguration,
        val volumeKey: VolumeStore.VolumeKey,
        val packageName: String,
    )

    private fun buildBigRemoteViews(): RemoteViews {
        val views = RemoteViews(packageName, R.layout.notification_volume_big)
        val entries = lastNotificationEntries
        views.setOnClickPendingIntent(
            R.id.notif_open_overlay,
            buildOverlayPendingIntent()
        )
        bindNotificationRow(views, 0, entries.getOrNull(0))
        bindNotificationRow(views, 1, entries.getOrNull(1))
        bindNotificationRow(views, 2, entries.getOrNull(2))
        return views
    }

    private fun bindNotificationRow(views: RemoteViews, index: Int, entry: NotificationEntry?) {
        val rowId = when (index) {
            0 -> R.id.notif_row_1
            1 -> R.id.notif_row_2
            else -> R.id.notif_row_3
        }
        val appId = when (index) {
            0 -> R.id.notif_app_1
            1 -> R.id.notif_app_2
            else -> R.id.notif_app_3
        }
        val valueId = when (index) {
            0 -> R.id.notif_value_1
            1 -> R.id.notif_value_2
            else -> R.id.notif_value_3
        }
        val minusId = when (index) {
            0 -> R.id.notif_minus_1
            1 -> R.id.notif_minus_2
            else -> R.id.notif_minus_3
        }
        val plusId = when (index) {
            0 -> R.id.notif_plus_1
            1 -> R.id.notif_plus_2
            else -> R.id.notif_plus_3
        }
        val muteId = when (index) {
            0 -> R.id.notif_mute_1
            1 -> R.id.notif_mute_2
            else -> R.id.notif_mute_3
        }
        val pct25Id = when (index) {
            0 -> R.id.notif_25_1
            1 -> R.id.notif_25_2
            else -> R.id.notif_25_3
        }
        val pct50Id = when (index) {
            0 -> R.id.notif_50_1
            1 -> R.id.notif_50_2
            else -> R.id.notif_50_3
        }
        val pct75Id = when (index) {
            0 -> R.id.notif_75_1
            1 -> R.id.notif_75_2
            else -> R.id.notif_75_3
        }

        if (entry == null) {
            views.setViewVisibility(rowId, android.view.View.GONE)
            return
        }
        views.setViewVisibility(rowId, android.view.View.VISIBLE)
        views.setTextViewText(appId, entry.label)
        views.setTextViewText(valueId, "${entry.percent}%")
        views.setTextViewText(muteId, if (entry.percent <= 0) "Unmute" else "Mute")
        val base = index * 10
        views.setOnClickPendingIntent(
            minusId,
            buildAdjustPendingIntent(entry, -NOTIF_VOLUME_STEP, base)
        )
        views.setOnClickPendingIntent(
            plusId,
            buildAdjustPendingIntent(entry, NOTIF_VOLUME_STEP, base + 1)
        )
        views.setOnClickPendingIntent(
            muteId,
            buildToggleMutePendingIntent(entry, base + 2)
        )
        views.setOnClickPendingIntent(
            pct25Id,
            buildSetPendingIntent(entry, 25, base + 3)
        )
        views.setOnClickPendingIntent(
            pct50Id,
            buildSetPendingIntent(entry, 50, base + 4)
        )
        views.setOnClickPendingIntent(
            pct75Id,
            buildSetPendingIntent(entry, 75, base + 5)
        )
    }

    private fun buildAdjustPendingIntent(
        entry: NotificationEntry,
        delta: Float,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_ADJUST
            putExtra(NotificationActionReceiver.EXTRA_PACKAGE, entry.packageName)
            putExtra(
                NotificationActionReceiver.EXTRA_USER_ID,
                entry.userId ?: NotificationActionReceiver.USER_ID_NONE
            )
            putExtra(NotificationActionReceiver.EXTRA_DELTA, delta)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutable()
        )
    }

    private fun buildSetPendingIntent(
        entry: NotificationEntry,
        percent: Int,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_SET
            putExtra(NotificationActionReceiver.EXTRA_PACKAGE, entry.packageName)
            putExtra(
                NotificationActionReceiver.EXTRA_USER_ID,
                entry.userId ?: NotificationActionReceiver.USER_ID_NONE
            )
            putExtra(NotificationActionReceiver.EXTRA_PERCENT, percent)
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutable()
        )
    }

    private fun buildToggleMutePendingIntent(
        entry: NotificationEntry,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_TOGGLE_MUTE
            putExtra(NotificationActionReceiver.EXTRA_PACKAGE, entry.packageName)
            putExtra(
                NotificationActionReceiver.EXTRA_USER_ID,
                entry.userId ?: NotificationActionReceiver.USER_ID_NONE
            )
        }
        return PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutable()
        )
    }

    private fun buildOverlayPendingIntent(): PendingIntent {
        val intent = Intent(this, VolumeOverlayService::class.java).apply {
            action = VolumeOverlayService.ACTION_SHOW
        }
        return PendingIntent.getService(
            this,
            99,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutable()
        )
    }

    private fun buildNotificationEntries(keys: Set<VolumeStore.VolumeKey>): List<NotificationEntry> {
        if (keys.isEmpty()) return emptyList()
        return keys.map { key ->
            val label = resolveLabel(key.packageName)
            val volume = volumeStore.getVolume(key) ?: 1f
            NotificationEntry(
                packageName = key.packageName,
                userId = key.userId,
                label = label,
                percent = (volume * 100).toInt().coerceIn(0, 100)
            )
        }.sortedBy { it.label.lowercase() }
            .take(NOTIF_MAX_ROWS)
    }

    private fun resolveLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun formatOutputDevice(device: VolumeStore.OutputDevice): String {
        val address = device.address?.takeIf { it.isNotBlank() } ?: "-"
        val name = device.name?.takeIf { it.isNotBlank() } ?: "-"
        return "type=${device.type},addr=$address,name=$name"
    }

    private fun truncateDetails(details: List<String>, maxChars: Int): String {
        if (details.isEmpty()) return ""
        val builder = StringBuilder()
        for (entry in details) {
            if (builder.isNotEmpty()) builder.append(',')
            if (builder.length + entry.length > maxChars) {
                if (builder.isNotEmpty()) builder.append(',')
                builder.append("truncated")
                break
            }
            builder.append(entry)
        }
        return builder.toString()
    }

    private fun setupCallStateListener() {
        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                telephonyManager.registerTelephonyCallback(mainExecutor, callback)
                telephonyCallback = callback
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Android API")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Call state listener unavailable", t)
            diagnosticsStore.recordLastError("Call state listener unavailable: ${t.javaClass.simpleName}")
        }
    }

    private fun teardownCallStateListener() {
        val telephonyManager = getSystemService(TelephonyManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 31) {
            val callback = telephonyCallback ?: return
            telephonyManager.unregisterTelephonyCallback(callback)
            telephonyCallback = null
        } else {
            @Suppress("DEPRECATION")
            val listener = phoneStateListener ?: return
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_NONE)
            phoneStateListener = null
        }
    }

    private fun handleCallState(state: Int) {
        val label = when (state) {
            TelephonyManager.CALL_STATE_IDLE -> "idle"
            TelephonyManager.CALL_STATE_RINGING -> "ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "offhook"
            else -> "unknown"
        }
        diagnosticsStore.recordCallState(label)
        val shouldMute = state == TelephonyManager.CALL_STATE_RINGING ||
            state == TelephonyManager.CALL_STATE_OFFHOOK
        if (shouldMute != callMuteActive) {
            callMuteActive = shouldMute
            safeApplyVolumes(audioManager.activePlaybackConfigurations)
        }
    }

    companion object {
        private const val TAG = "PlaybackController"
        private const val CHANNEL_ID = "app_volume_control"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_REAPPLY = "com.duda.appvolumecontrol.action.REAPPLY"
        const val ACTION_DIAGNOSTICS_DUMP = "com.duda.appvolumecontrol.action.DIAGNOSTICS_DUMP"
        const val ACTION_LOCATION_REFRESH = "com.duda.appvolumecontrol.action.LOCATION_REFRESH"
        const val ACTION_LOCATION_SYNC = "com.duda.appvolumecontrol.action.LOCATION_SYNC"
        private const val NOTIF_MAX_ROWS = 3
        private const val MAX_ROUTE_DETAIL_CHARS = 600
        private const val NOTIF_VOLUME_STEP = 0.05f

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

        fun requestLocationRefresh(context: Context) {
            val intent = Intent(context, PlaybackControllerService::class.java)
            intent.action = ACTION_LOCATION_REFRESH
            context.startForegroundService(intent)
        }

        fun requestLocationSync(context: Context) {
            val intent = Intent(context, PlaybackControllerService::class.java)
            intent.action = ACTION_LOCATION_SYNC
            context.startForegroundService(intent)
        }
    }

    private data class NotificationEntry(
        val packageName: String,
        val userId: Int?,
        val label: String,
        val percent: Int,
    )
}
