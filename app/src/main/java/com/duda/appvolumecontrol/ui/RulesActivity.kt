package com.duda.appvolumecontrol.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import com.duda.appvolumecontrol.core.Rule
import com.duda.appvolumecontrol.core.RuleAction
import com.duda.appvolumecontrol.core.RuleCondition
import com.duda.appvolumecontrol.core.RuleGroup
import com.duda.appvolumecontrol.core.RuleTarget
import com.duda.appvolumecontrol.core.RuleTargetRef
import com.duda.appvolumecontrol.core.VolumeStore
import kotlin.math.max
import kotlin.math.min

class RulesActivity : AppCompatActivity() {
    private lateinit var volumeStore: VolumeStore
    private lateinit var groupContainer: LinearLayout
    private lateinit var ruleContainer: LinearLayout
    private var installedAppsCache: List<AppEntry>? = null
    private lateinit var locationStore: com.duda.appvolumecontrol.core.LocationRuleStore
    private lateinit var locationContainer: LinearLayout
    private lateinit var locationInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeStore = VolumeStore(this)
        locationStore = com.duda.appvolumecontrol.core.LocationRuleStore(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val header = TextView(this).apply {
            text = "Rules"
            textSize = 20f
        }

        val helper = TextView(this).apply {
            text = "Rules let you override presets when apps are active or during time windows."
            textSize = 13f
            setPadding(0, 8, 0, 16)
        }

        val locationRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 8)
        }
        val locationLabel = TextView(this).apply {
            text = "Current location id (auto)"
            textSize = 13f
        }
        val locationHint = TextView(this).apply {
            text = "Set/clear to override until the next location fix."
            textSize = 12f
        }
        locationInput = EditText(this).apply {
            hint = "home, office, etc"
            setText(locationStore.getCurrentLocationId().orEmpty())
        }
        val locationButtons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val locationSave = Button(this).apply {
            text = "Set"
            setOnClickListener {
                val value = locationInput.text?.toString()
                locationStore.setCurrentLocationId(value)
                Toast.makeText(this@RulesActivity, "Location id updated", Toast.LENGTH_SHORT).show()
            }
        }
        val locationClear = Button(this).apply {
            text = "Clear"
            setOnClickListener {
                locationStore.setCurrentLocationId(null)
                locationInput.setText("")
                Toast.makeText(this@RulesActivity, "Location id cleared", Toast.LENGTH_SHORT).show()
            }
        }
        val locationPermission = Button(this).apply {
            text = "Grant location permission"
            setOnClickListener { requestLocationPermission() }
        }
        locationButtons.addView(locationSave)
        locationButtons.addView(locationClear)
        locationButtons.addView(locationPermission)
        locationRow.addView(locationLabel)
        locationRow.addView(locationInput)
        locationRow.addView(locationHint)
        locationRow.addView(locationButtons)

        val locationTitle = TextView(this).apply {
            text = "Locations"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }
        val addLocationButton = Button(this).apply {
            text = "Add location"
            setOnClickListener { showAddLocationDialog() }
        }
        locationContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val addGroupButton = Button(this).apply {
            text = "Add group"
            setOnClickListener { showAddGroupDialog() }
        }

        val addRuleButton = Button(this).apply {
            text = "Add rule"
            setOnClickListener { showAddRuleDialog() }
        }

        val groupTitle = TextView(this).apply {
            text = "Groups"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }

        groupContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val ruleTitle = TextView(this).apply {
            text = "Rules"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }

        ruleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        root.addView(header)
        root.addView(helper)
        root.addView(locationRow)
        root.addView(locationTitle)
        root.addView(addLocationButton)
        root.addView(locationContainer)
        root.addView(addGroupButton)
        root.addView(addRuleButton)
        root.addView(groupTitle)
        root.addView(groupContainer)
        root.addView(ruleTitle)
        root.addView(ruleContainer)

        val scroll = ScrollView(this).apply {
            addView(root)
        }

        setContentView(scroll)
        refresh()
    }

    private fun refresh() {
        val groups = volumeStore.listRuleGroups()
        val rules = volumeStore.listRules()
        val locations = locationStore.listLocations()

        groupContainer.removeAllViews()
        ruleContainer.removeAllViews()
        locationContainer.removeAllViews()

        if (groups.isEmpty()) {
            groupContainer.addView(buildEmptyRow("No groups yet"))
        } else {
            for (group in groups) {
                groupContainer.addView(buildGroupRow(group))
            }
        }

        if (locations.isEmpty()) {
            locationContainer.addView(buildEmptyRow("No locations yet"))
        } else {
            for (location in locations) {
                locationContainer.addView(buildLocationRow(location))
            }
        }

        if (rules.isEmpty()) {
            ruleContainer.addView(buildEmptyRow("No rules yet"))
        } else {
            for (rule in rules) {
                ruleContainer.addView(buildRuleRow(rule))
            }
        }
    }

    private fun buildEmptyRow(message: String): View {
        return TextView(this).apply {
            text = message
            textSize = 13f
            setPadding(0, 4, 0, 4)
        }
    }

    private fun buildGroupRow(group: RuleGroup): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val name = TextView(this).apply {
            text = group.name
            textSize = 15f
        }
        val idView = TextView(this).apply {
            text = "ID: ${group.id}"
            textSize = 12f
        }
        val detail = TextView(this).apply {
            val summary = group.packages.joinToString(", ") { it.storageKey() }
            text = "${group.packages.size} packages: ${if (summary.isBlank()) "-" else summary}"
            textSize = 12f
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val editButton = Button(this).apply {
            text = "Edit"
            setOnClickListener { showEditGroupDialog(group) }
        }
        val deleteButton = Button(this).apply {
            text = "Delete"
            setOnClickListener { confirmDeleteGroup(group) }
        }
        actions.addView(editButton)
        actions.addView(deleteButton)
        row.addView(name)
        row.addView(idView)
        row.addView(detail)
        row.addView(actions)
        return row
    }

    private fun buildRuleRow(rule: Rule): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val name = TextView(this).apply {
            text = rule.name
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val enabledToggle = CheckBox(this).apply {
            text = "Enabled"
            isChecked = rule.enabled
            setOnCheckedChangeListener { _, isChecked ->
                updateRule(rule.copy(enabled = isChecked))
            }
        }
        headerRow.addView(name)
        headerRow.addView(enabledToggle)

        val detail = TextView(this).apply {
            text = "Priority ${rule.priority}. ${buildConditionSummary(rule)}"
            textSize = 12f
        }
        val actionDetail = TextView(this).apply {
            text = buildActionSummary(rule)
            textSize = 12f
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val editButton = Button(this).apply {
            text = "Edit"
            setOnClickListener { showEditRuleDialog(rule) }
        }
        val deleteButton = Button(this).apply {
            text = "Delete"
            setOnClickListener { confirmDeleteRule(rule) }
        }
        actions.addView(editButton)
        actions.addView(deleteButton)
        row.addView(headerRow)
        row.addView(detail)
        row.addView(actionDetail)
        row.addView(actions)
        return row
    }

    private fun buildLocationRow(location: com.duda.appvolumecontrol.core.LocationRuleStore.LocationRule): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 8)
        }
        val name = TextView(this).apply {
            text = location.id
            textSize = 15f
        }
        val detail = TextView(this).apply {
            text = "Lat ${location.latitude}, Lon ${location.longitude}, Radius ${location.radiusMeters}m"
            textSize = 12f
        }
        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val editButton = Button(this).apply {
            text = "Edit"
            setOnClickListener { showEditLocationDialog(location) }
        }
        val deleteButton = Button(this).apply {
            text = "Delete"
            setOnClickListener { confirmDeleteLocation(location) }
        }
        actions.addView(editButton)
        actions.addView(deleteButton)
        row.addView(name)
        row.addView(detail)
        row.addView(actions)
        return row
    }

    private fun buildConditionSummary(rule: Rule): String {
        if (rule.conditions.isEmpty()) return "No conditions"
        val parts = rule.conditions.map { condition ->
            when (condition) {
                is RuleCondition.AnyActive -> {
                    val targets = condition.targets.joinToString(", ") { it.storageKey() }
                    "Any active: ${if (targets.isBlank()) "-" else targets}"
                }
                is RuleCondition.AllActive -> {
                    val targets = condition.targets.joinToString(", ") { it.storageKey() }
                    "All active: ${if (targets.isBlank()) "-" else targets}"
                }
                is RuleCondition.TimeWindow -> {
                    val days = if (condition.days.isEmpty()) "all days" else "days ${condition.days.joinToString(",") }"
                    val start = formatMinutes(condition.startMinute)
                    val end = formatMinutes(condition.endMinute)
                    "Time: $days $start-$end"
                }
                is RuleCondition.Location -> "Location: ${condition.locationIds.size} ids"
            }
        }
        return parts.joinToString(" | ")
    }

    private fun buildActionSummary(rule: Rule): String {
        if (rule.actions.isEmpty()) return "No actions"
        val parts = rule.actions.map { action ->
            when (action) {
                is RuleAction.SetVolume -> {
                    val target = action.targets.joinToString(", ") { it.toStorageValue() }
                    val pct = (action.volume * 100).toInt()
                    "Set volume $pct% for ${if (target.isBlank()) "-" else target}"
                }
            }
        }
        return parts.joinToString(" | ")
    }

    private fun confirmDeleteGroup(group: RuleGroup) {
        AlertDialog.Builder(this)
            .setTitle("Delete group")
            .setMessage("Delete ${group.name}?")
            .setPositiveButton("Delete") { _, _ ->
                val updated = volumeStore.listRuleGroups().filterNot { it.id == group.id }
                volumeStore.setRuleGroups(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteRule(rule: Rule) {
        AlertDialog.Builder(this)
            .setTitle("Delete rule")
            .setMessage("Delete ${rule.name}?")
            .setPositiveButton("Delete") { _, _ ->
                val updated = volumeStore.listRules().filterNot { it.id == rule.id }
                volumeStore.setRules(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddGroupDialog() {
        val nameInput = EditText(this).apply {
            hint = "Group name"
        }
        val packagesInput = EditText(this).apply {
            hint = "Packages (comma or space separated)"
            minLines = 3
        }
        val pickButton = Button(this).apply {
            text = "Pick apps"
            setOnClickListener {
                val initial = parseTargets(packagesInput.text?.toString().orEmpty())
                showPackagePicker("Pick group apps", initial) { selected ->
                    packagesInput.setText(selected.joinToString(", ") { it.storageKey() })
                }
            }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(nameInput)
            addView(packagesInput)
            addView(pickButton)
        }
        AlertDialog.Builder(this)
            .setTitle("Add group")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val packages = parseTargets(packagesInput.text?.toString().orEmpty())
                if (name.isBlank()) {
                    toast("Group name required")
                    return@setPositiveButton
                }
                if (packages.isEmpty()) {
                    toast("Add at least one package")
                    return@setPositiveButton
                }
                val group = RuleGroup(
                    id = "g_${System.currentTimeMillis()}",
                    name = name,
                    packages = packages,
                )
                val updated = volumeStore.listRuleGroups() + group
                volumeStore.setRuleGroups(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditGroupDialog(group: RuleGroup) {
        val nameInput = EditText(this).apply {
            hint = "Group name"
            setText(group.name)
        }
        val packagesInput = EditText(this).apply {
            hint = "Packages (comma or space separated)"
            minLines = 3
            setText(group.packages.joinToString(", ") { it.storageKey() })
        }
        val pickButton = Button(this).apply {
            text = "Pick apps"
            setOnClickListener {
                val initial = parseTargets(packagesInput.text?.toString().orEmpty())
                showPackagePicker("Pick group apps", initial) { selected ->
                    packagesInput.setText(selected.joinToString(", ") { it.storageKey() })
                }
            }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(nameInput)
            addView(packagesInput)
            addView(pickButton)
        }
        AlertDialog.Builder(this)
            .setTitle("Edit group")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                val packages = parseTargets(packagesInput.text?.toString().orEmpty())
                if (name.isBlank()) {
                    toast("Group name required")
                    return@setPositiveButton
                }
                if (packages.isEmpty()) {
                    toast("Add at least one package")
                    return@setPositiveButton
                }
                val updatedGroup = group.copy(name = name, packages = packages)
                val updated = volumeStore.listRuleGroups().map { existing ->
                    if (existing.id == group.id) updatedGroup else existing
                }
                volumeStore.setRuleGroups(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddRuleDialog() {
        showRuleDialog(existing = null)
    }

    private fun showEditRuleDialog(rule: Rule) {
        showRuleDialog(existing = rule)
    }

    private fun showRuleDialog(existing: Rule?) {
        val nameInput = EditText(this).apply {
            hint = "Rule name"
            setText(existing?.name.orEmpty())
        }
        val priorityInput = EditText(this).apply {
            hint = "Priority (higher wins)"
            setText(existing?.priority?.toString() ?: "100")
        }
        val enabledToggle = CheckBox(this).apply {
            text = "Enabled"
            isChecked = existing?.enabled ?: true
        }
        val anyActiveInput = EditText(this).apply {
            hint = "Any active packages (comma separated)"
            minLines = 2
        }
        val anyPickButton = Button(this).apply {
            text = "Pick any-active apps"
            setOnClickListener {
                val initial = parseTargets(anyActiveInput.text?.toString().orEmpty())
                showPackagePicker("Pick any-active apps", initial) { selected ->
                    anyActiveInput.setText(selected.joinToString(", ") { it.storageKey() })
                }
            }
        }
        val allActiveInput = EditText(this).apply {
            hint = "All active packages (comma separated)"
            minLines = 2
        }
        val allPickButton = Button(this).apply {
            text = "Pick all-active apps"
            setOnClickListener {
                val initial = parseTargets(allActiveInput.text?.toString().orEmpty())
                showPackagePicker("Pick all-active apps", initial) { selected ->
                    allActiveInput.setText(selected.joinToString(", ") { it.storageKey() })
                }
            }
        }
        val daysInput = EditText(this).apply {
            hint = "Time days (1-7, optional)"
        }
        val startInput = EditText(this).apply {
            hint = "Start time (HH:MM, optional)"
        }
        val endInput = EditText(this).apply {
            hint = "End time (HH:MM, optional)"
        }
        val locationInput = EditText(this).apply {
            hint = "Location ids (comma separated, stored only)"
            minLines = 2
        }
        val groupTargetInput = EditText(this).apply {
            hint = "Target group id (optional)"
        }
        val targetPackagesInput = EditText(this).apply {
            hint = "Target packages (comma separated if no group id)"
            minLines = 2
        }
        val targetPickButton = Button(this).apply {
            text = "Pick target apps"
            setOnClickListener {
                val initial = parseTargets(targetPackagesInput.text?.toString().orEmpty())
                showPackagePicker("Pick target apps", initial) { selected ->
                    targetPackagesInput.setText(selected.joinToString(", ") { it.storageKey() })
                }
            }
        }
        val volumeInput = EditText(this).apply {
            hint = "Volume percent (0-100)"
            setText(existing?.actions?.firstOrNull()?.let { action ->
                when (action) {
                    is RuleAction.SetVolume -> ((action.volume * 100f).toInt()).toString()
                }
            } ?: "100")
        }
        val groupHint = TextView(this).apply {
            val groups = volumeStore.listRuleGroups()
            text = if (groups.isEmpty()) {
                "No groups yet. Add a group first if you want group targets."
            } else {
                "Group IDs: " + groups.joinToString(", ") { it.id }
            }
            textSize = 12f
            setPadding(0, 8, 0, 8)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(nameInput)
            addView(priorityInput)
            addView(enabledToggle)
            addView(anyActiveInput)
            addView(anyPickButton)
            addView(allActiveInput)
            addView(allPickButton)
            addView(daysInput)
            addView(startInput)
            addView(endInput)
            addView(locationInput)
            addView(groupTargetInput)
            addView(groupHint)
            addView(targetPackagesInput)
            addView(targetPickButton)
            addView(volumeInput)
        }

        if (existing != null) {
            val anyActive = existing.conditions.filterIsInstance<RuleCondition.AnyActive>().firstOrNull()
            if (anyActive != null) {
                anyActiveInput.setText(anyActive.targets.joinToString(", ") { it.storageKey() })
            }
            val allActive = existing.conditions.filterIsInstance<RuleCondition.AllActive>().firstOrNull()
            if (allActive != null) {
                allActiveInput.setText(allActive.targets.joinToString(", ") { it.storageKey() })
            }
            val timeWindow = existing.conditions.filterIsInstance<RuleCondition.TimeWindow>().firstOrNull()
            if (timeWindow != null) {
                daysInput.setText(timeWindow.days.joinToString(","))
                startInput.setText(formatMinutes(timeWindow.startMinute))
                endInput.setText(formatMinutes(timeWindow.endMinute))
            }
            val location = existing.conditions.filterIsInstance<RuleCondition.Location>().firstOrNull()
            if (location != null) {
                locationInput.setText(location.locationIds.joinToString(", "))
            }
            val setVolume = existing.actions.filterIsInstance<RuleAction.SetVolume>().firstOrNull()
            if (setVolume != null) {
                val groupTarget = setVolume.targets.filterIsInstance<RuleTargetRef.Group>().firstOrNull()
                val packageTargets = setVolume.targets.filterIsInstance<RuleTargetRef.Package>().map { it.target }
                if (groupTarget != null) {
                    groupTargetInput.setText(groupTarget.groupId)
                } else if (packageTargets.isNotEmpty()) {
                    targetPackagesInput.setText(packageTargets.joinToString(", ") { it.storageKey() })
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add rule" else "Edit rule")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isBlank()) {
                    toast("Rule name required")
                    return@setPositiveButton
                }
                val priority = priorityInput.text?.toString()?.toIntOrNull() ?: 0
                val enabled = enabledToggle.isChecked

                val conditions = mutableListOf<RuleCondition>()
                val anyTargets = parseTargets(anyActiveInput.text?.toString().orEmpty())
                if (anyTargets.isNotEmpty()) {
                    conditions.add(RuleCondition.AnyActive(anyTargets))
                }
                val allTargets = parseTargets(allActiveInput.text?.toString().orEmpty())
                if (allTargets.isNotEmpty()) {
                    conditions.add(RuleCondition.AllActive(allTargets))
                }
                val (timeWindow, timeError) = parseTimeWindow(
                    daysInput.text?.toString().orEmpty(),
                    startInput.text?.toString().orEmpty(),
                    endInput.text?.toString().orEmpty(),
                )
                if (timeError != null) {
                    toast(timeError)
                    return@setPositiveButton
                }
                if (timeWindow != null) {
                    conditions.add(timeWindow)
                }
                val locationIds = parseLocationIds(locationInput.text?.toString().orEmpty())
                if (locationIds.isNotEmpty()) {
                    conditions.add(RuleCondition.Location(locationIds))
                }

                if (conditions.isEmpty()) {
                    toast("Add at least one condition")
                    return@setPositiveButton
                }

                val volumePercent = volumeInput.text?.toString()?.toIntOrNull() ?: 100
                val volume = normalizeVolume(volumePercent / 100f)

                val targetRefs = mutableListOf<RuleTargetRef>()
                val groupId = groupTargetInput.text?.toString()?.trim().orEmpty()
                if (groupId.isNotBlank()) {
                    val valid = volumeStore.listRuleGroups().any { it.id == groupId }
                    if (!valid) {
                        toast("Group id not found")
                        return@setPositiveButton
                    }
                    targetRefs.add(RuleTargetRef.Group(groupId))
                } else {
                    val targets = parseTargets(targetPackagesInput.text?.toString().orEmpty())
                    if (targets.isNotEmpty()) {
                        targetRefs.addAll(targets.map { RuleTargetRef.Package(it) })
                    }
                }

                if (targetRefs.isEmpty()) {
                    toast("Add a target group or package list")
                    return@setPositiveButton
                }

                val action = RuleAction.SetVolume(targets = targetRefs, volume = volume)
                val rule = Rule(
                    id = existing?.id ?: "r_${System.currentTimeMillis()}",
                    name = name,
                    enabled = enabled,
                    priority = priority,
                    conditions = conditions,
                    actions = listOf(action),
                )
                val updated = if (existing == null) {
                    volumeStore.listRules() + rule
                } else {
                    volumeStore.listRules().map { stored ->
                        if (stored.id == existing.id) rule else stored
                    }
                }
                volumeStore.setRules(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateRule(rule: Rule) {
        val updated = volumeStore.listRules().map { existing ->
            if (existing.id == rule.id) rule else existing
        }
        volumeStore.setRules(updated)
        refresh()
    }

    private fun parseTargets(input: String): List<RuleTarget> {
        if (input.isBlank()) return emptyList()
        val tokens = input.split(Regex("[\\s,]+"))
        val targets = mutableListOf<RuleTarget>()
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isBlank()) continue
            val target = RuleTarget.parse(trimmed) ?: continue
            targets.add(target)
        }
        return targets
    }

    private fun parseTimeWindow(
        daysRaw: String,
        startRaw: String,
        endRaw: String,
    ): Pair<RuleCondition.TimeWindow?, String?> {
        val days = parseDays(daysRaw)
        val startProvided = startRaw.trim().isNotBlank()
        val endProvided = endRaw.trim().isNotBlank()
        val anyProvided = days.isNotEmpty() || startProvided || endProvided
        if (!anyProvided) return null to null
        if (!startProvided || !endProvided) {
            return null to "Time window needs both start and end time"
        }
        val start = parseMinutes(startRaw) ?: return null to "Invalid start time"
        val end = parseMinutes(endRaw) ?: return null to "Invalid end time"
        if (start == end) {
            return null to "Start and end time cannot match"
        }
        return RuleCondition.TimeWindow(days, start, end, null) to null
    }

    private fun parseMinutes(input: String): Int? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val parts = trimmed.split(":", limit = 2)
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun formatMinutes(minutes: Int): String {
        val clamped = min(24 * 60, max(0, minutes))
        val hour = clamped / 60
        val minute = clamped % 60
        return String.format("%02d:%02d", hour, minute)
    }

    private fun parseDays(input: String): List<Int> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return emptyList()
        val tokens = trimmed.split(Regex("[\\s,]+"))
        val days = mutableListOf<Int>()
        for (token in tokens) {
            val value = token.toIntOrNull() ?: continue
            if (value in 1..7) days.add(value)
        }
        return days
    }

    private fun parseLocationIds(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val tokens = input.split(Regex("[\\s,]+"))
        val ids = mutableListOf<String>()
        for (token in tokens) {
            val trimmed = token.trim()
            if (trimmed.isBlank()) continue
            ids.add(trimmed)
        }
        return ids
    }

    private fun confirmDeleteLocation(location: com.duda.appvolumecontrol.core.LocationRuleStore.LocationRule) {
        AlertDialog.Builder(this)
            .setTitle("Delete location")
            .setMessage("Delete ${location.id}?")
            .setPositiveButton("Delete") { _, _ ->
                val updated = locationStore.listLocations().filterNot { it.id == location.id }
                locationStore.setLocations(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddLocationDialog() {
        showLocationDialog(existing = null)
    }

    private fun showEditLocationDialog(location: com.duda.appvolumecontrol.core.LocationRuleStore.LocationRule) {
        showLocationDialog(existing = location)
    }

    private fun showLocationDialog(existing: com.duda.appvolumecontrol.core.LocationRuleStore.LocationRule?) {
        val idInput = EditText(this).apply {
            hint = "Location id"
            setText(existing?.id.orEmpty())
        }
        val latInput = EditText(this).apply {
            hint = "Latitude"
            setText(existing?.latitude?.toString().orEmpty())
        }
        val lonInput = EditText(this).apply {
            hint = "Longitude"
            setText(existing?.longitude?.toString().orEmpty())
        }
        val radiusInput = EditText(this).apply {
            hint = "Radius meters"
            setText(existing?.radiusMeters?.toString().orEmpty())
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(idInput)
            addView(latInput)
            addView(lonInput)
            addView(radiusInput)
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add location" else "Edit location")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val id = idInput.text?.toString()?.trim().orEmpty()
                val lat = latInput.text?.toString()?.toDoubleOrNull()
                val lon = lonInput.text?.toString()?.toDoubleOrNull()
                val radius = radiusInput.text?.toString()?.toDoubleOrNull()
                if (id.isBlank()) {
                    toast("Location id required")
                    return@setPositiveButton
                }
                if (lat == null || lon == null || radius == null) {
                    toast("Latitude, longitude, and radius required")
                    return@setPositiveButton
                }
                if (radius <= 0.0) {
                    toast("Radius must be > 0")
                    return@setPositiveButton
                }
                val entry = com.duda.appvolumecontrol.core.LocationRuleStore.LocationRule(
                    id = id,
                    latitude = lat,
                    longitude = lon,
                    radiusMeters = radius,
                )
                val updated = if (existing == null) {
                    locationStore.listLocations() + entry
                } else {
                    locationStore.listLocations().map { stored ->
                        if (stored.id == existing.id) entry else stored
                    }
                }
                locationStore.setLocations(updated)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestLocationPermission() {
        val fine = checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            Toast.makeText(this, "Location permission already granted", Toast.LENGTH_SHORT).show()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            REQUEST_LOCATION_PERMISSION,
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
            Toast.makeText(
                this,
                if (granted) "Location permission granted" else "Location permission denied",
                Toast.LENGTH_SHORT
            ).show()
            if (granted) {
                com.duda.appvolumecontrol.service.PlaybackControllerService.requestLocationRefresh(this)
            }
        }
    }

    private fun normalizeVolume(value: Float): Float {
        return min(1f, max(0f, value))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showPackagePicker(
        title: String,
        initial: List<RuleTarget>,
        onResult: (List<RuleTarget>) -> Unit,
    ) {
        val allApps = loadInstalledApps()
        val selected = initial.map { it.packageName }.toMutableSet()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
        }
        val searchInput = EditText(this).apply {
            hint = "Search apps"
        }
        val selectedTitle = TextView(this).apply {
            text = "Selected"
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }
        val selectedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val resultsTitle = TextView(this).apply {
            text = "Results"
            textSize = 13f
            setPadding(0, 8, 0, 8)
        }
        val resultsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            addView(resultsContainer)
        }

        root.addView(searchInput)
        root.addView(selectedTitle)
        root.addView(selectedContainer)
        root.addView(resultsTitle)
        root.addView(scroll)

        val dialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(root)
            .setPositiveButton("Use selection") { _, _ ->
                val targets = selected.sorted().map { RuleTarget(it, null) }
                onResult(targets)
            }
            .setNegativeButton("Cancel", null)
            .create()

        val renderer = object {
            fun renderSelected() {
                selectedContainer.removeAllViews()
                if (selected.isEmpty()) {
                    selectedContainer.addView(buildEmptyRow("None selected."))
                    return
                }
                for (pkg in selected.sorted()) {
                    val row = LinearLayout(this@RulesActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 4, 0, 4)
                    }
                    val name = TextView(this@RulesActivity).apply {
                        text = resolveLabel(pkg)
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val remove = Button(this@RulesActivity).apply {
                        text = "Remove"
                        setOnClickListener {
                            selected.remove(pkg)
                            renderSelected()
                            renderResults()
                        }
                    }
                    row.addView(name)
                    row.addView(remove)
                    selectedContainer.addView(row)
                }
            }

            fun renderResults() {
                resultsContainer.removeAllViews()
                val query = searchInput.text?.toString()?.trim()?.lowercase().orEmpty()
                val filtered = if (query.isBlank()) {
                    allApps
                } else {
                    allApps.filter {
                        it.label.lowercase().contains(query) || it.packageName.lowercase().contains(query)
                    }
                }
                if (filtered.isEmpty()) {
                    resultsContainer.addView(buildEmptyRow("No matching apps."))
                    return
                }
                for (entry in filtered.sortedBy { it.label.lowercase() }) {
                    val row = LinearLayout(this@RulesActivity).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(0, 4, 0, 4)
                    }
                    val name = TextView(this@RulesActivity).apply {
                        text = entry.label
                        textSize = 13f
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    val action = Button(this@RulesActivity).apply {
                        val isSelected = selected.contains(entry.packageName)
                        text = if (isSelected) "Remove" else "Add"
                        setOnClickListener {
                            if (selected.contains(entry.packageName)) {
                                selected.remove(entry.packageName)
                            } else {
                                selected.add(entry.packageName)
                            }
                            renderSelected()
                            renderResults()
                        }
                    }
                    row.addView(name)
                    row.addView(action)
                    resultsContainer.addView(row)
                }
            }
        }

        searchInput.doAfterTextChanged { renderer.renderResults() }
        renderer.renderSelected()
        renderer.renderResults()
        dialog.show()
    }

    private fun loadInstalledApps(): List<AppEntry> {
        val cached = installedAppsCache
        if (cached != null) return cached
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val packages = packageManager.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
        val entries = packages.map { pkg ->
            AppEntry(pkg, resolveLabel(pkg))
        }
        installedAppsCache = entries
        return entries
    }

    private fun resolveLabel(packageName: String): String {
        return try {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info)?.toString() ?: packageName
        } catch (_: Exception) {
            packageName
        }
    }

    private data class AppEntry(
        val packageName: String,
        val label: String,
    )

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 2101
    }
}
