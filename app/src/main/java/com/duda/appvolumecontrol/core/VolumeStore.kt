package com.duda.appvolumecontrol.core

import android.content.Context
import kotlin.math.max
import kotlin.math.min
import org.json.JSONObject

class VolumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("volume_store", Context.MODE_PRIVATE)

    fun setVolume(packageName: String, volume: Float) {
        if (volume.isNaN()) return
        val normalized = min(1f, max(0f, volume))
        prefs.edit().putFloat(packageName, normalized).apply()
    }

    fun getVolume(packageName: String): Float? {
        if (!prefs.contains(packageName)) return null
        val stored = prefs.getFloat(packageName, 1f)
        return if (stored.isNaN()) null else stored
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun countPresets(): Int {
        return prefs.all.size
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
}
