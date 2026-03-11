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
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.service.PlaybackControllerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var diagnosticsStore: DiagnosticsStore
    private lateinit var contentView: TextView
    private var lastReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnosticsStore = DiagnosticsStore(this)

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

        contentView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }

        root.addView(header)
        root.addView(refreshButton)
        root.addView(selfTestButton)
        root.addView(copyButton)
        root.addView(shareButton)
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

        val hiddenApiPolicy = readGlobalInt("hidden_api_policy")
        val hiddenApiPolicyPreP = readGlobalInt("hidden_api_policy_pre_p_apps")
        val hiddenApiPolicyP = readGlobalInt("hidden_api_policy_p_apps")

        val hasWriteSecureSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val audioManager = getSystemService(AudioManager::class.java)
        val activePackages = audioManager.activePlaybackConfigurations
            .mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()
        val appInfo = applicationInfo
        val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val isPrivApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_PRIVILEGED) != 0

        val lines = listOf(
            "SDK: ${android.os.Build.VERSION.SDK_INT}",
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
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
            "Active packages now: ${if (activePackages.isEmpty()) "-" else activePackages.joinToString(\", \")}",
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

    private fun shareReport() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, lastReport)
        }
        startActivity(Intent.createChooser(sendIntent, "Share diagnostics"))
    }
}
