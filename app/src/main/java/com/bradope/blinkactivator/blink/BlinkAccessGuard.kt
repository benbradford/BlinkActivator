package com.bradope.blinkactivator.blink

open class BlinkAccessGuard {
    private var canAccess = true
    private var pauseAccess = false
    private var isInSchedule = true

    // :TODO: add something about schedule here

    fun canAccessBlink() = canAccess

    fun pauseAccess() {
        pauseAccess = true
        canAccess = false
    }

    fun resumeAccess() {
        pauseAccess = false
        if (isInSchedule ) canAccess = true
    }

    fun setScheduledToPerformChecks(shouldPerformChecks: Boolean) {
        isInSchedule =shouldPerformChecks
        if (isInSchedule && pauseAccess == false) {
            canAccess = true
        } else {
            isInSchedule = false
            canAccess = false
        }
    }

}