package com.duda.appvolumecontrol.core

import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.max
import kotlin.math.min

object RulesEngine {
    data class RuleEffects(
        val volumeOverrides: Map<VolumeStore.VolumeKey, Float>,
        val pauseTargets: Set<VolumeStore.VolumeKey>,
    )

    @Volatile
    private var locationIdProvider: (() -> String?)? = null

    fun setLocationIdProvider(provider: (() -> String?)?) {
        locationIdProvider = provider
    }

    fun computeEffects(
        activeKeys: Set<VolumeStore.VolumeKey>,
        groups: List<RuleGroup>,
        rules: List<Rule>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): RuleEffects {
        if (activeKeys.isEmpty() || rules.isEmpty()) {
            return RuleEffects(emptyMap(), emptySet())
        }
        val groupMap = groups.associateBy { it.id }
        val activeByPackage = activeKeys.groupBy { it.packageName }
        val overrides = mutableMapOf<VolumeStore.VolumeKey, Float>()
        val pauseTargets = mutableSetOf<VolumeStore.VolumeKey>()
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
                    is RuleAction.AudioFocus -> {
                        applyAudioFocus(
                            action,
                            activeKeys,
                            activeByPackage,
                            groupMap,
                            overrides,
                            pauseTargets,
                        )
                    }
                }
            }
        }
        return RuleEffects(overrides, pauseTargets)
    }

    fun computeOverrides(
        activeKeys: Set<VolumeStore.VolumeKey>,
        groups: List<RuleGroup>,
        rules: List<Rule>,
        now: ZonedDateTime = ZonedDateTime.now(),
    ): Map<VolumeStore.VolumeKey, Float> {
        return computeEffects(activeKeys, groups, rules, now).volumeOverrides
    }

    private fun conditionsMatch(
        conditions: List<RuleCondition>,
        activeKeys: Set<VolumeStore.VolumeKey>,
        activeByPackage: Map<String, List<VolumeStore.VolumeKey>>,
        now: ZonedDateTime,
    ): Boolean {
        if (conditions.isEmpty()) return false
        val currentLocationId = locationIdProvider?.invoke()?.trim().orEmpty()
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
                is RuleCondition.Location -> {
                    if (currentLocationId.isBlank()) false
                    else condition.locationIds.any { it.equals(currentLocationId, ignoreCase = true) }
                }
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

    private fun applyAudioFocus(
        action: RuleAction.AudioFocus,
        activeKeys: Set<VolumeStore.VolumeKey>,
        activeByPackage: Map<String, List<VolumeStore.VolumeKey>>,
        groupMap: Map<String, RuleGroup>,
        overrides: MutableMap<VolumeStore.VolumeKey, Float>,
        pauseTargets: MutableSet<VolumeStore.VolumeKey>,
    ) {
        val targets = resolveTargets(action.targets, groupMap)
        val duck = action.duckVolume?.let { normalizeVolume(it) } ?: DEFAULT_DUCK_VOLUME
        for (target in targets) {
            val keys = resolveActiveKeys(target, activeKeys, activeByPackage)
            for (key in keys) {
                when (action.mode) {
                    RuleAction.AudioFocusMode.PAUSE -> pauseTargets.add(key)
                    RuleAction.AudioFocusMode.DUCK -> {
                        if (!overrides.containsKey(key)) overrides[key] = duck
                    }
                    RuleAction.AudioFocusMode.MUTE -> {
                        if (!overrides.containsKey(key)) overrides[key] = 0f
                    }
                    RuleAction.AudioFocusMode.MUTE_PAUSE -> {
                        if (!overrides.containsKey(key)) overrides[key] = 0f
                        pauseTargets.add(key)
                    }
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

    private const val DEFAULT_DUCK_VOLUME = 0.2f
}
