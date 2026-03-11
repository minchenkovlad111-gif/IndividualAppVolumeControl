package com.duda.appvolumecontrol.ui

import android.content.Intent
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.PlaybackUtils
import com.duda.appvolumecontrol.core.VolumeStore
import com.duda.appvolumecontrol.service.PlaybackControllerService

class MainActivity : AppCompatActivity() {
    private lateinit var audioManager: AudioManager
    private lateinit var volumeStore: VolumeStore
    private lateinit var activeContainer: LinearLayout
    private lateinit var presetContainer: LinearLayout

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>?) {
            if (configs == null) return
            updateActiveApps(configs)
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
                updatePresetApps()
            }
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

        presetContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(header)
        root.addView(refreshButton)
        root.addView(activeTitle)
        root.addView(activeContainer)
        root.addView(presetTitle)
        root.addView(presetContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)

        startForegroundService(Intent(this, PlaybackControllerService::class.java))
        updateActiveApps(audioManager.activePlaybackConfigurations)
        updatePresetApps()
    }

    override fun onResume() {
        super.onResume()
        audioManager.registerAudioPlaybackCallback(playbackCallback, null)
    }

    override fun onPause() {
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        super.onPause()
    }

    private fun updateActiveApps(configs: List<AudioPlaybackConfiguration>) {
        activeContainer.removeAllViews()
        val packages = configs.mapNotNull { PlaybackUtils.getClientPackageName(it, packageManager) }
            .distinct()

        if (packages.isEmpty()) {
            activeContainer.addView(buildEmptyRow("No active audio sessions."))
            return
        }

        for (packageName in packages) {
            val label = resolveLabel(packageName)
            activeContainer.addView(buildVolumeRow(packageName, label))
        }
    }

    private fun updatePresetApps() {
        presetContainer.removeAllViews()
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val activities = packageManager.queryIntentActivities(mainIntent, 0)
        val packages = activities.map { it.activityInfo.packageName }.distinct().sorted()

        for (packageName in packages) {
            val label = resolveLabel(packageName)
            presetContainer.addView(buildVolumeRow(packageName, label))
        }
    }

    private fun buildVolumeRow(packageName: String, label: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val nameView = TextView(this).apply {
            text = label
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val slider = SeekBar(this).apply {
            max = 100
            val stored = volumeStore.getVolume(packageName) ?: 1f
            progress = (stored * 100).toInt()
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val volume = progress / 100f
                    volumeStore.setVolume(packageName, volume)
                    PlaybackControllerService.requestReapply(this@MainActivity)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

        row.addView(nameView)
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
}
