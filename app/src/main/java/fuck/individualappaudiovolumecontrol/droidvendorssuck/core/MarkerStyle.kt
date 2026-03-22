package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.graphics.Color
import kotlin.math.abs

object MarkerStyle {
    private val palette = intArrayOf(
        0xFF1E88E5.toInt(),
        0xFF43A047.toInt(),
        0xFFF4511E.toInt(),
        0xFF8E24AA.toInt(),
        0xFF00897B.toInt(),
        0xFFFDD835.toInt(),
        0xFF6D4C41.toInt(),
    )

    fun parseColorOrNull(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        return runCatching { Color.parseColor(value.trim()) }.getOrNull()
    }

    fun normalizeColorString(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        return if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    }

    fun resolveMarkerColor(packageName: String, overrideColor: String?): Int {
        val parsed = parseColorOrNull(overrideColor)
        return parsed ?: baseColorFor(packageName)
    }

    fun resolveSegmentColor(packageName: String, overrideColor: String?): Int {
        val parsed = parseColorOrNull(overrideColor)
        if (parsed != null) return parsed
        val base = baseColorFor(packageName)
        return (base and 0x00FFFFFF) or 0x33000000
    }

    private fun baseColorFor(packageName: String): Int {
        val idx = abs(packageName.hashCode()) % palette.size
        return palette[idx]
    }
}
