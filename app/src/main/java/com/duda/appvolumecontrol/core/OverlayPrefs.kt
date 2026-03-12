package com.duda.appvolumecontrol.core

import android.content.Context

class OverlayPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    fun isOverlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isAutoRefreshEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_REFRESH, true)
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
    }

    fun isOutputOnlyEnabled(): Boolean {
        return prefs.getBoolean(KEY_OUTPUT_ONLY, false)
    }

    fun setOutputOnlyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OUTPUT_ONLY, enabled).apply()
    }

    companion object {
        private const val KEY_ENABLED = "overlay_enabled"
        private const val KEY_AUTO_REFRESH = "overlay_auto_refresh"
        private const val KEY_OUTPUT_ONLY = "overlay_output_only"
    }
}
