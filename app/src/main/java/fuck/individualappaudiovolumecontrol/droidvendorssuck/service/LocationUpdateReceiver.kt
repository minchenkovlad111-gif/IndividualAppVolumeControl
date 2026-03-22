package fuck.individualappaudiovolumecontrol.droidvendorssuck.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.LocationRuleManager

class LocationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val location = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED, Location::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED) as? Location
        } ?: return
        LocationRuleManager.applyLocationFix(context, location)
    }
}
