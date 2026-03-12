package com.duda.appvolumecontrol.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

object RulesEngine {
    fun computeOverrides(
        activeKeys: Set<VolumeStore.VolumeKey>,
        groups: List<RuleGroup>,
        rules: List<Rule>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Map<VolumeStore.VolumeKey, Float> {
        if (activeKeys.isEmpty() || rules.isEmpty()) return emptyMap()
        val groupMap = groups.associateBy { it.id }
        val activeByPackage = activeKeys.groupBy { it.packageName }
        val overrides = mutableMapOf<VolumeStore.VolumeKey, Float>()
        val orderedRules = rules
            .filter { it.enabled }
            .sortedWith(compareByDescending<Rule> { it.priority }.thenBy { it.id })
        for (rule in orderedRules) {
            if (!conditionsMatch(rule.conditions, activeKeys, activeByPackage, now)) continue
            for (action in rule.actions) {
                when (action) {
                    is RuleAction.SetVolume -> {
                        applySetVolume(
                            action,
                            activeKeys,
                            activeByPackage,
                            groupMap,
                            overrides,
                        )
                    }
                }
            }
        }
        return overrides
    }

    private fun conditionsMatch(
        conditions: List<RuleCondition>,
        activeKeys: Set<VolumeStore.VolumeKey>,
        activeByPackage: Map<String, List<VolumeStore.VolumeKey>>,
        now: ZonedDateTime,
    ): Boolean {
        if (conditions.isEmpty()) return false
        for (condition in conditions) {
            val match = when (condition) {
                is RuleCondition.AllActive -> {
                    condition.targets.all { target ->
                        isTargetActive(target, activeKeys, activeByPackage)
                    }
                }
                is RuleCondition.AnyActive -> {
                    condition.targets.any { target ->
                        isTargetActive(target, activeKeys, activeByPackage)
                    }
                }
                is RuleCondition.TimeWindow -> isWithinTimeWindow(condition, now)
                is RuleCondition.Location -> false // Location rules need a provider before enabling.
            }
            if (!match) return false
        }
        return true
    }

    private fun isWithinTimeWindow(condition: RuleCondition.TimeWindow, now: ZonedDateTime): Boolean {
        val zone = condition.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        val zoned = now.withZoneSameInstant(zone)
        val dayValue = zoned.dayOfWeek.value
        if (condition.days.isNotEmpty() && dayValue !in condition.days) return false
        val minutes = zoned.hour * 60 + zoned.minute
        val start = condition.startMinute
        val end = condition.endMinute
        if (start == end) return false
        return if (start < end) {
            minutes in start until end
        } else {
            minutes >= start || minutes < end
        }
    }

    private fun isTargetActive(
        target: RuleTarget,
        activeKeys: Set<VolumeStore.VolumeKey>,
        activeByPackage: Map<String, List<VolumeStore.VolumeKey>>,
    ): Boolean {
        if (target.userId == null) {
            return activeByPackage[target.packageName].orEmpty().isNotEmpty()
        }
        return activeKeys.any { it.packageName == target.packageName && it.userId == target.userId }
    }

    private fun applySetVolume(
        action: RuleAction.SetVolume,
        activeKeys: Set<VolumeStore.VolumeKey>,
        activeByPackage: Map<String, List<VolumeStore.VolumeKey>>,
        groupMap: Map<String, RuleGroup>,
        overrides: MutableMap<VolumeStore.VolumeKey, Float>,
    ) {
        val volume = normalizeVolume(action.volume)
        val targets = resolveTargets(action.targets, groupMap)
        for (target in targets) {
            val keys = resolveActiveKeys(target, activeKeys, activeByPackage)
            for (key in keys) {
                if (!overrides.containsKey(key)) {
                    overrides[key] = volume
                }
            }
        }
    }

    private fun resolveTargets(
        targets: List<RuleTargetRef>,
        groupMap: Map<String, RuleGroup>,
    ): List<RuleTarget> {
        if (targets.isEmpty()) return emptyList()
        val resolved = mutableListOf<RuleTarget>()
        for (target in targets) {
            when (target) {
                is RuleTargetRef.Package -> resolved.add(target.target)
                is RuleTargetRef.Group -> {
                    val group = groupMap[target.groupId] ?: continue
                    resolved.addAll(group.packages)
                }
            }
        }
        return resolved
    }

    private fun resolveActiveKeys(
        target: RuleTarget,
        activeKeys: Set<VolumeStore.VolumeKey>,
        activeByPackage: Map<String, List<VolumeStore.VolumeKey>>,
    ): List<VolumeStore.VolumeKey> {
        return if (target.userId == null) {
            activeByPackage[target.packageName].orEmpty()
        } else {
            activeKeys.filter { it.packageName == target.packageName && it.userId == target.userId }
        }
    }

    private fun normalizeVolume(volume: Float): Float {
        return min(1f, max(0f, volume))
    }
}
