package com.bradope.blinkactivator

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.sqrt


interface BlinkListener {
    fun onRegister(success: Boolean)
    fun onStatusRefresh(state: BlinkArmState)
}

open class LocationStateTracker {
    enum class LocationState {
        AT_HOME,
        OUT,
        UNKNOWN
    }

    private val atHomeTolerance = 0.001783 // about 180 meters
    private val homeLocation = LatLng(51.083008, 1.161534)

    fun getLocationStateForLocation(location: Location): LocationState {
        val lat = location.latitude - homeLocation.latitude
        val lon = location.longitude - homeLocation.longitude
        val distSquared = (lat*lat) + (lon*lon)
        val distance = sqrt(distSquared)

        return if (distance < atHomeTolerance) LocationState.AT_HOME else LocationState.OUT
    }

}


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
    fun makeBackOff(quitRequested: AtomicBoolean, refreshRequested: AtomicBoolean, maxWaitTimeInSeconds: Long = MAX_WAIT_TIME_IN_SECONDS) = BackOff(quitRequested, refreshRequested, maxWaitTimeInSeconds)
}

const val BLINK_RENEW_SESSION_INTERVAL_IN_SECONDS = 60 * 60L
const val BLINK_REFRESH_STATUS_INTERVAL_IN_SECONDS = 60 * 10L

class BlinkAutomator(
    val handler: BlinkRequestHandler,
    val renewSessionPeriodInSeconds: Long = BLINK_RENEW_SESSION_INTERVAL_IN_SECONDS,
    val refreshStatusPeriodInSeconds: Long = BLINK_REFRESH_STATUS_INTERVAL_IN_SECONDS) {

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
        var lastRenew = System.currentTimeMillis() / 1000
        var lastRefresh = System.currentTimeMillis() / 1000

        while (!quitRequested.get()) {
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

class BlinkRequestHandler(
    val credentials: Credentials,
    val blinkApi: BlinkApi = BlinkApi(),
    val tracker: LocationStateTracker = LocationStateTracker(),
    val backOffFactory: BackOffFactory = BackOffFactory(),
    val listener: BlinkListener?) {

    private val LOG_TAG = "bradope_log " + BlinkRequestHandler::class.java.simpleName

    enum class RequestType {
        NEW_LOCATION,
        RENEW_AUTH,
        REFRESH_STATUS
    }

    data class Request(val type: RequestType, val data: Any?)

    private var lastKnownBlinkArmState = BlinkArmState.UNKNOWN
    private var lastKnownLocationState = LocationStateTracker.LocationState.UNKNOWN

    private val quitRequested = AtomicBoolean(false)
    private val refreshRequested = AtomicBoolean(false)
    private val quitCompleted = AtomicBoolean(false)


    @SuppressLint("NewApi")
    private val requestQueue = ConcurrentLinkedDeque<Request>()

    init {
        createNewBlinkApiSession()
        if (refreshArmState())
            listener?.onStatusRefresh(lastKnownBlinkArmState)
    }

    fun newLocation(latestLocation: Location) {
        requestQueue.offer(Request(RequestType.NEW_LOCATION, latestLocation))
    }

    fun requestRenewSession() {
        requestQueue.offer(Request(RequestType.RENEW_AUTH, null))
    }

    fun requestRefreshStatus() {
        requestQueue.offer(Request(RequestType.REFRESH_STATUS, null))
    }

    fun quit() {
        quitRequested.set(true)

        // :TODO: detect if we are in backoff and block waiting if so.
    }

    fun requestBlinkStatusRefresh() {
        requestQueue.clear()
        requestQueue.offer(Request(RequestType.REFRESH_STATUS, null))
    }

    fun pollRequestQueue() {

        val request = requestQueue.last()

        if (request != null) {
            Log.i(LOG_TAG, "got request $request")
            when (request.type) {
                RequestType.NEW_LOCATION -> lastKnownLocationState =
                    onNewLocation(request.data!! as Location, lastKnownLocationState)
                RequestType.RENEW_AUTH -> {
                    createNewBlinkApiSession()
                    requestRefreshStatus()
                }
                RequestType.REFRESH_STATUS -> {
                    refreshArmState()
                    listener?.onStatusRefresh(lastKnownBlinkArmState)
                }
            }
        }

    }

    private fun createNewBlinkApiSession(lastWaitTime: Long = 1L): Boolean {
        try {
            if (blinkApi.register(credentials)) {
                listener?.onRegister(true)
                return true
            } else {
                Log.w(LOG_TAG, "could not register blink session")
            }
        } catch( e: Exception) {
            Log.e(LOG_TAG, e.toString())
        }
        return withBackOff(::createNewBlinkApiSession, lastWaitTime)
    }

    private fun onNewLocation(latestLocation: Location, lastKnownLocationState: LocationStateTracker.LocationState): LocationStateTracker.LocationState {
        Log.i(LOG_TAG, "evaluating latestLocation $latestLocation")
        val latestLocationState = tracker.getLocationStateForLocation(latestLocation)

        if (latestLocationState != lastKnownLocationState) {
            if (latestLocationState == LocationStateTracker.LocationState.AT_HOME && lastKnownBlinkArmState != BlinkArmState.DISARMED) {
                arriveAtHome()
            } else if (latestLocationState == LocationStateTracker.LocationState.OUT && lastKnownBlinkArmState != BlinkArmState.ARMED) {
                leaveHome()
            }
        }

        return latestLocationState
    }

    private fun arriveAtHome(attempts: Int = 0): Boolean {
        Log.i(LOG_TAG, "going home - attempting disarm")
        if (blinkApi.disarm()) {
            lastKnownBlinkArmState = BlinkArmState.DISARMED
            listener?.onStatusRefresh(lastKnownBlinkArmState)
            return true
        }
        if (attempts < 3)
            return leaveHome(attempts+1)
        return false
    }

    private fun leaveHome(attempts: Int = 0): Boolean {
        Log.i(LOG_TAG, "leaving home - attempting to arm")

        if (blinkApi.arm()) {
            lastKnownBlinkArmState = BlinkArmState.ARMED
            listener?.onStatusRefresh(lastKnownBlinkArmState)
            return true
        }
        if (attempts < 3)
            return leaveHome(attempts+1)
        return false
    }

    private fun retryWhenUnexpectedState(expectedState: BlinkArmState, funcToRetry: (lastWaitTime: Long) ->Boolean, lastWaitTime: Long = 1L): Boolean {
        refreshArmState()

        if (lastKnownBlinkArmState != expectedState) {
            Log.w(LOG_TAG, "in unexpected state $lastKnownBlinkArmState expected: $expectedState")
            return withBackOff(funcToRetry, lastWaitTime)
        }
        return true
    }

    private fun refreshArmState(lastWaitTime: Long = 1L): Boolean {
        var state = blinkApi.getArmState()
        if (state != null) {
            lastKnownBlinkArmState = state
        }

        Log.i(LOG_TAG, "arm state " + lastKnownBlinkArmState)

        if (state == null || state == BlinkArmState.UNKNOWN) {
            Log.w(LOG_TAG, "invalid blink state - will retry")
            return withBackOff(::refreshArmState, lastWaitTime)
        }

        return true
    }

    private fun withBackOff(func: (lastWaitTime: Long)->Boolean, lastWaitTime: Long): Boolean {
        return backOffFactory.makeBackOff(quitRequested, refreshRequested).withBackOff(func, lastWaitTime)
    }
}