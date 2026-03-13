package com.duda.appvolumecontrol.core

import android.content.Context
import android.media.AudioAttributes
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

class VolumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("volume_store", Context.MODE_PRIVATE)

    enum class MuteBehavior(val id: String) {
        MUTE_ONLY("mute_only"),
        MUTE_AND_PAUSE("mute_pause");

        companion object {
            fun parse(value: String?): MuteBehavior? {
                if (value.isNullOrBlank()) return null
                return entries.firstOrNull { it.id.equals(value, ignoreCase = true) }
            }
        }
    }

    fun setVolume(packageName: String, volume: Float, userId: Int? = null) {
        if (volume.isNaN()) return
        val normalized = min(1f, max(0f, volume))
        prefs.edit().putFloat(buildKey(packageName, userId), normalized).apply()
    }

    fun setVolume(key: VolumeKey, volume: Float) {
        setVolume(key.packageName, volume, key.userId)
    }

    fun getVolume(packageName: String, userId: Int? = null): Float? {
        val key = buildKey(packageName, userId)
        if (prefs.contains(key)) {
            val stored = prefs.getFloat(key, 1f)
            return if (stored.isNaN()) null else stored
        }
        if (userId != null && prefs.contains(packageName)) {
            val stored = prefs.getFloat(packageName, 1f)
            return if (stored.isNaN()) null else stored
        }
        return null
    }

    fun getVolume(key: VolumeKey): Float? {
        return getVolume(key.packageName, key.userId)
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun countPresets(): Int {
        return prefs.all.keys.count { !it.startsWith("_") }
    }

    fun listPresetEntries(): List<VolumeKey> {
        return prefs.all.keys
            .filterNot { it.startsWith("_") }
            .mapNotNull { parseKey(it) }
            .sortedWith(compareBy({ it.userId ?: -1 }, { it.packageName }))
    }

    fun listPresetPackages(): List<String> {
        return listPresetEntries().map { it.storageKey() }
    }

    fun removePreset(packageName: String, userId: Int? = null) {
        val key = VolumeKey(packageName, userId)
        prefs.edit()
            .remove(buildKey(packageName, userId))
            .remove(markerPrefsKey(key))
            .remove(outputPrefsKey(key))
            .remove(mutePrefsKey(key))
            .remove(attrPrefsKey(key))
            .apply()
    }

    fun getMarkerConfig(key: VolumeKey): MarkerConfig? {
        val raw = prefs.getString(markerPrefsKey(key), null)
        val parsed = parseMarkerConfig(raw)
        if (parsed != null) return parsed
        if (key.userId != null) {
            val fallback = prefs.getString(markerPrefsKey(VolumeKey(key.packageName, null)), null)
            return parseMarkerConfig(fallback)
        }
        return null
    }

    fun setMarkerConfig(key: VolumeKey, config: MarkerConfig) {
        if (config.isEmpty()) {
            clearMarkerConfig(key)
            return
        }
        prefs.edit().putString(markerPrefsKey(key), config.toJson().toString()).apply()
    }

    fun clearMarkerConfig(key: VolumeKey) {
        prefs.edit().remove(markerPrefsKey(key)).apply()
    }

    fun getOutputDevice(key: VolumeKey): OutputDevice? {
        val raw = prefs.getString(outputPrefsKey(key), null)
        val parsed = parseOutputDevice(raw)
        if (parsed != null) return parsed
        if (key.userId != null) {
            val fallback = prefs.getString(outputPrefsKey(VolumeKey(key.packageName, null)), null)
            return parseOutputDevice(fallback)
        }
        return null
    }

    fun setOutputDevice(key: VolumeKey, device: OutputDevice?) {
        if (device == null) {
            clearOutputDevice(key)
            return
        }
        prefs.edit().putString(outputPrefsKey(key), device.toJson().toString()).apply()
    }

    fun clearOutputDevice(key: VolumeKey) {
        prefs.edit().remove(outputPrefsKey(key)).apply()
    }

    fun getAudioAttributesOverride(key: VolumeKey): AudioAttributesOverride? {
        val raw = prefs.getString(attrPrefsKey(key), null)
        val parsed = parseAudioAttributesOverride(raw)
        if (parsed != null) return parsed
        if (key.userId != null) {
            val fallback = prefs.getString(attrPrefsKey(VolumeKey(key.packageName, null)), null)
            return parseAudioAttributesOverride(fallback)
        }
        return null
    }

    fun setAudioAttributesOverride(key: VolumeKey, override: AudioAttributesOverride?) {
        if (override == null || override.isEmpty()) {
            clearAudioAttributesOverride(key)
            return
        }
        prefs.edit().putString(attrPrefsKey(key), override.toJson().toString()).apply()
    }

    fun clearAudioAttributesOverride(key: VolumeKey) {
        prefs.edit().remove(attrPrefsKey(key)).apply()
    }

    fun countAudioAttributesOverrides(): Int {
        return prefs.all.keys.count { it.startsWith(ATTR_PREFIX) }
    }

    fun getMuteBehavior(key: VolumeKey): MuteBehavior {
        val raw = prefs.getString(mutePrefsKey(key), null)
        val parsed = MuteBehavior.parse(raw)
        if (parsed != null) return parsed
        if (key.userId != null) {
            val fallback = prefs.getString(mutePrefsKey(VolumeKey(key.packageName, null)), null)
            return MuteBehavior.parse(fallback) ?: getDefaultMuteBehavior()
        }
        return getDefaultMuteBehavior()
    }

    fun setMuteBehavior(key: VolumeKey, behavior: MuteBehavior) {
        prefs.edit().putString(mutePrefsKey(key), behavior.id).apply()
    }

    fun clearMuteBehavior(key: VolumeKey) {
        prefs.edit().remove(mutePrefsKey(key)).apply()
    }

    fun countMuteBehaviorOverrides(): Int {
        return prefs.all.keys.count { it.startsWith(MUTE_PREFIX) }
    }

    fun getAutoResumeOverride(key: VolumeKey): Boolean? {
        val raw = prefs.getString(resumePrefsKey(key), null)
        val parsed = parseBooleanOverride(raw)
        if (parsed != null) return parsed
        if (key.userId != null) {
            val fallback = prefs.getString(resumePrefsKey(VolumeKey(key.packageName, null)), null)
            return parseBooleanOverride(fallback)
        }
        return null
    }

    fun setAutoResumeOverride(key: VolumeKey, enabled: Boolean?) {
        if (enabled == null) {
            clearAutoResumeOverride(key)
            return
        }
        prefs.edit().putString(resumePrefsKey(key), if (enabled) "on" else "off").apply()
    }

    fun clearAutoResumeOverride(key: VolumeKey) {
        prefs.edit().remove(resumePrefsKey(key)).apply()
    }

    fun countAutoResumeOverrides(): Int {
        return prefs.all.keys.count { it.startsWith(RESUME_PREFIX) }
    }

    fun getDefaultMuteBehavior(): MuteBehavior {
        val raw = prefs.getString(DEFAULT_MUTE_KEY, null)
        return MuteBehavior.parse(raw) ?: MuteBehavior.MUTE_ONLY
    }

    fun setDefaultMuteBehavior(behavior: MuteBehavior) {
        prefs.edit().putString(DEFAULT_MUTE_KEY, behavior.id).apply()
    }

    fun exportJson(): String {
        val obj = JSONObject()
        obj.put("_version", 1)
        obj.put("_exported_at", System.currentTimeMillis())
        obj.put("_preset_count", countPresets())
        val keys = prefs.all.keys
            .filterNot { it.startsWith("_") }
            .sorted()
        for (key in keys) {
            val value = prefs.all[key]
            if (value is Float && !value.isNaN()) {
                obj.put(key, value)
            }
        }
        val markerEntries = prefs.all.keys
            .filter { it.startsWith(MARKER_PREFIX) }
            .sorted()
        if (markerEntries.isNotEmpty()) {
            val markerObj = JSONObject()
            for (markerKey in markerEntries) {
                val storedKey = markerKey.removePrefix(MARKER_PREFIX)
                val raw = prefs.getString(markerKey, null) ?: continue
                val config = parseMarkerConfig(raw) ?: continue
                markerObj.put(storedKey, config.toJson())
            }
            if (markerObj.length() > 0) {
                obj.put("_marker_configs", markerObj)
            }
        }
        val outputEntries = prefs.all.keys
            .filter { it.startsWith(OUTPUT_PREFIX) }
            .sorted()
        if (outputEntries.isNotEmpty()) {
            val outputObj = JSONObject()
            for (outputKey in outputEntries) {
                val storedKey = outputKey.removePrefix(OUTPUT_PREFIX)
                val raw = prefs.getString(outputKey, null) ?: continue
                val config = parseOutputDevice(raw) ?: continue
                outputObj.put(storedKey, config.toJson())
            }
            if (outputObj.length() > 0) {
                obj.put("_output_configs", outputObj)
            }
        }
        val muteEntries = prefs.all.keys
            .filter { it.startsWith(MUTE_PREFIX) }
            .sorted()
        if (muteEntries.isNotEmpty()) {
            val muteObj = JSONObject()
            for (muteKey in muteEntries) {
                val storedKey = muteKey.removePrefix(MUTE_PREFIX)
                val raw = prefs.getString(muteKey, null)
                val behavior = MuteBehavior.parse(raw) ?: continue
                muteObj.put(storedKey, behavior.id)
            }
            if (muteObj.length() > 0) {
                obj.put("_mute_behaviors", muteObj)
            }
        }
        val resumeEntries = prefs.all.keys
            .filter { it.startsWith(RESUME_PREFIX) }
            .sorted()
        if (resumeEntries.isNotEmpty()) {
            val resumeObj = JSONObject()
            for (resumeKey in resumeEntries) {
                val storedKey = resumeKey.removePrefix(RESUME_PREFIX)
                val raw = prefs.getString(resumeKey, null)
                val override = parseBooleanOverride(raw) ?: continue
                resumeObj.put(storedKey, if (override) "on" else "off")
            }
            if (resumeObj.length() > 0) {
                obj.put("_resume_overrides", resumeObj)
            }
        }
        val attrEntries = prefs.all.keys
            .filter { it.startsWith(ATTR_PREFIX) }
            .sorted()
        if (attrEntries.isNotEmpty()) {
            val attrObj = JSONObject()
            for (attrKey in attrEntries) {
                val storedKey = attrKey.removePrefix(ATTR_PREFIX)
                val raw = prefs.getString(attrKey, null) ?: continue
                val config = parseAudioAttributesOverride(raw) ?: continue
                attrObj.put(storedKey, config.toJson())
            }
            if (attrObj.length() > 0) {
                obj.put("_attr_overrides", attrObj)
            }
        }
        val defaultMute = getDefaultMuteBehavior()
        if (defaultMute != MuteBehavior.MUTE_ONLY) {
            obj.put("_mute_default", defaultMute.id)
        }
        val ruleGroups = listRuleGroups()
        if (ruleGroups.isNotEmpty()) {
            val groupArray = JSONArray()
            for (group in ruleGroups) {
                groupArray.put(group.toJson())
            }
            obj.put("_rule_groups", groupArray)
        }
        val rules = listRules()
        if (rules.isNotEmpty()) {
            val ruleArray = JSONArray()
            for (rule in rules) {
                ruleArray.put(rule.toJson())
            }
            obj.put("_rules", ruleArray)
        }
        return obj.toString(2)
    }

    fun importJson(json: String): Int {
        val obj = JSONObject(json)
        val editor = prefs.edit()
        var count = 0
        for (key in obj.keys()) {
            if (key.startsWith("_")) continue
            val value = obj.optDouble(key, Double.NaN)
            if (!value.isNaN()) {
                val normalized = min(1f, max(0f, value.toFloat()))
                editor.putFloat(key, normalized)
                count += 1
            }
        }
        val markerObj = obj.optJSONObject("_marker_configs")
        if (markerObj != null) {
            val keys = markerObj.keys()
            while (keys.hasNext()) {
                val storageKey = keys.next()
                if (storageKey.isBlank()) continue
                val raw = markerObj.optJSONObject(storageKey) ?: continue
                val config = parseMarkerConfig(raw.toString()) ?: continue
                editor.putString(MARKER_PREFIX + storageKey, config.toJson().toString())
            }
        }
        val outputObj = obj.optJSONObject("_output_configs")
        if (outputObj != null) {
            val keys = outputObj.keys()
            while (keys.hasNext()) {
                val storageKey = keys.next()
                if (storageKey.isBlank()) continue
                val raw = outputObj.optJSONObject(storageKey) ?: continue
                val config = parseOutputDevice(raw) ?: continue
                editor.putString(OUTPUT_PREFIX + storageKey, config.toJson().toString())
            }
        }
        val muteObj = obj.optJSONObject("_mute_behaviors")
        if (muteObj != null) {
            val keys = muteObj.keys()
            while (keys.hasNext()) {
                val storageKey = keys.next()
                if (storageKey.isBlank()) continue
                val raw = muteObj.optString(storageKey)
                val behavior = MuteBehavior.parse(raw) ?: continue
                editor.putString(MUTE_PREFIX + storageKey, behavior.id)
            }
        }
        val resumeObj = obj.optJSONObject("_resume_overrides")
        if (resumeObj != null) {
            val keys = resumeObj.keys()
            while (keys.hasNext()) {
                val storageKey = keys.next()
                if (storageKey.isBlank()) continue
                val raw = resumeObj.optString(storageKey)
                val override = parseBooleanOverride(raw) ?: continue
                editor.putString(RESUME_PREFIX + storageKey, if (override) "on" else "off")
            }
        }
        val attrObj = obj.optJSONObject("_attr_overrides")
        if (attrObj != null) {
            val keys = attrObj.keys()
            while (keys.hasNext()) {
                val storageKey = keys.next()
                if (storageKey.isBlank()) continue
                val raw = attrObj.optJSONObject(storageKey) ?: continue
                val config = parseAudioAttributesOverride(raw) ?: continue
                editor.putString(ATTR_PREFIX + storageKey, config.toJson().toString())
            }
        }
        val defaultMute = MuteBehavior.parse(obj.optString("_mute_default", ""))
        if (defaultMute != null) {
            editor.putString(DEFAULT_MUTE_KEY, defaultMute.id)
        }
        val ruleGroups = parseRuleGroups(obj.optJSONArray("_rule_groups"))
        if (ruleGroups != null) {
            if (ruleGroups.isEmpty()) {
                editor.remove(RULE_GROUPS_KEY)
            } else {
                editor.putString(RULE_GROUPS_KEY, serializeRuleGroups(ruleGroups))
            }
        }
        val rules = parseRules(obj.optJSONArray("_rules"))
        if (rules != null) {
            if (rules.isEmpty()) {
                editor.remove(RULES_KEY)
            } else {
                editor.putString(RULES_KEY, serializeRules(rules))
            }
        }
        editor.apply()
        return count
    }

    fun listRuleGroups(): List<RuleGroup> {
        val raw = prefs.getString(RULE_GROUPS_KEY, null) ?: return emptyList()
        val groups = parseRuleGroups(raw) ?: return emptyList()
        return groups.sortedBy { it.name }
    }

    fun setRuleGroups(groups: List<RuleGroup>) {
        if (groups.isEmpty()) {
            prefs.edit().remove(RULE_GROUPS_KEY).apply()
            return
        }
        prefs.edit().putString(RULE_GROUPS_KEY, serializeRuleGroups(groups)).apply()
    }

    fun listRules(): List<Rule> {
        val raw = prefs.getString(RULES_KEY, null) ?: return emptyList()
        val rules = parseRules(raw) ?: return emptyList()
        return rules.sortedWith(compareByDescending<Rule> { it.priority }.thenBy { it.name })
    }

    fun setRules(rules: List<Rule>) {
        if (rules.isEmpty()) {
            prefs.edit().remove(RULES_KEY).apply()
            return
        }
        prefs.edit().putString(RULES_KEY, serializeRules(rules)).apply()
    }

    data class VolumeKey(
        val packageName: String,
        val userId: Int? = null,
    ) {
        fun storageKey(): String = buildKey(packageName, userId)
    }

    data class MarkerConfig(
        val markers: List<Marker>,
        val segments: List<Segment>,
    ) {
        data class Marker(
            val percent: Int,
            val color: String?,
        )

        data class Segment(
            val start: Int,
            val end: Int,
            val color: String?,
        )

        fun isEmpty(): Boolean = markers.isEmpty() && segments.isEmpty()

        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("version", 1)
            val markerArr = JSONArray()
            for (marker in markers) {
                val markerObj = JSONObject()
                markerObj.put("percent", marker.percent)
                if (!marker.color.isNullOrBlank()) {
                    markerObj.put("color", marker.color)
                }
                markerArr.put(markerObj)
            }
            val segmentArr = JSONArray()
            for (segment in segments) {
                val segObj = JSONObject()
                segObj.put("start", segment.start)
                segObj.put("end", segment.end)
                if (!segment.color.isNullOrBlank()) {
                    segObj.put("color", segment.color)
                }
                segmentArr.put(segObj)
            }
            obj.put("markers", markerArr)
            obj.put("segments", segmentArr)
            return obj
        }
    }

    data class OutputDevice(
        val type: Int,
        val address: String?,
        val name: String?,
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("type", type)
            if (!address.isNullOrBlank()) {
                obj.put("address", address)
            }
            if (!name.isNullOrBlank()) {
                obj.put("name", name)
            }
            return obj
        }
    }

    data class AudioAttributesOverride(
        val usage: Int?,
        val contentType: Int?,
    ) {
        fun isEmpty(): Boolean = usage == null && contentType == null

        fun matches(attributes: AudioAttributes?): Boolean {
            if (attributes == null) return false
            if (usage != null && attributes.usage != usage) return false
            if (contentType != null && attributes.contentType != contentType) return false
            return true
        }

        fun toJson(): JSONObject {
            val obj = JSONObject()
            if (usage != null) {
                obj.put("usage", usage)
            }
            if (contentType != null) {
                obj.put("content_type", contentType)
            }
            return obj
        }
    }

    companion object {
        private const val MARKER_PREFIX = "_markers:"
        private const val OUTPUT_PREFIX = "_output:"
        private const val MUTE_PREFIX = "_mute:"
        private const val RESUME_PREFIX = "_resume:"
        private const val ATTR_PREFIX = "_attr:"
        private const val DEFAULT_MUTE_KEY = "_mute_default"
        private const val RULE_GROUPS_KEY = "_rule_groups"
        private const val RULES_KEY = "_rules"

        fun buildKey(packageName: String, userId: Int?): String {
            return if (userId == null) packageName else "u${userId}:${packageName}"
        }

        fun parseKey(key: String): VolumeKey? {
            if (key.startsWith("u") && key.contains(":")) {
                val parts = key.split(":", limit = 2)
                val userPart = parts.getOrNull(0)?.removePrefix("u")
                val userId = userPart?.toIntOrNull() ?: return null
                val pkg = parts.getOrNull(1).orEmpty()
                if (pkg.isBlank()) return null
                return VolumeKey(pkg, userId)
            }
            if (key.isBlank()) return null
            return VolumeKey(key, null)
        }

        private fun markerPrefsKey(key: VolumeKey): String {
            return MARKER_PREFIX + key.storageKey()
        }

        private fun outputPrefsKey(key: VolumeKey): String {
            return OUTPUT_PREFIX + key.storageKey()
        }

        private fun mutePrefsKey(key: VolumeKey): String {
            return MUTE_PREFIX + key.storageKey()
        }

        private fun resumePrefsKey(key: VolumeKey): String {
            return RESUME_PREFIX + key.storageKey()
        }

        private fun attrPrefsKey(key: VolumeKey): String {
            return ATTR_PREFIX + key.storageKey()
        }

        private fun parseBooleanOverride(value: String?): Boolean? {
            if (value.isNullOrBlank()) return null
            return when (value.lowercase()) {
                "on", "true", "1", "yes" -> true
                "off", "false", "0", "no" -> false
                else -> null
            }
        }

        private fun parseMarkerConfig(raw: String?): MarkerConfig? {
            if (raw.isNullOrBlank()) return null
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            val markers = mutableListOf<MarkerConfig.Marker>()
            val markerArr = obj.optJSONArray("markers")
            if (markerArr != null) {
                for (i in 0 until markerArr.length()) {
                    val markerObj = markerArr.optJSONObject(i) ?: continue
                    val percent = markerObj.optInt("percent", -1)
                    if (percent < 0 || percent > 100) continue
                    val color = markerObj.optString("color").takeIf { it.isNotBlank() }
                    markers.add(MarkerConfig.Marker(percent, color))
                }
            }
            val segments = mutableListOf<MarkerConfig.Segment>()
            val segmentArr = obj.optJSONArray("segments")
            if (segmentArr != null) {
                for (i in 0 until segmentArr.length()) {
                    val segmentObj = segmentArr.optJSONObject(i) ?: continue
                    val start = segmentObj.optInt("start", -1)
                    val end = segmentObj.optInt("end", -1)
                    if (start < 0 || start > 100 || end < 0 || end > 100) continue
                    if (start == end) continue
                    val color = segmentObj.optString("color").takeIf { it.isNotBlank() }
                    val (minVal, maxVal) = if (start <= end) start to end else end to start
                    segments.add(MarkerConfig.Segment(minVal, maxVal, color))
                }
            }
            if (markers.isEmpty() && segments.isEmpty()) return null
            return MarkerConfig(markers, segments)
        }

        private fun parseOutputDevice(raw: String?): OutputDevice? {
            if (raw.isNullOrBlank()) return null
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            return parseOutputDevice(obj)
        }

        private fun parseOutputDevice(obj: JSONObject): OutputDevice? {
            val type = obj.optInt("type", -1)
            if (type <= 0) return null
            val address = obj.optString("address").takeIf { it.isNotBlank() }
            val name = obj.optString("name").takeIf { it.isNotBlank() }
            return OutputDevice(type, address, name)
        }

        private fun parseAudioAttributesOverride(raw: String?): AudioAttributesOverride? {
            if (raw.isNullOrBlank()) return null
            val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            return parseAudioAttributesOverride(obj)
        }

        private fun parseAudioAttributesOverride(obj: JSONObject): AudioAttributesOverride? {
            val usage = obj.optInt("usage", -1).takeIf { it >= 0 }
            val contentType = obj.optInt("content_type", -1).takeIf { it >= 0 }
            if (usage == null && contentType == null) return null
            return AudioAttributesOverride(usage, contentType)
        }

        private fun parseRuleGroups(raw: String?): List<RuleGroup>? {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
            return parseRuleGroups(array)
        }

        private fun parseRuleGroups(array: JSONArray?): List<RuleGroup>? {
            if (array == null) return null
            val groups = mutableListOf<RuleGroup>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val group = RuleGroup.fromJson(obj) ?: continue
                groups.add(group)
            }
            return groups
        }

        private fun parseRules(raw: String?): List<Rule>? {
            if (raw.isNullOrBlank()) return emptyList()
            val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
            return parseRules(array)
        }

        private fun parseRules(array: JSONArray?): List<Rule>? {
            if (array == null) return null
            val rules = mutableListOf<Rule>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val rule = Rule.fromJson(obj) ?: continue
                rules.add(rule)
            }
            return rules
        }

        private fun serializeRuleGroups(groups: List<RuleGroup>): String {
            val array = JSONArray()
            for (group in groups) {
                array.put(group.toJson())
            }
            return array.toString()
        }

        private fun serializeRules(rules: List<Rule>): String {
            val array = JSONArray()
            for (rule in rules) {
                array.put(rule.toJson())
            }
            return array.toString()
        }
    }
}
