package com.duda.appvolumecontrol.core

import android.content.Context

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

    companion object {
        private const val PREFS_NAME = "location_rules"
        private const val KEY_CURRENT_ID = "current_location_id"
    }
}
