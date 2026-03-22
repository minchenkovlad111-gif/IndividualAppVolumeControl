package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import fuck.individualappaudiovolumecontrol.droidvendorssuck.service.LocationUpdateReceiver
import kotlin.math.min

class LocationRuleManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val store = LocationRuleStore(context)
    private val diagnosticsStore = DiagnosticsStore(context)
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Deprecated in Android API")
        @Suppress("DEPRECATION")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (!store.hasLocationPermission(context)) return
        if (!running) {
            running = true
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                if (!locationManager.isProviderEnabled(provider)) continue
                locationManager.requestLocationUpdates(
                    provider,
                    MIN_TIME_MS,
                    MIN_DISTANCE_M,
                    listener,
                    Looper.getMainLooper(),
                )
            }
            updateFromLastKnown()
        }
        syncBackgroundUpdates()
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        if (!running) return
        running = false
        locationManager.removeUpdates(listener)
        if (!store.isBackgroundUpdatesEnabled()) {
            cancelBackgroundUpdates()
        }
    }

    fun syncBackgroundUpdates() {
        if (!store.isBackgroundUpdatesEnabled()) {
            cancelBackgroundUpdates()
            return
        }
        if (!store.hasLocationPermission(context)) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val granted = context.checkSelfPermission(
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        requestBackgroundUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun updateFromLastKnown() {
        if (!store.hasLocationPermission(context)) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: Location? = null
        for (provider in providers) {
            val location = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            if (location != null && (best == null || location.time > best.time)) {
                best = location
            }
        }
        if (best != null) handleLocation(best)
    }

    private fun handleLocation(location: Location) {
        applyLocationFix(context, location)
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val isMock = when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> location.isMock
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2 -> {
                @Suppress("DEPRECATION")
                location.isFromMockProvider
            }
            else -> false
        }
        diagnosticsStore.recordLocationFix(
            provider = location.provider,
            timeMs = location.time,
            accuracyMeters = accuracy,
            isMock = isMock,
        )
    }

    @SuppressLint("MissingPermission")
    private fun requestBackgroundUpdates() {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val pendingIntent = buildPendingIntent()
        for (provider in providers) {
            if (!locationManager.isProviderEnabled(provider)) continue
            locationManager.requestLocationUpdates(
                provider,
                MIN_TIME_MS,
                MIN_DISTANCE_M,
                pendingIntent,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun cancelBackgroundUpdates() {
        val pendingIntent = buildPendingIntent()
        locationManager.removeUpdates(pendingIntent)
    }

    private fun buildPendingIntent(): PendingIntent {
        val intent = Intent(context, LocationUpdateReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, 0, intent, flags)
    }

    companion object {
        private const val MIN_TIME_MS = 60_000L
        private const val MIN_DISTANCE_M = 50f

        fun applyLocationFix(context: Context, location: Location) {
            val store = LocationRuleStore(context)
            val locations = store.listLocations()
            val matchedId = resolveLocationIdStatic(locations, location)
            store.setCurrentLocationId(matchedId)
            store.setLastFixTime(location.time)
        }

        private fun resolveLocationIdStatic(
            locations: List<LocationRuleStore.LocationRule>,
            location: Location,
        ): String? {
            if (locations.isEmpty()) return null
            var bestId: String? = null
            var bestDistance = Double.MAX_VALUE
            for (entry in locations) {
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    entry.latitude,
                    entry.longitude,
                    results,
                )
                val distance = min(results[0].toDouble(), Double.MAX_VALUE)
                if (distance <= entry.radiusMeters && distance < bestDistance) {
                    bestDistance = distance
                    bestId = entry.id
                }
            }
            return bestId
        }
    }
}
