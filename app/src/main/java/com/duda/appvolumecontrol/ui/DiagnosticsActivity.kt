package com.duda.appvolumecontrol.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.VolumeStore
import com.duda.appvolumecontrol.service.PlaybackControllerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var diagnosticsStore: DiagnosticsStore
    private lateinit var contentView: TextView
    private var lastReport: String = ""
    private lateinit var volumeStore: VolumeStore

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

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }

        val selfTestButton = Button(this).apply {
            text = "Run self-test"
            setOnClickListener {
                PlaybackControllerService.requestDiagnosticsDump(this@DiagnosticsActivity)
                refresh()
            }
        }

        val copyButton = Button(this).apply {
            text = "Copy report"
            setOnClickListener { copyReport() }
        }

        val shareButton = Button(this).apply {
            text = "Share report"
            setOnClickListener { shareReport() }
        }

        val exportButton = Button(this).apply {
            text = "Export presets"
            setOnClickListener { exportPresets() }
        }

        val importButton = Button(this).apply {
            text = "Import presets"
            setOnClickListener { importPresets() }
        }

        val importReplaceButton = Button(this).apply {
            text = "Import replace"
            setOnClickListener { importPresets(replace = true) }
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
        root.addView(refreshButton)
        root.addView(selfTestButton)
        root.addView(copyButton)
        root.addView(shareButton)
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
        val lastActiveTime = formatTime(snapshot.lastActiveTime, dateFormat)
        val lastDumpTime = formatTime(snapshot.lastDumpTime, dateFormat)
        val hiddenApiTime = formatTime(snapshot.hiddenApiTime, dateFormat)
        val lastImportTime = formatTime(snapshot.lastImportTime, dateFormat)
        val lastExportTime = formatTime(snapshot.lastExportTime, dateFormat)

        val hiddenApiPolicy = readGlobalInt("hidden_api_policy")
        val hiddenApiPolicyPreP = readGlobalInt("hidden_api_policy_pre_p_apps")
        val hiddenApiPolicyP = readGlobalInt("hidden_api_policy_p_apps")

        val hasWriteSecureSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val audioManager = getSystemService(AudioManager::class.java)
        val activeConfigs = audioManager.activePlaybackConfigurations
        val activePackages = activeConfigs
            .mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()
        val presetCount = volumeStore.countPresets()
        val appInfo = applicationInfo
        val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val isPrivApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_PRIVILEGED) != 0
        val (versionName, versionCode) = getAppVersion()

        val lines = listOf(
            "SDK: ${android.os.Build.VERSION.SDK_INT}",
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "App version: $versionName ($versionCode)",
            "System app: $isSystemApp",
            "Privileged app: $isPrivApp",
            "Last error: ${snapshot.lastError ?: "-"}",
            "Last error time: $lastErrorTime",
            "Last apply count: ${snapshot.lastApplyCount}",
            "Last apply time: $lastApplyTime",
            "Last active count: ${snapshot.lastActiveCount}",
            "Last active time: $lastActiveTime",
            "Last dump count: ${snapshot.lastDumpCount}",
            "Last dump time: $lastDumpTime",
            "Active configs now: ${activeConfigs.size}",
            "Active packages now: ${if (activePackages.isEmpty()) "-" else activePackages.joinToString(\", \")}",
            "Stored presets: $presetCount",
            "Last import count: ${snapshot.lastImportCount}",
            "Last import time: $lastImportTime",
            "Last export count: ${snapshot.lastExportCount}",
            "Last export time: $lastExportTime",
            "Hidden API VMRuntime ok: ${formatTriState(snapshot.hiddenApiVmRuntimeOk)}",
            "Hidden API policy ok: ${formatTriState(snapshot.hiddenApiPolicyOk)}",
            "Hidden API last attempt: $hiddenApiTime",
            "hidden_api_policy: ${hiddenApiPolicy ?: "-"}",
            "hidden_api_policy_pre_p_apps: ${hiddenApiPolicyPreP ?: "-"}",
            "hidden_api_policy_p_apps: ${hiddenApiPolicyP ?: "-"}",
            "WRITE_SECURE_SETTINGS granted: $hasWriteSecureSettings",
        )

        lastReport = lines.joinToString("\n")
        contentView.text = lastReport
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

    private fun copyReport() {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("diagnostics", lastReport))
    }

    private fun exportPresets() {
        val json = volumeStore.exportJson()
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("presets", json))
        diagnosticsStore.recordLastExport(volumeStore.countPresets())
        Toast.makeText(this, "Exported ${volumeStore.countPresets()} presets", Toast.LENGTH_SHORT).show()
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
        val count = try {
            if (replace) {
                volumeStore.clearAll()
            }
            volumeStore.importJson(text)
        } catch (_: Exception) {
            0
        }
        diagnosticsStore.recordLastImport(count)
        if (count == 0) {
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
        startActivity(Intent.createChooser(sendIntent, "Share presets"))
    }

    private fun shareReport() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastReport)
        }
        startActivity(Intent.createChooser(sendIntent, "Share diagnostics"))
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
}
