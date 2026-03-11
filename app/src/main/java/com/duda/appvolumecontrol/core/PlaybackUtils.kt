package com.duda.appvolumecontrol.core

import android.content.pm.PackageManager
import android.media.AudioPlaybackConfiguration
import android.util.Log

object PlaybackUtils {
    private const val TAG = "PlaybackUtils"

    fun getClientPackageName(
        config: AudioPlaybackConfiguration,
        packageManager: PackageManager
    ): String? {
        try {
            val method = AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientPackageName")
            method.isAccessible = true
            return method.invoke(config) as? String
        } catch (_: Throwable) {
            // Fallback to resolving by UID if hidden API is blocked.
        }

        return try {
            val uidMethod = AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientUid")
            uidMethod.isAccessible = true
            val uid = uidMethod.invoke(config) as? Int ?: return null
            val packages = packageManager.getPackagesForUid(uid)
            packages?.firstOrNull()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve client package", t)
            null
        }
    }

    fun applyVolumeToPlayer(config: AudioPlaybackConfiguration, volume: Float): Boolean {
        return try {
            val method = AudioPlaybackConfiguration::class.java.getDeclaredMethod("getPlayerProxy")
            method.isAccessible = true
            val playerProxy = method.invoke(config) ?: return false
            val setVolume = playerProxy.javaClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
            setVolume.isAccessible = true
            setVolume.invoke(playerProxy, volume)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set player volume", t)
            false
        }
    }
}
