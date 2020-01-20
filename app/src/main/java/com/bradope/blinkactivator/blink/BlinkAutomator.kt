package com.bradope.blinkactivator.blink

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread


const val BLINK_RENEW_SESSION_INTERVAL_IN_SECONDS = 60 * 60L
const val BLINK_REFRESH_STATUS_INTERVAL_IN_SECONDS = 60 * 1L

class BlinkAutomator(
    val handler: BlinkRequestHandler,
    val blinkAccessGuard: BlinkAccessGuard,
    val renewSessionPeriodInSeconds: Long = BLINK_RENEW_SESSION_INTERVAL_IN_SECONDS,
    val refreshStatusPeriodInSeconds: Long = BLINK_REFRESH_STATUS_INTERVAL_IN_SECONDS
) {

    private val quitRequested = AtomicBoolean(false)

    private var pollThread: Thread? = null

    fun start() {
        quitRequested.set(false)
        pollThread = thread(start = true) { pollUntilQuit() }
    }

    fun quit() {
        quitRequested.set(true)

        pollThread?.join()

        handler.quit()
    }

    private fun pollUntilQuit() {
        handler.begin()
        var lastRenew = System.currentTimeMillis() / 1000
        var lastRefresh = System.currentTimeMillis() / 1000

        while (!quitRequested.get()) {

            if (!blinkAccessGuard.canAccessBlink()) {
                Thread.sleep(1000L)
                continue
            }

            val now = System.currentTimeMillis() / 1000

            if (now >= lastRenew + renewSessionPeriodInSeconds) {
                lastRenew = now
                handler.requestRenewSession()
            } else if (now >= lastRefresh + refreshStatusPeriodInSeconds) {
                handler.requestRefreshStatus()
                lastRefresh = now
            }
            handler.pollRequestQueue()
            Thread.sleep(5L)
        }
    }
}
