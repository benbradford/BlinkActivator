package com.bradope.blinkactivator.blink

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

open class BackOff(val quitRequested: AtomicBoolean, val maxWaitTimeInSeconds: Int) {
    private val LOG_TAG = "bradope_log " + BackOff::class.java.simpleName

    fun withBackOff(func: (Int) -> Boolean, lastWaitTime: Int): Boolean {
        var timeOfLastRequest = System.currentTimeMillis() / 1000
        while (!quitRequested.get()) {
            Thread.sleep(1000L)
            val currentTime = System.currentTimeMillis() / 1000
            if (currentTime > timeOfLastRequest + lastWaitTime) {

                val nextWaitTime = lastWaitTime * 2
                if (nextWaitTime >= maxWaitTimeInSeconds) {
                    Log.w(LOG_TAG, "max wait time exceeded, giving up")
                    return false
                }

                Log.i(LOG_TAG, "retrying api call with $nextWaitTime")
                return func(nextWaitTime)
            }
        }
        return false
    }
}

class BackOffFactory {
    fun makeBackOff(quitRequested: AtomicBoolean, maxWaitTimeInSeconds: Int) =
        BackOff(
            quitRequested,
            maxWaitTimeInSeconds
        )
}
