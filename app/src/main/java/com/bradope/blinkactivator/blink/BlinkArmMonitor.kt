package com.bradope.blinkactivator.blink

import android.util.Log

const val DEFAULT_NUM_OUT_LOGS_REQUIRED_TO_DISARM = 3

class BlinkArmMonitor(
    val blinkApi: BlinkApi,
    blinkSettings: BlinkSettings
) {
    val fetchNumLocationLogsOutToArm = blinkSettings.fetchNumLocationLogsOutToArm()

    var numConsecutiveOutLogs = 0
    var numConsecutiveHomeLogs = 0

    fun logLocationIn(): Boolean {
        numConsecutiveOutLogs = 0
        ++numConsecutiveHomeLogs
        return blinkApi.disarm()
    }

    fun logLocationOut(): Boolean {
        ++numConsecutiveOutLogs
        numConsecutiveHomeLogs = 0

        return (numConsecutiveOutLogs == fetchNumLocationLogsOutToArm()) && arm()
    }

    private fun arm(attempts: Int = 0): Boolean {
        Log.i("bradope_log_armMonitor", " attemping to arm")
        if (blinkApi.arm()) {
            return true
        }
        if (attempts < fetchNumLocationLogsOutToArm())
            return arm(attempts+1)
        return false
    }

}
