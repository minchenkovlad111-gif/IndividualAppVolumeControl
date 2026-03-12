package com.duda.appvolumecontrol.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.TileService
import com.duda.appvolumecontrol.ui.OverlayPermissionActivity

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
                startActivityAndCollapse(intent)
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
}
