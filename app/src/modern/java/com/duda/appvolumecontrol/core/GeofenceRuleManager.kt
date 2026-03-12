package com.duda.appvolumecontrol.core

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.util.Log
import com.duda.appvolumecontrol.service.GeofenceReceiver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceRuleManager(private val context: Context) {
    private val store = LocationRuleStore(context)
    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    fun isAvailable(): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        return status == ConnectionResult.SUCCESS
    }

    fun syncGeofences() {
        if (!store.isGeofencingEnabled()) {
            removeGeofences()
            return
        }
        if (!isAvailable()) return
        if (!store.hasLocationPermission(context)) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val granted = context.checkSelfPermission(
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val locations = store.listLocations()
        if (locations.isEmpty()) {
            removeGeofences()
            return
        }
        val geofences = locations.map { entry ->
            Geofence.Builder()
                .setRequestId(entry.id)
                .setCircularRegion(entry.latitude, entry.longitude, entry.radiusMeters.toFloat())
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(geofences)
            .build()
        geofencingClient.addGeofences(request, pendingIntent())
            .addOnFailureListener { err ->
                Log.w(TAG, "Failed to add geofences", err)
            }
    }

    fun removeGeofences() {
        geofencingClient.removeGeofences(pendingIntent())
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context, GeofenceReceiver::class.java).apply {
            action = LocationManager.KEY_LOCATION_CHANGED
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        private const val TAG = "GeofenceRuleManager"
    }
}
