package com.duda.appvolumecontrol.ui

import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.text.TextUtils
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.widget.addTextChangedListener
import android.widget.CheckBox
import com.duda.appvolumecontrol.core.ActiveAppStore
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.OverlayPrefs
import com.duda.appvolumecontrol.core.ProfileUtils
import com.duda.appvolumecontrol.core.VolumeStore
import com.duda.appvolumecontrol.service.PlaybackControllerService
import com.duda.appvolumecontrol.service.VolumeOverlayService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var volumeStore: VolumeStore
    private lateinit var activeStore: ActiveAppStore
    private lateinit var activeContainer: LinearLayout
    private lateinit var presetContainer: LinearLayout
    private lateinit var presetSearch: EditText
    private lateinit var clearFilterButton: Button
    private lateinit var manageAppsButton: Button
    private lateinit var lastUpdatedView: TextView
    private lateinit var activeTitle: TextView
    private lateinit var presetTitle: TextView
    private lateinit var profileSpinner: Spinner
    private lateinit var profileAdapter: ArrayAdapter<String>
    private lateinit var overlayPrefs: OverlayPrefs
    private lateinit var overlayStatusView: TextView
    private lateinit var overlayToggle: CheckBox
    private lateinit var overlayShowButton: Button
    private lateinit var overlayPermissionButton: Button
    private var overlayUpdating: Boolean = false
    private var presetEntries: List<AppEntry> = emptyList()
    private var activeEntries: List<AppEntry> = emptyList()
    private var activeKeys: Set<String> = emptySet()
    private var activePackagesByUser: Map<String, Set<Int?>> = emptyMap()
    private var profileOptions: List<ProfileOption> = emptyList()
    private var selectedProfileUserId: Int? = null
    private var profileUpdating: Boolean = false
    private val activeSince = mutableMapOf<String, Long>()
    private val lastNonZeroVolumes = mutableMapOf<String, Float>()
    private val iconCache = mutableMapOf<String, Drawable>()
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateActiveApps(audioManager.activePlaybackConfigurations)
            updateLastUpdated()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            updateActiveApps(configs)
            updateLastUpdated()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        volumeStore = VolumeStore(this)
        activeStore = ActiveAppStore(this)
        overlayPrefs = OverlayPrefs(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "App Volume Control"
            textSize = 20f
        }

        val quickStart = TextView(this).apply {
            text = "Quick start:\n1) Install the Magisk module and reboot.\n2) Play audio in an app.\n3) Move the slider for that app."
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }

        val profileLabel = TextView(this).apply {
            text = "Profile"
            textSize = 13f
        }

        profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf("All profiles")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        profileSpinner = Spinner(this).apply {
            adapter = profileAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (profileUpdating) return
                    val option = profileOptions.getOrNull(position) ?: ProfileOption(null, "All profiles")
                    selectedProfileUserId = option.userId
                    updateActiveApps(audioManager.activePlaybackConfigurations)
                    updatePresetApps()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
            addView(profileLabel)
            addView(profileSpinner)
        }

        val refreshButton = Button(this).apply {
            text = "Refresh list"
            setOnClickListener {
                updateActiveApps(audioManager.activePlaybackConfigurations)
                updateLastUpdated()
            }
        }

        val resetButton = Button(this).apply {
            text = "Clear saved volumes"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Clear saved volumes")
                    .setMessage("This clears all saved per-app sliders.")
                    .setPositiveButton("Clear") { _, _ ->
                        volumeStore.clearAll()
                        updateActiveApps(audioManager.activePlaybackConfigurations)
                        PlaybackControllerService.requestReapply(this@MainActivity)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        val boostActiveButton = Button(this).apply {
            text = "Make active loud"
            setOnClickListener {
                for (entry in activeEntries) {
                    volumeStore.setVolume(entry.key, 1f)
                }
                updateActiveApps(audioManager.activePlaybackConfigurations)
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val muteActiveButton = Button(this).apply {
            text = "Mute active"
            setOnClickListener {
                for (entry in activeEntries) {
                    volumeStore.setVolume(entry.key, 0f)
                }
                updateActiveApps(audioManager.activePlaybackConfigurations)
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val halfActiveButton = Button(this).apply {
            text = "Active half"
            setOnClickListener {
                for (entry in activeEntries) {
                    volumeStore.setVolume(entry.key, 0.5f)
                }
                updateActiveApps(audioManager.activePlaybackConfigurations)
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val diagnosticsButton = Button(this).apply {
            text = "Diagnostics / report"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
            }
        }

        val helpButton = Button(this).apply {
            text = "Help"
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Help")
                    .setMessage(
                        "This app needs to be installed as a system app via Magisk.\n" +
                            "Steps:\n" +
                            "1) Install the Magisk module and reboot.\n" +
                            "2) Play audio in any app.\n" +
                            "3) Move the slider for that app.\n\n" +
                            "If nothing happens, open Diagnostics and copy the report."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }

        val checkButton = Button(this).apply {
            text = "Check setup"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SetupCheckActivity::class.java))
            }
        }

        val overlayTitle = TextView(this).apply {
            text = "Overlay controls"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        overlayStatusView = TextView(this).apply {
            text = "Overlay permission: checking..."
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }

        overlayPermissionButton = Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, OverlayPermissionActivity::class.java))
            }
        }

        overlayShowButton = Button(this).apply {
            text = "Show overlay now"
            setOnClickListener {
                val intent = Intent(this@MainActivity, VolumeOverlayService::class.java)
                intent.action = VolumeOverlayService.ACTION_SHOW
                startService(intent)
            }
        }

        overlayToggle = CheckBox(this).apply {
            text = "Enable volume-button overlay"
            isChecked = overlayPrefs.isOverlayEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                if (overlayUpdating) return@setOnCheckedChangeListener
                overlayPrefs.setOverlayEnabled(isChecked)
                val intent = Intent(this@MainActivity, VolumeOverlayService::class.java)
                intent.action = if (isChecked) {
                    VolumeOverlayService.ACTION_START_WATCH
                } else {
                    VolumeOverlayService.ACTION_STOP_WATCH
                }
                startService(intent)
                updateOverlayStatus()
            }
        }

        lastUpdatedView = TextView(this).apply {
            text = "Last updated: -"
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }

        activeTitle = TextView(this).apply {
            text = "Active audio"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        activeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        presetTitle = TextView(this).apply {
            text = "Saved volumes"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        manageAppsButton = Button(this).apply {
            text = "Manage apps"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ManageAppsActivity::class.java))
            }
        }

        presetSearch = EditText(this).apply {
            hint = "Search saved apps"
            textSize = 14f
            setPadding(16, 8, 16, 8)
            addTextChangedListener { updatePresetApps(it?.toString().orEmpty()) }
        }

        clearFilterButton = Button(this).apply {
            text = "Clear search"
            setOnClickListener { presetSearch.setText("") }
        }

        presetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(header)
        root.addView(quickStart)
        root.addView(profileRow)
        root.addView(refreshButton)
        root.addView(resetButton)
        root.addView(boostActiveButton)
        root.addView(muteActiveButton)
        root.addView(halfActiveButton)
        root.addView(diagnosticsButton)
        root.addView(helpButton)
        root.addView(checkButton)
        root.addView(overlayTitle)
        root.addView(overlayStatusView)
        root.addView(overlayPermissionButton)
        root.addView(overlayShowButton)
        root.addView(overlayToggle)
        root.addView(lastUpdatedView)
        root.addView(activeTitle)
        root.addView(activeContainer)
        root.addView(presetTitle)
        root.addView(manageAppsButton)
        root.addView(presetSearch)
        root.addView(clearFilterButton)
        root.addView(presetContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)

        startForegroundService(Intent(this, PlaybackControllerService::class.java))
        updateOverlayStatus()
        updateActiveApps(audioManager.activePlaybackConfigurations)
        loadPresetApps()
        updatePresetApps()
    }

    override fun onResume() {
        super.onResume()
        maybeRequestNotifications()
        updateOverlayStatus()
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        loadPresetApps()
        updatePresetApps()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onPause()
    }

    private fun updateActiveApps(configs: List<AudioPlaybackConfiguration>) {
        activeContainer.removeAllViews()
        val now = System.currentTimeMillis()
        val counts = mutableMapOf<VolumeStore.VolumeKey, Int>()
        for (config in configs) {
            val packageName = PlaybackUtils.getClientPackageName(config, packageManager) ?: continue
            val uid = PlaybackUtils.getClientUid(config)
            val userId = PlaybackUtils.getUserIdFromUid(uid)
            val key = VolumeStore.VolumeKey(packageName, userId)
            counts[key] = (counts[key] ?: 0) + 1
        }

        activeEntries = counts.map { (key, count) ->
            AppEntry(key, resolveLabel(key.packageName), count)
        }.sortedBy { it.label.lowercase() }
        activeKeys = activeEntries.map { it.key.storageKey() }.toSet()
        activePackagesByUser = counts.keys.groupBy { it.packageName }
            .mapValues { entry -> entry.value.map { it.userId }.toSet() }

        updateActiveSince(counts.keys, now)
        updateProfileOptions()

        val filtered = filterActiveEntries(activeEntries)
        activeTitle.text = buildActiveTitle(filtered.size)

        if (filtered.isEmpty()) {
            activeContainer.addView(buildEmptyRow("No active audio. Start playing music or video."))
            updatePresetApps()
            return
        }

        for (entry in filtered) {
            activeContainer.addView(buildVolumeRow(entry, true))
        }
        updatePresetApps()
    }

    private fun loadPresetApps() {
        val entries = volumeStore.listPresetEntries()
        presetEntries = entries
            .distinctBy { it.storageKey() }
            .map { key -> AppEntry(key, resolveLabel(key.packageName), 0) }
            .sortedBy { it.label.lowercase() }
    }

    private fun updatePresetApps(filter: String = presetSearch.text?.toString().orEmpty()) {
        presetContainer.removeAllViews()
        loadPresetApps()
        updateProfileOptions()
        val trimmedFilter = filter.trim().lowercase()
        val filtered = filterPresetEntries(presetEntries)
        val packages = if (trimmedFilter.isBlank()) {
            filtered
        } else {
            filtered.filter {
                it.label.lowercase().contains(trimmedFilter) ||
                    it.key.packageName.lowercase().contains(trimmedFilter)
            }
        }
        presetTitle.text = "Saved volumes (${packages.size})"

        if (packages.isEmpty()) {
            val message = if (trimmedFilter.isBlank()) {
                "No saved apps yet. Use Manage apps to add."
            } else {
                "No matching apps."
            }
            presetContainer.addView(buildEmptyRow(message))
            return
        }

        val sorted = packages.sortedWith(
            compareByDescending<AppEntry> { isEntryActive(it) }
                .thenBy { it.label.lowercase() }
        )

        for (entry in sorted) {
            val isActive = isEntryActive(entry)
            presetContainer.addView(buildVolumeRow(entry, isActive))
        }
    }

    private fun buildVolumeRow(entry: AppEntry, isActive: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val indicator = View(this).apply {
            val size = dpToPx(10)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(8)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isActive) Color.parseColor("#2E7D32") else Color.parseColor("#9E9E9E"))
            }
        }

        val iconView = ImageView(this).apply {
            val size = dpToPx(32)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(12)
            }
            setImageDrawable(resolveIcon(entry.key.packageName))
        }

        val nameContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = entry.label
            textSize = 14f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener {
                showAppInfo(entry, isActive)
            }
            setOnLongClickListener {
                copyToClipboard(entry.key.packageName)
                true
            }
        }

        val profileView = TextView(this).apply {
            text = ProfileUtils.buildProfileLabel(this@MainActivity, entry.key.userId)
            textSize = 12f
            setTextColor(Color.parseColor("#616161"))
            visibility = if (shouldShowProfile(entry.key.userId)) View.VISIBLE else View.GONE
        }

        nameContainer.addView(nameView)
        nameContainer.addView(profileView)

        val countView = TextView(this).apply {
            text = "x${entry.activeCount}"
            textSize = 12f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            visibility = if (entry.activeCount > 1) View.VISIBLE else View.GONE
        }

        val stored = volumeStore.getVolume(entry.key) ?: 1f
        val valueView = TextView(this).apply {
            text = "${(stored * 100).toInt()}%"
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        var sliderRef: SeekBar? = null
        lateinit var muteButton: ImageButton

        val updateMuteIcon: (Float) -> Unit = { volume ->
            muteButton.setImageResource(
                if (volume <= 0f) android.R.drawable.ic_lock_silent_mode
                else android.R.drawable.ic_lock_silent_mode_off
            )
        }

        muteButton = ImageButton(this).apply {
            background = null
            layoutParams = LinearLayout.LayoutParams(dpToPx(32), dpToPx(32)).apply {
                marginStart = dpToPx(4)
            }
            setOnClickListener {
                val currentVolume = volumeStore.getVolume(entry.key) ?: 1f
                if (currentVolume > 0f) {
                    lastNonZeroVolumes[entry.key.storageKey()] = currentVolume
                    volumeStore.setVolume(entry.key, 0f)
                    valueView.text = "0%"
                    sliderRef?.progress = 0
                    updateMuteIcon(0f)
                } else {
                    val restored = lastNonZeroVolumes[entry.key.storageKey()] ?: 1f
                    volumeStore.setVolume(entry.key, restored)
                    val percent = (restored * 100).toInt()
                    valueView.text = "$percent%"
                    sliderRef?.progress = percent
                    updateMuteIcon(restored)
                }
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val slider = SeekBar(this).apply {
            max = 100
            progress = (stored * 100).toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val volume = progress / 100f
                    volumeStore.setVolume(entry.key, volume)
                    PlaybackControllerService.requestReapply(this@MainActivity)
                    valueView.text = "$progress%"
                    if (volume > 0f) {
                        lastNonZeroVolumes[entry.key.storageKey()] = volume
                    }
                    updateMuteIcon(volume)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        sliderRef = slider

        updateMuteIcon(stored)

        val sliderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(56), dpToPx(6), 0, 0)
        }

        sliderRow.addView(slider)
        sliderRow.addView(valueView)

        headerRow.addView(indicator)
        headerRow.addView(iconView)
        headerRow.addView(nameContainer)
        headerRow.addView(countView)
        headerRow.addView(muteButton)

        row.addView(headerRow)
        row.addView(sliderRow)
        return row
    }

    private fun buildEmptyRow(message: String): View {
        return TextView(this).apply {
            text = message
            textSize = 14f
            setPadding(0, 8, 0, 8)
        }
    }

    private fun resolveLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun updateActiveSince(keys: Set<VolumeStore.VolumeKey>, now: Long) {
        val activeKeyStrings = keys.map { it.storageKey() }.toSet()
        val toRemove = activeSince.keys.filterNot { activeKeyStrings.contains(it) }
        for (key in toRemove) {
            activeSince.remove(key)
        }
        for (key in keys) {
            if (!activeSince.containsKey(key.storageKey())) {
                activeSince[key.storageKey()] = now
            }
            activeStore.recordActive(key, now)
        }
    }

    private fun updateProfileOptions() {
        val userIds = mutableSetOf<Int>()
        activeEntries.mapNotNullTo(userIds) { it.key.userId }
        presetEntries.mapNotNullTo(userIds) { it.key.userId }

        val options = mutableListOf(ProfileOption(null, "All profiles"))
        val profileIds = ProfileUtils.collectUserIds(this, userIds)
        for (userId in profileIds) {
            options.add(ProfileOption(userId, ProfileUtils.buildProfileLabel(this, userId)))
        }

        if (options == profileOptions) return
        profileOptions = options
        profileUpdating = true
        profileAdapter.clear()
        profileAdapter.addAll(options.map { it.label })
        profileAdapter.notifyDataSetChanged()
        val selectedIndex = options.indexOfFirst { it.userId == selectedProfileUserId }
            .takeIf { it >= 0 } ?: 0
        selectedProfileUserId = options.getOrNull(selectedIndex)?.userId
        profileSpinner.setSelection(selectedIndex)
        profileUpdating = false
    }

    private fun filterActiveEntries(entries: List<AppEntry>): List<AppEntry> {
        val userId = selectedProfileUserId ?: return entries
        return entries.filter { it.key.userId == userId }
    }

    private fun filterPresetEntries(entries: List<AppEntry>): List<AppEntry> {
        val userId = selectedProfileUserId ?: return entries
        return entries.filter { it.key.userId == userId || it.key.userId == null }
    }

    private fun isEntryActive(entry: AppEntry): Boolean {
        return if (entry.key.userId == null) {
            isActiveForGlobal(entry.key.packageName)
        } else {
            activeKeys.contains(entry.key.storageKey())
        }
    }

    private fun isActiveForGlobal(packageName: String): Boolean {
        val userIds = activePackagesByUser[packageName] ?: return false
        return if (selectedProfileUserId == null) {
            userIds.isNotEmpty()
        } else {
            userIds.contains(selectedProfileUserId)
        }
    }

    private fun buildActiveTitle(count: Int): String {
        val label = selectedProfileUserId?.let { " (${ProfileUtils.buildProfileLabel(this, it)})" }.orEmpty()
        return "Active audio ($count)$label"
    }

    private fun shouldShowProfile(userId: Int?): Boolean {
        if (userId != null) return true
        return selectedProfileUserId != null
    }

    private fun showAppInfo(entry: AppEntry, isActive: Boolean) {
        val key = entry.key
        val infoLines = mutableListOf<String>()
        infoLines.add("Package: ${key.packageName}")
        infoLines.add("Profile: ${ProfileUtils.buildProfileLabel(this, key.userId)}")
        if (entry.activeCount > 1) {
            infoLines.add("Active instances: ${entry.activeCount}")
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val packageInfo = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    key.packageName,
                    PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(key.packageName, 0)
            }
        } catch (_: Exception) {
            null
        }

        val installTime = packageInfo?.firstInstallTime
        val updateTime = packageInfo?.lastUpdateTime
        if (installTime != null && installTime > 0L) {
            infoLines.add("Installed: ${formatTime(installTime, dateFormat)}")
        }
        if (updateTime != null && updateTime > 0L) {
            infoLines.add("Updated: ${formatTime(updateTime, dateFormat)}")
        }

        val activeSinceTime = activeSince[key.storageKey()]
        val stored = activeStore.getInfo(key)
        if (isActive && activeSinceTime != null) {
            infoLines.add("Active since: ${formatTime(activeSinceTime, dateFormat)}")
        } else {
            stored?.lastActiveTime?.let {
                infoLines.add("Last active: ${formatTime(it, dateFormat)}")
            }
        }
        stored?.firstActiveTime?.let {
            infoLines.add("First active: ${formatTime(it, dateFormat)}")
        }

        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setMessage(infoLines.joinToString("\n"))
            .setPositiveButton("OK", null)
            .setNeutralButton("Copy package") { _, _ ->
                copyToClipboard(key.packageName)
            }
            .show()
    }

    private fun formatTime(value: Long, dateFormat: SimpleDateFormat): String {
        return dateFormat.format(Date(value))
    }


    private fun updateLastUpdated() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lastUpdatedView.text = "Last updated: $time | Active: ${activeEntries.size}"
    }

    private fun maybeRequestNotifications() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_NOTIFICATIONS
        )
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
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("package", text))
        Toast.makeText(this, "Package copied", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun updateOverlayStatus() {
        val canDraw = canDrawOverlays()
        overlayStatusView.text = if (canDraw) {
            "Overlay permission: granted"
        } else {
            "Overlay permission: not granted"
        }
        overlayPermissionButton.isEnabled = !canDraw
        overlayShowButton.isEnabled = canDraw
        if (!canDraw && overlayPrefs.isOverlayEnabled()) {
            overlayPrefs.setOverlayEnabled(false)
            val intent = Intent(this, VolumeOverlayService::class.java)
            intent.action = VolumeOverlayService.ACTION_STOP_WATCH
            startService(intent)
        }
        overlayUpdating = true
        val desired = overlayPrefs.isOverlayEnabled() && canDraw
        if (overlayToggle.isChecked != desired) {
            overlayToggle.isChecked = desired
        }
        overlayToggle.isEnabled = canDraw
        overlayUpdating = false
    }

    private fun canDrawOverlays(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private data class AppEntry(
        val key: VolumeStore.VolumeKey,
        val label: String,
        val activeCount: Int = 0,
    )

    private data class ProfileOption(
        val userId: Int?,
        val label: String,
    )

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
        private const val REQUEST_NOTIFICATIONS = 100
    }
}
