package fuck.individualappaudiovolumecontrol.droidvendorssuck.ui

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.FrameLayout
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
import android.view.HapticFeedbackConstants
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.ActiveAppStore
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.AccessibilityUtils
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.DiagnosticsStore
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.ModuleInstaller
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.MarkerStyle
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.NotificationPrefs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.PlaybackUtils
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.OverlayPrefs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.ProfileUtils
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.SetupStatus
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.VolumeStore
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.AudioSettingsPrefs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.service.PlaybackControllerService
import fuck.individualappaudiovolumecontrol.droidvendorssuck.service.VolumeOverlayService
import fuck.individualappaudiovolumecontrol.droidvendorssuck.ui.SeekBarTicksView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.Toast
import kotlin.math.abs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.OutputLabelUtils

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
    private lateinit var outputOnlyToggle: CheckBox
    private lateinit var profileSpinner: Spinner
    private lateinit var profileAdapter: ArrayAdapter<String>
    private lateinit var overlayPrefs: OverlayPrefs
    private lateinit var overlayStatusView: TextView
    private lateinit var accessibilityStatusView: TextView
    private lateinit var accessibilityWarningView: TextView
    private lateinit var overlayToggle: CheckBox
    private lateinit var overlayShowButton: Button
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var notificationPresentationSpinner: Spinner
    private lateinit var notificationPresentationAdapter: ArrayAdapter<String>
    private lateinit var notificationPresentationHint: TextView
    private lateinit var overlayOnlyWhenPlayingToggle: CheckBox
    private lateinit var overlayRootFallbackToggle: CheckBox
    private lateinit var overlayRootFallbackHint: TextView
    private lateinit var overlayRootFallbackRefreshButton: Button
    private lateinit var overlayFocusBlockButton: Button
    private lateinit var overlayIgnoreButton: Button
    private lateinit var overlaySnoozeButton: Button
    private lateinit var overlaySnoozeStatusView: TextView
    private lateinit var overlayAutoHideToggle: CheckBox
    private lateinit var overlayDismissOutsideToggle: CheckBox
    private lateinit var defaultMuteToggle: CheckBox
    private lateinit var autoResumeToggle: CheckBox
    private lateinit var bluetoothPermissionButton: Button
    private lateinit var rulesButton: Button
    private var overlayUpdating: Boolean = false
    private lateinit var controlStatusView: TextView
    private var controlsEnabled: Boolean = true
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
    private val diagnosticsStore by lazy { DiagnosticsStore(this) }
    private val notificationPrefs by lazy { NotificationPrefs(this) }
    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateActiveApps()
            updateLastUpdated()
            handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            updateActiveApps()
            updateLastUpdated()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)
        volumeStore = VolumeStore(this)
        activeStore = ActiveAppStore(this)
        overlayPrefs = OverlayPrefs(this)
        overlayPrefs.ensureDefaultIgnoreList(packageManager)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "IndividualAppAudioVolumeControl"
            textSize = 20f
        }

        val quickStart = TextView(this).apply {
            text = "Quick start:\n1) Install the module (Magisk/KernelSU) and reboot.\n2) Play audio in an app.\n3) Move the slider for that app."
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }

        val moduleHelpButton = Button(this).apply {
            text = "Install module"
            setOnClickListener { showModuleHelp() }
        }

        controlStatusView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, 8)
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
                    updateActiveApps()
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
                updateActiveApps()
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
                        updateActiveApps()
                        PlaybackControllerService.requestReapply(this@MainActivity)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        val diagnosticsButton = Button(this).apply {
            text = "Diagnostics / report"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
            }
        }

        bluetoothPermissionButton = Button(this).apply {
            text = "Grant Bluetooth permission"
            setOnClickListener { requestBluetoothPermission() }
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

        val notificationTitle = TextView(this).apply {
            text = "Notification controls"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        notificationPresentationAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf(
                "Multi-app page (up to 3)",
                "Single-app pager",
                "One notification per app"
            )
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        notificationPresentationSpinner = Spinner(this).apply {
            adapter = notificationPresentationAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    val mode = when (position) {
                        1 -> NotificationPrefs.MODE_SINGLE_ROW_PAGED
                        2 -> NotificationPrefs.MODE_PER_APP
                        else -> NotificationPrefs.MODE_MULTI_ROW_PAGED
                    }
                    if (notificationPrefs.getPresentationMode() == mode) return
                    notificationPrefs.setPresentationMode(mode)
                    notificationPrefs.setPage(0)
                    PlaybackControllerService.requestNotificationRefresh(this@MainActivity)
                    updateNotificationPresentationUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        notificationPresentationHint = TextView(this).apply {
            textSize = 12f
            setTextColor(Color.parseColor("#616161"))
            setPadding(dpToPx(8), 0, 0, dpToPx(6))
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

        accessibilityStatusView = TextView(this).apply {
            text = "Volume key listener: checking..."
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }

        accessibilityWarningView = TextView(this).apply {
            text = "Overlay enabled, but volume key listener is off. Enable Accessibility service for reliable volume-button triggers."
            textSize = 12f
            setTextColor(0xFFB71C1C.toInt())
            setPadding(0, 0, 0, 8)
            visibility = View.GONE
        }

        accessibilityButton = Button(this).apply {
            text = "Enable volume key listener"
            setOnClickListener { openAccessibilityServiceSettings() }
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

        val overlayAutoToggle = CheckBox(this).apply {
            text = "Auto refresh overlay list"
            isChecked = overlayPrefs.isAutoRefreshEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setAutoRefreshEnabled(isChecked)
            }
        }

        overlayAutoHideToggle = CheckBox(this).apply {
            text = "Auto hide overlay"
            isChecked = overlayPrefs.isAutoHideEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setAutoHideEnabled(isChecked)
            }
        }

        overlayDismissOutsideToggle = CheckBox(this).apply {
            text = "Dismiss overlay when tapping outside"
            isChecked = overlayPrefs.isDismissOnOutsideTapEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setDismissOnOutsideTapEnabled(isChecked)
            }
        }

        overlayOnlyWhenPlayingToggle = CheckBox(this).apply {
            text = "Only show overlay when audio is playing"
            isChecked = overlayPrefs.isVolumeKeyOnlyWhenPlayingEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setVolumeKeyOnlyWhenPlayingEnabled(isChecked)
            }
        }

        overlayRootFallbackToggle = CheckBox(this).apply {
            text = "Root fallback for cross-profile detection (read-only)"
            isChecked = overlayPrefs.isRootAudioFallbackEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                overlayPrefs.setRootAudioFallbackEnabled(isChecked)
                updateRootFallbackUi()
                updateActiveApps()
            }
        }

        overlayRootFallbackHint = TextView(this).apply {
            text = "Uses dumpsys via root to list active audio when cross-profile access is denied."
            textSize = 12f
            setTextColor(Color.parseColor("#616161"))
            setPadding(dpToPx(8), 0, 0, dpToPx(6))
        }

        overlayRootFallbackRefreshButton = Button(this).apply {
            text = "Scan cross-profile audio now"
            setOnClickListener {
                updateActiveApps(forceRootRefresh = true)
                val rootCount = activeEntries.count { it.isRootDetected }
                Toast.makeText(
                    this@MainActivity,
                    if (rootCount > 0) {
                        "Found $rootCount root-detected app(s)"
                    } else {
                        "No cross-profile audio found"
                    },
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        overlayFocusBlockButton = Button(this).apply {
            text = "Suppress overlay for focused apps"
            setOnClickListener { showOverlayFocusBlockListDialog() }
        }

        overlayIgnoreButton = Button(this).apply {
            text = "Ignore apps for overlay"
            setOnClickListener { showOverlayIgnoreListDialog() }
        }

        overlaySnoozeStatusView = TextView(this).apply {
            text = "Snooze: off"
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }

        overlaySnoozeButton = Button(this).apply {
            text = "Snooze overlay"
            setOnClickListener { showOverlaySnoozeDialog() }
        }

        val audioSettingsTitle = TextView(this).apply {
            text = "Audio settings"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }

        val audioSettingsHint = TextView(this).apply {
            text = "Applies when a preset volume hits 0%."
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }

        defaultMuteToggle = CheckBox(this).apply {
            text = "Mute pauses playback by default"
            isChecked = volumeStore.getDefaultMuteBehavior() == VolumeStore.MuteBehavior.MUTE_AND_PAUSE
            setOnCheckedChangeListener { _, isChecked ->
                val behavior = if (isChecked) {
                    VolumeStore.MuteBehavior.MUTE_AND_PAUSE
                } else {
                    VolumeStore.MuteBehavior.MUTE_ONLY
                }
                volumeStore.setDefaultMuteBehavior(behavior)
            }
        }

        val audioSettingsPrefs = AudioSettingsPrefs(this)
        autoResumeToggle = CheckBox(this).apply {
            text = "Auto-resume after pause rules"
            isChecked = audioSettingsPrefs.isAutoResumeEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                audioSettingsPrefs.setAutoResumeEnabled(isChecked)
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

        val activeHeaderRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val activeTitleParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        activeTitle.layoutParams = activeTitleParams

        val activeAllLabel = TextView(this).apply {
            text = "All:"
            textSize = 12f
            setPadding(0, 24, dpToPx(6), 8)
        }

        val activeMuteButton = Button(this).apply {
            text = "0%"
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener {
                for (entry in activeEntries) {
                    volumeStore.setVolume(entry.key, 0f)
                }
                updateActiveApps()
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val activeHalfButton = Button(this).apply {
            text = "50%"
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener {
                for (entry in activeEntries) {
                    volumeStore.setVolume(entry.key, 0.5f)
                }
                updateActiveApps()
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val activeLoudButton = Button(this).apply {
            text = "100%"
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
            setOnClickListener {
                for (entry in activeEntries) {
                    volumeStore.setVolume(entry.key, 1f)
                }
                updateActiveApps()
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        activeHeaderRow.addView(activeTitle)
        activeHeaderRow.addView(activeAllLabel)
        activeHeaderRow.addView(activeMuteButton)
        activeHeaderRow.addView(activeHalfButton)
        activeHeaderRow.addView(activeLoudButton)

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

        rulesButton = Button(this).apply {
            text = "Rules"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, RulesActivity::class.java))
            }
        }

        outputOnlyToggle = CheckBox(this).apply {
            text = "Only show output-routed apps"
            isChecked = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_OUTPUT_ONLY, false)
            setOnCheckedChangeListener { _, isChecked ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(KEY_OUTPUT_ONLY, isChecked).apply()
                updatePresetApps()
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
        root.addView(moduleHelpButton)
        root.addView(controlStatusView)
        root.addView(profileRow)
        root.addView(refreshButton)
        root.addView(resetButton)
        root.addView(diagnosticsButton)
        root.addView(bluetoothPermissionButton)
        root.addView(helpButton)
        root.addView(checkButton)
        root.addView(notificationTitle)
        root.addView(notificationPresentationSpinner)
        root.addView(notificationPresentationHint)
        root.addView(overlayTitle)
        root.addView(overlayStatusView)
        root.addView(overlayPermissionButton)
        root.addView(accessibilityStatusView)
        root.addView(accessibilityWarningView)
        root.addView(accessibilityButton)
        root.addView(overlayShowButton)
        root.addView(overlayToggle)
        root.addView(overlayAutoToggle)
        root.addView(overlayAutoHideToggle)
        root.addView(overlayDismissOutsideToggle)
        root.addView(overlayOnlyWhenPlayingToggle)
        root.addView(overlayRootFallbackToggle)
        root.addView(overlayRootFallbackHint)
        root.addView(overlayRootFallbackRefreshButton)
        root.addView(overlayFocusBlockButton)
        root.addView(overlayIgnoreButton)
        root.addView(overlaySnoozeStatusView)
        root.addView(overlaySnoozeButton)
        root.addView(audioSettingsTitle)
        root.addView(audioSettingsHint)
        root.addView(defaultMuteToggle)
        root.addView(autoResumeToggle)
        root.addView(lastUpdatedView)
        root.addView(activeHeaderRow)
        root.addView(activeContainer)
        root.addView(presetTitle)
        root.addView(manageAppsButton)
        root.addView(rulesButton)
        root.addView(outputOnlyToggle)
        root.addView(presetSearch)
        root.addView(clearFilterButton)
        root.addView(presetContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)

        startForegroundService(Intent(this, PlaybackControllerService::class.java))
        updateControlStatus()
        updateOverlayStatus()
        updateBluetoothPermissionButton()
        updateActiveApps()
        loadPresetApps()
        updatePresetApps()
    }

    override fun onResume() {
        super.onResume()
        maybeRequestNotifications()
        updateControlStatus()
        updateOverlayStatus()
        ensureOverlayWatcher()
        updateBluetoothPermissionButton()
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

    private fun updateActiveApps(forceRootRefresh: Boolean = false) {
        activeContainer.removeAllViews()
        val now = System.currentTimeMillis()
        val activeInfo = PlaybackUtils.buildActivePackageInfo(
            context = this,
            packageManager = packageManager,
            diagnosticsStore = diagnosticsStore,
            overlayPrefs = overlayPrefs,
            filterOverlayIgnore = false,
            includeRootFallback = overlayPrefs.isRootAudioFallbackEnabled(),
            forceRootRefresh = forceRootRefresh
        )
        val keys = activeInfo.map { it.key }
        activeEntries = activeInfo.map { info ->
            AppEntry(
                key = info.key,
                label = resolveLabel(info.key.packageName),
                activeCount = info.sessionCount,
                isRootDetected = info.isRootDetected
            )
        }.sortedBy { it.label.lowercase() }
        activeKeys = activeEntries.map { it.key.storageKey() }.toSet()
        activePackagesByUser = keys.groupBy { it.packageName }
            .mapValues { entry -> entry.value.map { it.userId }.toSet() }

        updateActiveSince(keys.toSet(), now)
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

    private fun updateBluetoothPermissionButton() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            bluetoothPermissionButton.isEnabled = false
            bluetoothPermissionButton.text = "Bluetooth permission n/a"
            return
        }
        val granted = checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        bluetoothPermissionButton.isEnabled = !granted
        bluetoothPermissionButton.text = if (granted) {
            "Bluetooth permission granted"
        } else {
            "Grant Bluetooth permission"
        }
    }

    private fun requestBluetoothPermission() {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            Toast.makeText(this, "Bluetooth permission not required on this Android version", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth permission already granted", Toast.LENGTH_SHORT).show()
            updateBluetoothPermissionButton()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Toast.makeText(
                this,
                if (granted) "Bluetooth permission granted" else "Bluetooth permission denied",
                Toast.LENGTH_SHORT
            ).show()
            if (!granted &&
                android.os.Build.VERSION.SDK_INT >= 31 &&
                !shouldShowRequestPermissionRationale(Manifest.permission.BLUETOOTH_CONNECT)
            ) {
                showBluetoothSettingsDialog()
            }
            updateBluetoothPermissionButton()
        }
    }

    private fun showBluetoothSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Bluetooth permission blocked")
            .setMessage("Enable Bluetooth permission in system settings to read codec info.")
            .setPositiveButton("Open settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
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

        val outputBadge = TextView(this).apply {
            val output = volumeStore.getOutputDevice(entry.key)
            text = output?.let { "${OutputLabelUtils.shortType(it.type)} ${it.name ?: "Output"}" } ?: "Output"
            textSize = 11f
            setPadding(dpToPx(6), 0, dpToPx(6), 0)
            setTextColor(0xFF455A64.toInt())
            visibility = if (output == null) View.GONE else View.VISIBLE
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
                        updateMuteIcon(snapped / 100f)
                        seekBar?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        return
                    }
                    applyVolume(entry, progress, valueView)
                    updateMuteIcon(progress / 100f)
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
        ticksView.setPadding(slider.paddingLeft, 0, slider.paddingRight, 0)

        slider.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL
        )

        sliderFrame.addView(ticksView)
        sliderFrame.addView(slider)
        sliderRow.addView(sliderFrame)
        sliderRow.addView(valueView)

        headerRow.addView(indicator)
        headerRow.addView(iconView)
        headerRow.addView(nameContainer)
        headerRow.addView(countView)
        headerRow.addView(outputBadge)
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
        val userId = selectedProfileUserId
        val outputOnly = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .getBoolean(KEY_OUTPUT_ONLY, false)
        return entries.filter { entry ->
            val matchesProfile = if (userId == null) true else entry.key.userId == userId || entry.key.userId == null
            val matchesOutput = if (!outputOnly) true else volumeStore.getOutputDevice(entry.key) != null
            matchesProfile && matchesOutput
        }
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


    private fun copyToClipboard(text: String, label: String = "Package") {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("clipboard", text))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun showModuleHelp() {
        val installer = ModuleInstaller(this)
        val detection = installer.detectRootManagerDetails()
        val manager = detection.manager
        val moduleName = ModuleInstaller.MODULE_NAME
        val managerLine = when (manager) {
            ModuleInstaller.RootManager.MAGISK -> "Detected root manager: Magisk"
            ModuleInstaller.RootManager.KERNELSU -> "Detected root manager: KernelSU"
            ModuleInstaller.RootManager.NONE -> "Detected root manager: none"
        }
        val probeLine = "Probes: Magisk pkg=${boolLabel(detection.hasMagiskPackage)} " +
            "bin=${boolLabel(detection.hasMagiskBinary)} | KernelSU pkg=${boolLabel(detection.hasKernelSuPackage)} " +
            "bin=${boolLabel(detection.hasKernelSuBinary)}"
        val message = "System/privileged install is required for per-app volume control.\n\n" +
            "$managerLine\n" +
            "$probeLine\n\n" +
            "Steps:\n" +
            "1) Install the module ($moduleName).\n" +
            "2) Reboot your device.\n" +
            "3) Open Setup Check to verify Ready: YES.\n\n" +
            "Directly copying the app into /system is not supported from the app."
        val actions = listOf(
            "Install via Magisk",
            "Install via KernelSU",
            "Copy module name",
            "Copy package"
        )
        AlertDialog.Builder(this)
            .setTitle("Install module")
            .setMessage(message)
            .setItems(actions.toTypedArray()) { _, which ->
                when (which) {
                    0 -> confirmModuleInstall(installer, ModuleInstaller.RootManager.MAGISK)
                    1 -> confirmModuleInstall(installer, ModuleInstaller.RootManager.KERNELSU)
                    2 -> copyToClipboard(moduleName, "Module name")
                    3 -> copyToClipboard(packageName, "Package")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun boolLabel(value: Boolean): String {
        return if (value) "yes" else "no"
    }

    private fun confirmModuleInstall(installer: ModuleInstaller, manager: ModuleInstaller.RootManager) {
        AlertDialog.Builder(this)
            .setTitle("Install module")
            .setMessage("Install the bundled module using ${manager.label}? This requires root.")
            .setPositiveButton("Install") { _, _ ->
                Thread {
                    val result = installer.installBundledModule(manager)
                    runOnUiThread {
                        val status = if (result.success) {
                            "Install command finished. Reboot your device."
                        } else {
                            "Install failed: ${result.output}"
                        }
                        Toast.makeText(this, status, Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun applyVolume(entry: AppEntry, progress: Int, valueView: TextView) {
        val displayProgress = progress.coerceIn(0, 100)
        val volume = displayProgress / 100f
        volumeStore.setVolume(entry.key, volume)
        PlaybackControllerService.requestReapply(this@MainActivity)
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

    private fun snapToMarker(progress: Int, delta: Int, timeDelta: Long): Int {
        if (delta > SNAP_DELTA_MAX || timeDelta < SNAP_TIME_MIN_MS) return progress
        val markers = arrayOf(0, 10, 20, 25, 30, 40, 50, 60, 70, 75, 80, 90, 100)
        val nearest = markers.minByOrNull { abs(it - progress) } ?: progress
        return if (abs(nearest - progress) <= SNAP_THRESHOLD) nearest else progress
    }

    private fun updateOverlayStatus() {
        val canDraw = canDrawOverlays()
        val accessibilityEnabled = isAccessibilityServiceEnabled()
        overlayStatusView.text = if (canDraw) {
            "Overlay permission: granted"
        } else {
            "Overlay permission: not granted"
        }
        accessibilityStatusView.text = if (accessibilityEnabled) {
            "Volume key listener: enabled"
        } else {
            "Volume key listener: disabled (enable Accessibility service)"
        }
        accessibilityWarningView.visibility = if (
            overlayPrefs.isOverlayEnabled() && !accessibilityEnabled
        ) {
            View.VISIBLE
        } else {
            View.GONE
        }
        overlayPermissionButton.isEnabled = !canDraw
        accessibilityButton.isEnabled = !accessibilityEnabled
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
        overlayOnlyWhenPlayingToggle.isChecked = overlayPrefs.isVolumeKeyOnlyWhenPlayingEnabled()
        overlayRootFallbackToggle.isChecked = overlayPrefs.isRootAudioFallbackEnabled()
        overlayDismissOutsideToggle.isChecked = overlayPrefs.isDismissOnOutsideTapEnabled()
        updateNotificationPresentationUi()
        updateRootFallbackUi()
        updateOverlayFocusBlockButton()
        updateOverlayIgnoreButton()
        updateOverlaySnoozeStatus()
    }

    private fun updateNotificationPresentationUi() {
        val mode = notificationPrefs.getPresentationMode()
        val desiredIndex = when (mode) {
            NotificationPrefs.MODE_SINGLE_ROW_PAGED -> 1
            NotificationPrefs.MODE_PER_APP -> 2
            else -> 0
        }
        if (notificationPresentationSpinner.selectedItemPosition != desiredIndex) {
            notificationPresentationSpinner.setSelection(desiredIndex)
        }
        notificationPresentationHint.text = when (mode) {
            NotificationPrefs.MODE_SINGLE_ROW_PAGED ->
                "Shows one app per page with Next/Prev as the compatibility fallback."
            NotificationPrefs.MODE_PER_APP ->
                "Posts one notification per active app while keeping one foreground controller notification alive."
            else ->
                "Shows up to three apps per notification page, with Next/Prev when more are active."
        }
    }

    private fun updateRootFallbackUi() {
        val hasAcrossUsers = PlaybackUtils.hasAcrossUsersPermission(this)
        val fallbackEnabled = overlayPrefs.isRootAudioFallbackEnabled()
        overlayRootFallbackToggle.isEnabled = !hasAcrossUsers
        overlayRootFallbackRefreshButton.isEnabled = !hasAcrossUsers && fallbackEnabled
        overlayRootFallbackHint.text = when {
            hasAcrossUsers ->
                "Cross-profile playback access is granted, so root fallback is not needed."
            fallbackEnabled ->
                "Uses root dumpsys only because cross-profile permission is unavailable."
            else ->
                "Enable this to use root dumpsys only when cross-profile permission is unavailable."
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityUtils.isServiceEnabled(
            this,
            fuck.individualappaudiovolumecontrol.droidvendorssuck.service.VolumeKeyAccessibilityService::class.java
        )
    }

    private fun openAccessibilityServiceSettings() {
        val component = ComponentName(
            this,
            fuck.individualappaudiovolumecontrol.droidvendorssuck.service.VolumeKeyAccessibilityService::class.java
        )
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra(
                "android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME",
                component
            )
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(
                this,
                "Opening Accessibility settings",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Opening Accessibility settings",
                Toast.LENGTH_SHORT
            ).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun showOverlayFocusBlockListDialog() {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Focused app filter")
            .setMessage("Loading apps...")
            .setCancelable(false)
            .show()

        Thread {
            val apps = packageManager.getInstalledApplications(0)
                .map { OverlayAppOption(it.packageName, resolveLabel(it.packageName)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                loadingDialog.dismiss()
                if (apps.isEmpty()) {
                    Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val initial = overlayPrefs.getOverlayFocusBlockList()
                val selected = initial.toMutableSet()

                val density = resources.displayMetrics.density
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (16 * density).toInt()
                    setPadding(padding, padding, padding, padding)
                }

                val searchInput = EditText(this).apply {
                    hint = "Search apps"
                }

                val listView = ListView(this).apply {
                    choiceMode = ListView.CHOICE_MODE_MULTIPLE
                }

                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_multiple_choice,
                    apps.toMutableList()
                )
                listView.adapter = adapter

                fun applyCheckedState() {
                    for (i in 0 until adapter.count) {
                        val app = adapter.getItem(i) ?: continue
                        listView.setItemChecked(i, selected.contains(app.packageName))
                    }
                }

                fun updateList(query: String) {
                    val trimmed = query.trim().lowercase()
                    val filtered = if (trimmed.isBlank()) {
                        apps
                    } else {
                        apps.filter {
                            it.label.lowercase().contains(trimmed) ||
                                it.packageName.lowercase().contains(trimmed)
                        }
                    }
                    adapter.clear()
                    adapter.addAll(sortOverlayDialogApps(filtered, selected))
                    adapter.notifyDataSetChanged()
                    applyCheckedState()
                }

                listView.setOnItemClickListener { _, _, position, _ ->
                    val app = adapter.getItem(position) ?: return@setOnItemClickListener
                    if (listView.isItemChecked(position)) {
                        selected.add(app.packageName)
                    } else {
                        selected.remove(app.packageName)
                    }
                    updateList(searchInput.text?.toString().orEmpty())
                }

                searchInput.addTextChangedListener { editable ->
                    updateList(editable?.toString().orEmpty())
                }

                val listHeight = (320 * density).toInt()
                content.addView(
                    searchInput,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                content.addView(
                    listView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        listHeight
                    )
                )

                updateList("")

                AlertDialog.Builder(this)
                    .setTitle("Suppress overlay for focused apps")
                    .setView(content)
                    .setPositiveButton("Save") { _, _ ->
                        overlayPrefs.setOverlayFocusBlockList(selected)
                        updateOverlayFocusBlockButton()
                    }
                    .setNeutralButton("Clear") { _, _ ->
                        overlayPrefs.setOverlayFocusBlockList(emptySet())
                        updateOverlayFocusBlockButton()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun updateOverlayFocusBlockButton() {
        val count = overlayPrefs.getOverlayFocusBlockList().size
        overlayFocusBlockButton.text = if (count == 0) {
            "Suppress overlay for focused apps"
        } else {
            "Suppress overlay for focused apps ($count blocked)"
        }
    }

    private fun showOverlayIgnoreListDialog() {
        val loadingDialog = AlertDialog.Builder(this)
            .setTitle("Ignore apps for overlay")
            .setMessage("Loading apps...")
            .setCancelable(false)
            .show()

        Thread {
            val apps = packageManager.getInstalledApplications(0)
                .map { OverlayAppOption(it.packageName, resolveLabel(it.packageName)) }
                .sortedBy { it.label.lowercase() }
            runOnUiThread {
                loadingDialog.dismiss()
                if (apps.isEmpty()) {
                    Toast.makeText(this, "No apps found", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }
                val initial = overlayPrefs.getOverlayIgnorePackageList()
                val selected = initial.toMutableSet()

                val density = resources.displayMetrics.density
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    val padding = (16 * density).toInt()
                    setPadding(padding, padding, padding, padding)
                }

                val helper = TextView(this).apply {
                    text = "Ignored apps are treated as silent and will not trigger the overlay."
                    textSize = 12f
                    setPadding(0, 0, 0, (8 * density).toInt())
                }

                val searchInput = EditText(this).apply {
                    hint = "Search apps"
                }

                val listView = ListView(this).apply {
                    choiceMode = ListView.CHOICE_MODE_MULTIPLE
                }

                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_multiple_choice,
                    apps.toMutableList()
                )
                listView.adapter = adapter

                fun applyCheckedState() {
                    for (i in 0 until adapter.count) {
                        val app = adapter.getItem(i) ?: continue
                        listView.setItemChecked(i, selected.contains(app.packageName))
                    }
                }

                fun updateList(query: String) {
                    val trimmed = query.trim().lowercase()
                    val filtered = if (trimmed.isBlank()) {
                        apps
                    } else {
                        apps.filter {
                            it.label.lowercase().contains(trimmed) ||
                                it.packageName.lowercase().contains(trimmed)
                        }
                    }
                    adapter.clear()
                    adapter.addAll(sortOverlayDialogApps(filtered, selected))
                    adapter.notifyDataSetChanged()
                    applyCheckedState()
                }

                listView.setOnItemClickListener { _, _, position, _ ->
                    val app = adapter.getItem(position) ?: return@setOnItemClickListener
                    if (listView.isItemChecked(position)) {
                        selected.add(app.packageName)
                    } else {
                        selected.remove(app.packageName)
                    }
                    updateList(searchInput.text?.toString().orEmpty())
                }

                searchInput.addTextChangedListener { editable ->
                    updateList(editable?.toString().orEmpty())
                }

                val listHeight = (320 * density).toInt()
                content.addView(
                    helper,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                content.addView(
                    searchInput,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                content.addView(
                    listView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        listHeight
                    )
                )

                updateList("")

                AlertDialog.Builder(this)
                    .setTitle("Ignore apps for overlay")
                    .setView(content)
                    .setPositiveButton("Save") { _, _ ->
                        overlayPrefs.setOverlayIgnorePackageList(selected)
                        updateOverlayIgnoreButton()
                    }
                    .setNeutralButton("Clear") { _, _ ->
                        overlayPrefs.setOverlayIgnorePackageList(emptySet())
                        updateOverlayIgnoreButton()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun updateOverlayIgnoreButton() {
        val count = overlayPrefs.getOverlayIgnorePackageList().size
        overlayIgnoreButton.text = if (count == 0) {
            "Ignore apps for overlay"
        } else {
            "Ignore apps for overlay ($count)"
        }
    }

    private fun showOverlaySnoozeDialog() {
        val options = listOf(
            SnoozeOption("5 minutes", 5),
            SnoozeOption("15 minutes", 15),
            SnoozeOption("30 minutes", 30),
            SnoozeOption("1 hour", 60),
            SnoozeOption("2 hours", 120)
        )
        val labels = options.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Snooze overlay")
            .setItems(labels) { _, which ->
                val minutes = options[which].minutes
                val until = SystemClock.elapsedRealtime() + minutes * 60_000L
                overlayPrefs.setOverlaySnoozeUntil(until)
                updateOverlaySnoozeStatus()
            }
            .setNeutralButton("Clear") { _, _ ->
                overlayPrefs.clearOverlaySnooze()
                updateOverlaySnoozeStatus()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateOverlaySnoozeStatus() {
        val until = overlayPrefs.getOverlaySnoozeUntil()
        val now = SystemClock.elapsedRealtime()
        overlaySnoozeStatusView.text = if (until > now) {
            val remaining = ((until - now) + 59_999L) / 60_000L
            "Snooze: $remaining min remaining"
        } else {
            "Snooze: off"
        }
    }

    private fun ensureOverlayWatcher() {
        if (!overlayPrefs.isOverlayEnabled()) return
        if (!canDrawOverlays()) return
        val intent = Intent(this, VolumeOverlayService::class.java).apply {
            action = VolumeOverlayService.ACTION_START_WATCH
        }
        startService(intent)
    }

    private fun updateControlStatus() {
        val ready = SetupStatus.isControlReady(this)
        controlsEnabled = ready
        controlStatusView.text = if (ready) {
            "Controls: ready"
        } else {
            "Controls: disabled (open Setup Check)"
        }
        controlStatusView.setTextColor(if (ready) 0xFF1B5E20.toInt() else 0xFFB71C1C.toInt())
    }

    private fun canDrawOverlays(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun sortOverlayDialogApps(
        apps: List<OverlayAppOption>,
        selected: Set<String>,
    ): List<OverlayAppOption> {
        return apps.sortedWith(
            compareBy<OverlayAppOption> { !selected.contains(it.packageName) }
                .thenBy { it.label.lowercase() }
                .thenBy { it.packageName.lowercase() }
        )
    }

    private data class AppEntry(
        val key: VolumeStore.VolumeKey,
        val label: String,
        val activeCount: Int = 0,
        val isRootDetected: Boolean = false,
    )

    private data class ProfileOption(
        val userId: Int?,
        val label: String,
    )

    private data class OverlayAppOption(
        val packageName: String,
        val label: String,
    ) {
        override fun toString(): String {
            return "$label ($packageName)"
        }
    }

    private data class SnoozeOption(
        val label: String,
        val minutes: Int,
    )

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
        private const val REQUEST_NOTIFICATIONS = 100
        private const val REQUEST_BLUETOOTH_PERMISSION = 101
        private const val SNAP_THRESHOLD = 2
        private const val SNAP_DELTA_MAX = 2
        private const val SNAP_TIME_MIN_MS = 50L
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_OUTPUT_ONLY = "show_output_only"
    }
}
