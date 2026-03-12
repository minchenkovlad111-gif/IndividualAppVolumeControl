package com.duda.appvolumecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duda.appvolumecontrol.core.VolumeStore

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val delta = intent.getFloatExtra(EXTRA_DELTA, 0f)
        val rawUserId = intent.getIntExtra(EXTRA_USER_ID, USER_ID_NONE)
        val userId = if (rawUserId == USER_ID_NONE) null else rawUserId
        val store = VolumeStore(context)
        val key = VolumeStore.VolumeKey(packageName, userId)
        val current = store.getVolume(key) ?: 1f
        val next = (current + delta).coerceIn(0f, 1f)
        store.setVolume(key, next)
        PlaybackControllerService.requestReapply(context)
    }

    companion object {
        const val ACTION_ADJUST = "com.duda.appvolumecontrol.action.NOTIF_ADJUST"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_USER_ID = "user_id"
        const val EXTRA_DELTA = "delta"
        const val USER_ID_NONE = -1
    }
}
