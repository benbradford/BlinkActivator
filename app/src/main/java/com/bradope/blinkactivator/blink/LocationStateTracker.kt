package com.bradope.blinkactivator.blink

import android.location.Location
import kotlin.math.sqrt

open class LocationStateTracker(blinkSettings: BlinkSettings) {
    enum class LocationState {
        AT_HOME,
        OUT,
        UNKNOWN
    }

    val homeLocationFetcher = getSettingFetcher(blinkSettings::homeLocation)
    val minDistanceFromHomeFetcher = getSettingFetcher(blinkSettings::minDistFromHome)

    fun getLocationStateForLocation(location: Location): LocationState {
        val lat = location.latitude - homeLocationFetcher().latitude
        val lon = location.longitude - homeLocationFetcher().longitude
        val distSquared = (lat*lat) + (lon*lon)
        val distance = sqrt(distSquared)
        val minAllowedDistance = minDistanceFromHomeFetcher()/ 100000.0
        return if (distance < minAllowedDistance) LocationState.AT_HOME else LocationState.OUT
    }

}