package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context

class NotificationPrefs(context: Context) {
    private val store = DeferredPreferenceStore(context, PREFS_NAME)

    fun getPresentationMode(): String {
        val stored = store.getString(KEY_PRESENTATION_MODE) ?: MODE_MULTI_ROW_PAGED
        return when (stored) {
            MODE_SINGLE_ROW_PAGED,
            MODE_PER_APP -> stored
            else -> MODE_MULTI_ROW_PAGED
        }
    }

    fun setPresentationMode(mode: String) {
        val normalized = when (mode) {
            MODE_SINGLE_ROW_PAGED,
            MODE_PER_APP -> mode
            else -> MODE_MULTI_ROW_PAGED
        }
        store.update { put(KEY_PRESENTATION_MODE, normalized) }
    }

    fun getRowsPerPage(): Int {
        return when (getPresentationMode()) {
            MODE_SINGLE_ROW_PAGED,
            MODE_PER_APP -> 1
            else -> 3
        }
    }

    fun getPage(): Int {
        return store.getInt(KEY_PAGE) ?: 0
    }

    fun setPage(page: Int) {
        store.update { put(KEY_PAGE, page.coerceAtLeast(0)) }
    }

    companion object {
        const val MODE_MULTI_ROW_PAGED = "multi_row_paged"
        const val MODE_SINGLE_ROW_PAGED = "single_row_paged"
        const val MODE_PER_APP = "per_app_notifications"

        private const val PREFS_NAME = "notif_paging"
        private const val KEY_PAGE = "page"
        private const val KEY_PRESENTATION_MODE = "presentation_mode"
    }
}
