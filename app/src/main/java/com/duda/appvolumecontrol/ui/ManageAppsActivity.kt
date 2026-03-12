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
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.AdapterView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import com.duda.appvolumecontrol.core.MarkerStyle
import com.duda.appvolumecontrol.core.ProfileUtils
import com.duda.appvolumecontrol.core.VolumeStore
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.duda.appvolumecontrol.core.BluetoothInfoUtils

class ManageAppsActivity : AppCompatActivity() {
    private lateinit var volumeStore: VolumeStore
    private lateinit var savedContainer: LinearLayout
    private lateinit var searchContainer: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var packageInput: EditText
    private lateinit var volumeInput: EditText
    private lateinit var searchAllToggle: CheckBox
    private lateinit var profileSpinner: Spinner
    private lateinit var profileAdapter: ArrayAdapter<String>
    private lateinit var profileHint: TextView
    private var searchEntries: List<AppEntry> = emptyList()
    private var searchLoadedFromAll: Boolean = false
    private var profileOptions: List<ProfileOption> = emptyList()
    private var selectedProfileUserId: Int? = null
    private var profileUpdating: Boolean = false

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

        val profileLabel = TextView(this).apply {
            text = "Profile"
            textSize = 13f
        }

        profileAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, mutableListOf("All profiles")).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        profileSpinner = Spinner(this).apply {
            adapter = profileAdapter
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    if (profileUpdating) return
                    val option = profileOptions.getOrNull(position) ?: ProfileOption(null, "All profiles")
                    selectedProfileUserId = option.userId
                    updateProfileHint()
                    refreshSavedList()
                    updateSearchResults()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

        val profileRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 8)
            addView(profileLabel)
            addView(profileSpinner)
        }

        profileHint = TextView(this).apply {
            text = "Adding presets for: All profiles"
            textSize = 12f
            setPadding(0, 0, 0, 8)
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
        root.addView(profileRow)
        root.addView(profileHint)
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
        updateProfileOptions()
        updateProfileHint()
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
        volumeStore.setVolume(packageName, volume, selectedProfileUserId)
        packageInput.setText("")
        volumeInput.setText("")
        Toast.makeText(this, "Added $packageName", Toast.LENGTH_SHORT).show()
        updateProfileOptions()
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

        val savedKeys = volumeStore.listPresetEntries()
            .map { it.storageKey() }
            .toSet()
        for (entry in filtered.sortedBy { it.label.lowercase() }) {
            val key = VolumeStore.VolumeKey(entry.packageName, selectedProfileUserId).storageKey()
            val isSaved = savedKeys.contains(key)
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
        val saved = volumeStore.listPresetEntries()
        val filtered = if (selectedProfileUserId == null) {
            saved
        } else {
            saved.filter { it.userId == selectedProfileUserId }
        }
        if (filtered.isEmpty()) {
            savedContainer.addView(buildEmptyRow("No saved apps yet."))
            return
        }
        for (entry in filtered) {
            savedContainer.addView(buildSavedRow(entry, resolveLabel(entry.packageName)))
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
                volumeStore.setVolume(entry.packageName, volume, selectedProfileUserId)
                Toast.makeText(this@ManageAppsActivity, "Added ${entry.packageName}", Toast.LENGTH_SHORT).show()
                updateProfileOptions()
                refreshSavedList()
                updateSearchResults()
            }
        }

        row.addView(nameView)
        row.addView(addButton)
        return row
    }

    private fun buildSavedRow(entry: VolumeStore.VolumeKey, label: String): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 8, 0, 8)
        }

        val nameContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val nameView = TextView(this).apply {
            text = label
            textSize = 14f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            setOnLongClickListener {
                copyToClipboard(entry.packageName)
                true
            }
        }

        val profileView = TextView(this).apply {
            text = ProfileUtils.buildProfileLabel(this@ManageAppsActivity, entry.userId)
            textSize = 12f
            setTextColor(0xFF616161.toInt())
        }

        nameContainer.addView(nameView)
        nameContainer.addView(profileView)

        val hasMarkers = volumeStore.getMarkerConfig(entry) != null
        val markerButton = Button(this).apply {
            text = if (hasMarkers) "Markers ✓" else "Markers"
            setOnClickListener {
                showMarkerDialog(entry, label)
            }
        }

        val hasOutput = volumeStore.getOutputDevice(entry) != null
        val outputButton = Button(this).apply {
            text = if (hasOutput) "Output ✓" else "Output"
            setOnClickListener {
                showOutputDialog(entry, label)
            }
        }

        val deleteButton = Button(this).apply {
            text = "Remove"
            setOnClickListener {
                volumeStore.removePreset(entry.packageName, entry.userId)
                Toast.makeText(this@ManageAppsActivity, "Removed ${entry.packageName}", Toast.LENGTH_SHORT).show()
                updateProfileOptions()
                refreshSavedList()
                updateSearchResults()
            }
        }

        row.addView(nameContainer)
        row.addView(markerButton)
        row.addView(outputButton)
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

    private fun updateProfileOptions() {
        val savedUserIds = volumeStore.listPresetEntries().mapNotNull { it.userId }
        val options = mutableListOf(ProfileOption(null, "All profiles"))
        val profileIds = ProfileUtils.collectUserIds(this, savedUserIds + listOfNotNull(selectedProfileUserId))
        for (userId in profileIds) {
            options.add(ProfileOption(userId, ProfileUtils.buildProfileLabel(this, userId)))
        }
        if (options == profileOptions) return
        profileOptions = options
        profileUpdating = true
        profileAdapter.clear()
        profileAdapter.addAll(options.map { it.label })
        profileAdapter.notifyDataSetChanged()
        val selectedIndex = options.indexOfFirst { it.userId == selectedProfileUserId }
            .takeIf { it >= 0 } ?: 0
        selectedProfileUserId = options.getOrNull(selectedIndex)?.userId
        profileSpinner.setSelection(selectedIndex)
        profileUpdating = false
    }

    private fun updateProfileHint() {
        profileHint.text = "Adding presets for: ${ProfileUtils.buildProfileLabel(this, selectedProfileUserId)}"
    }

    private fun showMarkerDialog(entry: VolumeStore.VolumeKey, label: String) {
        val existing = volumeStore.getMarkerConfig(entry)
        val existingMarker = existing?.markers?.firstOrNull()
        val existingSegment = existing?.segments?.firstOrNull()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
        }

        val markerTitle = TextView(this).apply {
            text = "Marker (optional)"
            textSize = 13f
        }
        val markerPercentInput = EditText(this).apply {
            hint = "Marker % (0-100)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existingMarker?.percent?.toString().orEmpty())
        }
        val markerColorInput = EditText(this).apply {
            hint = "Marker color (#RRGGBB)"
            setText(existingMarker?.color.orEmpty())
        }

        val segmentTitle = TextView(this).apply {
            text = "Segment (optional)"
            textSize = 13f
            setPadding(0, 12, 0, 0)
        }
        val segmentStartInput = EditText(this).apply {
            hint = "Segment start %"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existingSegment?.start?.toString().orEmpty())
        }
        val segmentEndInput = EditText(this).apply {
            hint = "Segment end %"
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(existingSegment?.end?.toString().orEmpty())
        }
        val segmentColorInput = EditText(this).apply {
            hint = "Segment color (#RRGGBB)"
            setText(existingSegment?.color.orEmpty())
        }

        container.addView(markerTitle)
        container.addView(markerPercentInput)
        container.addView(markerColorInput)
        container.addView(segmentTitle)
        container.addView(segmentStartInput)
        container.addView(segmentEndInput)
        container.addView(segmentColorInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Markers for $label")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                volumeStore.clearMarkerConfig(entry)
                refreshSavedList()
                updateSearchResults()
            }
            .setPositiveButton("Save", null)
            .create()

        dialog.setOnShowListener {
            val saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            saveButton.setOnClickListener {
                val errors = mutableListOf<String>()
                val markerPercent = parseOptionalPercent(markerPercentInput.text?.toString().orEmpty(), "Marker", errors)
                val markerColor = parseOptionalColor(markerColorInput.text?.toString().orEmpty(), "Marker color", errors)
                val segmentStart = parseOptionalPercent(segmentStartInput.text?.toString().orEmpty(), "Segment start", errors)
                val segmentEnd = parseOptionalPercent(segmentEndInput.text?.toString().orEmpty(), "Segment end", errors)
                val segmentColor = parseOptionalColor(segmentColorInput.text?.toString().orEmpty(), "Segment color", errors)
                if (errors.isNotEmpty()) {
                    Toast.makeText(this, errors.first(), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if ((segmentStart != null && segmentEnd == null) || (segmentStart == null && segmentEnd != null)) {
                    Toast.makeText(this, "Segment start and end are required together", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val markers = if (markerPercent != null) {
                    listOf(VolumeStore.MarkerConfig.Marker(markerPercent, markerColor))
                } else {
                    emptyList()
                }
                val segments = if (segmentStart != null && segmentEnd != null) {
                    listOf(VolumeStore.MarkerConfig.Segment(segmentStart, segmentEnd, segmentColor))
                } else {
                    emptyList()
                }
                val config = VolumeStore.MarkerConfig(markers, segments)
                if (config.isEmpty()) {
                    volumeStore.clearMarkerConfig(entry)
                } else {
                    volumeStore.setMarkerConfig(entry, config)
                }
                refreshSavedList()
                updateSearchResults()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun parseOptionalPercent(input: String, label: String, errors: MutableList<String>): Int? {
        if (input.isBlank()) return null
        val value = input.trim().toIntOrNull()
        if (value == null || value < 0 || value > 100) {
            errors.add("$label must be 0-100")
            return null
        }
        return value
    }

    private fun parseOptionalColor(input: String, label: String, errors: MutableList<String>): String? {
        if (input.isBlank()) return null
        val normalized = MarkerStyle.normalizeColorString(input)
        if (MarkerStyle.parseColorOrNull(normalized) == null) {
            errors.add("$label is invalid")
            return null
        }
        return normalized
    }

    private fun showOutputDialog(entry: VolumeStore.VolumeKey, label: String) {
        val audioManager = getSystemService(AudioManager::class.java)
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .sortedWith(compareBy({ audioTypeLabel(it.type) }, { it.productName?.toString().orEmpty() }))
        val options = mutableListOf<OutputOption>()
        options.add(OutputOption(null, "Default/system"))
        for (device in devices) {
            val name = device.productName?.toString().orEmpty().ifBlank { "Unknown" }
            val typeLabel = audioTypeLabel(device.type)
            val address = device.address.takeIf { it.isNotBlank() }
            val labelText = if (address == null) {
                "$name • $typeLabel"
            } else {
                "$name • $typeLabel • $address"
            }
            options.add(
                OutputOption(
                    VolumeStore.OutputDevice(device.type, address, name),
                    labelText
                )
            )
        }

        val current = volumeStore.getOutputDevice(entry)
        val selectedIndex = options.indexOfFirst { option ->
            val device = option.device ?: return@indexOfFirst current == null
            current != null && device.type == current.type && device.address == current.address
        }.takeIf { it >= 0 } ?: 0

        var chosenIndex = selectedIndex
        val labels = options.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Output device for $label")
            .setSingleChoiceItems(labels, selectedIndex) { _, which ->
                chosenIndex = which
                maybeShowBluetoothInfo(options.getOrNull(which)?.device)
            }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Set") { _, _ ->
                val selection = options.getOrNull(chosenIndex)?.device
                volumeStore.setOutputDevice(entry, selection)
                Toast.makeText(this, "Output updated", Toast.LENGTH_SHORT).show()
                refreshSavedList()
                updateSearchResults()
            }
            .show()
            .also {
                maybeShowBluetoothInfo(options.getOrNull(selectedIndex)?.device)
            }
    }

    private fun maybeShowBluetoothInfo(device: VolumeStore.OutputDevice?) {
        if (device == null) return
        if (!isBluetoothType(device.type)) return
        val address = device.address.orEmpty()
        BluetoothInfoUtils.fetchA2dpCodecLabel(this, address) { label ->
            runOnUiThread {
                Toast.makeText(this, label, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isBluetoothType(type: Int): Boolean {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
    }

    private fun audioTypeLabel(type: Int): String {
        return when (type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Earpiece"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB device"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
            AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB accessory"
            AudioDeviceInfo.TYPE_HDMI -> "HDMI"
            AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
            AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI eARC"
            AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out"
            AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line digital"
            else -> "Type $type"
        }
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
    )

    private data class ProfileOption(
        val userId: Int?,
        val label: String,
    )

    private data class OutputOption(
        val device: VolumeStore.OutputDevice?,
        val label: String,
    )

    companion object {
        private const val MIN_SEARCH_LENGTH = 2
        private const val PREFS_NAME = "ui_prefs"
        private const val KEY_SEARCH_ALL = "search_all_apps"
    }
}
