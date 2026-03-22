package fuck.individualappaudiovolumecontrol.droidvendorssuck.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.content.Intent
import android.app.ActivityManager
import android.app.NotificationManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.AccessibilityUtils
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.DiagnosticsStore
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.ForegroundAppResolver
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.HiddenApi
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.PlaybackUtils
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.OverlayPrefs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.VolumeStore
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.SetupStatus
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.AudioSettingsPrefs
import fuck.individualappaudiovolumecontrol.droidvendorssuck.service.PlaybackControllerService
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.ModuleInstaller
import fuck.individualappaudiovolumecontrol.droidvendorssuck.service.VolumeKeyAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var diagnosticsStore: DiagnosticsStore
    private lateinit var contentView: TextView
    private lateinit var searchView: EditText
    private var lastReport: String = ""
    private var lastCompatibilityRow: String = ""
    private var lastBundle: String = ""
    private var lastCrossProfileDiag: String = ""
    private var lastReportLines: List<String> = emptyList()
    private lateinit var volumeStore: VolumeStore
    private lateinit var bluetoothPermissionButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosticsStore = DiagnosticsStore(this)
        volumeStore = VolumeStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Diagnostics"
            textSize = 20f
        }

        val helper = TextView(this).apply {
            text = "If something does not work, tap \"Copy report\" and share it.\n" +
                "Look for \"Ready to control volume: yes\"."
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }

        searchView = EditText(this).apply {
            hint = "Search diagnostics"
            setSingleLine(true)
            setPadding(0, 8, 0, 8)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    applyReportFilter()
                }

                override fun afterTextChanged(s: Editable?) = Unit
            })
        }

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }

        val selfTestButton = Button(this).apply {
            text = "Self-test"
            setOnClickListener {
                PlaybackControllerService.requestDiagnosticsDump(this@DiagnosticsActivity)
                refresh()
            }
        }

        val crossProfileProbeButton = Button(this).apply {
            text = "Cross-profile probe"
            setOnClickListener {
                PlaybackControllerService.requestCrossProfileProbe(this@DiagnosticsActivity)
                Toast.makeText(this@DiagnosticsActivity, "Cross-profile probe requested", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }

        val hiddenApiButton = Button(this).apply {
            text = "Retry hidden API"
            setOnClickListener {
                HiddenApi.tryAllowHiddenApiAccess(this@DiagnosticsActivity)
                Toast.makeText(this@DiagnosticsActivity, "Hidden API attempt recorded", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }

        bluetoothPermissionButton = Button(this).apply {
            text = "Grant Bluetooth permission"
            setOnClickListener { requestBluetoothPermission() }
        }

        val copyButton = Button(this).apply {
            text = "Copy report"
            setOnClickListener { copyReport() }
        }

        val copyCompatibilityButton = Button(this).apply {
            text = "Copy compatibility row"
            setOnClickListener { copyCompatibilityRow() }
        }

        val copyCrossProfileButton = Button(this).apply {
            text = "Copy cross-profile diag"
            setOnClickListener { copyCrossProfileDiag() }
        }

        val copyBundleButton = Button(this).apply {
            text = "Copy bundle"
            setOnClickListener { copyBundle() }
        }

        val shareButton = Button(this).apply {
            text = "Share report"
            setOnClickListener { shareReport() }
        }

        val shareBundleButton = Button(this).apply {
            text = "Share bundle"
            setOnClickListener { shareBundle() }
        }

        val exportButton = Button(this).apply {
            text = "Copy presets"
            setOnClickListener { exportPresets() }
        }

        val importButton = Button(this).apply {
            text = "Paste presets"
            setOnClickListener { importPresets() }
        }

        val importReplaceButton = Button(this).apply {
            text = "Replace presets"
            setOnClickListener {
                AlertDialog.Builder(this@DiagnosticsActivity)
                    .setTitle("Replace presets")
                    .setMessage("This clears saved presets before importing.")
                    .setPositiveButton("Replace") { _, _ ->
                        importPresets(replace = true)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        val sharePresetsButton = Button(this).apply {
            text = "Share presets"
            setOnClickListener { sharePresets() }
        }

        contentView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }

        root.addView(header)
        root.addView(helper)
        root.addView(searchView)
        root.addView(refreshButton)
        root.addView(selfTestButton)
        root.addView(crossProfileProbeButton)
        root.addView(hiddenApiButton)
        root.addView(bluetoothPermissionButton)
        root.addView(copyButton)
        root.addView(copyCompatibilityButton)
        root.addView(copyCrossProfileButton)
        root.addView(copyBundleButton)
        root.addView(shareButton)
        root.addView(shareBundleButton)
        root.addView(exportButton)
        root.addView(importButton)
        root.addView(importReplaceButton)
        root.addView(sharePresetsButton)
        root.addView(contentView)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        val snapshot = diagnosticsStore.snapshot()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        val lastErrorTime = formatTime(snapshot.lastErrorTime, dateFormat)
        val lastApplyTime = formatTime(snapshot.lastApplyTime, dateFormat)
        val lastRouteTime = formatTime(snapshot.lastRouteTime, dateFormat)
        val lastActiveTime = formatTime(snapshot.lastActiveTime, dateFormat)
        val lastDumpTime = formatTime(snapshot.lastDumpTime, dateFormat)
        val hiddenApiTime = formatTime(snapshot.hiddenApiTime, dateFormat)
        val lastImportTime = formatTime(snapshot.lastImportTime, dateFormat)
        val lastExportTime = formatTime(snapshot.lastExportTime, dateFormat)
        val callStateTime = formatTime(snapshot.callStateTime, dateFormat)
        val overlayWatchStartTime = formatTime(snapshot.overlayWatchStartTime, dateFormat)
        val overlayWatchStopTime = formatTime(snapshot.overlayWatchStopTime, dateFormat)
        val overlayObserverTime = formatTime(snapshot.overlayObserverTime, dateFormat)
        val overlayBroadcastTime = formatTime(snapshot.overlayBroadcastTime, dateFormat)
        val overlayPollTime = formatTime(snapshot.overlayPollTime, dateFormat)
        val overlayManualTime = formatTime(snapshot.overlayManualTime, dateFormat)
        val overlayShowTime = formatTime(snapshot.overlayShowTime, dateFormat)
        val controllerServiceStartTime = formatTime(snapshot.controllerServiceStartTime, dateFormat)
        val controllerNotificationPostTime = formatTime(snapshot.controllerNotificationPostTime, dateFormat)
        val crossProfileProbeTime = formatTime(snapshot.crossProfileProbeTime, dateFormat)
        val crossProfileApplyTime = formatTime(snapshot.crossProfileApplyTime, dateFormat)

        val hiddenApiPolicy = readGlobalInt("hidden_api_policy")
        val hiddenApiPolicyPreP = readGlobalInt("hidden_api_policy_pre_p_apps")
        val hiddenApiPolicyP = readGlobalInt("hidden_api_policy_p_apps")

        val hasWriteSecureSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val postNotificationsStatus = if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                "granted"
            } else {
                "denied"
            }
        } else {
            "n/a"
        }
        val hasModifyAudio = checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val hasBluetoothConnect = if (android.os.Build.VERSION.SDK_INT >= 31) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        updateBluetoothButton(hasBluetoothConnect)
        val hasModifyRouting = checkSelfPermission(PERMISSION_MODIFY_AUDIO_ROUTING) ==
            PackageManager.PERMISSION_GRANTED
        val hasQueryAllPackages = checkSelfPermission(Manifest.permission.QUERY_ALL_PACKAGES) ==
            PackageManager.PERMISSION_GRANTED
        val hasAcrossUsers = checkSelfPermission(PERMISSION_INTERACT_ACROSS_USERS) ==
            PackageManager.PERMISSION_GRANTED
        val notificationManager = getSystemService(NotificationManager::class.java)
        val notificationsEnabled = if (android.os.Build.VERSION.SDK_INT >= 24) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
        val controllerChannelImportance = if (android.os.Build.VERSION.SDK_INT >= 26) {
            notificationManager.getNotificationChannel(PLAYBACK_CHANNEL_ID)?.importance
        } else {
            null
        }
        val controllerChannelName = if (android.os.Build.VERSION.SDK_INT >= 26) {
            notificationManager.getNotificationChannel(PLAYBACK_CHANNEL_ID)?.name?.toString()
        } else {
            null
        }
        val controllerServiceRunning = isServiceRunning(PlaybackControllerService::class.java)
        val canDrawOverlays = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
        val accessibilityEnabled = AccessibilityUtils.isServiceEnabled(
            this,
            VolumeKeyAccessibilityService::class.java
        )
        val usageAccessGranted = ForegroundAppResolver(this).hasUsageAccess()
        val overlayPrefEnabled = OverlayPrefs(this).isOverlayEnabled()
        val requestedPermissions = getRequestedPermissions()
        val permissionStatuses = buildPermissionStatuses(requestedPermissions)
        val missingCritical = permissionStatuses
            .filter { it.required && it.requested && !it.granted }
            .map { it.name }
        val activeConfigs = PlaybackUtils.getActivePlaybackConfigurationsSafe(this, diagnosticsStore)
        val activePackages = activeConfigs
            .mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()
            .sorted()
        val activeUserIds = activeConfigs
            .mapNotNull { PlaybackUtils.getUserIdFromUid(PlaybackUtils.getClientUid(it)) }
            .distinct()
            .sorted()
        val activeUserSummary = if (activeUserIds.isEmpty()) "-" else activeUserIds.joinToString(", ") { "u$it" }
        val acrossUsersStatus = if (hasAcrossUsers) "granted" else "denied"
        val presetCount = volumeStore.countPresets()
        val ruleGroups = volumeStore.listRuleGroups()
        val rules = volumeStore.listRules()
        val enabledRules = rules.filter { it.enabled }
        val ruleSummary = buildRuleSummary(enabledRules)
        val muteDefault = volumeStore.getDefaultMuteBehavior()
        val muteOverrideCount = volumeStore.countMuteBehaviorOverrides()
        val audioSettings = AudioSettingsPrefs(this)
        val resumeOverrideCount = volumeStore.countAutoResumeOverrides()
        val attrOverrideCount = volumeStore.countAudioAttributesOverrides()
        val locationStore = fuck.individualappaudiovolumecontrol.droidvendorssuck.core.LocationRuleStore(this)
        val locationId = locationStore.getCurrentLocationId() ?: "-"
        val locationCount = locationStore.listLocations().size
        val lastFixTime = formatTime(locationStore.getLastFixTime(), dateFormat)
        val lastLocationFixTime = formatTime(snapshot.locationTime, dateFormat)
        val locationProvider = snapshot.locationProvider ?: "-"
        val locationAccuracy = if (snapshot.locationAccuracy >= 0f) {
            "${snapshot.locationAccuracy}m"
        } else {
            "-"
        }
        val locationMock = when (snapshot.locationMock) {
            1 -> "yes"
            0 -> "no"
            else -> "-"
        }
        val backgroundLocationEnabled = if (locationStore.isBackgroundUpdatesEnabled()) "enabled" else "disabled"
        val appInfo = applicationInfo
        val privilegeInfo = SetupStatus.privilegeInfo(appInfo)
        val isPrivApp = privilegeInfo.isPrivilegedApp
        val isDebuggable = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val (versionName, versionCode) = getAppVersion()
        val targetSdk = appInfo.targetSdkVersion
        val readyForControl = isPrivApp && hasModifyAudio &&
            (snapshot.hiddenApiVmRuntimeOk == 1 || snapshot.hiddenApiPolicyOk == 1)
        val readyStatus = if (readyForControl) "yes" else "no"
        val bundledModuleStatus = readBundledModuleStatus()

        val lines = listOf(
            "SDK: ${android.os.Build.VERSION.SDK_INT}",
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "App package: $packageName",
            "App version: $versionName ($versionCode)",
            "Target SDK: $targetSdk",
            "Bundled module: $bundledModuleStatus",
            "Ready to control volume: $readyStatus",
            "System app: ${privilegeInfo.isSystemApp}",
            "Updated system app: ${privilegeInfo.isUpdatedSystemApp}",
            "Privileged app: $isPrivApp",
            "Privileged flag (flags): ${privilegeInfo.flagPrivileged}",
            "Privileged flag (private): ${privilegeInfo.privateFlagPrivileged}",
            "Debuggable: $isDebuggable",
            "APK source dir: ${privilegeInfo.sourceDir ?: "-"}",
            "APK public dir: ${privilegeInfo.publicSourceDir ?: "-"}",
            "APK data dir: ${privilegeInfo.dataDir ?: "-"}",
            "Last error: ${snapshot.lastError ?: "-"}",
            "Last error time: $lastErrorTime",
            "Last apply count: ${snapshot.lastApplyCount}",
            "Last apply time: $lastApplyTime",
            "Last routing time: $lastRouteTime",
            "Last routing attempted/applied/missing/failed: " +
                "${snapshot.lastRouteAttempted}/${snapshot.lastRouteApplied}/" +
                "${snapshot.lastRouteMissing}/${snapshot.lastRouteFailed}",
            "Last routing details: ${snapshot.lastRouteDetails ?: "-"}",
            "Last active count: ${snapshot.lastActiveCount}",
            "Last active time: $lastActiveTime",
            "Last dump count: ${snapshot.lastDumpCount}",
            "Last dump time: $lastDumpTime",
            "Overlay watch starts: ${snapshot.overlayWatchCount}",
            "Overlay watch last start: $overlayWatchStartTime",
            "Overlay watch last stop: $overlayWatchStopTime",
            "Overlay last show: $overlayShowTime",
            "Overlay last show reason: ${snapshot.overlayShowReason ?: "-"}",
            "Overlay last show active apps: ${snapshot.overlayShowActiveCount}",
            "Overlay signals (observer): ${snapshot.overlayObserverCount} @ $overlayObserverTime",
            "Overlay signals (broadcast): ${snapshot.overlayBroadcastCount} @ $overlayBroadcastTime",
            "Overlay signals (poll): ${snapshot.overlayPollCount} @ $overlayPollTime",
            "Overlay signals (manual): ${snapshot.overlayManualCount} @ $overlayManualTime",
            "Playback controller service running: $controllerServiceRunning",
            "Playback controller starts: ${snapshot.controllerServiceStarts}",
            "Playback controller last start: $controllerServiceStartTime",
            "Playback notifications enabled (manager): $notificationsEnabled",
            "Playback notification channel: ${controllerChannelName ?: "-"}",
            "Playback notification channel importance: ${formatImportance(controllerChannelImportance ?: Int.MIN_VALUE)}",
            "Playback notification posts: ${snapshot.controllerNotificationPosts}",
            "Playback notification last post: $controllerNotificationPostTime",
            "Playback notification last active apps: ${snapshot.controllerNotificationActiveCount}",
            "Playback notifications enabled at last post: ${formatTriState(snapshot.controllerNotificationsEnabled)}",
            "Playback channel importance at last post: ${formatImportance(snapshot.controllerNotificationChannelImportance)}",
            "Active configs now: ${activeConfigs.size}",
            "Active packages count: ${activePackages.size}",
            "Active packages now: ${if (activePackages.isEmpty()) "-" else activePackages.joinToString(", ")}",
            "Active userIds now: $activeUserSummary",
            "Cross-profile playback access: $acrossUsersStatus",
            "Cross-profile diagnostics:",
            "Cross-profile probe summary: ${snapshot.crossProfileProbeSummary ?: "-"}",
            "Cross-profile probe time: $crossProfileProbeTime",
            "Cross-profile apply summary: ${snapshot.crossProfileApplySummary ?: "-"}",
            "Cross-profile apply time: $crossProfileApplyTime",
            "Stored presets: $presetCount",
            "Rule groups: ${ruleGroups.size}",
            "Rules enabled/total: ${enabledRules.size}/${rules.size}",
            "Rule names: $ruleSummary",
            "Default mute behavior: ${muteDefault.id}",
            "Per-app mute overrides: $muteOverrideCount",
            "Auto-resume after pause rules: ${audioSettings.isAutoResumeEnabled()}",
            "Per-app auto-resume overrides: $resumeOverrideCount",
            "Per-app audio attribute overrides: $attrOverrideCount",
            "Current location id: $locationId",
            "Locations stored: $locationCount",
            "Last location fix: $lastFixTime",
            "Last location fix (listener): $lastLocationFixTime",
            "Location provider (listener): $locationProvider",
            "Location accuracy (listener): $locationAccuracy",
            "Location mock (listener): $locationMock",
            "Background location updates: $backgroundLocationEnabled",
            "Last import count: ${snapshot.lastImportCount}",
            "Last import time: $lastImportTime",
            "Last export count: ${snapshot.lastExportCount}",
            "Last export time: $lastExportTime",
            "Call state: ${snapshot.callState ?: "-"}",
            "Call state time: $callStateTime",
            "Hidden API VMRuntime ok: ${formatTriState(snapshot.hiddenApiVmRuntimeOk)}",
            "Hidden API policy ok: ${formatTriState(snapshot.hiddenApiPolicyOk)}",
            "Hidden API VMRuntime error: ${snapshot.hiddenApiVmRuntimeError ?: "-"}",
            "Hidden API policy error: ${snapshot.hiddenApiPolicyError ?: "-"}",
            "Hidden API last attempt: $hiddenApiTime",
            "hidden_api_policy: ${hiddenApiPolicy ?: "-"}",
            "hidden_api_policy_pre_p_apps: ${hiddenApiPolicyPreP ?: "-"}",
            "hidden_api_policy_p_apps: ${hiddenApiPolicyP ?: "-"}",
            "WRITE_SECURE_SETTINGS granted: $hasWriteSecureSettings",
            "POST_NOTIFICATIONS: $postNotificationsStatus",
            "MODIFY_AUDIO_SETTINGS granted: $hasModifyAudio",
            "MODIFY_AUDIO_ROUTING granted: $hasModifyRouting",
            "QUERY_ALL_PACKAGES granted: $hasQueryAllPackages",
            "INTERACT_ACROSS_USERS granted: $hasAcrossUsers",
            "Overlay permission: $canDrawOverlays",
            "Overlay enabled (pref): $overlayPrefEnabled",
            "Volume key listener: $accessibilityEnabled",
            "Usage access (focused app detection): $usageAccessGranted",
            "Missing critical permissions: ${if (missingCritical.isEmpty()) "-" else missingCritical.joinToString(", ")}",
            "Permission status:",
            *permissionStatuses.map { it.toReportLine() }.toTypedArray(),
        )

        lastReportLines = lines
        lastReport = lines.joinToString("\n")
        applyReportFilter()
        lastCompatibilityRow = buildCompatibilityRow(
            isPrivApp,
            snapshot.hiddenApiVmRuntimeOk,
            snapshot.hiddenApiPolicyOk
        )
        lastCrossProfileDiag = buildCrossProfileDiag(snapshot, dateFormat)
        lastBundle = buildBundle()
    }

    private fun readGlobalInt(key: String): Int? {
        return try {
            Settings.Global.getInt(contentResolver, key)
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTime(value: Long, dateFormat: SimpleDateFormat): String {
        if (value <= 0L) return "-"
        return dateFormat.format(Date(value))
    }

    private fun formatTriState(value: Int): String {
        return when (value) {
            1 -> "yes"
            0 -> "no"
            else -> "-"
        }
    }

    private fun formatImportance(value: Int): String {
        return when (value) {
            Int.MIN_VALUE -> "-"
            NotificationManager.IMPORTANCE_NONE -> "none"
            NotificationManager.IMPORTANCE_MIN -> "min"
            NotificationManager.IMPORTANCE_LOW -> "low"
            NotificationManager.IMPORTANCE_DEFAULT -> "default"
            NotificationManager.IMPORTANCE_HIGH -> "high"
            NotificationManager.IMPORTANCE_MAX -> "max"
            else -> value.toString()
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(ActivityManager::class.java) ?: return false
        @Suppress("DEPRECATION")
        return manager.getRunningServices(Int.MAX_VALUE).any { it.service.className == serviceClass.name }
    }

    private fun applyReportFilter() {
        val query = searchView.text?.toString()?.trim().orEmpty()
        if (query.isBlank()) {
            contentView.text = lastReport
            return
        }
        val filtered = lastReportLines.filter { it.contains(query, ignoreCase = true) }
        contentView.text = if (filtered.isEmpty()) {
            "No diagnostics rows match \"$query\"."
        } else {
            filtered.joinToString("\n")
        }
    }

    private fun buildRuleSummary(rules: List<fuck.individualappaudiovolumecontrol.droidvendorssuck.core.Rule>): String {
        if (rules.isEmpty()) return "-"
        val names = rules.map { it.name }
        if (names.size <= 4) return names.joinToString(", ")
        val shown = names.take(4).joinToString(", ")
        val remaining = names.size - 4
        return "$shown, +$remaining more"
    }

    private fun copyReport() {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("diagnostics", lastReport))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun updateBluetoothButton(hasBluetoothConnect: Boolean) {
        if (android.os.Build.VERSION.SDK_INT < 31) {
            bluetoothPermissionButton.isEnabled = false
            bluetoothPermissionButton.text = "Bluetooth permission n/a"
            return
        }
        bluetoothPermissionButton.isEnabled = !hasBluetoothConnect
        bluetoothPermissionButton.text = if (hasBluetoothConnect) {
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
            updateBluetoothButton(true)
            return
        }
        requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_BLUETOOTH_PERMISSION)
    }

    private fun readBundledModuleStatus(): String {
        return runCatching {
            assets.open(ModuleInstaller.MODULE_NAME).use { stream ->
                val bytes = stream.available()
                if (bytes > 0) "present (${bytes} bytes)" else "present"
            }
        }.getOrElse { err ->
            "missing (${err.javaClass.simpleName})"
        }
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
            refresh()
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

    private fun copyCompatibilityRow() {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("compatibility", lastCompatibilityRow))
        Toast.makeText(this, "Compatibility row copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyCrossProfileDiag() {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("cross_profile", lastCrossProfileDiag))
        Toast.makeText(this, "Cross-profile diagnostics copied", Toast.LENGTH_SHORT).show()
    }

    private fun copyBundle() {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("bundle", lastBundle))
        Toast.makeText(this, "Bundle copied", Toast.LENGTH_SHORT).show()
    }

    private fun exportPresets() {
        val json = volumeStore.exportJson()
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("presets", json))
        diagnosticsStore.recordLastExport(volumeStore.countPresets())
        Toast.makeText(this, "Exported ${volumeStore.countPresets()} presets", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun importPresets(replace: Boolean = false) {
        val manager = getSystemService(ClipboardManager::class.java)
        val clip = manager.primaryClip ?: run {
            Toast.makeText(this, "Clipboard empty", Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: run {
            Toast.makeText(this, "Clipboard has no text", Toast.LENGTH_SHORT).show()
            return
        }
        var importFailed = false
        val count = try {
            if (replace) {
                volumeStore.clearAll()
            }
            volumeStore.importJson(text)
        } catch (_: Exception) {
            importFailed = true
            0
        }
        diagnosticsStore.recordLastImport(count)
        if (importFailed) {
            Toast.makeText(this, "Import failed", Toast.LENGTH_SHORT).show()
        } else if (count == 0) {
            Toast.makeText(this, "No presets imported", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Imported $count presets", Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    private fun sharePresets() {
        val json = volumeStore.exportJson()
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        diagnosticsStore.recordLastExport(volumeStore.countPresets())
        refresh()
        startActivity(Intent.createChooser(sendIntent, "Share presets"))
    }

    private fun shareReport() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastReport)
        }
        startActivity(Intent.createChooser(sendIntent, "Share diagnostics"))
    }

    private fun shareBundle() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastBundle)
        }
        startActivity(Intent.createChooser(sendIntent, "Share bundle"))
    }

    private fun buildCompatibilityRow(
        isPrivApp: Boolean,
        vmRuntimeOk: Int,
        policyOk: Int,
    ): String {
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"
        val androidVersion = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val priv = if (isPrivApp) "yes" else "no"
        val vm = formatTriState(vmRuntimeOk)
        val policy = formatTriState(policyOk)
        val perApp = "unknown"
        val notes = "fill in"
        return "| $device | $androidVersion | $priv | $vm | $policy | $perApp | $notes |"
    }

    private fun buildCrossProfileDiag(
        snapshot: DiagnosticsStore.DiagnosticsSnapshot,
        dateFormat: SimpleDateFormat,
    ): String {
        val probeTime = formatTime(snapshot.crossProfileProbeTime, dateFormat)
        val applyTime = formatTime(snapshot.crossProfileApplyTime, dateFormat)
        return listOf(
            "Cross-profile probe summary: ${snapshot.crossProfileProbeSummary ?: "-"}",
            "Cross-profile probe time: $probeTime",
            "Cross-profile apply summary: ${snapshot.crossProfileApplySummary ?: "-"}",
            "Cross-profile apply time: $applyTime",
        ).joinToString("\n")
    }

    private fun buildBundle(): String {
        val presets = volumeStore.exportJson()
        return listOf(
            "=== Diagnostics ===",
            lastReport,
            "",
            "=== Presets ===",
            presets,
            "",
            "=== Compatibility Row ===",
            lastCompatibilityRow,
            "",
            "=== Cross-Profile Diagnostics ===",
            lastCrossProfileDiag,
        ).joinToString("\n")
    }

    private fun getAppVersion(): Pair<String, Long> {
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val versionName = info.versionName ?: "-"
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            versionName to versionCode
        } catch (_: Exception) {
            "-" to 0L
        }
    }

    private fun getRequestedPermissions(): Set<String> {
        return try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(
                        PackageManager.GET_PERMISSIONS.toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            info.requestedPermissions?.toSet() ?: emptySet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun buildPermissionStatuses(requested: Set<String>): List<PermissionStatus> {
        val all = listOf(
            PermissionStatus(Manifest.permission.MODIFY_AUDIO_SETTINGS, required = true),
            PermissionStatus(PERMISSION_MODIFY_AUDIO_ROUTING, required = false),
            PermissionStatus(Manifest.permission.QUERY_ALL_PACKAGES, required = true),
            PermissionStatus(PERMISSION_INTERACT_ACROSS_USERS, required = false),
            PermissionStatus(Manifest.permission.WRITE_SECURE_SETTINGS, required = false),
            PermissionStatus(Manifest.permission.FOREGROUND_SERVICE, required = true),
            PermissionStatus(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK, required = true),
            PermissionStatus(Manifest.permission.POST_NOTIFICATIONS, required = false),
            PermissionStatus(Manifest.permission.BLUETOOTH, required = false),
            PermissionStatus(Manifest.permission.BLUETOOTH_CONNECT, required = false),
            PermissionStatus(Manifest.permission.READ_PHONE_STATE, required = false),
            PermissionStatus(Manifest.permission.ACCESS_FINE_LOCATION, required = false),
            PermissionStatus(Manifest.permission.ACCESS_COARSE_LOCATION, required = false),
            PermissionStatus(Manifest.permission.ACCESS_BACKGROUND_LOCATION, required = false),
        )
        return all.map { status ->
            val requestedFlag = requested.contains(status.name)
            val grantedFlag = if (status.name == Manifest.permission.POST_NOTIFICATIONS &&
                android.os.Build.VERSION.SDK_INT < 33
            ) {
                true
            } else if (status.name == Manifest.permission.ACCESS_BACKGROUND_LOCATION &&
                android.os.Build.VERSION.SDK_INT < 29
            ) {
                true
            } else {
                checkSelfPermission(status.name) == PackageManager.PERMISSION_GRANTED
            }
            status.copy(requested = requestedFlag, granted = grantedFlag)
        }
    }

    private data class PermissionStatus(
        val name: String,
        val required: Boolean,
        val requested: Boolean = false,
        val granted: Boolean = false,
    ) {
        fun toReportLine(): String {
            val req = if (requested) "requested" else "not requested"
            val grant = if (granted) "granted" else "denied"
            val critical = if (required) "required" else "optional"
            return " - $name: $grant ($req, $critical)"
        }
    }

    companion object {
        private const val PERMISSION_MODIFY_AUDIO_ROUTING = "android.permission.MODIFY_AUDIO_ROUTING"
        private const val PERMISSION_INTERACT_ACROSS_USERS = "android.permission.INTERACT_ACROSS_USERS"
        private const val PLAYBACK_CHANNEL_ID = "app_volume_control"
        private const val REQUEST_BLUETOOTH_PERMISSION = 1001
    }
}
