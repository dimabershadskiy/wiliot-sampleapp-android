package com.wiliot.wiliotsampleapp.wlt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.io.Serializable
import java.util.*

data class Location(var lat: Double, var lng: Double) : Serializable {
    init {
        this.lat = lat.apiPrecise()
        this.lng = lng.apiPrecise()
    }
}

private fun Double.apiPrecise() = String.format(Locale.ROOT, "%.5f", this).toDouble()

object LocationManager {

    private const val TAG = "__LocationManager"

    private var lastLocation: Location? = null
    private lateinit var locationCallback: LocationCallback

    fun startObserveLocation(context: Context) {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            null != lastLocation
        ) {
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                lastLocation = p0.lastLocation
            }
        }

        startLocationUpdates(context)
    }

    @SuppressLint("MissingPermission")
    fun stopLocationUpdates(context: Context?) {
        context?.let { c ->
            try {
                LocationServices.getFusedLocationProviderClient(c)
                    .removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                Log.e(TAG, "Error occurred", e)
            }
        }
    }

    fun getLastLocation(): Location? =
        lastLocation

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            lastLocation = location
        }
        fusedLocationProviderClient
            .requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
    }

    @Suppress("DEPRECATION")
    private val locationRequest = LocationRequest.create().apply {
        this.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        this.interval = 15000
        this.fastestInterval = 1000
    }

    fun getLocationSettings(): LocationSettingsRequest =
        LocationSettingsRequest.Builder().addLocationRequest(locationRequest).setNeedBle(true)
            .build()
}