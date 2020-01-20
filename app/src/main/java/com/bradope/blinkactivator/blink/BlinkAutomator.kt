package com.bradope.blinkactivator.blink

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class BlinkAutomator(
    val handler: BlinkRequestHandler,
    val blinkAccessGuard: BlinkAccessGuard,
    val blinkScheduleHandler: BlinkScheduleHandler,
    blinkSettings: BlinkSettings
) {
    private val fetchRenewSessionIntervalInSeconds = fetcher(blinkSettings::renewSessionIntervalInSeconds)
    private val fetchRefreshStatusIntervalInSeconds = fetcher(blinkSettings::refreshStatusIntervalInSeconds)
    private val fetchCheckScheduleIntervalInSeconds = fetcher(blinkSettings::checkScheduleIntervalInSeconds)

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
        val startTime = System.currentTimeMillis() / 1000

        var lastRenew = startTime
        var lastRefresh = startTime
        var lastScheduleCheck = startTime

        while (!quitRequested.get()) {

            val now = System.currentTimeMillis() / 1000

            if (now >= lastScheduleCheck + fetchCheckScheduleIntervalInSeconds()) {
                blinkScheduleHandler.performCheck()
                lastScheduleCheck = now
            }

            if (!blinkAccessGuard.canAccessBlink()) {
                Thread.sleep(1000L)
                continue
            }

            if (now >= lastRenew + fetchRenewSessionIntervalInSeconds()) {
                lastRenew = now
                handler.requestRenewSession()
            } else if (now >= lastRefresh + fetchRefreshStatusIntervalInSeconds()) {
                handler.requestRefreshStatus()
                lastRefresh = now
            }
            handler.pollRequestQueue()
            Thread.sleep(5L)
        }
    }
}
