package com.duda.appvolumecontrol.core

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import org.json.JSONObject

class LocationRuleStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun setCurrentLocationId(id: String?) {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isBlank()) {
            prefs.edit().remove(KEY_CURRENT_ID).apply()
        } else {
            prefs.edit().putString(KEY_CURRENT_ID, trimmed).apply()
        }
    }

    fun getCurrentLocationId(): String? {
        return prefs.getString(KEY_CURRENT_ID, null)
    }

    fun setLastFixTime(timeMs: Long) {
        if (timeMs <= 0L) return
        prefs.edit().putLong(KEY_LAST_FIX_TIME, timeMs).apply()
    }

    fun getLastFixTime(): Long {
        return prefs.getLong(KEY_LAST_FIX_TIME, 0L)
    }

    fun listLocations(): List<LocationRule> {
        val raw = prefs.getString(KEY_LOCATIONS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val entries = mutableListOf<LocationRule>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val parsed = LocationRule.fromJson(obj) ?: continue
            entries.add(parsed)
        }
        return entries.sortedBy { it.id }
    }

    fun setLocations(locations: List<LocationRule>) {
        if (locations.isEmpty()) {
            prefs.edit().remove(KEY_LOCATIONS).apply()
            return
        }
        val array = JSONArray()
        for (entry in locations) {
            array.put(entry.toJson())
        }
        prefs.edit().putString(KEY_LOCATIONS, array.toString()).apply()
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    data class LocationRule(
        val id: String,
        val latitude: Double,
        val longitude: Double,
        val radiusMeters: Double,
    ) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("id", id)
            obj.put("lat", latitude)
            obj.put("lon", longitude)
            obj.put("radius_m", radiusMeters)
            return obj
        }

        companion object {
            fun fromJson(obj: JSONObject): LocationRule? {
                val id = obj.optString("id").trim()
                if (id.isBlank()) return null
                val lat = obj.optDouble("lat", Double.NaN)
                val lon = obj.optDouble("lon", Double.NaN)
                val radius = obj.optDouble("radius_m", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || radius.isNaN()) return null
                if (radius <= 0.0) return null
                return LocationRule(id, lat, lon, radius)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "location_rules"
        private const val KEY_CURRENT_ID = "current_location_id"
        private const val KEY_LAST_FIX_TIME = "last_fix_time"
        private const val KEY_LOCATIONS = "locations"
    }
}
