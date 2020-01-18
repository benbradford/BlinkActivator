package com.bradope.blinkactivator.blink

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean
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

    private val atHomeTolerance = 0.001583 // about 160 meters
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
    fun makeBackOff(quitRequested: AtomicBoolean, refreshRequested: AtomicBoolean, maxWaitTimeInSeconds: Long = MAX_WAIT_TIME_IN_SECONDS) =
        BackOff(
            quitRequested,
            refreshRequested,
            maxWaitTimeInSeconds
        )
}

const val BLINK_RENEW_SESSION_INTERVAL_IN_SECONDS = 60 * 60L
const val BLINK_REFRESH_STATUS_INTERVAL_IN_SECONDS = 60 * 1L

class BlinkAutomator(
    val handler: BlinkRequestHandler,
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

const val DEFAULT_NUM_OUT_LOGS_REQUIRED_TO_DISARM = 3
class BlinkArmMonitor(val blinkApi: BlinkApi, val numOutLogsRequiredToDisarm: Int = DEFAULT_NUM_OUT_LOGS_REQUIRED_TO_DISARM) {

    var numConsecutiveOutLogs = 0
    var numConsecutiveHomeLogs = 0

    fun logLocationIn(): Boolean {
        numConsecutiveOutLogs = 0
        ++numConsecutiveHomeLogs
        return disarm()
    }

    fun logLocationOut(): Boolean {
        ++numConsecutiveOutLogs
        numConsecutiveHomeLogs = 0

        return (numConsecutiveOutLogs == numOutLogsRequiredToDisarm) && arm()
    }

    private fun arm(attempts: Int = 0): Boolean {
        Log.i("bradope_log_armMonitor", " attemping to arm")
        if (blinkApi.arm()) {
            return true
        }
        if (attempts < 3)
            return arm(attempts+1)
        return false
    }

    private fun disarm(attempts: Int = 0): Boolean {
        if (blinkApi.disarm()) {
            return true
        }
        if (attempts < 3)
            return disarm(attempts+1)
        return false
    }
}

class BlinkRequestHandler(
    val credentials: Credentials,
    val blinkApi: BlinkApi = BlinkApi(),
    val tracker: LocationStateTracker = LocationStateTracker(),
    val backOffFactory: BackOffFactory = BackOffFactory(),
    var listener: BlinkListener? = null) {

    private val LOG_TAG = "bradope_log " + BlinkRequestHandler::class.java.simpleName

    enum class RequestType {
        NEW_LOCATION,
        RENEW_AUTH,
        REFRESH_STATUS
    }

    data class Request(val type: RequestType, val data: Any?)

    private val blinkArmMonitor = BlinkArmMonitor(blinkApi)
    private var lastKnownBlinkArmState = BlinkArmState.UNKNOWN
    private var lastKnownLocationState =
        LocationStateTracker.LocationState.UNKNOWN
    private var lastLocation: Location? = null

    private val quitRequested = AtomicBoolean(false)
    private val refreshRequested = AtomicBoolean(false)

    @SuppressLint("NewApi")
    private val requestQueue = ConcurrentLinkedDeque<Request>()

    fun getLastLocationState() = lastKnownLocationState
    fun getLastLocation() = lastLocation
    fun getLastBlinkState() = lastKnownBlinkArmState

    fun begin() {
        createNewBlinkApiSession()
        refreshArmState()
    }

    fun newLocation(latestLocation: Location) {
        requestQueue.offer(
            Request(
                RequestType.NEW_LOCATION,
                latestLocation
            )
        )
    }

    fun requestRenewSession() {
        requestQueue.offer(
            Request(
                RequestType.RENEW_AUTH,
                null
            )
        )
    }

    fun requestRefreshStatus() {
        requestQueue.offer(
            Request(
                RequestType.REFRESH_STATUS,
                null
            )
        )
    }

    fun quit() {
        quitRequested.set(true)

        // :TODO: detect if we are in backoff and block waiting if so.
    }

    fun requestBlinkStatusRefresh() {
        requestQueue.clear()
        lastKnownLocationState =
            LocationStateTracker.LocationState.UNKNOWN
        requestQueue.offer(
            Request(
                RequestType.REFRESH_STATUS,
                null
            )
        )
    }

    fun pollRequestQueue() {

        val request = requestQueue.poll()

        if (request != null) {

            when (request.type) {
                RequestType.NEW_LOCATION -> lastKnownLocationState =
                    onNewLocation(request.data!! as Location)
                RequestType.RENEW_AUTH -> {
                    createNewBlinkApiSession()
                    requestRefreshStatus()
                }
                RequestType.REFRESH_STATUS -> {
                    refreshArmState()
                }
            }
        }

    }

    private fun notifyNewBlinkState(state: BlinkArmState) {
        lastKnownBlinkArmState = state
        listener?.onStatusRefresh(lastKnownBlinkArmState)
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

    private fun onNewLocation(latestLocation: Location): LocationStateTracker.LocationState {
        lastLocation = latestLocation
        val latestLocationState = tracker.getLocationStateForLocation(latestLocation)

        lookForRequiredArmOperation(latestLocationState)

        return latestLocationState
    }

    private fun lookForRequiredArmOperation(latestLocationState: LocationStateTracker.LocationState) {
        if (latestLocationState == LocationStateTracker.LocationState.AT_HOME && lastKnownBlinkArmState == BlinkArmState.ARMED) {
            arriveAtHome()
        } else if (latestLocationState == LocationStateTracker.LocationState.OUT && lastKnownBlinkArmState == BlinkArmState.DISARMED) {
            leaveHome()
        }
    }

    private fun arriveAtHome(): Boolean {
        Log.i(LOG_TAG, "going home - attempting disarm")
        if (blinkArmMonitor.logLocationIn()) {
            notifyNewBlinkState(BlinkArmState.DISARMED)
            return true
        }
        return false
    }

    private fun leaveHome(): Boolean {
        Log.i(LOG_TAG, "not at home, logging...")
        if (blinkArmMonitor.logLocationOut()) {
            notifyNewBlinkState(BlinkArmState.ARMED)
            return true
        }
        return false
    }

    private fun refreshArmState(lastWaitTime: Long = 1L): Boolean {
        var state = blinkApi.getArmState()
        if (state == null || state == BlinkArmState.UNKNOWN) {
            Log.w(LOG_TAG, "invalid blink state - will retry")
            return withBackOff(::refreshArmState, lastWaitTime)
        }

        lookForRequiredArmOperation(lastKnownLocationState)
        notifyNewBlinkState(state)
        return true;
    }

    private fun withBackOff(func: (lastWaitTime: Long)->Boolean, lastWaitTime: Long): Boolean {
        return backOffFactory.makeBackOff(quitRequested, refreshRequested).withBackOff(func, lastWaitTime)
    }
}