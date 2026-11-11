package com.jpb.steptrackr.utils

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppLocationTracker(context: Context) {
    private val appContext = context.applicationContext
    private val database = StepDatabase.getDatabase(appContext)

    // GMS Fused Location Client
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)

    // Non-GMS Android Framework Location Manager
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var isTracking = false

    private val gmsLocationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { saveRoutePoint(it.latitude, it.longitude) }
        }
    }

    private val nonGmsLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            saveRoutePoint(location.latitude, location.longitude)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun saveRoutePoint(lat: Double, lon: Double) {
        CoroutineScope(Dispatchers.IO).launch {
            database.stepDao().insertRoutePoint(
                RoutePointEntity(
                    latitude = lat,
                    longitude = lon,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(context: Context) {
        if (isTracking) return
        isTracking = true

        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

        if (gmsAvailable) {
            try {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000L)
                    .setMinUpdateIntervalMillis(5000L)
                    .build()
                fusedLocationClient.requestLocationUpdates(request, gmsLocationCallback, Looper.getMainLooper())
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Fallback to Non-GMS LocationManager
        try {
            val provider = when {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return
            }
            locationManager.requestLocationUpdates(provider, 5000L, 5f, nonGmsLocationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopTracking() {
        if (!isTracking) return
        isTracking = false
        try {
            fusedLocationClient.removeLocationUpdates(gmsLocationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            locationManager.removeUpdates(nonGmsLocationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}