package com.duda.appvolumecontrol.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.duda.appvolumecontrol.core.VolumeStore

class ManageAppsActivity : AppCompatActivity() {
    private lateinit var volumeStore: VolumeStore
    private lateinit var savedContainer: LinearLayout
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var volumeInput: EditText
    private lateinit var searchAllToggle: CheckBox
    private var searchEntries: List<AppEntry> = emptyList()
    private var searchLoadedFromAll: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeStore = VolumeStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Manage apps"
            textSize = 20f
        }

        val description = TextView(this).apply {
            text = "Add apps here to show them in Saved volumes on the main screen."
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }

        val manualTitle = TextView(this).apply {
            text = "Add by package"
            textSize = 16f
            setPadding(0, 8, 0, 8)
        }

        packageInput = EditText(this).apply {
            hint = "com.example.app"
            textSize = 14f
            setPadding(16, 8, 16, 8)
        }

        volumeInput = EditText(this).apply {
            hint = "Preset volume (%)"
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 14f
            setPadding(16, 8, 16, 8)
        }

        val addPackageButton = Button(this).apply {
            text = "Add package"
            setOnClickListener { addPackageFromInput() }
        }

        val searchTitle = TextView(this).apply {
            text = "Search installed apps"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        searchAllToggle = CheckBox(this).apply {
            text = "Search all installed apps"
            isChecked = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_SEARCH_ALL, false)
            setOnCheckedChangeListener { _, isChecked ->
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().putBoolean(KEY_SEARCH_ALL, isChecked).apply()
                searchEntries = emptyList()
                searchLoadedFromAll = false
                updateSearchResults()
            }
        }

        searchInput = EditText(this).apply {
            hint = "Search apps"
            textSize = 14f
            setPadding(16, 8, 16, 8)
            addTextChangedListener { updateSearchResults() }
        }

        val clearSearchButton = Button(this).apply {
            text = "Clear search"
            setOnClickListener { searchInput.setText("") }
        }

        searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val savedTitle = TextView(this).apply {
            text = "Saved apps"
            textSize = 16f
            setPadding(0, 24, 0, 8)
        }

        savedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(header)
        root.addView(description)
        root.addView(manualTitle)
        root.addView(packageInput)
        root.addView(volumeInput)
        root.addView(addPackageButton)
        root.addView(searchTitle)
        root.addView(searchAllToggle)
        root.addView(searchInput)
        root.addView(clearSearchButton)
        root.addView(searchContainer)
        root.addView(savedTitle)
        root.addView(savedContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)
        refreshSavedList()
        updateSearchResults()
    }

    private fun addPackageFromInput() {
        val packageName = packageInput.text?.toString()?.trim().orEmpty()
        if (packageName.isBlank()) {
            Toast.makeText(this, "Enter a package name", Toast.LENGTH_SHORT).show()
            return
        }
        val volume = parseVolumeInput(volumeInput.text?.toString().orEmpty()) ?: return
        volumeStore.setVolume(packageName, volume, null)
        packageInput.setText("")
        volumeInput.setText("")
        Toast.makeText(this, "Added $packageName", Toast.LENGTH_SHORT).show()
        refreshSavedList()
        updateSearchResults()
    }

    private fun updateSearchResults() {
        searchContainer.removeAllViews()
        val query = searchInput.text?.toString()?.trim().orEmpty().lowercase()
        if (query.isBlank()) {
            searchContainer.addView(buildEmptyRow("Type to search for apps."))
            return
        }
        if (query.length < MIN_SEARCH_LENGTH) {
            searchContainer.addView(buildEmptyRow("Type at least $MIN_SEARCH_LENGTH characters."))
            return
        }

        val searchAll = searchAllToggle.isChecked
        if (searchEntries.isEmpty() || searchLoadedFromAll != searchAll) {
            searchEntries = loadSearchEntries(searchAll)
            searchLoadedFromAll = searchAll
        }

        val filtered = searchEntries.filter {
            it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
        }

        if (filtered.isEmpty()) {
            searchContainer.addView(buildEmptyRow("No matching apps."))
            return
        }

        val savedPackages = volumeStore.listPresetEntries()
            .filter { it.userId == null }
            .map { it.packageName }
            .toSet()
        for (entry in filtered.sortedBy { it.label.lowercase() }) {
            val isSaved = savedPackages.contains(entry.packageName)
            searchContainer.addView(buildSearchRow(entry, isSaved))
        }
    }

    private fun loadSearchEntries(searchAll: Boolean): List<AppEntry> {
        val packages = if (searchAll) {
            packageManager.getInstalledApplications(0).map { it.packageName }
        } else {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            packageManager.queryIntentActivities(mainIntent, 0).map { it.activityInfo.packageName }
        }
        return packages
            .distinct()
            .map { packageName -> AppEntry(packageName, resolveLabel(packageName)) }
    }

    private fun refreshSavedList() {
        savedContainer.removeAllViews()
        val saved = volumeStore.listPresetEntries().filter { it.userId == null }
        if (saved.isEmpty()) {
            savedContainer.addView(buildEmptyRow("No saved apps yet."))
            return
        }
        for (entry in saved) {
            savedContainer.addView(buildSavedRow(entry.packageName, resolveLabel(entry.packageName)))
        }
    }

    private fun buildSearchRow(entry: AppEntry, isSaved: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val nameView = TextView(this).apply {
            text = entry.label
            textSize = 14f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnLongClickListener {
                copyToClipboard(entry.packageName)
                true
            }
        }

        val addButton = Button(this).apply {
            text = if (isSaved) "Saved" else "Add"
            isEnabled = !isSaved
            setOnClickListener {
                val volume = parseVolumeInput(volumeInput.text?.toString().orEmpty()) ?: return@setOnClickListener
                volumeStore.setVolume(entry.packageName, volume, null)
                Toast.makeText(this@ManageAppsActivity, "Added ${entry.packageName}", Toast.LENGTH_SHORT).show()
                refreshSavedList()
                updateSearchResults()
            }
        }

        row.addView(nameView)
        row.addView(addButton)
        return row
    }

    private fun buildSavedRow(packageName: String, label: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val nameView = TextView(this).apply {
            text = label
            textSize = 14f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnLongClickListener {
                copyToClipboard(packageName)
                true
            }
        }

        val deleteButton = Button(this).apply {
            text = "Remove"
            setOnClickListener {
                volumeStore.removePreset(packageName, null)
                Toast.makeText(this@ManageAppsActivity, "Removed $packageName", Toast.LENGTH_SHORT).show()
                refreshSavedList()
                updateSearchResults()
            }
        }

        row.addView(nameView)
        row.addView(deleteButton)
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

    private fun parseVolumeInput(input: String): Float? {
        if (input.isBlank()) return 1f
        val value = input.toIntOrNull()
        if (value == null || value < 0 || value > 100) {
            Toast.makeText(this, "Volume must be 0-100", Toast.LENGTH_SHORT).show()
            return null
        }
        return value / 100f
    }

    private fun copyToClipboard(text: String) {
        val manager = getSystemService(ClipboardManager::class.java)
        manager.setPrimaryClip(ClipData.newPlainText("package", text))
        Toast.makeText(this, "Package copied", Toast.LENGTH_SHORT).show()
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
    )

    companion object {
        private const val MIN_SEARCH_LENGTH = 2
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_SEARCH_ALL = "search_all_apps"
    }
}
