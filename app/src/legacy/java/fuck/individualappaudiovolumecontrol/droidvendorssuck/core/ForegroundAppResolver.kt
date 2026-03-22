package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context

class ForegroundAppResolver(private val context: Context) {
    fun getForegroundPackage(): String? {
        return null
    }

    fun hasUsageAccess(): Boolean {
        return false
    }
}
