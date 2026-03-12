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
import androidx.appcompat.app.AlertDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeStore = VolumeStore(this)

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

        groupContainer.removeAllViews()
        ruleContainer.removeAllViews()

        if (groups.isEmpty()) {
            groupContainer.addView(buildEmptyRow("No groups yet"))
        } else {
            for (group in groups) {
                groupContainer.addView(buildGroupRow(group))
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(nameInput)
            addView(packagesInput)
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
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 0)
            addView(nameInput)
            addView(packagesInput)
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
        val daysInput = EditText(this).apply {
            hint = "Time days (1-7, optional)"
        }
        val startInput = EditText(this).apply {
            hint = "Start time (HH:MM, optional)"
        }
        val endInput = EditText(this).apply {
            hint = "End time (HH:MM, optional)"
        }
        val groupTargetInput = EditText(this).apply {
            hint = "Target group id (optional)"
        }
        val targetPackagesInput = EditText(this).apply {
            hint = "Target packages (comma separated if no group id)"
            minLines = 2
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
            addView(daysInput)
            addView(startInput)
            addView(endInput)
            addView(groupTargetInput)
            addView(groupHint)
            addView(targetPackagesInput)
            addView(volumeInput)
        }

        if (existing != null) {
            val anyActive = existing.conditions.filterIsInstance<RuleCondition.AnyActive>().firstOrNull()
            if (anyActive != null) {
                anyActiveInput.setText(anyActive.targets.joinToString(", ") { it.storageKey() })
            }
            val timeWindow = existing.conditions.filterIsInstance<RuleCondition.TimeWindow>().firstOrNull()
            if (timeWindow != null) {
                daysInput.setText(timeWindow.days.joinToString(","))
                startInput.setText(formatMinutes(timeWindow.startMinute))
                endInput.setText(formatMinutes(timeWindow.endMinute))
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
                val timeWindow = parseTimeWindow(
                    daysInput.text?.toString().orEmpty(),
                    startInput.text?.toString().orEmpty(),
                    endInput.text?.toString().orEmpty(),
                )
                if (timeWindow != null) {
                    conditions.add(timeWindow)
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

    private fun parseTimeWindow(daysRaw: String, startRaw: String, endRaw: String): RuleCondition.TimeWindow? {
        val start = parseMinutes(startRaw)
        val end = parseMinutes(endRaw)
        if (start == null || end == null) return null
        val days = parseDays(daysRaw)
        return RuleCondition.TimeWindow(days, start, end, null)
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

    private fun normalizeVolume(value: Float): Float {
        return min(1f, max(0f, value))
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
