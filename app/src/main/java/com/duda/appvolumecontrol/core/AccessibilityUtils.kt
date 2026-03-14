package com.duda.appvolumecontrol.core

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils

object AccessibilityUtils {
    fun isServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val enabled = Settings.Secure.getInt(
            context.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        ) == 1
        if (!enabled) return false
        val service = ComponentName(context, serviceClass).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        for (item in splitter) {
            if (item.equals(service, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
