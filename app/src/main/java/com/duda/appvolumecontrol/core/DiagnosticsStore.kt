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
            hiddenApiVmRuntimeOk = prefs.getInt(KEY_HIDDEN_API_VM_OK, -1),
            hiddenApiPolicyOk = prefs.getInt(KEY_HIDDEN_API_POLICY_OK, -1),
            hiddenApiTime = prefs.getLong(KEY_HIDDEN_API_TIME, 0L),
            lastImportCount = prefs.getInt(KEY_LAST_IMPORT_COUNT, 0),
            lastImportTime = prefs.getLong(KEY_LAST_IMPORT_TIME, 0L),
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
        val hiddenApiVmRuntimeOk: Int,
        val hiddenApiPolicyOk: Int,
        val hiddenApiTime: Long,
        val lastImportCount: Int,
        val lastImportTime: Long,
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
        private const val KEY_HIDDEN_API_VM_OK = "hidden_api_vm_ok"
        private const val KEY_HIDDEN_API_POLICY_OK = "hidden_api_policy_ok"
        private const val KEY_HIDDEN_API_TIME = "hidden_api_time"
        private const val KEY_LAST_IMPORT_COUNT = "last_import_count"
        private const val KEY_LAST_IMPORT_TIME = "last_import_time"
    }
}
