package com.duda.appvolumecontrol.core

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

object SetupStatus {
    data class PrivilegeInfo(
        val isSystemApp: Boolean,
        val isUpdatedSystemApp: Boolean,
        val isPrivilegedApp: Boolean,
        val flagPrivileged: Boolean,
        val privateFlagPrivileged: Boolean,
        val sourceDir: String?,
        val publicSourceDir: String?,
        val dataDir: String?,
    )

    fun isControlReady(context: Context, diagnostics: DiagnosticsStore = DiagnosticsStore(context)): Boolean {
        val appInfo = context.applicationInfo
        val isPrivApp = privilegeInfo(appInfo).isPrivilegedApp
        val hasModifyAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
        val snapshot = diagnostics.snapshot()
        val hiddenApiOk = snapshot.hiddenApiVmRuntimeOk == 1 || snapshot.hiddenApiPolicyOk == 1
        return isPrivApp && hasModifyAudio && hiddenApiOk
    }

    fun privilegeInfo(appInfo: ApplicationInfo): PrivilegeInfo {
        val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val flagPrivileged = isFlagSet(appInfo, "FLAG_PRIVILEGED", appInfo.flags)
        val privateFlagPrivileged = isPrivateFlagSet(appInfo, "PRIVATE_FLAG_PRIVILEGED")
        val isPrivileged = flagPrivileged || privateFlagPrivileged
        return PrivilegeInfo(
            isSystemApp = isSystemApp,
            isUpdatedSystemApp = isUpdatedSystemApp,
            isPrivilegedApp = isPrivileged,
            flagPrivileged = flagPrivileged,
            privateFlagPrivileged = privateFlagPrivileged,
            sourceDir = appInfo.sourceDir,
            publicSourceDir = appInfo.publicSourceDir,
            dataDir = appInfo.dataDir,
        )
    }

    fun isPrivilegedApp(appInfo: ApplicationInfo): Boolean {
        return privilegeInfo(appInfo).isPrivilegedApp
    }

    private fun isFlagSet(appInfo: ApplicationInfo, fieldName: String, flags: Int): Boolean {
        val flag = runCatching {
            ApplicationInfo::class.java.getDeclaredField(fieldName).apply { isAccessible = true }.getInt(null)
        }.getOrNull() ?: return false
        return (flags and flag) != 0
    }

    private fun isPrivateFlagSet(appInfo: ApplicationInfo, fieldName: String): Boolean {
        val privateFlags = runCatching {
            ApplicationInfo::class.java.getDeclaredField("privateFlags").apply { isAccessible = true }.getInt(appInfo)
        }.getOrNull() ?: return false
        val flag = runCatching {
            ApplicationInfo::class.java.getDeclaredField(fieldName).apply { isAccessible = true }.getInt(null)
        }.getOrNull() ?: return false
        return (privateFlags and flag) != 0
    }
}
