package com.duda.appvolumecontrol.ui

import android.Manifest
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.PlaybackUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SetupCheckActivity : AppCompatActivity() {
    private lateinit var diagnosticsStore: DiagnosticsStore
    private lateinit var contentView: TextView

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

        val refreshButton = Button(this).apply {
            text = "Run check"
            setOnClickListener { refresh() }
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
        root.addView(refreshButton)
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
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isPrivApp = isPrivilegedApp(appInfo)
        val hasModifyAudio = checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val hasWriteSecureSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

        val snapshot = diagnosticsStore.snapshot()
        val hiddenApiOk = snapshot.hiddenApiVmRuntimeOk == 1 || snapshot.hiddenApiPolicyOk == 1
        val hiddenApiTime = formatTime(snapshot.hiddenApiTime)

        val audioManager = getSystemService(AudioManager::class.java)
        val activeConfigs = audioManager.activePlaybackConfigurations
        val activePackages = activeConfigs
            .mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()
            .sorted()

        val ready = isPrivApp && hasModifyAudio && hiddenApiOk
        val readyLine = if (ready) {
            "Ready: yes"
        } else {
            "Ready: no (see checklist below)"
        }

        val lines = listOf(
            readyLine,
            "",
            "Checklist:",
            "System app: ${boolLine(isSystemApp)}",
            "Privileged app: ${boolLine(isPrivApp)}",
            "MODIFY_AUDIO_SETTINGS: ${boolLine(hasModifyAudio)}",
            "WRITE_SECURE_SETTINGS: ${boolLine(hasWriteSecureSettings)}",
            "Hidden API ok: ${boolLine(hiddenApiOk)}",
            "Hidden API last attempt: $hiddenApiTime",
            "Active audio apps: ${if (activePackages.isEmpty()) "-" else activePackages.joinToString(", ")}",
            "",
            "Tips:",
            "1) Install the module and reboot.",
            "2) Play audio in any app.",
            "3) Move that app's slider.",
            "4) If Ready is no, open Diagnostics and copy the report."
        )

        contentView.text = lines.joinToString("\n")
    }

    private fun boolLine(value: Boolean): String {
        return if (value) "yes" else "no"
    }

    private fun formatTime(value: Long): String {
        if (value <= 0L) return "-"
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return dateFormat.format(Date(value))
    }

    private fun isPrivilegedApp(appInfo: ApplicationInfo): Boolean {
        return try {
            val field = ApplicationInfo::class.java.getDeclaredField("FLAG_PRIVILEGED").apply {
                isAccessible = true
            }
            val flag = field.getInt(null)
            (appInfo.flags and flag) != 0
        } catch (_: Exception) {
            false
        }
    }
}
