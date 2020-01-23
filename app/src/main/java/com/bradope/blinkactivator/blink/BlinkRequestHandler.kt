package com.bradope.blinkactivator.blink

import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

interface BlinkListener {
    fun onRegister(success: Boolean)
    fun onStatusRefresh(state: BlinkArmState)
    fun onScheduleRefresh(dailySchedule: BlinkDailySchedule)
}

class BlinkRequestHandler(
    val credentials: Credentials,
    blinkSettings: BlinkSettings,
    val blinkApi: BlinkApi,
    val tracker: LocationStateTracker = LocationStateTracker(blinkSettings),
    val backOffFactory: BackOffFactory = BackOffFactory(),
    val blinkAccessGuard: BlinkAccessGuard,
    var listener: BlinkListener? = null) {

    private val LOG_TAG = "bradope_log " + BlinkRequestHandler::class.java.simpleName

    enum class RequestType {
        NEW_LOCATION,
        RENEW_AUTH,
        REFRESH_STATUS
    }

    data class Request(val type: RequestType, val data: Any?)

    private val fetchMaxStatusRefreshBackoffTime = getSettingFetcher(blinkSettings::maxStatusRefreshBackoffTimeInSeconds)
    private val blinkArmMonitor = BlinkArmMonitor(blinkApi, blinkSettings)
    private var lastKnownBlinkArmState = BlinkArmState.UNKNOWN
    private var lastKnownLocationState = LocationStateTracker.LocationState.UNKNOWN
    private var lastLocation: Location? = null

    private val quitRequested = AtomicBoolean(false)

    @SuppressLint("NewApi")
    private val requestQueue = ConcurrentLinkedDeque<Request>()

    fun getLastLocationState() = lastKnownLocationState
    fun getLastLocation() = lastLocation
    fun getLastBlinkState() = lastKnownBlinkArmState

    fun begin() {
        createNewBlinkApiSession()
        refreshArmState()
        refreshScheule()
    }

    fun newLocation(latestLocation: Location) = offer(RequestType.NEW_LOCATION, latestLocation)

    fun requestRenewSession() = offer(RequestType.RENEW_AUTH)

    fun requestRefreshStatus() = offer(RequestType.REFRESH_STATUS)
    
    fun quit() {
        quitRequested.set(true)

        // :TODO: detect if we are in backoff and block waiting if so.
    }

    fun pollRequestQueue() {
        if (!blinkAccessGuard.canAccessBlink()) return

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
                    refreshScheule()
                }
            }
        }

    }
    
    private fun offer(requestType: RequestType, data: Location? = null) {
        if (!blinkAccessGuard.canAccessBlink()) return
        requestQueue.offer(
            Request(
                requestType,
                data
            )
        )
    }

    private fun notifyNewBlinkState(state: BlinkArmState) {
        lastKnownBlinkArmState = state
        listener?.onStatusRefresh(lastKnownBlinkArmState)
    }

    private fun createNewBlinkApiSession(lastWaitTime: Int = 1): Boolean {
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

    private fun refreshArmState(lastWaitTime: Int = 1): Boolean {
        var state = blinkApi.getArmState()
        if (state == null || state == BlinkArmState.UNKNOWN) {
            Log.w(LOG_TAG, "invalid blink state - will retry")
            return withBackOff(::refreshArmState, lastWaitTime)
        }

        lookForRequiredArmOperation(lastKnownLocationState)
        notifyNewBlinkState(state)
        return true;
    }

    private fun refreshScheule(lastWaitTime: Int = 1): Boolean {
        Log.i(LOG_TAG, "refreshing schedule")
        var blinkScheduleTimes = blinkApi.getSchedule()
        if (blinkScheduleTimes == null) {
            Log.w(LOG_TAG, "invalid schedule - will retry")
            return withBackOff(::refreshScheule, lastWaitTime)
        }
        Log.i(LOG_TAG, "updating listener")
        listener?.onScheduleRefresh(blinkScheduleTimes)
        return true
    }

    private fun withBackOff(func: (lastWaitTime: Int)->Boolean, lastWaitTime: Int): Boolean
            = backOffFactory.makeBackOff(quitRequested, fetchMaxStatusRefreshBackoffTime())
                .withBackOff(func, lastWaitTime)

}