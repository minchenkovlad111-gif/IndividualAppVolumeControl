package com.duda.appvolumecontrol.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.DiagnosticsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticsActivity : AppCompatActivity() {
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
            text = "Diagnostics"
            textSize = 20f
        }

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refresh() }
        }

        contentView = TextView(this).apply {
            textSize = 14f
            setPadding(0, 16, 0, 16)
        }

        root.addView(header)
        root.addView(refreshButton)
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

        val hiddenApiPolicy = readGlobalInt("hidden_api_policy")
        val hiddenApiPolicyPreP = readGlobalInt("hidden_api_policy_pre_p_apps")
        val hiddenApiPolicyP = readGlobalInt("hidden_api_policy_p_apps")

        val hasWriteSecureSettings = checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

        val lines = listOf(
            "Last error: ${snapshot.lastError ?: "-"}",
            "Last error time: $lastErrorTime",
            "Last apply count: ${snapshot.lastApplyCount}",
            "Last apply time: $lastApplyTime",
            "Last active count: ${snapshot.lastActiveCount}",
            "Last active time: $lastActiveTime",
            "hidden_api_policy: ${hiddenApiPolicy ?: "-"}",
            "hidden_api_policy_pre_p_apps: ${hiddenApiPolicyPreP ?: "-"}",
            "hidden_api_policy_p_apps: ${hiddenApiPolicyP ?: "-"}",
            "WRITE_SECURE_SETTINGS granted: $hasWriteSecureSettings",
        )

        contentView.text = lines.joinToString("\n")
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
}
