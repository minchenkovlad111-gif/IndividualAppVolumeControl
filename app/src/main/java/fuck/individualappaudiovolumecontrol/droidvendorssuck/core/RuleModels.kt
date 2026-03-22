package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

sealed class RuleCondition {
    data class AllActive(val targets: List<RuleTarget>) : RuleCondition()
    data class AnyActive(val targets: List<RuleTarget>) : RuleCondition()
    data class TimeWindow(
        val days: List<Int>,
        val startMinute: Int,
        val endMinute: Int,
        val timeZone: String?,
    ) : RuleCondition()
    data class Location(val locationIds: List<String>) : RuleCondition()

    fun toJson(): JSONObject {
        val obj = JSONObject()
        when (this) {
            is AllActive -> {
                obj.put("type", "all_active")
                obj.put("packages", targetsToJsonArray(targets))
            }
            is AnyActive -> {
                obj.put("type", "any_active")
                obj.put("packages", targetsToJsonArray(targets))
            }
            is TimeWindow -> {
                obj.put("type", "time_window")
                obj.put("days", JSONArray(days))
                obj.put("start_min", startMinute)
                obj.put("end_min", endMinute)
                if (!timeZone.isNullOrBlank()) {
                    obj.put("tz", timeZone)
                }
            }
            is Location -> {
                obj.put("type", "location")
                obj.put("ids", JSONArray(locationIds))
            }
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): RuleCondition? {
            return when (obj.optString("type")) {
                "all_active" -> {
                    val targets = parseTargets(obj.optJSONArray("packages"))
                    if (targets.isEmpty()) null else AllActive(targets)
                }
                "any_active" -> {
                    val targets = parseTargets(obj.optJSONArray("packages"))
                    if (targets.isEmpty()) null else AnyActive(targets)
                }
                "time_window" -> {
                    val days = parseDays(obj.optJSONArray("days"))
                    val start = obj.optInt("start_min", -1)
                    val end = obj.optInt("end_min", -1)
                    if (start !in 0..1440 || end !in 0..1440) return null
                    val tz = obj.optString("tz").takeIf { it.isNotBlank() }
                    TimeWindow(days, start, end, tz)
                }
                "location" -> {
                    val ids = parseStringList(obj.optJSONArray("ids"))
                    if (ids.isEmpty()) null else Location(ids)
                }
                else -> null
            }
        }

        private fun parseTargets(array: JSONArray?): List<RuleTarget> {
            if (array == null) return emptyList()
            val targets = mutableListOf<RuleTarget>()
            for (i in 0 until array.length()) {
                val entry = array.optString(i)
                val target = RuleTarget.parse(entry) ?: continue
                targets.add(target)
            }
            return targets
        }

        private fun targetsToJsonArray(targets: List<RuleTarget>): JSONArray {
            val arr = JSONArray()
            for (target in targets) {
                arr.put(target.storageKey())
            }
            return arr
        }

        private fun parseDays(array: JSONArray?): List<Int> {
            if (array == null) return emptyList()
            val days = mutableListOf<Int>()
            for (i in 0 until array.length()) {
                val value = array.optInt(i, -1)
                if (value in 1..7) days.add(value)
            }
            return days
        }

        private fun parseStringList(array: JSONArray?): List<String> {
            if (array == null) return emptyList()
            val values = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) values.add(value)
            }
            return values
        }
    }
}

sealed class RuleAction {
    data class SetVolume(
        val targets: List<RuleTargetRef>,
        val volume: Float,
    ) : RuleAction()

    data class AudioFocus(
        val targets: List<RuleTargetRef>,
        val mode: AudioFocusMode,
        val duckVolume: Float?,
    ) : RuleAction()

    enum class AudioFocusMode(val id: String) {
        PAUSE("pause"),
        DUCK("duck"),
        MUTE("mute"),
        MUTE_PAUSE("mute_pause");

        companion object {
            fun parse(value: String?): AudioFocusMode? {
                if (value.isNullOrBlank()) return null
                return entries.firstOrNull { it.id.equals(value, ignoreCase = true) }
            }
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        when (this) {
            is SetVolume -> {
                obj.put("type", "set_volume")
                obj.put("targets", targetsToJsonArray(targets))
                obj.put("volume", volume)
            }
            is AudioFocus -> {
                obj.put("type", "audio_focus")
                obj.put("targets", targetsToJsonArray(targets))
                obj.put("mode", mode.id)
                if (duckVolume != null) {
                    obj.put("duck", duckVolume)
                }
            }
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): RuleAction? {
            return when (obj.optString("type")) {
                "set_volume" -> {
                    val targets = parseTargetRefs(obj.optJSONArray("targets"))
                    if (targets.isEmpty()) return null
                    val volume = obj.optDouble("volume", Double.NaN)
                    if (volume.isNaN()) return null
                    SetVolume(targets, normalizeVolume(volume.toFloat()))
                }
                "audio_focus" -> {
                    val targets = parseTargetRefs(obj.optJSONArray("targets"))
                    if (targets.isEmpty()) return null
                    val mode = AudioFocusMode.parse(obj.optString("mode")) ?: return null
                    val duck = if (obj.has("duck")) {
                        normalizeVolume(obj.optDouble("duck", 0.2).toFloat())
                    } else {
                        null
                    }
                    AudioFocus(targets, mode, duck)
                }
                else -> null
            }
        }

        private fun parseTargetRefs(array: JSONArray?): List<RuleTargetRef> {
            if (array == null) return emptyList()
            val targets = mutableListOf<RuleTargetRef>()
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                val target = RuleTargetRef.parse(value) ?: continue
                targets.add(target)
            }
            return targets
        }

        private fun targetsToJsonArray(targets: List<RuleTargetRef>): JSONArray {
            val arr = JSONArray()
            for (target in targets) {
                arr.put(target.toStorageValue())
            }
            return arr
        }

        private fun normalizeVolume(volume: Float): Float {
            return min(1f, max(0f, volume))
        }
    }
}

data class Rule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val priority: Int,
    val conditions: List<RuleCondition>,
    val actions: List<RuleAction>,
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("id", id)
        obj.put("name", name)
        obj.put("enabled", enabled)
        obj.put("priority", priority)
        val conditionsArray = JSONArray()
        for (condition in conditions) {
            conditionsArray.put(condition.toJson())
        }
        obj.put("conditions", conditionsArray)
        val actionsArray = JSONArray()
        for (action in actions) {
            actionsArray.put(action.toJson())
        }
        obj.put("actions", actionsArray)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): Rule? {
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) return null
            val enabled = obj.optBoolean("enabled", true)
            val priority = obj.optInt("priority", 0)
            val conditions = mutableListOf<RuleCondition>()
            val conditionArray = obj.optJSONArray("conditions")
            if (conditionArray != null) {
                for (i in 0 until conditionArray.length()) {
                    val conditionObj = conditionArray.optJSONObject(i) ?: continue
                    val condition = RuleCondition.fromJson(conditionObj) ?: continue
                    conditions.add(condition)
                }
            }
            val actions = mutableListOf<RuleAction>()
            val actionArray = obj.optJSONArray("actions")
            if (actionArray != null) {
                for (i in 0 until actionArray.length()) {
                    val actionObj = actionArray.optJSONObject(i) ?: continue
                    val action = RuleAction.fromJson(actionObj) ?: continue
                    actions.add(action)
                }
            }
            if (conditions.isEmpty() || actions.isEmpty()) return null
            return Rule(id, name, enabled, priority, conditions, actions)
        }
    }
}

data class RuleGroup(
    val id: String,
    val name: String,
    val packages: List<RuleTarget>,
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("id", id)
        obj.put("name", name)
        val packagesArray = JSONArray()
        for (target in packages) {
            packagesArray.put(target.storageKey())
        }
        obj.put("packages", packagesArray)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): RuleGroup? {
            val id = obj.optString("id")
            val name = obj.optString("name")
            if (id.isBlank() || name.isBlank()) return null
            val packagesArray = obj.optJSONArray("packages") ?: return null
            val packages = mutableListOf<RuleTarget>()
            for (i in 0 until packagesArray.length()) {
                val entry = packagesArray.optString(i)
                val target = RuleTarget.parse(entry) ?: continue
                packages.add(target)
            }
            return RuleGroup(id, name, packages)
        }
    }
}

data class RuleTarget(
    val packageName: String,
    val userId: Int?,
) {
    fun storageKey(): String {
        return if (userId == null) packageName else "u${userId}:${packageName}"
    }

    companion object {
        fun parse(storageKey: String): RuleTarget? {
            if (storageKey.isBlank()) return null
            if (storageKey.startsWith("u") && storageKey.contains(":")) {
                val parts = storageKey.split(":", limit = 2)
                val userPart = parts.getOrNull(0)?.removePrefix("u")
                val userId = userPart?.toIntOrNull() ?: return null
                val pkg = parts.getOrNull(1).orEmpty()
                if (pkg.isBlank()) return null
                return RuleTarget(pkg, userId)
            }
            return RuleTarget(storageKey, null)
        }
    }
}

sealed class RuleTargetRef {
    data class Package(val target: RuleTarget) : RuleTargetRef()
    data class Group(val groupId: String) : RuleTargetRef()

    fun toStorageValue(): String {
        return when (this) {
            is Package -> target.storageKey()
            is Group -> "group:${groupId}"
        }
    }

    companion object {
        fun parse(value: String): RuleTargetRef? {
            if (value.isBlank()) return null
            if (value.startsWith("group:")) {
                val groupId = value.removePrefix("group:").trim()
                if (groupId.isBlank()) return null
                return Group(groupId)
            }
            val target = RuleTarget.parse(value) ?: return null
            return Package(target)
        }
    }
}
