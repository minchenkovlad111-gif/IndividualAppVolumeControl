package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context

class NotificationPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPresentationMode(): String {
        val stored = prefs.getString(KEY_PRESENTATION_MODE, MODE_MULTI_ROW_PAGED)
        return if (stored == MODE_SINGLE_ROW_PAGED) stored else MODE_MULTI_ROW_PAGED
    }

    fun setPresentationMode(mode: String) {
        val normalized = if (mode == MODE_SINGLE_ROW_PAGED) mode else MODE_MULTI_ROW_PAGED
        prefs.edit().putString(KEY_PRESENTATION_MODE, normalized).apply()
    }

    fun getRowsPerPage(): Int {
        return if (getPresentationMode() == MODE_SINGLE_ROW_PAGED) 1 else 3
    }

    fun getPage(): Int {
        return prefs.getInt(KEY_PAGE, 0)
    }

    fun setPage(page: Int) {
        prefs.edit().putInt(KEY_PAGE, page.coerceAtLeast(0)).apply()
    }

    companion object {
        const val MODE_MULTI_ROW_PAGED = "multi_row_paged"
        const val MODE_SINGLE_ROW_PAGED = "single_row_paged"

        private const val PREFS_NAME = "notif_paging"
        private const val KEY_PAGE = "page"
        private const val KEY_PRESENTATION_MODE = "presentation_mode"
    }
}
