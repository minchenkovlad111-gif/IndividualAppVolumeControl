package com.duda.appvolumecontrol.core

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlin.math.min

class LocationRuleManager(private val context: Context) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val store = LocationRuleStore(context)
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleLocation(location)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        if (!store.hasLocationPermission(context)) return
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

    fun stop() {
        if (!running) return
        running = false
        locationManager.removeUpdates(listener)
    }

    @SuppressLint("MissingPermission")
    private fun updateFromLastKnown() {
        if (!store.hasLocationPermission(context)) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        var best: Location? = null
        for (provider in providers) {
            val location = runCatching { locationManager.getLastKnownLocation(provider) }.getOrNull()
            if (location != null) {
                if (best == null || location.time > best!!.time) {
                    best = location
                }
            }
        }
        if (best != null) handleLocation(best)
    }

    private fun handleLocation(location: Location) {
        val locations = store.listLocations()
        val matchedId = resolveLocationId(locations, location)
        store.setCurrentLocationId(matchedId)
        store.setLastFixTime(location.time)
    }

    private fun resolveLocationId(
        locations: List<LocationRuleStore.LocationRule>,
        location: Location,
    ): String? {
        if (locations.isEmpty()) return null
        var bestId: String? = null
        var bestDistance = Double.MAX_VALUE
        for (entry in locations) {
            val distance = distanceMeters(
                location.latitude,
                location.longitude,
                entry.latitude,
                entry.longitude,
            )
            if (distance <= entry.radiusMeters && distance < bestDistance) {
                bestDistance = distance
                bestId = entry.id
            }
        }
        return bestId
    }

    private fun distanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return min(results[0].toDouble(), Double.MAX_VALUE)
    }

    companion object {
        private const val MIN_TIME_MS = 60_000L
        private const val MIN_DISTANCE_M = 50f
    }
}
