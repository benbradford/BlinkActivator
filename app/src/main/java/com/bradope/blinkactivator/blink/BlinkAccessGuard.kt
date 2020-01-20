package com.bradope.blinkactivator.blink

open class BlinkAccessGuard {

    private var pauseAccess = false
    private var isInSchedule = true

    fun canAccessBlink() = isInSchedule && pauseAccess == false

    fun pauseAccess() {
        pauseAccess = true
    }

    fun resumeAccess() {
        pauseAccess = false
    }

    fun setScheduledToPerformChecks(shouldPerformChecks: Boolean) {
        isInSchedule = shouldPerformChecks
    }

}