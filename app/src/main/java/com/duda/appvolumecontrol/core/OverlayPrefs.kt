package com.duda.appvolumecontrol.core

import android.content.Context
import android.content.pm.PackageManager

class OverlayPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("overlay_prefs", Context.MODE_PRIVATE)

    fun isOverlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, false)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isAutoRefreshEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_REFRESH, true)
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_REFRESH, enabled).apply()
    }

    fun isAutoHideEnabled(): Boolean {
        return prefs.getBoolean(KEY_AUTO_HIDE, true)
    }

    fun setAutoHideEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE, enabled).apply()
    }

    fun isOutputOnlyEnabled(): Boolean {
        return prefs.getBoolean(KEY_OUTPUT_ONLY, false)
    }

    fun setOutputOnlyEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OUTPUT_ONLY, enabled).apply()
    }

    fun isOverlayPaused(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_PAUSED, false)
    }

    fun setOverlayPaused(paused: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_PAUSED, paused).apply()
    }

    fun isVolumeKeyOnlyWhenPlayingEnabled(): Boolean {
        return prefs.getBoolean(KEY_VOLUME_KEY_ONLY_WHEN_PLAYING, false)
    }

    fun setVolumeKeyOnlyWhenPlayingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOLUME_KEY_ONLY_WHEN_PLAYING, enabled).apply()
    }

    fun getOverlayFocusBlockList(): Set<String> {
        return prefs.getStringSet(KEY_OVERLAY_FOCUS_BLOCKLIST, emptySet())?.toSet() ?: emptySet()
    }

    fun setOverlayFocusBlockList(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_OVERLAY_FOCUS_BLOCKLIST, packages).apply()
    }

    fun getOverlayIgnorePackageList(): Set<String> {
        return prefs.getStringSet(KEY_OVERLAY_IGNORE_PACKAGES, emptySet())?.toSet() ?: emptySet()
    }

    fun setOverlayIgnorePackageList(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_OVERLAY_IGNORE_PACKAGES, packages).apply()
    }

    fun ensureDefaultIgnoreList(packageManager: PackageManager) {
        if (prefs.contains(KEY_OVERLAY_IGNORE_PACKAGES)) return
        val defaults = mutableSetOf("com.android.systemui", "co.aospa.sense")
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
            prefs.edit().putStringSet(KEY_OVERLAY_IGNORE_PACKAGES, defaults).apply()
        }
    }

    fun setLastFocusedPackage(packageName: String?) {
        prefs.edit().putString(KEY_LAST_FOCUSED_PACKAGE, packageName).apply()
    }

    fun getLastFocusedPackage(): String? {
        return prefs.getString(KEY_LAST_FOCUSED_PACKAGE, null)
    }

    fun shouldSuppressForFocusedPackage(): Boolean {
        val focused = getLastFocusedPackage() ?: return false
        val blockList = getOverlayFocusBlockList()
        return blockList.contains(focused)
    }

    fun setOverlaySnoozeUntil(untilElapsedRealtime: Long) {
        prefs.edit().putLong(KEY_OVERLAY_SNOOZE_UNTIL, untilElapsedRealtime).apply()
    }

    fun getOverlaySnoozeUntil(): Long {
        return prefs.getLong(KEY_OVERLAY_SNOOZE_UNTIL, 0L)
    }

    fun clearOverlaySnooze() {
        prefs.edit().remove(KEY_OVERLAY_SNOOZE_UNTIL).apply()
    }

    companion object {
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
    }
}
