package com.bradope.blinkactivator.blink

import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.bradope.blinkactivator.R
import com.google.android.gms.location.*

interface BlinkAccessListener {
    fun onConnectToBlink(success: Boolean)
    fun onStatusChange()
}

private var listener: BlinkAccessListener? = null

class BlinkHandlerListener: BlinkListener{
    override fun onRegister(success: Boolean) {
        if (listener != null) {
            listener!!.onConnectToBlink(success)
        }
    }

    override fun onStatusRefresh(state: BlinkArmState) {
        if (listener != null) {
            listener!!.onStatusChange()
        }
    }
}

private const val UPDATE_INTERVAL_IN_MILLISECONDS = 10000L
private const val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 10L

private var blinkRequestHandler: BlinkRequestHandler? = null
private var blinkAutomator: BlinkAutomator? = null
private var fusedLocationClient: FusedLocationProviderClient? = null
private var blinkScheduleHandler: BlinkScheduleHandler? = null
private var blinkAccessGuard: BlinkAccessGuard? = null
private var locationCallback: LocationCallback? = null

private val locationRequest = createLocationRequest()
private val blinkRequestListener = BlinkHandlerListener()
private val blinkSettings = BlinkSettings()

fun blinkInit(context: Context) {
    if (blinkRequestHandler == null) {

        var cred = getCredentials(context)
        if (cred == null) {
            // A file in res/value/cred.xml is requied, with email and pass string values set
            val email = context.getString(R.string.email)
            val pass = context.getString(R.string.pass)
            cred = createCredentials(email, pass)
            storeCredewntials(context, cred)
        }

        blinkAccessGuard = BlinkAccessGuard()
        blinkScheduleHandler = BlinkScheduleHandler(blinkAccessGuard!!)
        blinkRequestHandler = BlinkRequestHandler(
            credentials = cred,
            listener = blinkRequestListener,
            blinkAccessGuard = blinkAccessGuard!!,
            blinkSettings = blinkSettings)
        blinkAutomator = BlinkAutomator(
            handler = blinkRequestHandler!!,
            blinkAccessGuard = blinkAccessGuard!!,
            blinkScheduleHandler = blinkScheduleHandler!!,
            blinkSettings = blinkSettings)
        blinkAutomator!!.start()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        locationCallback = (object: LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                blinkAddLocation(locationResult.lastLocation)
                listener!!.onStatusChange()
            }
        })
        fusedLocationClient!!.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

        fusedLocationClient!!.lastLocation.addOnCompleteListener { task ->
            if (task.isSuccessful && task.result != null) {
                blinkAddLocation(task.result!!)
                listener!!.onStatusChange()
            }
        };

    }
}

fun blinkAddLocation(location: Location) {
    blinkRequestHandler!!.newLocation(location)
}

fun blinkStatusRefresh() {
    blinkRequestHandler!!.requestRefreshStatus()
}

fun blinkSetListener(accessListener: BlinkAccessListener?) {
    listener = accessListener
}

fun blinkQuit() {
    if (fusedLocationClient == null) return
    blinkSetListener(null)
    fusedLocationClient!!.removeLocationUpdates(locationCallback)
    blinkAutomator!!.quit()
    blinkAutomator = null
    blinkRequestHandler = null
    fusedLocationClient = null
}

fun blinkGetLastBlinkState(): BlinkArmState {
    if (blinkRequestHandler == null) {
        return BlinkArmState.UNKNOWN
    }
    return blinkRequestHandler!!.getLastBlinkState()

}

fun blinkGetLastLocationState(): LocationStateTracker.LocationState {
    if (blinkRequestHandler == null) {
        return LocationStateTracker.LocationState.UNKNOWN
    }
    return blinkRequestHandler!!.getLastLocationState()
}

fun blinkGetLastLocation(): Location? {
    if (blinkRequestHandler == null)
        return null
    return blinkRequestHandler!!.getLastLocation()
}

fun blinkGetSettings(): BlinkSettings {
    return blinkSettings
}

private fun createLocationRequest(): LocationRequest {
    val locationRequest = LocationRequest()
    locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
    locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
    return locationRequest
}
