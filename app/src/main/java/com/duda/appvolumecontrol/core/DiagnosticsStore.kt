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

    fun recordLastDump(count: Int) {
        prefs.edit()
            .putInt(KEY_LAST_DUMP_COUNT, count)
            .putLong(KEY_LAST_DUMP_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordOverlayWatchStart() {
        val count = prefs.getInt(KEY_OVERLAY_WATCH_COUNT, 0) + 1
        prefs.edit()
            .putInt(KEY_OVERLAY_WATCH_COUNT, count)
            .putLong(KEY_OVERLAY_WATCH_START_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordOverlayWatchStop() {
        prefs.edit()
            .putLong(KEY_OVERLAY_WATCH_STOP_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordOverlaySignal(type: String) {
        val now = System.currentTimeMillis()
        when (type) {
            OVERLAY_SIGNAL_OBSERVER -> {
                val count = prefs.getInt(KEY_OVERLAY_OBSERVER_COUNT, 0) + 1
                prefs.edit()
                    .putInt(KEY_OVERLAY_OBSERVER_COUNT, count)
                    .putLong(KEY_OVERLAY_OBSERVER_TIME, now)
                    .apply()
            }
            OVERLAY_SIGNAL_BROADCAST -> {
                val count = prefs.getInt(KEY_OVERLAY_BROADCAST_COUNT, 0) + 1
                prefs.edit()
                    .putInt(KEY_OVERLAY_BROADCAST_COUNT, count)
                    .putLong(KEY_OVERLAY_BROADCAST_TIME, now)
                    .apply()
            }
            OVERLAY_SIGNAL_POLL -> {
                val count = prefs.getInt(KEY_OVERLAY_POLL_COUNT, 0) + 1
                prefs.edit()
                    .putInt(KEY_OVERLAY_POLL_COUNT, count)
                    .putLong(KEY_OVERLAY_POLL_TIME, now)
                    .apply()
            }
            OVERLAY_SIGNAL_MANUAL -> {
                val count = prefs.getInt(KEY_OVERLAY_MANUAL_COUNT, 0) + 1
                prefs.edit()
                    .putInt(KEY_OVERLAY_MANUAL_COUNT, count)
                    .putLong(KEY_OVERLAY_MANUAL_TIME, now)
                    .apply()
            }
        }
    }

    fun recordOverlayShow(reason: String, activeCount: Int) {
        prefs.edit()
            .putString(KEY_OVERLAY_SHOW_REASON, reason)
            .putInt(KEY_OVERLAY_SHOW_ACTIVE_COUNT, activeCount)
            .putLong(KEY_OVERLAY_SHOW_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordHiddenApiAttempt(vmRuntimeOk: Boolean?, policyOk: Boolean?) {
        val editor = prefs.edit()
        if (vmRuntimeOk != null) {
            editor.putInt(KEY_HIDDEN_API_VM_OK, if (vmRuntimeOk) 1 else 0)
        }
        if (policyOk != null) {
            editor.putInt(KEY_HIDDEN_API_POLICY_OK, if (policyOk) 1 else 0)
        }
        editor.putLong(KEY_HIDDEN_API_TIME, System.currentTimeMillis())
        editor.apply()
    }

    fun recordLastImport(count: Int) {
        prefs.edit()
            .putInt(KEY_LAST_IMPORT_COUNT, count)
            .putLong(KEY_LAST_IMPORT_TIME, System.currentTimeMillis())
            .apply()
    }

    fun recordLastExport(count: Int) {
        prefs.edit()
            .putInt(KEY_LAST_EXPORT_COUNT, count)
            .putLong(KEY_LAST_EXPORT_TIME, System.currentTimeMillis())
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
            lastDumpCount = prefs.getInt(KEY_LAST_DUMP_COUNT, 0),
            lastDumpTime = prefs.getLong(KEY_LAST_DUMP_TIME, 0L),
            overlayWatchCount = prefs.getInt(KEY_OVERLAY_WATCH_COUNT, 0),
            overlayWatchStartTime = prefs.getLong(KEY_OVERLAY_WATCH_START_TIME, 0L),
            overlayWatchStopTime = prefs.getLong(KEY_OVERLAY_WATCH_STOP_TIME, 0L),
            overlayObserverCount = prefs.getInt(KEY_OVERLAY_OBSERVER_COUNT, 0),
            overlayObserverTime = prefs.getLong(KEY_OVERLAY_OBSERVER_TIME, 0L),
            overlayBroadcastCount = prefs.getInt(KEY_OVERLAY_BROADCAST_COUNT, 0),
            overlayBroadcastTime = prefs.getLong(KEY_OVERLAY_BROADCAST_TIME, 0L),
            overlayPollCount = prefs.getInt(KEY_OVERLAY_POLL_COUNT, 0),
            overlayPollTime = prefs.getLong(KEY_OVERLAY_POLL_TIME, 0L),
            overlayManualCount = prefs.getInt(KEY_OVERLAY_MANUAL_COUNT, 0),
            overlayManualTime = prefs.getLong(KEY_OVERLAY_MANUAL_TIME, 0L),
            overlayShowReason = prefs.getString(KEY_OVERLAY_SHOW_REASON, null),
            overlayShowActiveCount = prefs.getInt(KEY_OVERLAY_SHOW_ACTIVE_COUNT, 0),
            overlayShowTime = prefs.getLong(KEY_OVERLAY_SHOW_TIME, 0L),
            hiddenApiVmRuntimeOk = prefs.getInt(KEY_HIDDEN_API_VM_OK, -1),
            hiddenApiPolicyOk = prefs.getInt(KEY_HIDDEN_API_POLICY_OK, -1),
            hiddenApiTime = prefs.getLong(KEY_HIDDEN_API_TIME, 0L),
            lastImportCount = prefs.getInt(KEY_LAST_IMPORT_COUNT, 0),
            lastImportTime = prefs.getLong(KEY_LAST_IMPORT_TIME, 0L),
            lastExportCount = prefs.getInt(KEY_LAST_EXPORT_COUNT, 0),
            lastExportTime = prefs.getLong(KEY_LAST_EXPORT_TIME, 0L),
        )
    }

    data class DiagnosticsSnapshot(
        val lastError: String?,
        val lastErrorTime: Long,
        val lastApplyCount: Int,
        val lastApplyTime: Long,
        val lastActiveCount: Int,
        val lastActiveTime: Long,
        val lastDumpCount: Int,
        val lastDumpTime: Long,
        val overlayWatchCount: Int,
        val overlayWatchStartTime: Long,
        val overlayWatchStopTime: Long,
        val overlayObserverCount: Int,
        val overlayObserverTime: Long,
        val overlayBroadcastCount: Int,
        val overlayBroadcastTime: Long,
        val overlayPollCount: Int,
        val overlayPollTime: Long,
        val overlayManualCount: Int,
        val overlayManualTime: Long,
        val overlayShowReason: String?,
        val overlayShowActiveCount: Int,
        val overlayShowTime: Long,
        val hiddenApiVmRuntimeOk: Int,
        val hiddenApiPolicyOk: Int,
        val hiddenApiTime: Long,
        val lastImportCount: Int,
        val lastImportTime: Long,
        val lastExportCount: Int,
        val lastExportTime: Long,
    )

    companion object {
        private const val PREFS_NAME = "diagnostics"
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_ERROR_TIME = "last_error_time"
        private const val KEY_LAST_APPLY_COUNT = "last_apply_count"
        private const val KEY_LAST_APPLY_TIME = "last_apply_time"
        private const val KEY_LAST_ACTIVE_COUNT = "last_active_count"
        private const val KEY_LAST_ACTIVE_TIME = "last_active_time"
        private const val KEY_LAST_DUMP_COUNT = "last_dump_count"
        private const val KEY_LAST_DUMP_TIME = "last_dump_time"
        private const val KEY_OVERLAY_WATCH_COUNT = "overlay_watch_count"
        private const val KEY_OVERLAY_WATCH_START_TIME = "overlay_watch_start_time"
        private const val KEY_OVERLAY_WATCH_STOP_TIME = "overlay_watch_stop_time"
        private const val KEY_OVERLAY_OBSERVER_COUNT = "overlay_observer_count"
        private const val KEY_OVERLAY_OBSERVER_TIME = "overlay_observer_time"
        private const val KEY_OVERLAY_BROADCAST_COUNT = "overlay_broadcast_count"
        private const val KEY_OVERLAY_BROADCAST_TIME = "overlay_broadcast_time"
        private const val KEY_OVERLAY_POLL_COUNT = "overlay_poll_count"
        private const val KEY_OVERLAY_POLL_TIME = "overlay_poll_time"
        private const val KEY_OVERLAY_MANUAL_COUNT = "overlay_manual_count"
        private const val KEY_OVERLAY_MANUAL_TIME = "overlay_manual_time"
        private const val KEY_OVERLAY_SHOW_REASON = "overlay_show_reason"
        private const val KEY_OVERLAY_SHOW_ACTIVE_COUNT = "overlay_show_active_count"
        private const val KEY_OVERLAY_SHOW_TIME = "overlay_show_time"
        private const val KEY_HIDDEN_API_VM_OK = "hidden_api_vm_ok"
        private const val KEY_HIDDEN_API_POLICY_OK = "hidden_api_policy_ok"
        private const val KEY_HIDDEN_API_TIME = "hidden_api_time"
        private const val KEY_LAST_IMPORT_COUNT = "last_import_count"
        private const val KEY_LAST_IMPORT_TIME = "last_import_time"
        private const val KEY_LAST_EXPORT_COUNT = "last_export_count"
        private const val KEY_LAST_EXPORT_TIME = "last_export_time"

        const val OVERLAY_SIGNAL_OBSERVER = "observer"
        const val OVERLAY_SIGNAL_BROADCAST = "broadcast"
        const val OVERLAY_SIGNAL_POLL = "poll"
        const val OVERLAY_SIGNAL_MANUAL = "manual"
    }
}
