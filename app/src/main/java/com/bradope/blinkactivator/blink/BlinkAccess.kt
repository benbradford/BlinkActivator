package com.bradope.blinkactivator.blink

import android.content.Context
import android.location.Location
import android.os.Looper
import com.bradope.blinkactivator.R
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task

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

private var blinkRequestHandler: BlinkRequestHandler? = null
private var blinkAutomator: BlinkAutomator? = null
private var fusedLocationClient: FusedLocationProviderClient? = null
private var blinkScheduleHandler: BlinkScheduleHandler? = null
private var blinkAccessGuard: BlinkAccessGuard? = null
private var locationCallback: LocationCallback? = null

private val blinkRequestListener = BlinkHandlerListener()
private val blinkSettings = BlinkSettings()

fun blinkInit(context: Context) {
    if (blinkRequestHandler == null) {

        val credentials = createCredentials(context)

        blinkAccessGuard = BlinkAccessGuard()
        blinkScheduleHandler = createScheduleHandler()
        blinkRequestHandler = createRequestHandler(credentials)
        blinkAutomator = createAutomator()
        blinkAutomator!!.start()
        locationCallback = createLocationCallback()

        createFusedLocationClient(context)
    }
}

fun blinkAddLocation(location: Location) {
    if (blinkRequestHandler == null) return
    blinkRequestHandler!!.newLocation(location)
}

fun blinkStatusRefresh() {
    if (blinkRequestHandler == null) return
    blinkRequestHandler!!.requestRefreshStatus()
}

fun blinkSetListener(accessListener: BlinkAccessListener?) {
    listener = accessListener
}

fun blinkQuit() {

    blinkSetListener(null)
    fusedLocationClient?.removeLocationUpdates(locationCallback)
    blinkAutomator?.quit()
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

fun blinkRecreateLocationRequestClient(context: Context) {
    if (fusedLocationClient != null) {
        fusedLocationClient!!.removeLocationUpdates(locationCallback)
    }
    createFusedLocationClient(context)
}

private fun createCredentials(context: Context): Credentials {
    var cred = getCredentials(context)
    if (cred == null) {
        // A file in res/value/cred.xml is requied, with email and pass string values set
        val email = context.getString(R.string.email)
        val pass = context.getString(R.string.pass)
        cred = createCredentials(email, pass)
        storeCredewntials(context, cred)
    }

    return cred
}

private fun createScheduleHandler() = BlinkScheduleHandler(
    blinkAccessGuard = blinkAccessGuard!!,
    blinkSettings = blinkSettings,
    hoursAndMinsFactory = DefaultHoursAndMinsFactory())

private fun createRequestHandler(credentials: Credentials) = BlinkRequestHandler(
    credentials = credentials,
    listener = blinkRequestListener,
    blinkAccessGuard = blinkAccessGuard!!,
    blinkSettings = blinkSettings)

private fun createAutomator() = BlinkAutomator(
    handler = blinkRequestHandler!!,
    blinkAccessGuard = blinkAccessGuard!!,
    blinkScheduleHandler = blinkScheduleHandler!!,
    blinkSettings = blinkSettings)

private fun createLocationCallback() = (object: LocationCallback() {
    override fun onLocationResult(locationResult: LocationResult) {
        super.onLocationResult(locationResult)
        blinkAddLocation(locationResult.lastLocation)
        listener!!.onStatusChange()
    }
})

private fun createFusedLocationClient(context: Context) {
    val locationRequest = LocationRequest()
    locationRequest.setInterval(fetcher(blinkSettings::locationUpdateIntervalInMS)())
    locationRequest.setFastestInterval(fetcher(blinkSettings::fastestLocationUpdateIntervalInMS)())
    locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)


    fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    fusedLocationClient!!.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());

    fusedLocationClient!!.lastLocation.addOnCompleteListener { task ->
        if (task.isSuccessful && task.result != null) {
            blinkAddLocation(task.result!!)
            listener!!.onStatusChange()
        }
    }
}
