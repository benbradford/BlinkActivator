package com.bradope.blinkactivator.blink

import android.util.Log

// "schedule":[{"action":"arm","devices":[],"dow":["sun","mon","tue","wed","thu","fri","sat"],"id":0,"time":"2020-01-07 23:00:00 +0000"},{"action":"disarm","devices":[],"dow":["sun","mon","tue","wed","thu","fri","sat"],"id":1,"time":"2020-01-07 06:15:00 +0000"}],
data class BlinkDailySchedule(var scheduledDisarmTime: HoursAndMins?, var scheduledArmTime: HoursAndMins?)

open class BlinkScheduleHandler(
    val blinkAccessGuard: BlinkAccessGuard,
    val hoursAndMinsFactory: HoursAndMinsFactory,
    var onOffTimes: BlinkDailySchedule) {

    fun performCheck() {
        Log.i("bradope_log_schedule" ,"performing check")
        blinkAccessGuard.setScheduledToPerformChecks(isScheduledToPerformChecks())
    }

    private fun isScheduledToPerformChecks(): Boolean {
        val enableAfter = onOffTimes.scheduledDisarmTime
        val disableAfter = onOffTimes.scheduledArmTime
        if (enableAfter == null || disableAfter == null) return true

        val now = hoursAndMinsFactory.now()

        val before = HoursAndMins.compare(enableAfter, now)
        val after = HoursAndMins.compare(disableAfter, now)
        return (before == 1 || before == 0) && after == -1
    }
}