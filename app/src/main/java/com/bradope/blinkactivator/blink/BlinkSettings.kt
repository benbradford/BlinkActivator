package com.bradope.blinkactivator.blink

import com.google.android.gms.location.LocationRequest
import com.google.android.gms.maps.model.LatLng
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

interface LocationPriorityIntMap {
    fun mapToValue(): Int
}

enum class LocationPriority: LocationPriorityIntMap {
    HIGH_ACCURACY { override fun mapToValue() = LocationRequest.PRIORITY_HIGH_ACCURACY },
    BALANCED_POWER_ACCURACY  { override fun mapToValue() = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY },
    LOW_POWER  { override fun mapToValue() = LocationRequest.PRIORITY_LOW_POWER },
    NO_POWER  { override fun mapToValue() = LocationRequest.PRIORITY_NO_POWER }
}

data class BlinkSettings (
    var homeLocation: LatLng = LatLng(0.0, 0.0),
    var minDistFromHome: Double = 0.001583,

    var numLocationLogsOutToArm: Int = 3,

    var maxStatusRefreshBackoffTimeInSeconds: Int = 10,
    var maxCommandStatusCheckBackoffTimeInSeconds: Int = 10,
    var apiCallTimeoutInSeconds: Double = 10.0,

    var renewSessionIntervalInMinutes: Int = 60,
    var refreshStatusIntervalInMinutes: Int = 1,
    var checkScheduleIntervalInMinutes: Int = 5,

    var locationUpdateIntervalInSeconds: Int = 10,
    var fastestLocationUpdateIntervalInSeconds: Int = 1,
    var locationPriority: LocationPriority = LocationPriority.HIGH_ACCURACY
 )

fun <T> getSettingFetcher(field: KProperty<T>): () -> T = { -> field.call() }
fun <T> syncSettingIfChanged(option: T, prop: KMutableProperty<T>): Boolean {
    if (option != prop.call()) {
        prop.setter.call(option)
        return true
    }
    return false
}