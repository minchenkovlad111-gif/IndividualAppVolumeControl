package com.duda.appvolumecontrol.core

import android.content.Context
import kotlin.math.max
import kotlin.math.min

class VolumeStore(context: Context) {
    private val prefs = context.getSharedPreferences("volume_store", Context.MODE_PRIVATE)

    fun setVolume(packageName: String, volume: Float) {
        val normalized = min(1f, max(0f, volume))
        prefs.edit().putFloat(packageName, normalized).apply()
    }

    fun getVolume(packageName: String): Float? {
        return if (prefs.contains(packageName)) prefs.getFloat(packageName, 1f) else null
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun countPresets(): Int {
        return prefs.all.size
    }
}
