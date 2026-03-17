package com.duda.appvolumecontrol.ui

import android.Manifest
import android.content.Intent
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.AccessibilityUtils
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.ForegroundAppResolver
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.SetupStatus
import com.duda.appvolumecontrol.service.VolumeKeyAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SetupCheckActivity : AppCompatActivity() {
    private lateinit var diagnosticsStore: DiagnosticsStore
    private lateinit var contentView: TextView
    private lateinit var statusView: TextView
    private lateinit var fixView: TextView
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityButton: Button
    private lateinit var usageAccessButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosticsStore = DiagnosticsStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Setup Check"
            textSize = 20f
        }

        val helper = TextView(this).apply {
            text = "This checks if the app is installed correctly and can control volume."
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }

        statusView = TextView(this).apply {
            text = "Ready: checking..."
            textSize = 20f
            setPadding(0, 12, 0, 4)
        }

        fixView = TextView(this).apply {
            textSize = 13f
            setPadding(0, 0, 0, 8)
        }

        val refreshButton = Button(this).apply {
            text = "Run check"
            setOnClickListener { refresh() }
        }

        overlayPermissionButton = Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener { openOverlaySettings() }
        }

        accessibilityButton = Button(this).apply {
            text = "Enable volume key listener"
            setOnClickListener { openAccessibilitySettings() }
        }

        usageAccessButton = Button(this).apply {
            text = "Grant usage access"
            setOnClickListener { openUsageAccessSettings() }
        }

        val copyButton = Button(this).apply {
            text = "Copy report"
            setOnClickListener { copyReport() }
        }

        val diagnosticsButton = Button(this).apply {
            text = "Open diagnostics"
            setOnClickListener { startActivity(Intent(this@SetupCheckActivity, DiagnosticsActivity::class.java)) }
        }

        contentView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }

        root.addView(header)
        root.addView(helper)
        root.addView(statusView)
        root.addView(fixView)
        root.addView(refreshButton)
        root.addView(overlayPermissionButton)
        root.addView(accessibilityButton)
        root.addView(usageAccessButton)
        root.addView(copyButton)
        root.addView(diagnosticsButton)
        root.addView(contentView)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        val appInfo = applicationInfo
        val privilegeInfo = SetupStatus.privilegeInfo(appInfo)
        val isSystemApp = privilegeInfo.isSystemApp
        val isPrivApp = privilegeInfo.isPrivilegedApp
        val isUpdatedSystemApp = privilegeInfo.isUpdatedSystemApp
        val hasModifyAudio = checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val hasWriteSecureSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
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
        overlayPermissionButton.isEnabled = !canDrawOverlays
        overlayPermissionButton.text = if (canDrawOverlays) {
            "Overlay permission granted"
        } else {
            "Grant overlay permission"
        }
        accessibilityButton.isEnabled = !accessibilityEnabled
        accessibilityButton.text = if (accessibilityEnabled) {
            "Volume key listener enabled"
        } else {
            "Enable volume key listener"
        }
        usageAccessButton.isEnabled = !usageAccessGranted
        usageAccessButton.text = if (usageAccessGranted) {
            "Usage access granted"
        } else {
            "Grant usage access"
        }

        val snapshot = diagnosticsStore.snapshot()
        val hiddenApiOk = snapshot.hiddenApiVmRuntimeOk == 1 || snapshot.hiddenApiPolicyOk == 1
        val hiddenApiTime = formatTime(snapshot.hiddenApiTime)

        val audioManager = getSystemService(AudioManager::class.java)
        val activeConfigs = PlaybackUtils.getActivePlaybackConfigurationsSafe(audioManager, diagnosticsStore)
        val activePackages = activeConfigs
            .mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()
            .sorted()

        val ready = isPrivApp && hasModifyAudio && hiddenApiOk
        val readyLine = if (ready) {
            "Ready: YES"
        } else {
            "Ready: NO (see checklist below)"
        }
        statusView.text = readyLine
        statusView.setTextColor(if (ready) 0xFF1B5E20.toInt() else 0xFFB71C1C.toInt())
        fixView.text = if (ready) {
            "Great! You can control per-app volume now."
        } else {
            buildFixText(
                isSystemApp,
                isPrivApp,
                hasModifyAudio,
                hiddenApiOk,
                canDrawOverlays,
                accessibilityEnabled,
                usageAccessGranted
            )
        }

        val lines = listOf(
            readyLine,
            "",
            "Checklist:",
            "System app: ${boolLine(isSystemApp)}",
            "Updated system app: ${boolLine(isUpdatedSystemApp)}",
            "Privileged app: ${boolLine(isPrivApp)}",
            "Privileged flag (flags): ${boolLine(privilegeInfo.flagPrivileged)}",
            "Privileged flag (private): ${boolLine(privilegeInfo.privateFlagPrivileged)}",
            "MODIFY_AUDIO_SETTINGS: ${boolLine(hasModifyAudio)}",
            "WRITE_SECURE_SETTINGS: ${boolLine(hasWriteSecureSettings)}",
            "Hidden API ok: ${boolLine(hiddenApiOk)}",
            "Hidden API last attempt: $hiddenApiTime",
            "Overlay permission: ${boolLine(canDrawOverlays)}",
            "Volume key listener: ${boolLine(accessibilityEnabled)}",
            "Usage access (focused app detection): ${boolLine(usageAccessGranted)}",
            "Active audio apps: ${if (activePackages.isEmpty()) "-" else activePackages.joinToString(", ")}",
            "APK source dir: ${privilegeInfo.sourceDir ?: "-"}",
            "APK public dir: ${privilegeInfo.publicSourceDir ?: "-"}",
            "APK data dir: ${privilegeInfo.dataDir ?: "-"}",
            "",
            "Tips:",
            "1) Install the module and reboot.",
            "2) Play audio in any app.",
            "3) Move that app's slider.",
            "4) If Ready is no, open Diagnostics and copy the report.",
            "5) If overlay is missing, grant overlay + enable volume key listener."
        )

        contentView.text = lines.joinToString("\n")
    }

    private fun boolLine(value: Boolean): String {
        return if (value) "yes" else "no"
    }

    private fun buildFixText(
        isSystemApp: Boolean,
        isPrivApp: Boolean,
        hasModifyAudio: Boolean,
        hiddenApiOk: Boolean,
        canDrawOverlays: Boolean,
        accessibilityEnabled: Boolean,
        usageAccessGranted: Boolean,
    ): String {
        val steps = mutableListOf<String>()
        if (!isSystemApp || !isPrivApp) {
            steps.add("Install the module and reboot (APK must live in /system/priv-app/).")
            steps.add("If you installed an APK update, uninstall updates so the priv-app copy is used.")
        }
        if (!hasModifyAudio) {
            steps.add("Check the privapp permissions file is applied.")
        }
        if (!hiddenApiOk) {
            steps.add("Open Diagnostics and try the self-test.")
        }
        if (!canDrawOverlays) {
            steps.add("Grant overlay permission to show the volume overlay.")
        }
        if (!accessibilityEnabled) {
            steps.add("Enable the volume key Accessibility service for reliable volume-button triggers.")
        }
        if (!usageAccessGranted) {
            steps.add("Grant Usage Access for reliable focused-app overlay suppression.")
        }
        if (steps.isEmpty()) {
            steps.add("Play audio in any app and run the check again.")
        }
        return "Next steps:\n" + steps.joinToString("\n") { "- $it" }
    }

    private fun openOverlaySettings() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        val component = ComponentName(this, VolumeKeyAccessibilityService::class.java)
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            putExtra("android.provider.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME", component)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun formatTime(value: Long): String {
        if (value <= 0L) return "-"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return dateFormat.format(Date(value))
    }

    private fun copyReport() {
        val manager = getSystemService(android.content.ClipboardManager::class.java)
        manager.setPrimaryClip(
            android.content.ClipData.newPlainText("setup_check", contentView.text)
        )
        android.widget.Toast.makeText(this, "Report copied", android.widget.Toast.LENGTH_SHORT).show()
    }
}
