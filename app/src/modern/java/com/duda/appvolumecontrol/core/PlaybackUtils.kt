package com.duda.appvolumecontrol.core

import android.content.pm.PackageManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioDeviceInfo
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

    private val setVolumeMethodCache = mutableMapOf<Class<*>, java.lang.reflect.Method>()
    private val pauseMethodCache = mutableMapOf<Class<*>, java.lang.reflect.Method>()
    private val playMethodCache = mutableMapOf<Class<*>, java.lang.reflect.Method>()

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

    fun getClientUid(config: AudioPlaybackConfiguration): Int? {
        return try {
            val uidMethod = clientUidMethod ?: return null
            uidMethod.invoke(config) as? Int
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve client uid", t)
            null
        }
    }

    fun getUserIdFromUid(uid: Int?): Int? {
        if (uid == null) return null
        return try {
            val method = android.os.UserHandle::class.java.getDeclaredMethod(
                "getUserId",
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(null, uid) as? Int
        } catch (_: Throwable) {
            null
        }
    }

    fun applyVolumeToPlayer(config: AudioPlaybackConfiguration, volume: Float): Boolean {
        return try {
            val method = playerProxyMethod ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val setVolume = synchronized(setVolumeMethodCache) {
                setVolumeMethodCache[proxyClass] ?: run {
                    val resolved = try {
                        proxyClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
                    } catch (_: Throwable) {
                        proxyClass.getDeclaredMethod(
                            "setVolume",
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                        )
                    }
                    resolved.isAccessible = true
                    setVolumeMethodCache[proxyClass] = resolved
                    resolved
                }
            }
            try {
                if (setVolume.parameterTypes.size == 2) {
                    setVolume.invoke(playerProxy, volume, 0)
                } else {
                    setVolume.invoke(playerProxy, volume)
                }
                true
            } catch (t: Throwable) {
                synchronized(setVolumeMethodCache) {
                    setVolumeMethodCache.remove(proxyClass)
                }
                Log.w(TAG, "Failed to set player volume", t)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set player volume", t)
            false
        }
    }

    fun applyPreferredDeviceToPlayer(
        config: AudioPlaybackConfiguration,
        device: AudioDeviceInfo
    ): Boolean {
        return try {
            val method = playerProxyMethod ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val preferred = runCatching {
                proxyClass.getDeclaredMethod("setPreferredDevice", AudioDeviceInfo::class.java)
            }.getOrElse {
                proxyClass.getDeclaredMethod("setDevice", AudioDeviceInfo::class.java)
            }
            preferred.isAccessible = true
            preferred.invoke(playerProxy, device)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set preferred device", t)
            false
        }
    }

    fun pausePlayer(config: AudioPlaybackConfiguration): Boolean {
        return try {
            val method = playerProxyMethod ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val pause = synchronized(pauseMethodCache) {
                pauseMethodCache[proxyClass] ?: run {
                    val resolved = resolvePauseMethod(proxyClass) ?: return false
                    resolved.isAccessible = true
                    pauseMethodCache[proxyClass] = resolved
                    resolved
                }
            }
            try {
                if (pause.parameterTypes.isEmpty()) {
                    pause.invoke(playerProxy)
                } else {
                    pause.invoke(playerProxy, true)
                }
                true
            } catch (t: Throwable) {
                synchronized(pauseMethodCache) {
                    pauseMethodCache.remove(proxyClass)
                }
                Log.w(TAG, "Failed to pause player", t)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to pause player", t)
            false
        }
    }

    fun playPlayer(config: AudioPlaybackConfiguration): Boolean {
        return try {
            val method = playerProxyMethod ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val play = synchronized(playMethodCache) {
                playMethodCache[proxyClass] ?: run {
                    val resolved = resolvePlayMethod(proxyClass) ?: return false
                    resolved.isAccessible = true
                    playMethodCache[proxyClass] = resolved
                    resolved
                }
            }
            try {
                if (play.parameterTypes.isEmpty()) {
                    play.invoke(playerProxy)
                } else {
                    play.invoke(playerProxy, true)
                }
                true
            } catch (t: Throwable) {
                synchronized(playMethodCache) {
                    playMethodCache.remove(proxyClass)
                }
                Log.w(TAG, "Failed to start player", t)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start player", t)
            false
        }
    }

    private fun resolvePauseMethod(proxyClass: Class<*>): java.lang.reflect.Method? {
        return runCatching {
            proxyClass.getDeclaredMethod("pause")
        }.getOrElse {
            runCatching { proxyClass.getDeclaredMethod("pause", Boolean::class.javaPrimitiveType) }.getOrNull()
        }
    }

    private fun resolvePlayMethod(proxyClass: Class<*>): java.lang.reflect.Method? {
        return runCatching {
            proxyClass.getDeclaredMethod("start")
        }.getOrElse {
            runCatching { proxyClass.getDeclaredMethod("play") }.getOrNull()
        }
    }
}
