package com.duda.appvolumecontrol.ui

import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.VolumeStore
import com.duda.appvolumecontrol.service.PlaybackControllerService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var volumeStore: VolumeStore
    private lateinit var activeContainer: LinearLayout
    private lateinit var presetContainer: LinearLayout
    private lateinit var presetSearch: EditText
    private lateinit var lastUpdatedView: TextView
    private var presetEntries: List<AppEntry> = emptyList()
    private var activePackages: Set<String> = emptySet()
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

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "App Volume Control"
            textSize = 20f
        }

        val refreshButton = Button(this).apply {
            text = "Refresh"
            setOnClickListener {
                updateActiveApps(audioManager.activePlaybackConfigurations)
                updateLastUpdated()
                updatePresetApps()
            }
        }

        val resetButton = Button(this).apply {
            text = "Reset presets"
            setOnClickListener {
                volumeStore.clearAll()
                updateActiveApps(audioManager.activePlaybackConfigurations)
                updatePresetApps()
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        val diagnosticsButton = Button(this).apply {
            text = "Diagnostics"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, DiagnosticsActivity::class.java))
            }
        }

        lastUpdatedView = TextView(this).apply {
            text = "Last updated: -"
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }

        val activeTitle = TextView(this).apply {
            text = "Active audio"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        activeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val presetTitle = TextView(this).apply {
            text = "Presets"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        presetSearch = EditText(this).apply {
            hint = "Filter apps"
            textSize = 14f
            setPadding(16, 8, 16, 8)
            addTextChangedListener { updatePresetApps(it?.toString().orEmpty()) }
        }

        presetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(header)
        root.addView(refreshButton)
        root.addView(resetButton)
        root.addView(diagnosticsButton)
        root.addView(lastUpdatedView)
        root.addView(activeTitle)
        root.addView(activeContainer)
        root.addView(presetTitle)
        root.addView(presetSearch)
        root.addView(presetContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)

        startForegroundService(Intent(this, PlaybackControllerService::class.java))
        updateActiveApps(audioManager.activePlaybackConfigurations)
        loadPresetApps()
        updatePresetApps()
    }

    override fun onResume() {
        super.onResume()
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshRunnable)
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onPause()
    }

    private fun updateActiveApps(configs: List<AudioPlaybackConfiguration>) {
        activeContainer.removeAllViews()
        val packages = configs.mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()
        activePackages = packages.toSet()

        if (packages.isEmpty()) {
            activeContainer.addView(buildEmptyRow("No active audio sessions."))
            updatePresetApps()
            return
        }

        for (packageName in packages) {
            val label = resolveLabel(packageName)
            activeContainer.addView(buildVolumeRow(packageName, label, true))
        }
        updatePresetApps()
    }

    private fun loadPresetApps() {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = packageManager.queryIntentActivities(mainIntent, 0)
        presetEntries = activities
            .map { it.activityInfo.packageName }
            .distinct()
            .map { packageName -> AppEntry(packageName, resolveLabel(packageName)) }
            .sortedBy { it.label.lowercase() }
    }

    private fun updatePresetApps(filter: String = presetSearch.text?.toString().orEmpty()) {
        presetContainer.removeAllViews()
        val trimmedFilter = filter.trim().lowercase()
        val packages = if (trimmedFilter.isBlank()) {
            presetEntries
        } else {
            presetEntries.filter {
                it.label.lowercase().contains(trimmedFilter) ||
                    it.packageName.lowercase().contains(trimmedFilter)
            }
        }

        if (packages.isEmpty()) {
            presetContainer.addView(buildEmptyRow("No matching apps."))
            return
        }

        for (entry in packages) {
            val isActive = activePackages.contains(entry.packageName)
            presetContainer.addView(buildVolumeRow(entry.packageName, entry.label, isActive))
        }
    }

    private fun buildVolumeRow(packageName: String, label: String, isActive: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val iconView = ImageView(this).apply {
            val size = dpToPx(32)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dpToPx(12)
            }
            setImageDrawable(resolveIcon(packageName))
        }

        val nameView = TextView(this).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val activeBadge = TextView(this).apply {
            text = "ACTIVE"
            textSize = 12f
            setPadding(dpToPx(6), dpToPx(2), dpToPx(6), dpToPx(2))
            visibility = if (isActive) View.VISIBLE else View.GONE
        }

        val stored = volumeStore.getVolume(packageName) ?: 1f
        val valueView = TextView(this).apply {
            text = "${(stored * 100).toInt()}%"
            textSize = 12f
            setPadding(dpToPx(8), 0, dpToPx(8), 0)
        }

        val slider = SeekBar(this).apply {
            max = 100
            progress = (stored * 100).toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val volume = progress / 100f
                    volumeStore.setVolume(packageName, volume)
                    PlaybackControllerService.requestReapply(this@MainActivity)
                    valueView.text = "$progress%"
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        val muteButton = Button(this).apply {
            text = "Mute"
            setOnClickListener {
                volumeStore.setVolume(packageName, 0f)
                valueView.text = "0%"
                slider.progress = 0
                PlaybackControllerService.requestReapply(this@MainActivity)
            }
        }

        row.addView(iconView)
        row.addView(nameView)
        row.addView(activeBadge)
        row.addView(muteButton)
        row.addView(valueView)
        row.addView(slider)
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

    private fun updateLastUpdated() {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        lastUpdatedView.text = "Last updated: $time"
    }

    private fun resolveIcon(packageName: String) = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationIcon(appInfo)
    } catch (_: Exception) {
        getDrawable(android.R.drawable.sym_def_app_icon)
    }

    private fun dpToPx(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
    )

    companion object {
        private const val REFRESH_INTERVAL_MS = 2000L
    }
}
