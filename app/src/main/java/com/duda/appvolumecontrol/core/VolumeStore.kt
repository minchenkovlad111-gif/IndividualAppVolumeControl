package com.duda.appvolumecontrol.core

import android.content.Context
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

class VolumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("volume_store", Context.MODE_PRIVATE)

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
        editor.apply()
        return count
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

    companion object {
        private const val MARKER_PREFIX = "_markers:"
        private const val OUTPUT_PREFIX = "_output:"

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
    }
}
