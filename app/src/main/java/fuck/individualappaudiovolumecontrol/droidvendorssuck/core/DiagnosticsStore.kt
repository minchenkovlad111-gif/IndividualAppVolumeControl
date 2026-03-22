package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DiagnosticsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        synchronized(cacheLock) {
            if (sharedPrefs == null) {
                sharedPrefs = prefs
            }
            if (cachedSnapshot == null) {
                cachedSnapshot = loadSnapshot(prefsLocked())
            }
        }
    }

    fun recordLastError(message: String) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastError = message,
                lastErrorTime = now,
            )
        }
    }

    fun recordLastApply(count: Int) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastApplyCount = count,
                lastApplyTime = now,
            )
        }
    }

    fun recordLastRouting(
        attempted: Int,
        applied: Int,
        missing: Int,
        failed: Int,
        details: String,
    ) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastRouteAttempted = attempted,
                lastRouteApplied = applied,
                lastRouteMissing = missing,
                lastRouteFailed = failed,
                lastRouteDetails = details,
                lastRouteTime = now,
            )
        }
    }

    fun recordLastActive(count: Int) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastActiveCount = count,
                lastActiveTime = now,
            )
        }
    }

    fun recordLastDump(count: Int) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastDumpCount = count,
                lastDumpTime = now,
            )
        }
    }

    fun recordOverlayWatchStart() {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                overlayWatchCount = overlayWatchCount + 1,
                overlayWatchStartTime = now,
            )
        }
    }

    fun recordOverlayWatchStop() {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(overlayWatchStopTime = now)
        }
    }

    fun recordOverlaySignal(type: String) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            when (type) {
                OVERLAY_SIGNAL_OBSERVER -> copy(
                    overlayObserverCount = overlayObserverCount + 1,
                    overlayObserverTime = now,
                )
                OVERLAY_SIGNAL_BROADCAST -> copy(
                    overlayBroadcastCount = overlayBroadcastCount + 1,
                    overlayBroadcastTime = now,
                )
                OVERLAY_SIGNAL_POLL -> copy(
                    overlayPollCount = overlayPollCount + 1,
                    overlayPollTime = now,
                )
                OVERLAY_SIGNAL_MANUAL -> copy(
                    overlayManualCount = overlayManualCount + 1,
                    overlayManualTime = now,
                )
                else -> this
            }
        }
    }

    fun recordOverlayShow(reason: String, activeCount: Int) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                overlayShowReason = reason,
                overlayShowActiveCount = activeCount,
                overlayShowTime = now,
            )
        }
    }

    fun recordHiddenApiAttempt(
        vmRuntimeOk: Boolean?,
        policyOk: Boolean?,
        vmRuntimeError: String? = null,
        policyError: String? = null,
    ) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                hiddenApiVmRuntimeOk = vmRuntimeOk?.let { if (it) 1 else 0 } ?: hiddenApiVmRuntimeOk,
                hiddenApiPolicyOk = policyOk?.let { if (it) 1 else 0 } ?: hiddenApiPolicyOk,
                hiddenApiVmRuntimeError = vmRuntimeError ?: hiddenApiVmRuntimeError,
                hiddenApiPolicyError = policyError ?: hiddenApiPolicyError,
                hiddenApiTime = now,
            )
        }
    }

    fun recordLastImport(count: Int) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastImportCount = count,
                lastImportTime = now,
            )
        }
    }

    fun recordLastExport(count: Int) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                lastExportCount = count,
                lastExportTime = now,
            )
        }
    }

    fun recordCallState(state: String) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                callState = state,
                callStateTime = now,
            )
        }
    }

    fun recordControllerServiceStart() {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                controllerServiceStarts = controllerServiceStarts + 1,
                controllerServiceStartTime = now,
            )
        }
    }

    fun recordControllerNotificationPost(
        activeCount: Int,
        notificationsEnabled: Boolean,
        channelImportance: Int,
    ) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                controllerNotificationPosts = controllerNotificationPosts + 1,
                controllerNotificationPostTime = now,
                controllerNotificationActiveCount = activeCount,
                controllerNotificationsEnabled = if (notificationsEnabled) 1 else 0,
                controllerNotificationChannelImportance = channelImportance,
            )
        }
    }

    fun recordLocationFix(
        provider: String?,
        timeMs: Long,
        accuracyMeters: Float?,
        isMock: Boolean?,
    ) {
        updateSnapshot {
            copy(
                locationProvider = provider,
                locationTime = timeMs,
                locationAccuracy = accuracyMeters ?: -1f,
                locationMock = when (isMock) {
                    true -> 1
                    false -> 0
                    null -> -1
                },
            )
        }
    }

    fun recordCrossProfileProbe(summary: String) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                crossProfileProbeSummary = summary,
                crossProfileProbeTime = now,
            )
        }
    }

    fun recordCrossProfileApply(summary: String) {
        val now = System.currentTimeMillis()
        updateSnapshot {
            copy(
                crossProfileApplySummary = summary,
                crossProfileApplyTime = now,
            )
        }
    }

    fun snapshot(): DiagnosticsSnapshot {
        synchronized(cacheLock) {
            return ensureSnapshotLocked()
        }
    }

    private fun updateSnapshot(transform: DiagnosticsSnapshot.() -> DiagnosticsSnapshot) {
        synchronized(cacheLock) {
            val current = ensureSnapshotLocked()
            cachedSnapshot = current.transform()
            dirtyGeneration += 1
            scheduleFlushLocked(FLUSH_DELAY_MS)
        }
    }

    private fun scheduleFlushLocked(delayMs: Long) {
        if (flushScheduled) {
            return
        }
        flushScheduled = true
        flushExecutor.schedule(
            { flushOnce() },
            delayMs,
            TimeUnit.MILLISECONDS
        )
    }

    private fun flushOnce() {
        val snapshotToWrite: DiagnosticsSnapshot
        val generationToWrite: Int
        val prefsToWrite: SharedPreferences
        synchronized(cacheLock) {
            prefsToWrite = prefsLocked()
            snapshotToWrite = ensureSnapshotLocked()
            generationToWrite = dirtyGeneration
        }

        val writeOk = runCatching {
            writeSnapshot(prefsToWrite, snapshotToWrite)
        }.isSuccess

        synchronized(cacheLock) {
            flushScheduled = false
            when {
                !writeOk -> scheduleFlushLocked(WRITE_RETRY_DELAY_MS)
                dirtyGeneration != generationToWrite -> scheduleFlushLocked(FLUSH_DELAY_MS)
            }
        }
    }

    private fun ensureSnapshotLocked(): DiagnosticsSnapshot {
        val cached = cachedSnapshot
        if (cached != null) {
            return cached
        }
        return loadSnapshot(prefsLocked()).also { cachedSnapshot = it }
    }

    private fun prefsLocked(): SharedPreferences {
        val existing = sharedPrefs
        if (existing != null) {
            return existing
        }
        sharedPrefs = prefs
        return prefs
    }

    private fun loadSnapshot(prefs: SharedPreferences): DiagnosticsSnapshot {
        return DiagnosticsSnapshot(
            lastError = prefs.getString(KEY_LAST_ERROR, null),
            lastErrorTime = prefs.getLong(KEY_LAST_ERROR_TIME, 0L),
            lastApplyCount = prefs.getInt(KEY_LAST_APPLY_COUNT, 0),
            lastApplyTime = prefs.getLong(KEY_LAST_APPLY_TIME, 0L),
            lastRouteAttempted = prefs.getInt(KEY_LAST_ROUTE_ATTEMPTED, 0),
            lastRouteApplied = prefs.getInt(KEY_LAST_ROUTE_APPLIED, 0),
            lastRouteMissing = prefs.getInt(KEY_LAST_ROUTE_MISSING, 0),
            lastRouteFailed = prefs.getInt(KEY_LAST_ROUTE_FAILED, 0),
            lastRouteDetails = prefs.getString(KEY_LAST_ROUTE_DETAILS, null),
            lastRouteTime = prefs.getLong(KEY_LAST_ROUTE_TIME, 0L),
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
            hiddenApiVmRuntimeError = prefs.getString(KEY_HIDDEN_API_VM_ERROR, null),
            hiddenApiPolicyError = prefs.getString(KEY_HIDDEN_API_POLICY_ERROR, null),
            hiddenApiTime = prefs.getLong(KEY_HIDDEN_API_TIME, 0L),
            lastImportCount = prefs.getInt(KEY_LAST_IMPORT_COUNT, 0),
            lastImportTime = prefs.getLong(KEY_LAST_IMPORT_TIME, 0L),
            lastExportCount = prefs.getInt(KEY_LAST_EXPORT_COUNT, 0),
            lastExportTime = prefs.getLong(KEY_LAST_EXPORT_TIME, 0L),
            callState = prefs.getString(KEY_CALL_STATE, null),
            callStateTime = prefs.getLong(KEY_CALL_STATE_TIME, 0L),
            controllerServiceStarts = prefs.getInt(KEY_CONTROLLER_SERVICE_STARTS, 0),
            controllerServiceStartTime = prefs.getLong(KEY_CONTROLLER_SERVICE_START_TIME, 0L),
            controllerNotificationPosts = prefs.getInt(KEY_CONTROLLER_NOTIFICATION_POSTS, 0),
            controllerNotificationPostTime = prefs.getLong(KEY_CONTROLLER_NOTIFICATION_POST_TIME, 0L),
            controllerNotificationActiveCount = prefs.getInt(KEY_CONTROLLER_NOTIFICATION_ACTIVE_COUNT, -1),
            controllerNotificationsEnabled = prefs.getInt(KEY_CONTROLLER_NOTIFICATIONS_ENABLED, -1),
            controllerNotificationChannelImportance = prefs.getInt(KEY_CONTROLLER_NOTIFICATION_CHANNEL_IMPORTANCE, Int.MIN_VALUE),
            locationProvider = prefs.getString(KEY_LOCATION_PROVIDER, null),
            locationTime = prefs.getLong(KEY_LOCATION_TIME, 0L),
            locationAccuracy = prefs.getFloat(KEY_LOCATION_ACCURACY, -1f),
            locationMock = prefs.getInt(KEY_LOCATION_MOCK, -1),
            crossProfileProbeSummary = prefs.getString(KEY_CROSS_PROFILE_PROBE_SUMMARY, null),
            crossProfileProbeTime = prefs.getLong(KEY_CROSS_PROFILE_PROBE_TIME, 0L),
            crossProfileApplySummary = prefs.getString(KEY_CROSS_PROFILE_APPLY_SUMMARY, null),
            crossProfileApplyTime = prefs.getLong(KEY_CROSS_PROFILE_APPLY_TIME, 0L),
        )
    }

    private fun writeSnapshot(prefs: SharedPreferences, snapshot: DiagnosticsSnapshot) {
        prefs.edit()
            .putString(KEY_LAST_ERROR, snapshot.lastError)
            .putLong(KEY_LAST_ERROR_TIME, snapshot.lastErrorTime)
            .putInt(KEY_LAST_APPLY_COUNT, snapshot.lastApplyCount)
            .putLong(KEY_LAST_APPLY_TIME, snapshot.lastApplyTime)
            .putInt(KEY_LAST_ROUTE_ATTEMPTED, snapshot.lastRouteAttempted)
            .putInt(KEY_LAST_ROUTE_APPLIED, snapshot.lastRouteApplied)
            .putInt(KEY_LAST_ROUTE_MISSING, snapshot.lastRouteMissing)
            .putInt(KEY_LAST_ROUTE_FAILED, snapshot.lastRouteFailed)
            .putString(KEY_LAST_ROUTE_DETAILS, snapshot.lastRouteDetails)
            .putLong(KEY_LAST_ROUTE_TIME, snapshot.lastRouteTime)
            .putInt(KEY_LAST_ACTIVE_COUNT, snapshot.lastActiveCount)
            .putLong(KEY_LAST_ACTIVE_TIME, snapshot.lastActiveTime)
            .putInt(KEY_LAST_DUMP_COUNT, snapshot.lastDumpCount)
            .putLong(KEY_LAST_DUMP_TIME, snapshot.lastDumpTime)
            .putInt(KEY_OVERLAY_WATCH_COUNT, snapshot.overlayWatchCount)
            .putLong(KEY_OVERLAY_WATCH_START_TIME, snapshot.overlayWatchStartTime)
            .putLong(KEY_OVERLAY_WATCH_STOP_TIME, snapshot.overlayWatchStopTime)
            .putInt(KEY_OVERLAY_OBSERVER_COUNT, snapshot.overlayObserverCount)
            .putLong(KEY_OVERLAY_OBSERVER_TIME, snapshot.overlayObserverTime)
            .putInt(KEY_OVERLAY_BROADCAST_COUNT, snapshot.overlayBroadcastCount)
            .putLong(KEY_OVERLAY_BROADCAST_TIME, snapshot.overlayBroadcastTime)
            .putInt(KEY_OVERLAY_POLL_COUNT, snapshot.overlayPollCount)
            .putLong(KEY_OVERLAY_POLL_TIME, snapshot.overlayPollTime)
            .putInt(KEY_OVERLAY_MANUAL_COUNT, snapshot.overlayManualCount)
            .putLong(KEY_OVERLAY_MANUAL_TIME, snapshot.overlayManualTime)
            .putString(KEY_OVERLAY_SHOW_REASON, snapshot.overlayShowReason)
            .putInt(KEY_OVERLAY_SHOW_ACTIVE_COUNT, snapshot.overlayShowActiveCount)
            .putLong(KEY_OVERLAY_SHOW_TIME, snapshot.overlayShowTime)
            .putInt(KEY_HIDDEN_API_VM_OK, snapshot.hiddenApiVmRuntimeOk)
            .putInt(KEY_HIDDEN_API_POLICY_OK, snapshot.hiddenApiPolicyOk)
            .putString(KEY_HIDDEN_API_VM_ERROR, snapshot.hiddenApiVmRuntimeError)
            .putString(KEY_HIDDEN_API_POLICY_ERROR, snapshot.hiddenApiPolicyError)
            .putLong(KEY_HIDDEN_API_TIME, snapshot.hiddenApiTime)
            .putInt(KEY_LAST_IMPORT_COUNT, snapshot.lastImportCount)
            .putLong(KEY_LAST_IMPORT_TIME, snapshot.lastImportTime)
            .putInt(KEY_LAST_EXPORT_COUNT, snapshot.lastExportCount)
            .putLong(KEY_LAST_EXPORT_TIME, snapshot.lastExportTime)
            .putString(KEY_CALL_STATE, snapshot.callState)
            .putLong(KEY_CALL_STATE_TIME, snapshot.callStateTime)
            .putInt(KEY_CONTROLLER_SERVICE_STARTS, snapshot.controllerServiceStarts)
            .putLong(KEY_CONTROLLER_SERVICE_START_TIME, snapshot.controllerServiceStartTime)
            .putInt(KEY_CONTROLLER_NOTIFICATION_POSTS, snapshot.controllerNotificationPosts)
            .putLong(KEY_CONTROLLER_NOTIFICATION_POST_TIME, snapshot.controllerNotificationPostTime)
            .putInt(KEY_CONTROLLER_NOTIFICATION_ACTIVE_COUNT, snapshot.controllerNotificationActiveCount)
            .putInt(KEY_CONTROLLER_NOTIFICATIONS_ENABLED, snapshot.controllerNotificationsEnabled)
            .putInt(KEY_CONTROLLER_NOTIFICATION_CHANNEL_IMPORTANCE, snapshot.controllerNotificationChannelImportance)
            .putString(KEY_LOCATION_PROVIDER, snapshot.locationProvider)
            .putLong(KEY_LOCATION_TIME, snapshot.locationTime)
            .putFloat(KEY_LOCATION_ACCURACY, snapshot.locationAccuracy)
            .putInt(KEY_LOCATION_MOCK, snapshot.locationMock)
            .putString(KEY_CROSS_PROFILE_PROBE_SUMMARY, snapshot.crossProfileProbeSummary)
            .putLong(KEY_CROSS_PROFILE_PROBE_TIME, snapshot.crossProfileProbeTime)
            .putString(KEY_CROSS_PROFILE_APPLY_SUMMARY, snapshot.crossProfileApplySummary)
            .putLong(KEY_CROSS_PROFILE_APPLY_TIME, snapshot.crossProfileApplyTime)
            .commit()
    }

    data class DiagnosticsSnapshot(
        val lastError: String?,
        val lastErrorTime: Long,
        val lastApplyCount: Int,
        val lastApplyTime: Long,
        val lastRouteAttempted: Int,
        val lastRouteApplied: Int,
        val lastRouteMissing: Int,
        val lastRouteFailed: Int,
        val lastRouteDetails: String?,
        val lastRouteTime: Long,
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
        val hiddenApiVmRuntimeError: String?,
        val hiddenApiPolicyError: String?,
        val hiddenApiTime: Long,
        val lastImportCount: Int,
        val lastImportTime: Long,
        val lastExportCount: Int,
        val lastExportTime: Long,
        val callState: String?,
        val callStateTime: Long,
        val controllerServiceStarts: Int,
        val controllerServiceStartTime: Long,
        val controllerNotificationPosts: Int,
        val controllerNotificationPostTime: Long,
        val controllerNotificationActiveCount: Int,
        val controllerNotificationsEnabled: Int,
        val controllerNotificationChannelImportance: Int,
        val locationProvider: String?,
        val locationTime: Long,
        val locationAccuracy: Float,
        val locationMock: Int,
        val crossProfileProbeSummary: String?,
        val crossProfileProbeTime: Long,
        val crossProfileApplySummary: String?,
        val crossProfileApplyTime: Long,
    )

    companion object {
        private val cacheLock = Any()
        private val flushExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "DiagnosticsStoreFlush")
        }

        private var sharedPrefs: SharedPreferences? = null
        private var cachedSnapshot: DiagnosticsSnapshot? = null
        private var dirtyGeneration: Int = 0
        private var flushScheduled: Boolean = false

        private const val PREFS_NAME = "diagnostics"
        private const val FLUSH_DELAY_MS = 750L
        private const val WRITE_RETRY_DELAY_MS = 2_000L
        private const val KEY_LAST_ERROR = "last_error"
        private const val KEY_LAST_ERROR_TIME = "last_error_time"
        private const val KEY_LAST_APPLY_COUNT = "last_apply_count"
        private const val KEY_LAST_APPLY_TIME = "last_apply_time"
        private const val KEY_LAST_ROUTE_ATTEMPTED = "last_route_attempted"
        private const val KEY_LAST_ROUTE_APPLIED = "last_route_applied"
        private const val KEY_LAST_ROUTE_MISSING = "last_route_missing"
        private const val KEY_LAST_ROUTE_FAILED = "last_route_failed"
        private const val KEY_LAST_ROUTE_DETAILS = "last_route_details"
        private const val KEY_LAST_ROUTE_TIME = "last_route_time"
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
        private const val KEY_HIDDEN_API_VM_ERROR = "hidden_api_vm_error"
        private const val KEY_HIDDEN_API_POLICY_ERROR = "hidden_api_policy_error"
        private const val KEY_HIDDEN_API_TIME = "hidden_api_time"
        private const val KEY_LAST_IMPORT_COUNT = "last_import_count"
        private const val KEY_LAST_IMPORT_TIME = "last_import_time"
        private const val KEY_LAST_EXPORT_COUNT = "last_export_count"
        private const val KEY_LAST_EXPORT_TIME = "last_export_time"
        private const val KEY_CALL_STATE = "call_state"
        private const val KEY_CALL_STATE_TIME = "call_state_time"
        private const val KEY_CONTROLLER_SERVICE_STARTS = "controller_service_starts"
        private const val KEY_CONTROLLER_SERVICE_START_TIME = "controller_service_start_time"
        private const val KEY_CONTROLLER_NOTIFICATION_POSTS = "controller_notification_posts"
        private const val KEY_CONTROLLER_NOTIFICATION_POST_TIME = "controller_notification_post_time"
        private const val KEY_CONTROLLER_NOTIFICATION_ACTIVE_COUNT = "controller_notification_active_count"
        private const val KEY_CONTROLLER_NOTIFICATIONS_ENABLED = "controller_notifications_enabled"
        private const val KEY_CONTROLLER_NOTIFICATION_CHANNEL_IMPORTANCE = "controller_notification_channel_importance"
        private const val KEY_LOCATION_PROVIDER = "location_provider"
        private const val KEY_LOCATION_TIME = "location_time"
        private const val KEY_LOCATION_ACCURACY = "location_accuracy"
        private const val KEY_LOCATION_MOCK = "location_mock"
        private const val KEY_CROSS_PROFILE_PROBE_SUMMARY = "cross_profile_probe_summary"
        private const val KEY_CROSS_PROFILE_PROBE_TIME = "cross_profile_probe_time"
        private const val KEY_CROSS_PROFILE_APPLY_SUMMARY = "cross_profile_apply_summary"
        private const val KEY_CROSS_PROFILE_APPLY_TIME = "cross_profile_apply_time"

        const val OVERLAY_SIGNAL_OBSERVER = "observer"
        const val OVERLAY_SIGNAL_BROADCAST = "broadcast"
        const val OVERLAY_SIGNAL_POLL = "poll"
        const val OVERLAY_SIGNAL_MANUAL = "manual"
    }
}
