package com.duda.appvolumecontrol.ui

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.VolumeStore
import kotlin.math.roundToInt

class LegacyPresetsActivity : AppCompatActivity() {
    private lateinit var volumeStore: VolumeStore
    private lateinit var listContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeStore = VolumeStore(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Presets (legacy)"
            textSize = 20f
        }
        val helper = TextView(this).apply {
            text = "Edit stored volumes. These won't apply on API < 26, " +
                "but they can be exported and used on newer devices."
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }
        val addButton = Button(this).apply {
            text = "Add preset"
            setOnClickListener { showAddDialog() }
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }

        content.addView(title)
        content.addView(helper)
        content.addView(addButton)
        content.addView(listContainer)

        val scroll = ScrollView(this).apply {
            addView(content)
        }
        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        listContainer.removeAllViews()
        val entries = volumeStore.listPresetEntries()
        if (entries.isEmpty()) {
            listContainer.addView(TextView(this).apply { text = "No presets stored." })
            return
        }
        for (entry in entries) {
            val percent = volumeStore.getVolume(entry)?.let { (it * 100f).roundToInt() } ?: 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val label = TextView(this).apply {
                text = "${entry.storageKey()} • $percent%"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val editButton = Button(this).apply {
                text = "Edit"
                setOnClickListener { showEditDialog(entry.storageKey(), percent) }
            }
            val deleteButton = Button(this).apply {
                text = "Delete"
                setOnClickListener {
                    volumeStore.removePreset(entry.packageName, entry.userId)
                    Toast.makeText(this@LegacyPresetsActivity, "Preset removed", Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
            row.addView(label)
            row.addView(editButton)
            row.addView(deleteButton)
            listContainer.addView(row)
        }
    }

    private fun showAddDialog() {
        val keyInput = EditText(this).apply {
            hint = "package or u10:package"
        }
        val volumeInput = EditText(this).apply {
            hint = "Volume percent (0-100)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(keyInput)
            addView(volumeInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Add preset")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val key = keyInput.text?.toString()?.trim().orEmpty()
                val volume = volumeInput.text?.toString()?.trim()?.toIntOrNull()
                if (!savePreset(key, volume)) return@setPositiveButton
                Toast.makeText(this, "Preset saved", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditDialog(storageKey: String, currentPercent: Int) {
        val volumeInput = EditText(this).apply {
            hint = "Volume percent (0-100)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentPercent.toString())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(volumeInput)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit preset")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val volume = volumeInput.text?.toString()?.trim()?.toIntOrNull()
                if (!savePreset(storageKey, volume)) return@setPositiveButton
                Toast.makeText(this, "Preset updated", Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun savePreset(storageKey: String, percent: Int?): Boolean {
        val key = VolumeStore.parseKey(storageKey)
        if (key == null) {
            Toast.makeText(this, "Invalid package key", Toast.LENGTH_SHORT).show()
            return false
        }
        if (percent == null || percent < 0 || percent > 100) {
            Toast.makeText(this, "Volume must be 0-100", Toast.LENGTH_SHORT).show()
            return false
        }
        val volume = percent / 100f
        volumeStore.setVolume(key, volume)
        return true
    }
}
