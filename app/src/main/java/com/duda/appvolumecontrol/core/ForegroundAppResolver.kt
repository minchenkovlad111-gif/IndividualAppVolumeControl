package com.duda.appvolumecontrol.core

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

class ForegroundAppResolver(private val context: Context) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    private val appOpsManager =
        context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager

    fun getForegroundPackage(): String? {
        val usageStatsManager = usageStatsManager ?: return null
        if (!hasUsageAccess()) return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - WINDOW_MS
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        ).orEmpty()
        if (stats.isEmpty()) return null
        return stats.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    fun hasUsageAccess(): Boolean {
        val appOpsManager = appOpsManager ?: return true
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOpsManager.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_FOREGROUND
    }

    companion object {
        private const val WINDOW_MS = 10_000L
    }
}
