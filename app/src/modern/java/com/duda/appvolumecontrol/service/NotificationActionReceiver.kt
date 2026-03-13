package com.duda.appvolumecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duda.appvolumecontrol.core.VolumeStore

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val rawUserId = intent.getIntExtra(EXTRA_USER_ID, USER_ID_NONE)
        val userId = if (rawUserId == USER_ID_NONE) null else rawUserId
        val store = VolumeStore(context)
        val key = VolumeStore.VolumeKey(packageName, userId)
        when (intent.action) {
            ACTION_ADJUST -> {
                val delta = intent.getFloatExtra(EXTRA_DELTA, 0f)
                val current = store.getVolume(key) ?: 1f
                val next = (current + delta).coerceIn(0f, 1f)
                store.setVolume(key, next)
                rememberNonZeroIfNeeded(context, key, next)
                PlaybackControllerService.requestReapply(context)
            }
            ACTION_SET -> {
                val percent = intent.getIntExtra(EXTRA_PERCENT, 100)
                val next = (percent.coerceIn(0, 100) / 100f)
                store.setVolume(key, next)
                rememberNonZeroIfNeeded(context, key, next)
                PlaybackControllerService.requestReapply(context)
            }
            ACTION_TOGGLE_MUTE -> {
                val current = store.getVolume(key) ?: 1f
                if (current > 0f) {
                    rememberNonZero(context, key, current)
                    store.setVolume(key, 0f)
                } else {
                    val restored = readNonZero(context, key) ?: 1f
                    store.setVolume(key, restored)
                }
                PlaybackControllerService.requestReapply(context)
            }
        }
    }

    private fun rememberNonZeroIfNeeded(context: Context, key: VolumeStore.VolumeKey, value: Float) {
        if (value > 0f) {
            rememberNonZero(context, key, value)
        }
    }

    private fun rememberNonZero(context: Context, key: VolumeStore.VolumeKey, value: Float) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(key.storageKey(), value).apply()
    }

    private fun readNonZero(context: Context, key: VolumeStore.VolumeKey): Float? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return if (prefs.contains(key.storageKey())) prefs.getFloat(key.storageKey(), 1f) else null
    }

    companion object {
        const val ACTION_ADJUST = "com.duda.appvolumecontrol.action.NOTIF_ADJUST"
        const val ACTION_SET = "com.duda.appvolumecontrol.action.NOTIF_SET"
        const val ACTION_TOGGLE_MUTE = "com.duda.appvolumecontrol.action.NOTIF_TOGGLE_MUTE"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_DELTA = "delta"
        const val EXTRA_PERCENT = "percent"
        const val USER_ID_NONE = -1
        private const val PREFS_NAME = "notif_volume"
    }
}
