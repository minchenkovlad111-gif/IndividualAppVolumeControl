package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeferredPreferenceStore(
    context: Context,
    prefsName: String,
    private val flushDelayMs: Long = DEFAULT_FLUSH_DELAY_MS,
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    private val lock = Any()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "DeferredPreferenceStore-$prefsName")
    }

    private val pendingValues = mutableMapOf<String, Any?>()
    private var clearRequested = false
    private var dirtyGeneration = 0
    private var flushScheduled = false

    fun contains(key: String): Boolean = snapshot()[key] != null

    fun getFloat(key: String): Float? = (snapshot()[key] as? Number)?.toFloat()

    fun getInt(key: String): Int? = (snapshot()[key] as? Number)?.toInt()

    fun getLong(key: String): Long? = (snapshot()[key] as? Number)?.toLong()

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return (snapshot()[key] as? Boolean) ?: defaultValue
    }

    fun getString(key: String): String? = snapshot()[key] as? String

    fun getStringSet(key: String): Set<String>? {
        @Suppress("UNCHECKED_CAST")
        return (snapshot()[key] as? Set<String>)?.toSet()
    }

    fun all(): Map<String, Any?> = snapshot()

    fun update(transform: MutableMap<String, Any?>.() -> Unit) {
        synchronized(lock) {
            pendingValues.transform()
            dirtyGeneration += 1
            scheduleFlushLocked(flushDelayMs)
        }
    }

    fun clear() {
        synchronized(lock) {
            clearRequested = true
            pendingValues.clear()
            dirtyGeneration += 1
            scheduleFlushLocked(flushDelayMs)
        }
    }

    private fun snapshot(): Map<String, Any?> {
        synchronized(lock) {
            val merged = if (clearRequested) mutableMapOf() else prefs.all.toMutableMap()
            for ((key, value) in pendingValues) {
                if (value == null) {
                    merged.remove(key)
                } else {
                    merged[key] = copyValue(value)
                }
            }
            return merged
        }
    }

    private fun scheduleFlushLocked(delayMs: Long) {
        if (flushScheduled) return
        flushScheduled = true
        executor.schedule({ flushOnce() }, delayMs, TimeUnit.MILLISECONDS)
    }

    private fun flushOnce() {
        val generation: Int
        val valuesToWrite: Map<String, Any?>
        val shouldClear: Boolean
        synchronized(lock) {
            generation = dirtyGeneration
            valuesToWrite = pendingValues.mapValues { (_, value) -> value?.let(::copyValue) }
            shouldClear = clearRequested
        }
        val success = runCatching {
            val editor = prefs.edit()
            if (shouldClear) {
                editor.clear()
            }
            for ((key, value) in valuesToWrite) {
                when (value) {
                    null -> editor.remove(key)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                    else -> error("Unsupported SharedPreferences value for $key: ${value::class.java.name}")
                }
            }
            editor.commit()
        }.isSuccess
        synchronized(lock) {
            flushScheduled = false
            if (success) {
                if (dirtyGeneration == generation) {
                    pendingValues.clear()
                    clearRequested = false
                }
            }
            if (!success || dirtyGeneration != generation) {
                scheduleFlushLocked(flushDelayMs)
            }
        }
    }

    private fun copyValue(value: Any): Any {
        return when (value) {
            is Set<*> -> value.filterIsInstance<String>().toSet()
            else -> value
        }
    }

    companion object {
        private const val DEFAULT_FLUSH_DELAY_MS = 400L
    }
}
