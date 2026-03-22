package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

object HiddenApi {
    private const val TAG = "HiddenApi"

    fun tryAllowHiddenApiAccess(context: Context) {
        var vmRuntimeOk: Boolean?
        var policyOk: Boolean?
        var vmRuntimeError: String? = null
        var policyError: String? = null

        try {
            val vmRuntimeClass = Class.forName("dalvik.system.VMRuntime")
            val getRuntime = vmRuntimeClass.getDeclaredMethod("getRuntime")
            val setExemptions = vmRuntimeClass.getDeclaredMethod(
                "setHiddenApiExemptions",
                Array<String>::class.java
            )
            val runtime = getRuntime.invoke(null)
            setExemptions.invoke(runtime, arrayOf("L"))
            Log.i(TAG, "Hidden API exemptions applied via VMRuntime")
            vmRuntimeOk = true
        } catch (t: Throwable) {
            Log.w(TAG, "Hidden API exemption via VMRuntime failed", t)
            vmRuntimeOk = false
            vmRuntimeError = t.javaClass.simpleName + ": " + (t.message ?: "unknown")
        }

        try {
            val resolver = context.contentResolver
            when {
                Build.VERSION.SDK_INT >= 29 -> {
                    Settings.Global.putInt(resolver, "hidden_api_policy", 1)
                }
                Build.VERSION.SDK_INT == 28 -> {
                    Settings.Global.putInt(resolver, "hidden_api_policy_pre_p_apps", 1)
                    Settings.Global.putInt(resolver, "hidden_api_policy_p_apps", 1)
                }
            }
            Log.i(TAG, "Hidden API policy updated via Settings.Global")
            policyOk = true
        } catch (t: Throwable) {
            Log.w(TAG, "Hidden API policy update failed", t)
            policyOk = false
            policyError = t.javaClass.simpleName + ": " + (t.message ?: "unknown")
        }

        DiagnosticsStore(context).recordHiddenApiAttempt(vmRuntimeOk, policyOk, vmRuntimeError, policyError)
    }
}
