package fuck.individualappaudiovolumecontrol.droidvendorssuck.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import fuck.individualappaudiovolumecontrol.droidvendorssuck.ui.OverlayPermissionActivity

class OverlayTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (canDrawOverlays()) {
            val intent = Intent(this, VolumeOverlayService::class.java).apply {
                action = VolumeOverlayService.ACTION_SHOW
            }
            startService(intent)
        } else {
            val intent = Intent(this, OverlayPermissionActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                startActivityAndCollapseCompat(intent)
            } else {
                startActivity(intent)
            }
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
