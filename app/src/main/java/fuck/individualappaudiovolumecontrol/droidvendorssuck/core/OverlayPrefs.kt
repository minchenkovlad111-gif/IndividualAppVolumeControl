package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.pm.PackageManager
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.VolumeStore.VolumeKey

class OverlayPrefs(context: Context) {
    private val store = DeferredPreferenceStore(context, "overlay_prefs")

    fun isOverlayEnabled(): Boolean {
        return store.getBoolean(KEY_ENABLED, false)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        store.update { put(KEY_ENABLED, enabled) }
    }

    fun isAutoRefreshEnabled(): Boolean {
        return store.getBoolean(KEY_AUTO_REFRESH, true)
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        store.update { put(KEY_AUTO_REFRESH, enabled) }
    }

    fun isAutoHideEnabled(): Boolean {
        return store.getBoolean(KEY_AUTO_HIDE, true)
    }

    fun setAutoHideEnabled(enabled: Boolean) {
        store.update { put(KEY_AUTO_HIDE, enabled) }
    }

    fun isOutputOnlyEnabled(): Boolean {
        return store.getBoolean(KEY_OUTPUT_ONLY, false)
    }

    fun setOutputOnlyEnabled(enabled: Boolean) {
        store.update { put(KEY_OUTPUT_ONLY, enabled) }
    }

    fun isOverlayPaused(): Boolean {
        return store.getBoolean(KEY_OVERLAY_PAUSED, false)
    }

    fun setOverlayPaused(paused: Boolean) {
        store.update { put(KEY_OVERLAY_PAUSED, paused) }
    }

    fun isDismissOnOutsideTapEnabled(): Boolean {
        return store.getBoolean(KEY_OVERLAY_DISMISS_OUTSIDE, true)
    }

    fun setDismissOnOutsideTapEnabled(enabled: Boolean) {
        store.update { put(KEY_OVERLAY_DISMISS_OUTSIDE, enabled) }
    }

    fun isVolumeKeyOnlyWhenPlayingEnabled(): Boolean {
        return store.getBoolean(KEY_VOLUME_KEY_ONLY_WHEN_PLAYING, false)
    }

    fun setVolumeKeyOnlyWhenPlayingEnabled(enabled: Boolean) {
        store.update { put(KEY_VOLUME_KEY_ONLY_WHEN_PLAYING, enabled) }
    }

    fun isRootAudioFallbackEnabled(): Boolean {
        return store.getBoolean(KEY_ROOT_AUDIO_FALLBACK, false)
    }

    fun setRootAudioFallbackEnabled(enabled: Boolean) {
        store.update { put(KEY_ROOT_AUDIO_FALLBACK, enabled) }
    }

    fun getOverlayFocusBlockList(): Set<String> {
        return store.getStringSet(KEY_OVERLAY_FOCUS_BLOCKLIST) ?: emptySet()
    }

    fun setOverlayFocusBlockList(packages: Set<String>) {
        store.update { put(KEY_OVERLAY_FOCUS_BLOCKLIST, packages.toSet()) }
    }

    fun getOverlayIgnorePackageList(): Set<String> {
        return store.getStringSet(KEY_OVERLAY_IGNORE_PACKAGES) ?: emptySet()
    }

    fun setOverlayIgnorePackageList(packages: Set<String>) {
        store.update { put(KEY_OVERLAY_IGNORE_PACKAGES, packages.toSet()) }
    }

    fun isOverlayIgnored(key: VolumeKey): Boolean {
        return isOverlayIgnored(key.packageName, key.userId)
    }

    fun isOverlayIgnored(packageName: String, userId: Int? = null): Boolean {
        val ignored = getOverlayIgnorePackageList()
        if (ignored.contains(packageName)) return true
        if (userId != null && ignored.contains(VolumeStore.buildKey(packageName, userId))) return true
        return false
    }

    fun addOverlayIgnorePackage(packageName: String, userId: Int? = null): Boolean {
        val updated = getOverlayIgnorePackageList().toMutableSet()
        val changed = updated.add(ignoreEntry(packageName, userId))
        if (changed) {
            setOverlayIgnorePackageList(updated)
        }
        return changed
    }

    fun ensureDefaultIgnoreList(packageManager: PackageManager) {
        if (store.contains(KEY_OVERLAY_IGNORE_PACKAGES)) return
        val defaults = mutableSetOf(
            "com.android.systemui",
            "com.android.location.fused",
            "co.aospa.sense",
            "org.lineageos.lineagesettings",
        )
        runCatching {
            val apps = packageManager.getInstalledApplications(0)
            for (app in apps) {
                val label = packageManager.getApplicationLabel(app).toString()
                if (label.contains("face unlock", ignoreCase = true)) {
                    defaults.add(app.packageName)
                }
            }
        }
        if (defaults.isNotEmpty()) {
            store.update { put(KEY_OVERLAY_IGNORE_PACKAGES, defaults.toSet()) }
        }
    }

    fun setLastFocusedPackage(packageName: String?) {
        store.update {
            if (packageName == null) remove(KEY_LAST_FOCUSED_PACKAGE)
            else put(KEY_LAST_FOCUSED_PACKAGE, packageName)
        }
    }

    fun getLastFocusedPackage(): String? {
        return store.getString(KEY_LAST_FOCUSED_PACKAGE)
    }

    fun shouldSuppressForFocusedPackage(): Boolean {
        val focused = getLastFocusedPackage() ?: return false
        val blockList = getOverlayFocusBlockList()
        return blockList.contains(focused)
    }

    fun setOverlaySnoozeUntil(untilElapsedRealtime: Long) {
        store.update { put(KEY_OVERLAY_SNOOZE_UNTIL, untilElapsedRealtime) }
    }

    fun getOverlaySnoozeUntil(): Long {
        return store.getLong(KEY_OVERLAY_SNOOZE_UNTIL) ?: 0L
    }

    fun clearOverlaySnooze() {
        store.update { remove(KEY_OVERLAY_SNOOZE_UNTIL) }
    }

    companion object {
        private fun ignoreEntry(packageName: String, userId: Int?): String {
            return if (userId == null) packageName else VolumeStore.buildKey(packageName, userId)
        }

        private const val KEY_ENABLED = "overlay_enabled"
        private const val KEY_AUTO_REFRESH = "overlay_auto_refresh"
        private const val KEY_AUTO_HIDE = "overlay_auto_hide"
        private const val KEY_OUTPUT_ONLY = "overlay_output_only"
        private const val KEY_VOLUME_KEY_ONLY_WHEN_PLAYING = "overlay_volume_key_only_when_playing"
        private const val KEY_OVERLAY_FOCUS_BLOCKLIST = "overlay_focus_blocklist"
        private const val KEY_OVERLAY_IGNORE_PACKAGES = "overlay_ignore_packages"
        private const val KEY_LAST_FOCUSED_PACKAGE = "overlay_last_focused_package"
        private const val KEY_OVERLAY_SNOOZE_UNTIL = "overlay_snooze_until_elapsed"
        private const val KEY_OVERLAY_PAUSED = "overlay_paused"
        private const val KEY_OVERLAY_DISMISS_OUTSIDE = "overlay_dismiss_outside"
        private const val KEY_ROOT_AUDIO_FALLBACK = "overlay_root_audio_fallback"
    }
}
