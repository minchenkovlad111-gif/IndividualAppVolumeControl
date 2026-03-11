package com.duda.appvolumecontrol.core

import android.util.Log

object HiddenApi {
    private const val TAG = "HiddenApi"

    fun tryAllowHiddenApiAccess() {
        try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            val setExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            val runtime = getRuntime.invoke(null)
            setExemptions.invoke(runtime, arrayOf("Landroid/media/", "Landroid/app/"))
            Log.i(TAG, "Hidden API exemptions applied")
        } catch (t: Throwable) {
            Log.w(TAG, "Hidden API exemption failed", t)
        }
    }
}
