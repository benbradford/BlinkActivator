package com.bradope.blinkactivator.blink

import android.util.Log

open class BlinkAccessGuard {

    private var pauseAccess = false
    private var isInSchedule = true

    fun canAccessBlink() = isInSchedule && pauseAccess == false
    fun isPaused() = pauseAccess

    fun pauseAccess() {
        pauseAccess = true
    }

    fun resumeAccess() {
        pauseAccess = false
    }

    fun setScheduledToPerformChecks(shouldPerformChecks: Boolean) {
        Log.i("bradope_log_guard",  "shouldPerformChecks: $shouldPerformChecks" )
        isInSchedule = shouldPerformChecks
    }

}