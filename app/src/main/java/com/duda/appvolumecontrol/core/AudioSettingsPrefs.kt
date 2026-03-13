package com.duda.appvolumecontrol.core

import android.content.Context

class AudioSettingsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isAutoResumeEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_RESUME, true)
    }

    fun setAutoResumeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RESUME, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "audio_settings"
        private const val KEY_AUTO_RESUME = "auto_resume"
    }
}
