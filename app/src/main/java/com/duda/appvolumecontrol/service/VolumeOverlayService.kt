package com.duda.appvolumecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.ProfileUtils
import com.duda.appvolumecontrol.core.SetupStatus
import com.duda.appvolumecontrol.core.VolumeStore
import com.duda.appvolumecontrol.core.OverlayPrefs
import com.duda.appvolumecontrol.core.MarkerStyle
import com.duda.appvolumecontrol.core.DiagnosticsStore
import android.graphics.drawable.Drawable
import android.widget.Toast
import com.duda.appvolumecontrol.core.OutputLabelUtils
import com.duda.appvolumecontrol.ui.OverlayPermissionActivity
import com.duda.appvolumecontrol.ui.SeekBarTicksView
import kotlin.math.abs
import kotlin.math.roundToInt

class VolumeOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val volumeStore by lazy { VolumeStore(this) }
    private val overlayPrefs by lazy { OverlayPrefs(this) }
    private val diagnosticsStore by lazy { DiagnosticsStore(this) }
    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private var overlayView: View? = null
    private var listContainer: LinearLayout? = null
    private var statusView: TextView? = null
    private var isWatching: Boolean = false
    private var lastVolumeSignature: String? = null
    private var stopAfterHide: Boolean = false
    private var autoHideEnabled: Boolean = true
    private var activeSignature: String? = null
    private var controlsEnabled: Boolean = true
    private val lastNonZeroVolumes = mutableMapOf<String, Float>()
    private var lastShowReason: String = DiagnosticsStore.OVERLAY_SIGNAL_MANUAL
    private val iconCache = mutableMapOf<String, Drawable>()

    private val hideRunnable = Runnable {
        removeOverlay()
        if (stopAfterHide && !isWatching) {
            stopAfterHide = false
            stopSelf()
        }
    }

    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            if (overlayView?.parent == null) return
            if (overlayPrefs.isAutoRefreshEnabled()) {
                val signature = buildActiveSignature()
                if (signature != activeSignature) {
                    activeSignature = signature
                    populateList()
                }
            }
            handler.postDelayed(this, AUTO_REFRESH_MS)
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isWatching) return
            handleVolumeSignal(DiagnosticsStore.OVERLAY_SIGNAL_POLL)
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            onVolumeChanged()
        }
    }

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            if (action != VOLUME_CHANGED_ACTION &&
                action != STREAM_MUTE_CHANGED_ACTION &&
                action != MASTER_MUTE_CHANGED_ACTION &&
                action != RINGER_MODE_CHANGED_ACTION
            ) {
                return
            }
            handleVolumeSignal(DiagnosticsStore.OVERLAY_SIGNAL_BROADCAST, force = true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_WATCH -> startWatching()
            ACTION_STOP_WATCH -> {
                stopWatching()
                stopSelf()
            }
            ACTION_SHOW -> {
                diagnosticsStore.recordOverlaySignal(DiagnosticsStore.OVERLAY_SIGNAL_MANUAL)
                lastShowReason = DiagnosticsStore.OVERLAY_SIGNAL_MANUAL
                showOverlay(stopWhenHidden = false, autoHide = false)
            }
            null -> {
                if (overlayPrefs.isOverlayEnabled()) {
                    startWatching()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopWatching(clearPrefs = false)
        removeOverlay()
        super.onDestroy()
    }

    private fun startWatching() {
        if (isWatching) return
        if (!canDrawOverlays()) {
            launchPermissionActivity()
            return
        }
        overlayPrefs.setOverlayEnabled(true)
        registerVolumeObservers()
        lastVolumeSignature = currentVolumeSignature()
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED_ACTION)
            addAction(STREAM_MUTE_CHANGED_ACTION)
            addAction(MASTER_MUTE_CHANGED_ACTION)
            addAction(RINGER_MODE_CHANGED_ACTION)
        }
        registerReceiver(volumeReceiver, filter)
        isWatching = true
        diagnosticsStore.recordOverlayWatchStart()
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    private fun stopWatching(clearPrefs: Boolean = true) {
        if (!isWatching) return
        runCatching { contentResolver.unregisterContentObserver(volumeObserver) }
        runCatching { unregisterReceiver(volumeReceiver) }
        handler.removeCallbacks(pollRunnable)
        isWatching = false
        if (clearPrefs) {
            overlayPrefs.setOverlayEnabled(false)
        }
        diagnosticsStore.recordOverlayWatchStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun onVolumeChanged() {
        handleVolumeSignal(DiagnosticsStore.OVERLAY_SIGNAL_OBSERVER, force = true)
    }

    private fun handleVolumeSignal(reason: String, force: Boolean = false) {
        val signature = currentVolumeSignature()
        if (!force && signature == lastVolumeSignature) return
        lastVolumeSignature = signature
        diagnosticsStore.recordOverlaySignal(reason)
        lastShowReason = reason
        showOverlay(stopWhenHidden = false, autoHide = overlayPrefs.isAutoHideEnabled())
    }

    private fun showOverlay(stopWhenHidden: Boolean, autoHide: Boolean) {
        if (!canDrawOverlays()) {
            launchPermissionActivity()
            return
        }
        stopAfterHide = stopWhenHidden
        autoHideEnabled = autoHide
        val root = ensureOverlayView()
        val activeCount = populateList()
        startAutoRefresh()
        diagnosticsStore.recordOverlayShow(lastShowReason, activeCount)
        if (root.parent == null) {
            windowManager.addView(root, buildLayoutParams())
        }
        if (autoHide) {
            scheduleHide()
        } else {
            handler.removeCallbacks(hideRunnable)
        }
    }

    private fun ensureOverlayView(): View {
        overlayView?.let { return it }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F5F5F5"))
                cornerRadius = dpToPx(12).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#E0E0E0"))
            }
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    scheduleHideIfEnabled()
                }
                false
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "App volumes"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener {
                activeSignature = buildActiveSignature()
                populateList()
                scheduleHideIfEnabled()
            }
        }

        val autoToggle = CheckBox(this).apply {
            text = "Auto"
            textSize = 12f
            isChecked = overlayPrefs.isAutoRefreshEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setAutoRefreshEnabled(isChecked)
                scheduleHideIfEnabled()
            }
        }

        val autoHideToggle = CheckBox(this).apply {
            text = "Hide"
            textSize = 12f
            isChecked = overlayPrefs.isAutoHideEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setAutoHideEnabled(isChecked)
                autoHideEnabled = isChecked
                scheduleHideIfEnabled()
            }
        }

        val outputOnlyToggle = CheckBox(this).apply {
            text = "Output"
            textSize = 12f
            isChecked = overlayPrefs.isOutputOnlyEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setOutputOnlyEnabled(isChecked)
                populateList()
                scheduleHideIfEnabled()
            }
        }

        val closeButton = Button(this).apply {
            text = "Close"
            setOnClickListener { removeOverlay() }
        }

        headerRow.addView(title)
        headerRow.addView(refreshButton)
        headerRow.addView(autoToggle)
        headerRow.addView(autoHideToggle)
        headerRow.addView(outputOnlyToggle)
        headerRow.addView(closeButton)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#B71C1C"))
            setPadding(0, dpToPx(6), 0, dpToPx(6))
            visibility = View.GONE
        }

        root.addView(headerRow)
        root.addView(statusView)
        root.addView(listContainer)
        overlayView = root
        return root
    }

    private fun populateList(): Int {
        val container = listContainer ?: return 0
        container.removeAllViews()
        controlsEnabled = SetupStatus.isControlReady(this)
        statusView?.let { status ->
            if (controlsEnabled) {
                status.visibility = View.GONE
            } else {
                status.text = "Controls disabled (open Setup Check)"
                status.visibility = View.VISIBLE
            }
        }
        val outputOnly = overlayPrefs.isOutputOnlyEnabled()
        val entries = if (outputOnly) {
            loadActiveEntries().filter { volumeStore.getOutputDevice(it.key) != null }
        } else {
            loadActiveEntries()
        }
        if (entries.isEmpty()) {
            container.addView(buildEmptyRow("No active audio."))
            return 0
        }
        for (entry in entries) {
            container.addView(buildVolumeRow(entry))
        }
        return entries.size
    }

    private fun loadActiveEntries(): List<AppEntry> {
        val configs = audioManager.activePlaybackConfigurations
        val counts = mutableMapOf<VolumeStore.VolumeKey, Int>()
        for (config in configs) {
            val packageName = PlaybackUtils.getClientPackageName(config, packageManager) ?: continue
            val uid = PlaybackUtils.getClientUid(config)
            val userId = PlaybackUtils.getUserIdFromUid(uid)
            val key = VolumeStore.VolumeKey(packageName, userId)
            counts[key] = (counts[key] ?: 0) + 1
        }
        return counts.map { (key, count) ->
            AppEntry(key, resolveLabel(key.packageName), count)
        }.sortedBy { it.label.lowercase() }
    }

    private fun buildVolumeRow(entry: AppEntry): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFFFFF"))
                cornerRadius = dpToPx(10).toFloat()
                setStroke(dpToPx(1), Color.parseColor("#E0E0E0"))
            }
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(20), dpToPx(20)).apply {
                marginEnd = dpToPx(6)
            }
            setImageDrawable(resolveIcon(entry.key.packageName))
        }

        val nameView = TextView(this).apply {
            text = entry.label
            textSize = 13f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnLongClickListener {
                copyToClipboard(entry.key.packageName)
                true
            }
        }

        val profileView = TextView(this).apply {
            text = ProfileUtils.buildProfileLabel(this@VolumeOverlayService, entry.key.userId)
            textSize = 11f
            setTextColor(Color.parseColor("#616161"))
            visibility = if (entry.key.userId != null) View.VISIBLE else View.GONE
            setPadding(dpToPx(6), 0, 0, 0)
        }

        val countView = TextView(this).apply {
            text = "x${entry.activeCount}"
            textSize = 11f
            setPadding(dpToPx(6), 0, 0, 0)
            visibility = if (entry.activeCount > 1) View.VISIBLE else View.GONE
        }

        val outputBadge = TextView(this).apply {
            val output = volumeStore.getOutputDevice(entry.key)
            text = output?.let { "${OutputLabelUtils.shortType(it.type)} ${it.name ?: "Output"}" } ?: "Output"
            textSize = 10f
            setPadding(dpToPx(6), 0, 0, 0)
            setTextColor(Color.parseColor("#455A64"))
            visibility = if (output == null) View.GONE else View.VISIBLE
        }

        headerRow.addView(iconView)
        headerRow.addView(nameView)
        headerRow.addView(profileView)
        headerRow.addView(countView)
        headerRow.addView(outputBadge)

        val stored = volumeStore.getVolume(entry.key) ?: 1f
        val valueView = TextView(this).apply {
            text = "${(stored * 100).toInt()}%"
            textSize = 11f
            setPadding(dpToPx(6), 0, 0, 0)
        }

        val sliderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        lateinit var muteButton: ImageButton

        val sliderFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val ticksView = SeekBarTicksView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(12),
                Gravity.CENTER_VERTICAL
            )
        }
        applyCustomMarkers(ticksView, entry.key)

        val slider = SeekBar(this).apply {
            max = 100
            progress = (stored * 100).toInt()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL
            )
            var lastProgress = progress
            var lastChangeTime = SystemClock.elapsedRealtime()
            var ignoreNextProgrammatic = false
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) {
                        if (ignoreNextProgrammatic) {
                            ignoreNextProgrammatic = false
                        }
                        return
                    }
                    val now = SystemClock.elapsedRealtime()
                    val delta = abs(progress - lastProgress)
                    val timeDelta = now - lastChangeTime
                    lastProgress = progress
                    lastChangeTime = now
                    val snapped = snapToMarker(progress, delta, timeDelta)
                    if (snapped != progress) {
                        ignoreNextProgrammatic = true
                        seekBar?.progress = snapped
                        applyVolume(entry, snapped, valueView)
                        updateMuteIcon(muteButton, snapped / 100f)
                        if (snapped != progress) {
                            seekBar?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        }
                        scheduleHideIfEnabled()
                        return
                    }
                    applyVolume(entry, progress, valueView)
                    updateMuteIcon(muteButton, progress / 100f)
                    scheduleHideIfEnabled()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    scheduleHideIfEnabled()
                }

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    scheduleHideIfEnabled()
                }
            })
        }

        sliderFrame.addView(ticksView)
        sliderFrame.addView(slider)
        sliderRow.addView(sliderFrame)
        sliderRow.addView(valueView)

        muteButton = ImageButton(this).apply {
            background = null
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                marginStart = dpToPx(6)
            }
        }

        updateMuteIcon(muteButton, stored)
        muteButton.setOnClickListener {
            val currentVolume = volumeStore.getVolume(entry.key) ?: 1f
            if (currentVolume > 0f) {
                lastNonZeroVolumes[entry.key.storageKey()] = currentVolume
                applyVolume(entry, 0, valueView)
                slider.progress = 0
                updateMuteIcon(muteButton, 0f)
            } else {
                val restored = lastNonZeroVolumes[entry.key.storageKey()] ?: 1f
                val percent = (restored * 100).roundToInt()
                applyVolume(entry, percent, valueView)
                slider.progress = percent
                updateMuteIcon(muteButton, restored)
            }
            scheduleHideIfEnabled()
        }

        headerRow.addView(muteButton)

        card.addView(headerRow)
        card.addView(sliderRow)
        row.addView(card)

        return row
    }

    private fun buildEmptyRow(message: String): View {
        return TextView(this).apply {
            text = message
            textSize = 12f
            setPadding(0, dpToPx(8), 0, dpToPx(8))
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(hideRunnable)
        handler.removeCallbacks(autoRefreshRunnable)
        overlayView?.let {
            runCatching { windowManager.removeView(it) }
        }
        if (stopAfterHide && !isWatching) {
            stopAfterHide = false
            stopSelf()
        }
    }

    private fun scheduleHide() {
        handler.removeCallbacks(hideRunnable)
        handler.postDelayed(hideRunnable, AUTO_HIDE_MS)
    }

    private fun scheduleHideIfEnabled() {
        if (!autoHideEnabled) return
        scheduleHide()
    }

    private fun startAutoRefresh() {
        handler.removeCallbacks(autoRefreshRunnable)
        handler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_MS)
    }

    private fun buildActiveSignature(): String {
        val entries = loadActiveEntries()
        if (entries.isEmpty()) return ""
        return entries.joinToString("|") { entry ->
            val userPart = entry.key.userId?.toString() ?: "x"
            "${entry.key.packageName}@$userPart:${entry.activeCount}"
        }
    }

    private fun currentVolumeSignature(): String {
        val parts = mutableListOf<Int>()
        STREAMS_TO_WATCH.forEach { stream ->
            val volume = runCatching { audioManager.getStreamVolume(stream) }.getOrDefault(0)
            parts.add(volume)
        }
        return parts.joinToString("|")
    }

    private fun snapToMarker(progress: Int, delta: Int, timeDelta: Long): Int {
        if (delta > SNAP_DELTA_MAX || timeDelta < SNAP_TIME_MIN_MS) return progress
        val markers = arrayOf(0, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100)
        val nearest = markers.minByOrNull { abs(it - progress) } ?: progress
        return if (abs(nearest - progress) <= SNAP_THRESHOLD) nearest else progress
    }

    private fun applyVolume(entry: AppEntry, progress: Int, valueView: TextView) {
        val displayProgress = progress.coerceIn(0, 100)
        val volume = displayProgress / 100f
        volumeStore.setVolume(entry.key, volume)
        PlaybackControllerService.requestReapply(this@VolumeOverlayService)
        valueView.text = "$displayProgress%"
        if (volume > 0f) {
            lastNonZeroVolumes[entry.key.storageKey()] = volume
        }
    }

    private fun applyCustomMarkers(ticksView: SeekBarTicksView, key: VolumeStore.VolumeKey) {
        val config = volumeStore.getMarkerConfig(key)
        if (config == null) {
            ticksView.setCustomMarkers(emptyList(), emptyList())
            return
        }
        val markers = config.markers.mapNotNull { marker ->
            val percent = marker.percent
            if (percent < 0 || percent > 100) return@mapNotNull null
            SeekBarTicksView.MarkerLine(
                percent / 100f,
                MarkerStyle.resolveMarkerColor(key.packageName, marker.color)
            )
        }
        val segments = config.segments.mapNotNull { segment ->
            if (segment.start == segment.end) return@mapNotNull null
            val start = segment.start.coerceIn(0, 100)
            val end = segment.end.coerceIn(0, 100)
            val (minVal, maxVal) = if (start <= end) start to end else end to start
            SeekBarTicksView.SegmentRect(
                minVal / 100f,
                maxVal / 100f,
                MarkerStyle.resolveSegmentColor(key.packageName, segment.color)
            )
        }
        ticksView.setCustomMarkers(markers, segments)
    }

    private fun updateMuteIcon(button: ImageButton, volume: Float) {
        button.setImageResource(
            if (volume <= 0f) android.R.drawable.ic_lock_silent_mode
            else android.R.drawable.ic_lock_silent_mode_off
        )
    }

    private fun resolveLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }


    private fun resolveIcon(packageName: String): Drawable? {
        return iconCache[packageName] ?: try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationIcon(appInfo).also { iconCache[packageName] = it }
        } catch (_: Exception) {
            getDrawable(android.R.drawable.sym_def_app_icon)
        }
    }

    private fun copyToClipboard(text: String) {
        val manager = getSystemService(android.content.ClipboardManager::class.java)
        manager.setPrimaryClip(android.content.ClipData.newPlainText("package", text))
        Toast.makeText(this, "Package copied", Toast.LENGTH_SHORT).show()
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dpToPx(32)
        }
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay watcher",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows overlay when volume buttons are pressed"
            manager.createNotificationChannel(channel)
        }
        val stopIntent = Intent(this, VolumeOverlayService::class.java).apply {
            action = ACTION_STOP_WATCH
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutable()
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Volume overlay enabled")
            .setContentText("Shows per-app sliders when volume changes.")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopPending).build())
            .build()
    }

    private fun pendingIntentImmutable(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun launchPermissionActivity() {
        val intent = Intent(this, OverlayPermissionActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class AppEntry(
        val key: VolumeStore.VolumeKey,
        val label: String,
        val activeCount: Int = 0,
    )

    companion object {
        const val ACTION_SHOW = "com.duda.appvolumecontrol.action.SHOW_OVERLAY"
        const val ACTION_START_WATCH = "com.duda.appvolumecontrol.action.START_OVERLAY_WATCH"
        const val ACTION_STOP_WATCH = "com.duda.appvolumecontrol.action.STOP_OVERLAY_WATCH"
        private const val CHANNEL_ID = "overlay_watcher"
        private const val NOTIFICATION_ID = 1201
        private const val AUTO_HIDE_MS = 3500L
        private const val AUTO_REFRESH_MS = 1000L
        private const val VOLUME_MUSIC_SETTING = "volume_music"
        private const val VOLUME_RING_SETTING = "volume_ring"
        private const val VOLUME_ALARM_SETTING = "volume_alarm"
        private const val VOLUME_NOTIFICATION_SETTING = "volume_notification"
        private const val VOLUME_SYSTEM_SETTING = "volume_system"
        private const val VOLUME_VOICE_SETTING = "volume_voice"
        private const val VOLUME_DTMF_SETTING = "volume_dtmf"
        private const val VOLUME_BLUETOOTH_SCO_SETTING = "volume_bluetooth_sco"
        private const val VOLUME_ACCESSIBILITY_SETTING = "volume_accessibility"
        private const val VOLUME_ASSISTANT_SETTING = "volume_assistant"
        private const val POLL_INTERVAL_MS = 500L
        private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
        private const val STREAM_MUTE_CHANGED_ACTION = "android.media.STREAM_MUTE_CHANGED_ACTION"
        private const val MASTER_MUTE_CHANGED_ACTION = "android.media.MASTER_MUTE_CHANGED_ACTION"
        private const val RINGER_MODE_CHANGED_ACTION = "android.media.RINGER_MODE_CHANGED"
        private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val SNAP_THRESHOLD = 2
        private const val SNAP_DELTA_MAX = 2
        private const val SNAP_TIME_MIN_MS = 50L

        private val BASE_STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_ACCESSIBILITY,
        )

        private val OPTIONAL_STREAMS = listOfNotNull(
            resolveOptionalStream("STREAM_BLUETOOTH_SCO"),
            resolveOptionalStream("STREAM_ASSISTANT"),
        )

        private val STREAMS_TO_WATCH = buildList {
            addAll(BASE_STREAMS.toList())
            addAll(OPTIONAL_STREAMS)
        }.toIntArray()

        private val VOLUME_SETTING_KEYS = listOf(
            VOLUME_MUSIC_SETTING,
            VOLUME_RING_SETTING,
            VOLUME_ALARM_SETTING,
            VOLUME_NOTIFICATION_SETTING,
            VOLUME_SYSTEM_SETTING,
            VOLUME_VOICE_SETTING,
            VOLUME_DTMF_SETTING,
            VOLUME_BLUETOOTH_SCO_SETTING,
            VOLUME_ACCESSIBILITY_SETTING,
            VOLUME_ASSISTANT_SETTING,
        )

        private fun resolveOptionalStream(name: String): Int? {
            return runCatching {
                AudioManager::class.java.getField(name).getInt(null)
            }.getOrNull()
        }
    }

    private fun registerVolumeObservers() {
        val resolver = contentResolver
        val seen = HashSet<Uri>()
        for (key in VOLUME_SETTING_KEYS) {
            Settings.System.getUriFor(key)?.let { uri ->
                if (seen.add(uri)) {
                    resolver.registerContentObserver(uri, false, volumeObserver)
                }
            }
            Settings.Secure.getUriFor(key)?.let { uri ->
                if (seen.add(uri)) {
                    resolver.registerContentObserver(uri, false, volumeObserver)
                }
            }
        }
        // Legacy key to keep older devices covered.
        Settings.System.getUriFor(VOLUME_MUSIC_SETTING)?.let { uri ->
            if (seen.add(uri)) {
                resolver.registerContentObserver(uri, false, volumeObserver)
            }
        }
    }
}
