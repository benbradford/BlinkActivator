package com.bradope.blinkactivator.blink

import com.bradope.blinkactivator.R
import com.google.android.gms.maps.model.LatLng
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty

data class BlinkSettings (
    var homeLocation: LatLng = LatLng(0.0, 0.0),
    var minDistFromHome: Double = 0.001583,

    var numLocationLogsOutToArm: Int = 3,

    var maxStatusRefreshBackoffTimeInSeconds: Int = 10,
    var maxCommandStatusCheckBackoffTimeInSeconds: Int = 10,
    var apiCallTimeoutInSeconds: Double = 10.0,

    var renewSessionIntervalInSeconds: Int = 60 * 60,
    var refreshStatusIntervalInSeconds: Int = 60 * 1,
    var checkScheduleIntervalInSeconds: Int = 55 * 1,

    var enableAfterTime: HoursAndMins? = null,
    var disableAfterTime: HoursAndMins? = null
)

fun <T> fetcher(field: KProperty<T>): () -> T = { -> field.call() }