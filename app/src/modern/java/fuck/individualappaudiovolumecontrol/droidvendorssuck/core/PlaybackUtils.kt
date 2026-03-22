package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.UserHandle
import android.util.Log
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.VolumeStore.VolumeKey
import kotlin.math.max

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

    private val playerStateMethod = runCatching {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getPlayerState").apply {
            isAccessible = true
        }
    }.getOrNull()

    private val isActiveMethod = runCatching {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("isActive").apply {
            isAccessible = true
        }
    }.getOrNull()

    private val playerStateStartedValue: Int? = runCatching {
        AudioPlaybackConfiguration::class.java.getDeclaredField("PLAYER_STATE_STARTED").apply {
            isAccessible = true
        }.getInt(null)
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

    fun getActivePlaybackConfigurationsSafe(
        audioManager: AudioManager,
        diagnosticsStore: DiagnosticsStore? = null,
    ): List<AudioPlaybackConfiguration> {
        return try {
            audioManager.activePlaybackConfigurations
        } catch (t: Throwable) {
            diagnosticsStore?.recordLastError(
                "activePlaybackConfigurations failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            )
            emptyList()
        }
    }

    fun getActivePlaybackConfigurationsSafe(
        context: Context,
        diagnosticsStore: DiagnosticsStore? = null,
    ): List<AudioPlaybackConfiguration> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val baseConfigs = getActivePlaybackConfigurationsSafe(audioManager, diagnosticsStore)
        val hasAcrossUsers = hasAcrossUsersPermission(context)
        val probeParts = mutableListOf(
            "perm=${if (hasAcrossUsers) "granted" else "denied"}",
            "base=${baseConfigs.size}",
        )
        if (!hasAcrossUsers) {
            diagnosticsStore?.recordCrossProfileProbe(probeParts.joinToString(" | "))
            return baseConfigs
        }

        val results = mutableListOf<AudioPlaybackConfiguration>()
        results.addAll(baseConfigs)

        val currentUserId = ProfileUtils.currentUserId()
        val userIds = ProfileUtils.collectUserIds(context)
        probeParts += "current=u${currentUserId ?: "?"}"
        probeParts += "profiles=${if (userIds.isEmpty()) "-" else userIds.joinToString(",") { "u$it" }}"
        for (userId in userIds) {
            if (currentUserId != null && userId == currentUserId) continue
            val userHandle = createUserHandle(userId)
            if (userHandle == null) {
                probeParts += "u$userId:userHandle=fail"
                continue
            }
            val userContext = createContextAsUser(context, userHandle)
            if (userContext == null) {
                probeParts += "u$userId:context=fail"
                continue
            }
            val userAudioManager = userContext.getSystemService(AudioManager::class.java)
            try {
                val configs = userAudioManager.activePlaybackConfigurations
                results.addAll(configs)
                probeParts += "u$userId:configs=${configs.size}"
            } catch (t: Throwable) {
                probeParts += "u$userId:${t.javaClass.simpleName}"
                diagnosticsStore?.recordLastError(
                    "activePlaybackConfigurations failed for u$userId: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
                )
            }
        }
        diagnosticsStore?.recordCrossProfileProbe(probeParts.joinToString(" | "))
        return results
    }

    fun hasAcrossUsersPermission(context: Context): Boolean {
        val granted = context.checkSelfPermission(PERMISSION_INTERACT_ACROSS_USERS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        return context.checkSelfPermission(PERMISSION_INTERACT_ACROSS_USERS_FULL) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createUserHandle(userId: Int): UserHandle? {
        return try {
            val method = UserHandle::class.java.getDeclaredMethod(
                "of",
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(null, userId) as? UserHandle
        } catch (_: Throwable) {
            null
        }
    }

    private fun createContextAsUser(context: Context, userHandle: UserHandle): Context? {
        return try {
            val method = Context::class.java.getDeclaredMethod(
                "createContextAsUser",
                UserHandle::class.java,
                Int::class.javaPrimitiveType
            )
            method.isAccessible = true
            method.invoke(context, userHandle, 0) as? Context
        } catch (_: Throwable) {
            null
        }
    }

    private const val PERMISSION_INTERACT_ACROSS_USERS =
        "android.permission.INTERACT_ACROSS_USERS"
    private const val PERMISSION_INTERACT_ACROSS_USERS_FULL =
        "android.permission.INTERACT_ACROSS_USERS_FULL"

    fun hasActivePlayback(configs: List<AudioPlaybackConfiguration>): Boolean {
        for (config in configs) {
            val isStarted = runCatching {
                val state = playerStateMethod?.invoke(config) as? Int
                val startedValue = playerStateStartedValue
                state != null && startedValue != null && state == startedValue
            }.getOrDefault(false)
            if (isStarted) return true
            val isActive = runCatching {
                (isActiveMethod?.invoke(config) as? Boolean) == true
            }.getOrDefault(false)
            if (isActive) return true
        }
        return false
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

    data class ActivePackageInfo(
        val key: VolumeKey,
        val sessionCount: Int,
        val isRootDetected: Boolean,
    )

    fun shouldUseRootAudioFallback(
        context: Context,
        overlayPrefs: OverlayPrefs? = null,
        includeRootFallback: Boolean = overlayPrefs?.isRootAudioFallbackEnabled() == true,
    ): Boolean {
        return includeRootFallback && !hasAcrossUsersPermission(context)
    }

    fun buildActivePackageInfo(
        context: Context,
        packageManager: PackageManager,
        diagnosticsStore: DiagnosticsStore? = null,
        overlayPrefs: OverlayPrefs? = null,
        filterOverlayIgnore: Boolean = false,
        includeRootFallback: Boolean = overlayPrefs?.isRootAudioFallbackEnabled() == true,
        forceRootRefresh: Boolean = false,
    ): List<ActivePackageInfo> {
        val counts = mutableMapOf<VolumeKey, Pair<Int, Boolean>>()
        val configs = getActivePlaybackConfigurationsSafe(context, diagnosticsStore)
        for (config in configs) {
            val packageName = getClientPackageName(config, packageManager) ?: continue
            val uid = getClientUid(config)
            val userId = getUserIdFromUid(uid)
            if (filterOverlayIgnore && overlayPrefs?.isOverlayIgnored(packageName, userId) == true) {
                continue
            }
            val key = VolumeKey(packageName, userId)
            val current = counts[key]
            val nextCount = (current?.first ?: 0) + 1
            counts[key] = nextCount to false
        }
        val useRootFallback = shouldUseRootAudioFallback(context, overlayPrefs, includeRootFallback)
        if (useRootFallback) {
            val rootEntries = RootAudioDumpsys.getActiveEntries(
                diagnosticsStore = diagnosticsStore,
                force = forceRootRefresh
            )
            for (entry in rootEntries) {
                val userId = getUserIdFromUid(entry.uid)
                if (filterOverlayIgnore && overlayPrefs?.isOverlayIgnored(entry.packageName, userId) == true) {
                    continue
                }
                val key = VolumeKey(entry.packageName, userId)
                if (counts.containsKey(key)) continue
                val count = max(1, entry.sessionIds.size)
                counts[key] = count to true
            }
        }
        return counts.map { (key, data) ->
            ActivePackageInfo(
                key = key,
                sessionCount = data.first,
                isRootDetected = data.second,
            )
        }
    }

    fun hasNonIgnoredPlayback(
        context: Context,
        packageManager: PackageManager,
        overlayPrefs: OverlayPrefs,
        diagnosticsStore: DiagnosticsStore? = null,
    ): Boolean {
        val entries = buildActivePackageInfo(
            context = context,
            packageManager = packageManager,
            diagnosticsStore = diagnosticsStore,
            overlayPrefs = overlayPrefs,
            filterOverlayIgnore = true,
            includeRootFallback = overlayPrefs.isRootAudioFallbackEnabled(),
            forceRootRefresh = false
        )
        return entries.isNotEmpty()
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
