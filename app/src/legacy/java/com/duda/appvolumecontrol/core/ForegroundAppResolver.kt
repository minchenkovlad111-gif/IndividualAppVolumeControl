package com.duda.appvolumecontrol.core

import android.content.Context

class ForegroundAppResolver(private val context: Context) {
    fun getForegroundPackage(): String? {
        return null
    }

    fun hasUsageAccess(): Boolean {
        return false
    }
}
