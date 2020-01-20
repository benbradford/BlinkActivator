package com.bradope.blinkactivator.blink

import com.google.android.gms.maps.model.LatLng

class BlinkSettings (
    var homeLocation: LatLng = LatLng(0.0, 0.0),
    var minDistFromHome: Double = 0.001583,

    var numLocationLogsOutToArm: Int = 3,

    var maxStatusRefreshBackoffTimeInSeconds: Int = 10,
    var maxCommandStatusCheckBackoffTimeInSeconds: Int = 10,
    var apiCallTimeoutInSeconds: Double = 10.0,

    var renewSessionIntervalInSeconds: Int = 60 * 60,
    var refreshStatusIntervalInSeconds: Int = 60 * 1,

    var enableAfterTime: HoursAndMins? = null,
    var disableAfterTime: HoursAndMins? = null
) {

    fun fetchHomeLocation() = { -> homeLocation}
    fun fetchMinDistFromHome() = { -> minDistFromHome}

    fun fetchNumLocationLogsOutToArm() = { -> numLocationLogsOutToArm }

    fun fetchMaxStatusRefreshBackoffTimeInSeconds() = { -> maxStatusRefreshBackoffTimeInSeconds }
    fun fetchMaxCommandStatusCheckBackoffTimeInSeconds() = { -> maxCommandStatusCheckBackoffTimeInSeconds}
    fun fetchBlinkApiTimeoutInSeconds() = { -> apiCallTimeoutInSeconds }

    fun fetchRenewSessionIntervalInSeconds() = { -> renewSessionIntervalInSeconds }
    fun fetchRefreshStatusIntervalInSeconds() = { -> refreshStatusIntervalInSeconds }

}