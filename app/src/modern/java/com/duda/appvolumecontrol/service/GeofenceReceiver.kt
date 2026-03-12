package com.duda.appvolumecontrol.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.duda.appvolumecontrol.core.LocationRuleManager
import com.duda.appvolumecontrol.core.LocationRuleStore
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Geofence error: ${event.errorCode}")
            return
        }
        val store = LocationRuleStore(context)
        val triggeringLocation = event.triggeringLocation
        if (triggeringLocation != null) {
            LocationRuleManager.applyLocationFix(context, triggeringLocation)
            return
        }
        val ids = event.triggeringGeofences?.mapNotNull { it.requestId } ?: emptyList()
        if (ids.isEmpty()) return
        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                store.setCurrentLocationId(ids.first())
                store.setLastFixTime(System.currentTimeMillis())
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                val current = store.getCurrentLocationId()
                if (current != null && ids.contains(current)) {
                    store.setCurrentLocationId(null)
                    store.setLastFixTime(System.currentTimeMillis())
                }
            }
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "GeofenceReceiver"
    }
}
