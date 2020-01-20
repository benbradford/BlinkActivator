package com.bradope.blinkactivator.blink

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

open class BlinkScheduleHandler(val blinkAccessGuard: BlinkAccessGuard) {


    private var enableAfter: HoursAndMins? = null
    private var disableAfter: HoursAndMins? = null

    fun removeScheduling() {
        enableAfter = null
        disableAfter = null
    }

    fun setScheduling(enableAfterTime: HoursAndMins, disableAfterTime: HoursAndMins) {
        enableAfter = enableAfterTime
        disableAfter = disableAfterTime
    }

    fun checkShouldCheckerBeActive(): Boolean {
        if (enableAfter == null || disableAfter == null) return true
        val now = HoursAndMins.now()
        return enableAfter!!.isGivenTimeAfterOrEqualToMyTime(now) &&
                disableAfter!!.isGivenTimeBeforeMyTime(now)
    }
}