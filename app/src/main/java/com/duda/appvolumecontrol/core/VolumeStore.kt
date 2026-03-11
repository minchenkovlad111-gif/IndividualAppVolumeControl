package com.duda.appvolumecontrol.core

import android.content.Context
import kotlin.math.max
import kotlin.math.min
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
        prefs.edit().remove(buildKey(packageName, userId)).apply()
    }

    fun exportJson(): String {
        val obj = JSONObject()
        obj.put("_version", 1)
        obj.put("_exported_at", System.currentTimeMillis())
        obj.put("_preset_count", prefs.all.size)
        val keys = prefs.all.keys
            .filterNot { it.startsWith("_") }
            .sorted()
        for (key in keys) {
            val value = prefs.all[key]
            if (value is Float && !value.isNaN()) {
                obj.put(key, value)
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
        editor.apply()
        return count
    }

    data class VolumeKey(
        val packageName: String,
        val userId: Int? = null,
    ) {
        fun storageKey(): String = buildKey(packageName, userId)
    }

    companion object {
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
    }
}
