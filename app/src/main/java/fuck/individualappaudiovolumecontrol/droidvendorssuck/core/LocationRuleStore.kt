package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

class LocationRuleStore(context: Context) {
    private val store = DeferredPreferenceStore(context, PREFS_NAME)

    fun setCurrentLocationId(id: String?) {
        val trimmed = id?.trim().orEmpty()
        if (trimmed.isBlank()) {
            store.update { remove(KEY_CURRENT_ID) }
        } else {
            store.update { put(KEY_CURRENT_ID, trimmed) }
        }
    }

    fun getCurrentLocationId(): String? {
        return store.getString(KEY_CURRENT_ID)
    }

    fun setLastFixTime(timeMs: Long) {
        if (timeMs <= 0L) return
        store.update { put(KEY_LAST_FIX_TIME, timeMs) }
    }

    fun getLastFixTime(): Long {
        return store.getLong(KEY_LAST_FIX_TIME) ?: 0L
    }

    fun listLocations(): List<LocationRule> {
        val raw = store.getString(KEY_LOCATIONS) ?: return emptyList()
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
            store.update { remove(KEY_LOCATIONS) }
            return
        }
        val array = JSONArray()
        for (entry in locations) {
            array.put(entry.toJson())
        }
        store.update { put(KEY_LOCATIONS, array.toString()) }
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        ) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
        ) ==
            PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun isBackgroundUpdatesEnabled(): Boolean {
        return store.getBoolean(KEY_BACKGROUND_ENABLED, false)
    }

    fun setBackgroundUpdatesEnabled(enabled: Boolean) {
        store.update { put(KEY_BACKGROUND_ENABLED, enabled) }
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
        private const val KEY_BACKGROUND_ENABLED = "background_updates_enabled"
    }
}
