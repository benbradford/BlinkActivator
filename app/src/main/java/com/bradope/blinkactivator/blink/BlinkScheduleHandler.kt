package com.bradope.blinkactivator.blink

open class BlinkScheduleHandler(
    val blinkAccessGuard: BlinkAccessGuard,
    val hoursAndMinsFactory: HoursAndMinsFactory,
    blinkSettings: BlinkSettings) {

    private val fetchEnableAfter = fetcher(blinkSettings::enableAfterTime)
    private val fetchDisableAfter = fetcher(blinkSettings::disableAfterTime)

    fun performCheck() {
        blinkAccessGuard.setScheduledToPerformChecks(isScheduledToPerformChecks())
    }

    private fun isScheduledToPerformChecks(): Boolean {
        val enableAfter = fetchEnableAfter()
        val disableAfter = fetchDisableAfter()
        if (enableAfter == null || disableAfter == null) return true

        val now = hoursAndMinsFactory.now()

        val before = HoursAndMins.compare(enableAfter, now)
        val after = HoursAndMins.compare(disableAfter, now)
        return (before == 1 || before == 0) && after == -1
    }
}