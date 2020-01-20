package com.bradope.blinkactivator.blink

import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlin.math.sqrt


open class LocationStateTracker {
    enum class LocationState {
        AT_HOME,
        OUT,
        UNKNOWN
    }

    private val atHomeTolerance = 0.001583 // about 160 meters
    private val homeLocation = LatLng(51.083008, 1.161534)

    fun getLocationStateForLocation(location: Location): LocationState {
        val lat = location.latitude - homeLocation.latitude
        val lon = location.longitude - homeLocation.longitude
        val distSquared = (lat*lat) + (lon*lon)
        val distance = sqrt(distSquared)

        return if (distance < atHomeTolerance) LocationState.AT_HOME else LocationState.OUT
    }

}