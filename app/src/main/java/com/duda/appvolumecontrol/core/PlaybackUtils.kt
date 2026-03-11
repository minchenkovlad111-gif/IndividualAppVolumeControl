package com.duda.appvolumecontrol.core

import android.content.pm.PackageManager
import android.media.AudioPlaybackConfiguration
import android.util.Log

object PlaybackUtils {
    private const val TAG = "PlaybackUtils"

    private val clientPackageMethod = runCatching {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientPackageName").apply {
            isAccessible = true
        }
    }.getOrNull()

    private val clientUidMethod = runCatching {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientUid").apply {
            isAccessible = true
        }
    }.getOrNull()

    private val playerProxyMethod = runCatching {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getPlayerProxy").apply {
            isAccessible = true
        }
    }.getOrNull()

    fun getClientPackageName(
        config: AudioPlaybackConfiguration,
        packageManager: PackageManager
    ): String? {
        try {
            val method = clientPackageMethod
            if (method != null) {
                return method.invoke(config) as? String
            }
        } catch (_: Throwable) {
            // Fallback to resolving by UID if hidden API is blocked.
        }

        return try {
            val uidMethod = clientUidMethod ?: return null
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
            val method = playerProxyMethod ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val setVolume = try {
                playerProxy.javaClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
            } catch (_: Throwable) {
                playerProxy.javaClass.getDeclaredMethod(
                    "setVolume",
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
            }
            setVolume.isAccessible = true
            if (setVolume.parameterTypes.size == 2) {
                setVolume.invoke(playerProxy, volume, 0)
            } else {
                setVolume.invoke(playerProxy, volume)
            }
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set player volume", t)
            false
        }
    }
}
