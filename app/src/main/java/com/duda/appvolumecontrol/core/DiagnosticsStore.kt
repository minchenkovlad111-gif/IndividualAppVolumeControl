package com.duda.appvolumecontrol.core

import android.content.Context

class DiagnosticsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordLastError(message: String) {
        prefs.edit()
            .putString(KEY_LAST_ERROR, message)
            .putLong(KEY_LAST_ERROR_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordLastApply(count: Int) {
        prefs.edit()
            .putInt(KEY_LAST_APPLY_COUNT, count)
            .putLong(KEY_LAST_APPLY_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordLastActive(count: Int) {
        prefs.edit()
            .putInt(KEY_LAST_ACTIVE_COUNT, count)
            .putLong(KEY_LAST_ACTIVE_TIME, System.currentTimeMillis())
            .apply()
    }

    fun snapshot(): DiagnosticsSnapshot {
        return DiagnosticsSnapshot(
            lastError = prefs.getString(KEY_LAST_ERROR, null),
            lastErrorTime = prefs.getLong(KEY_LAST_ERROR_TIME, 0L),
            lastApplyCount = prefs.getInt(KEY_LAST_APPLY_COUNT, 0),
            lastApplyTime = prefs.getLong(KEY_LAST_APPLY_TIME, 0L),
            lastActiveCount = prefs.getInt(KEY_LAST_ACTIVE_COUNT, 0),
            lastActiveTime = prefs.getLong(KEY_LAST_ACTIVE_TIME, 0L),
        )
    }

    data class DiagnosticsSnapshot(
        val lastError: String?,
        val lastErrorTime: Long,
        val lastApplyCount: Int,
        val lastApplyTime: Long,
        val lastActiveCount: Int,
        val lastActiveTime: Long,
    )

    companion object {
        private const val PREFS_NAME = "diagnostics"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_ERROR_TIME = "last_error_time"
        private const val KEY_LAST_APPLY_COUNT = "last_apply_count"
        private const val KEY_LAST_APPLY_TIME = "last_apply_time"
        private const val KEY_LAST_ACTIVE_COUNT = "last_active_count"
        private const val KEY_LAST_ACTIVE_TIME = "last_active_time"
    }
}
