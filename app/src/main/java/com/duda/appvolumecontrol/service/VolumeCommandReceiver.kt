package com.duda.appvolumecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.duda.appvolumecontrol.core.VolumeStore

class VolumeCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SET_VOLUME) return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE) ?: return
        val volume = intent.getFloatExtra(EXTRA_VOLUME, 1f)
        VolumeStore(context).setVolume(packageName, volume)
        PlaybackControllerService.requestReapply(context)
    }

    companion object {
        const val ACTION_SET_VOLUME = "com.duda.appvolumecontrol.action.SET_VOLUME"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_VOLUME = "volume"
    }
}
