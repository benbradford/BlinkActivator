package com.bradope.blinkactivator.blink

data class BlinkOnAndOffTimes(var enableAfterTime: HoursAndMins?, var disableAfterTime: HoursAndMins?)

open class BlinkScheduleHandler(
    val blinkAccessGuard: BlinkAccessGuard,
    val hoursAndMinsFactory: HoursAndMinsFactory,
    var onOffTimes: BlinkOnAndOffTimes) {

    fun performCheck() {
        blinkAccessGuard.setScheduledToPerformChecks(isScheduledToPerformChecks())
    }

    private fun isScheduledToPerformChecks(): Boolean {
        val enableAfter = onOffTimes.enableAfterTime
        val disableAfter = onOffTimes.disableAfterTime
        if (enableAfter == null || disableAfter == null) return true

        val now = hoursAndMinsFactory.now()

        val before = HoursAndMins.compare(enableAfter, now)
        val after = HoursAndMins.compare(disableAfter, now)
        return (before == 1 || before == 0) && after == -1
    }
}