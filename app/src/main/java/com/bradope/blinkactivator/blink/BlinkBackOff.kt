package com.bradope.blinkactivator.blink

import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean


open class BackOff(val quitRequested: AtomicBoolean, val refreshRequested: AtomicBoolean, val maxWaitTimeInSeconds: Long) {
    private val LOG_TAG = "bradope_log " + BackOff::class.java.simpleName

    fun withBackOff(func: (lastWaitTime: Long)->Boolean, lastWaitTime: Long): Boolean {
        var timeOfLastRequest = System.currentTimeMillis() / 1000
        while (!quitRequested.get()) {
            if (refreshRequested.getAndSet(false)) {
                Log.i(LOG_TAG, "exiting back-off due to refresh request")
                return false
            }
            Thread.sleep(1000L)
            val currentTime = System.currentTimeMillis() / 1000
            if (currentTime > timeOfLastRequest + lastWaitTime) {

                val nextWaitTime = (lastWaitTime * 2).coerceAtMost(60 * 15)
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
    private val MAX_WAIT_TIME_IN_SECONDS = 20L
    fun makeBackOff(quitRequested: AtomicBoolean, refreshRequested: AtomicBoolean, maxWaitTimeInSeconds: Long = MAX_WAIT_TIME_IN_SECONDS) =
        BackOff(
            quitRequested,
            refreshRequested,
            maxWaitTimeInSeconds
        )
}
