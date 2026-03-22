package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context

class ActiveAppStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("active_app_store", Context.MODE_PRIVATE)

    fun recordActive(key: VolumeStore.VolumeKey, timestamp: Long = System.currentTimeMillis()) {
        val storageKey = key.storageKey()
        val first = prefs.getLong(firstKey(storageKey), 0L)
        val last = prefs.getLong(lastKey(storageKey), 0L)
        if (first > 0L && timestamp - last < MIN_PERSIST_INTERVAL_MS) {
            return
        }
        prefs.edit().apply {
            if (first <= 0L) {
                putLong(firstKey(storageKey), timestamp)
            }
            putLong(lastKey(storageKey), timestamp)
        }.apply()
    }

    fun getInfo(key: VolumeStore.VolumeKey): ActiveInfo? {
        val storageKey = key.storageKey()
        val first = prefs.getLong(firstKey(storageKey), 0L)
        val last = prefs.getLong(lastKey(storageKey), 0L)
        if (first <= 0L && last <= 0L) return null
        return ActiveInfo(
            firstActiveTime = if (first > 0L) first else null,
            lastActiveTime = if (last > 0L) last else null,
        )
    }

    data class ActiveInfo(
        val firstActiveTime: Long?,
        val lastActiveTime: Long?,
    )

    companion object {
        private const val MIN_PERSIST_INTERVAL_MS = 15_000L
    }

    private fun firstKey(storageKey: String): String = "${storageKey}:first_active"
    private fun lastKey(storageKey: String): String = "${storageKey}:last_active"
}
