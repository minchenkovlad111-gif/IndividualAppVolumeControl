package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context

class AudioSettingsPrefs(context: Context) {
    private val store = DeferredPreferenceStore(context, PREFS_NAME)

    fun isAutoResumeEnabled(): Boolean {
        return store.getBoolean(KEY_AUTO_RESUME, true)
    }

    fun setAutoResumeEnabled(enabled: Boolean) {
        store.update { put(KEY_AUTO_RESUME, enabled) }
    }

    companion object {
        private const val PREFS_NAME = "audio_settings"
        private const val KEY_AUTO_RESUME = "auto_resume"
    }
}
