package com.duda.appvolumecontrol.core

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object SetupStatus {
    fun isControlReady(context: Context, diagnostics: DiagnosticsStore = DiagnosticsStore(context)): Boolean {
        val appInfo = context.applicationInfo
        val isPrivApp = isPrivilegedApp(appInfo)
        val hasModifyAudio = context.checkSelfPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val snapshot = diagnostics.snapshot()
        val hiddenApiOk = snapshot.hiddenApiVmRuntimeOk == 1 || snapshot.hiddenApiPolicyOk == 1
        return isPrivApp && hasModifyAudio && hiddenApiOk
    }

    fun isPrivilegedApp(appInfo: ApplicationInfo): Boolean {
        return try {
            val field = ApplicationInfo::class.java.getDeclaredField("FLAG_PRIVILEGED").apply {
                isAccessible = true
            }
            val flag = field.getInt(null)
            (appInfo.flags and flag) != 0
        } catch (_: Exception) {
            false
        }
    }
}
