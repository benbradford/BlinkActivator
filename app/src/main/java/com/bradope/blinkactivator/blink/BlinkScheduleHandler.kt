package com.bradope.blinkactivator.blink

open class BlinkScheduleHandler(
    val blinkAccessGuard: BlinkAccessGuard,
    blinkSettings: BlinkSettings) {

    private val fetchEnableAfter = fetcher(blinkSettings::enableAfterTime)
    private val fetchDisableAfter = fetcher(blinkSettings::disableAfterTime)

    fun performCheck() {
        blinkAccessGuard.setScheduledToPerformChecks(isScheduledToPerformChecks())
    }

    private fun isScheduledToPerformChecks(): Boolean {
        if (fetchEnableAfter() == null || fetchDisableAfter() == null) return true
        val now = HoursAndMins.now()
        return fetchEnableAfter()!!.isGivenTimeAfterOrEqualToMyTime(now) &&
                fetchDisableAfter()!!.isGivenTimeBeforeMyTime(now)
    }
}