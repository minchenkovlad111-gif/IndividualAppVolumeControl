package com.duda.appvolumecontrol.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.DiagnosticsStore
import com.duda.appvolumecontrol.core.VolumeStore
import java.util.Date

class LegacyDiagnosticsActivity : AppCompatActivity() {
    private lateinit var volumeStore: VolumeStore
    private lateinit var diagnosticsStore: DiagnosticsStore
    private lateinit var reportView: TextView
    private var lastReport: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeStore = VolumeStore(this)
        diagnosticsStore = DiagnosticsStore(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Diagnostics (legacy)"
            textSize = 20f
        }

        val copyButton = Button(this).apply {
            text = "Copy report"
            setOnClickListener { copyReport() }
        }
        val exportButton = Button(this).apply {
            text = "Export presets"
            setOnClickListener { exportPresets() }
        }
        val importButton = Button(this).apply {
            text = "Import presets (merge)"
            setOnClickListener { importPresets(replace = false) }
        }
        val replaceButton = Button(this).apply {
            text = "Import presets (replace)"
            setOnClickListener { importPresets(replace = true) }
        }
        val shareButton = Button(this).apply {
            text = "Share presets"
            setOnClickListener { sharePresets() }
        }

        reportView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 16, 0, 0)
        }

        content.addView(title)
        content.addView(copyButton)
        content.addView(exportButton)
        content.addView(importButton)
        content.addView(replaceButton)
        content.addView(shareButton)
        content.addView(reportView)

        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        val snapshot = diagnosticsStore.snapshot()
        val dateFormat = DateFormat.getMediumDateFormat(this)
        val timeFormat = DateFormat.getTimeFormat(this)
        val lines = listOf(
            "SDK: ${Build.VERSION.SDK_INT}",
            "App package: $packageName",
            "App version: ${getAppVersion()}",
            "Legacy mode: per-app volume disabled",
            "Stored presets: ${volumeStore.countPresets()}",
            "Last import count: ${snapshot.lastImportCount}",
            "Last import time: ${formatTime(snapshot.lastImportTime, dateFormat, timeFormat)}",
            "Last export count: ${snapshot.lastExportCount}",
            "Last export time: ${formatTime(snapshot.lastExportTime, dateFormat, timeFormat)}",
        )
        lastReport = lines.joinToString("\n")
        reportView.text = lastReport
    }

    private fun formatTime(value: Long, dateFormat: java.text.DateFormat, timeFormat: java.text.DateFormat): String {
        if (value <= 0L) return "-"
        val date = Date(value)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    private fun getAppVersion(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            val code = if (Build.VERSION.SDK_INT >= 28) {
                info.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toString()
            }
            "${info.versionName} ($code)"
        } catch (_: Exception) {
            "unknown"
        }
    }

    private fun copyReport() {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("diagnostics", lastReport))
        Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
    }

    private fun exportPresets() {
        val json = volumeStore.exportJson()
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("presets", json))
        diagnosticsStore.recordLastExport(volumeStore.countPresets())
        Toast.makeText(this, "Exported ${volumeStore.countPresets()} presets", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun importPresets(replace: Boolean) {
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
}
