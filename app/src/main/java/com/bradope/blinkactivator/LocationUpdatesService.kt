package com.bradope.blinkactivator

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat
import android.util.Log;
import androidx.localbroadcastmanager.content.LocalBroadcastManager

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

/**
 * A bound and started service that is promoted to a foreground service when location updates have
 * been requested and all clients unbind.
 *
 * For apps running in the background on "O" devices, location is computed only once every 10
 * minutes and delivered batched every 30 minutes. This restriction applies even to apps
 * targeting "N" or lower which are run on "O" devices.
 *
 * This sample show how to use a long-running service for location updates. When an activity is
 * bound to this service, frequent location updates are permitted. When the activity is removed
 * from the foreground, the service promotes itself to a foreground service, and location updates
 * continue. When the activity comes back to the foreground, the foreground service stops, and the
 * notification assocaited with that service is removed.
 */

private const val PACKAGE_NAME = "com.exmaple.blinkActivator.locationupdatesforegroundservice"
const val ACTION_BROADCAST = PACKAGE_NAME + ".broadcast"

const val EXTRA_LOCATION = PACKAGE_NAME + ".location"
const val EXTRA_STATUS = PACKAGE_NAME + ".status"
const val EXTRA_REGISTER_STATUS = PACKAGE_NAME + ".registyerstatus"
const val EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME + ".started_from_notification"

private var blinkRequestHandler: BlinkRequestHandler? = null
private var blinkAutomator: BlinkAutomator? = null

class LocationUpdatesService: Service(), BlinkListener {

    private val LOG_TAG =  "bradope_log " + LocationUpdatesService::class.java.simpleName

    private val NOTIFICATION_CHANNEL_ID = "channel_01"
    private val UPDATE_INTERVAL_IN_MILLISECONDS = 10000L
    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 10L

    /**
     * The identifier for the notification displayed for the foreground service.
     */
    private val NOTIFICATION_ID = 12345678

    private val mBinder = LocalBinder(this)
    /**
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private var mChangingConfiguration = false

    private lateinit var mNotificationManager: NotificationManager



    /**
     * Contains parameters used by {@link com.google.android.gms.location.FusedLocationProviderApi}.
     */
    private var mLocationRequest = createLocationRequest()

    /**
     * Provides access to the Fused Location Provider API.
     */
    private lateinit var mFusedLocationClient: FusedLocationProviderClient

    /**
     * Callback for changes in location.
     */
    private var mLocationCallback: LocationCallback? = null

    private lateinit var mServiceHandler: Handler

    /**
     * The current location.
     */
    private var mLocation: Location? = null

    override fun onCreate() {
        Log.i(LOG_TAG, "Service onCreate")
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = (object: LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                onNewLocation(locationResult.lastLocation)
            }
        })

       requestLocation()

        val handlerThread = HandlerThread(LOG_TAG)
        handlerThread.start()
        mServiceHandler = Handler(handlerThread.looper)
        mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Android O requires a Notification Channel.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            // Create the channel for the notification
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            // Set the Notification Channel for the Notification Manager.
            mNotificationManager.createNotificationChannel(channel);
        }

        if (blinkAutomator == null) {
            blinkRequestHandler = BlinkRequestHandler(
                credentials = makeCredentials(),
                listener = this)

            blinkAutomator = BlinkAutomator(blinkRequestHandler!!)
            blinkAutomator!!.start()
        }

    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i(LOG_TAG, "Service started")
        val startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false)

        // We got here because the user decided to remove location updates from the notification.
        if (startedFromNotification) {
            removeLocationUpdates()
            stopSelf()
        }
        // Tells the system to not try to recreate the service after it has been killed.
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.i(LOG_TAG, "onConfigurationChanged")
        mChangingConfiguration = true;
    }

    override fun onBind(intent: Intent): IBinder {
        // Called when a client (MainActivity in case of this sample) comes to the foreground
        // and binds with this service. The service should cease to be a foreground service
        // when that happens.
        Log.i(LOG_TAG, "in onBind()")
        stopForeground(true)
        mChangingConfiguration = false
        return mBinder
    }

   override fun onRebind(intent: Intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(LOG_TAG, "in onRebind()")
        stopForeground(true)
        mChangingConfiguration = false
        super.onRebind(intent)
    }

    override fun onUnbind(intent: Intent): Boolean {
        Log.i(LOG_TAG, "Last client unbound from service")

        // Called when the last client (MainActivity in case of this sample) unbinds from this
        // service. If this method is called due to a configuration change in MainActivity, we
        // do nothing. Otherwise, we make this service a foreground service.
        if (!mChangingConfiguration && requestingLocationUpdates(this)) {
            Log.i(LOG_TAG, "Starting foreground service")

            //startForeground(NOTIFICATION_ID, getNotification())
        }
        return true // Ensures onRebind() is called when a client re-binds.
    }

    override fun onDestroy() {
        Log.i(LOG_TAG, "Service onDestroy")
        mServiceHandler.removeCallbacksAndMessages(null)
        blinkRequestHandler?.quit()
    }

    override fun onStatusRefresh(state: BlinkArmState) {
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_STATUS, state.toString())
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        // Update notification content if running as a foreground service.
        if (serviceIsRunningInForeground(this)) {
            mNotificationManager.notify(NOTIFICATION_ID, getStatusUpdateNotification(state));
        }

    }

    override fun onRegister(success: Boolean) {
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_REGISTER_STATUS, success)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        // Update notification content if running as a foreground service.
        //if (serviceIsRunningInForeground(this)) {
        //    mNotificationManager.notify(NOTIFICATION_ID, getConnectedToBlinkNotification());
       // }
    }

    fun refreshBlinkState() {
        blinkRequestHandler?.requestBlinkStatusRefresh()
    }

    /**
     * Makes a request for location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    fun requestLocationUpdates() {
        Log.i(LOG_TAG, "Requesting location updates")
        setRequestingLocationUpdates(this, true)
        val intent = Intent(applicationContext, LocationUpdatesService::class.java)
        startService(intent)
        try {
                mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, false)
            Log.e(LOG_TAG, "Lost location permission. Could not request updates. " + unlikely)
        }
    }

    /**
     * Removes location updates. Note that in this sample we merely log the
     * {@link SecurityException}.
     */
    fun removeLocationUpdates() {
        Log.i(LOG_TAG, "Removing location updates")
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback)
            setRequestingLocationUpdates(this, false)
            stopSelf()
        } catch (unlikely: SecurityException) {
            setRequestingLocationUpdates(this, true)
            Log.e(LOG_TAG, "Lost location permission. Could not remove updates. " + unlikely)
        }
    }

    fun requestBlinkRefresh() {
        blinkRequestHandler?.requestBlinkStatusRefresh()
    }

    private fun requestLocation() {
        try {
            mFusedLocationClient.lastLocation
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        mLocation = task.result
                       if (blinkAutomator!= null && mLocation != null) blinkRequestHandler?.newLocation(mLocation!!)
                    } else {
                        Log.w(LOG_TAG, "Failed to get location.")
                    }
                };
        } catch ( unlikely: SecurityException) {
            Log.e(LOG_TAG, "Lost location permission." + unlikely)
        }
    }

    /**
     * Returns the {@link NotificationCompat} used as part of the foreground service.
     */
    private fun getStatusUpdateNotification(state: BlinkArmState): Notification {
        val intent = Intent(this, LocationUpdatesService::class.java)

        // :TODO: oneline this:
        var text = "Blink Status Change: $state"

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true)

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        val servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT)

        // The PendingIntent to launch activity.
       val activityPendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MapsActivity::class.java), 0)

        val builder = NotificationCompat.Builder(this)
              //  .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
              //          activityPendingIntent)
              //  .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),
              //          servicePendingIntent)
                .setContentText(text)
                .setContentTitle(getLocationTitle(this))
                .setOngoing(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis())

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(NOTIFICATION_CHANNEL_ID); // Channel ID
        }

        return builder.build();
    }

    private fun onNewLocation(location: Location) {
        Log.i(LOG_TAG, "New location: $location")

        mLocation = location

        if (blinkRequestHandler != null) blinkRequestHandler!!.newLocation(location)
        // Notify anyone listening for broadcasts about the new location.
        val intent = Intent(ACTION_BROADCAST)
        intent.putExtra(EXTRA_LOCATION, location)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        // Update notification content if running as a foreground service.
        //if (serviceIsRunningInForeground(this)) {
       //     mNotificationManager.notify(NOTIFICATION_ID, getNotification());
        //}
    }

    /**
     * Sets the location request parameters.
     */
    private fun createLocationRequest(): LocationRequest {
        val locationRequest = LocationRequest()
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
        locationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        return locationRequest
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
     class LocalBinder(self_: LocationUpdatesService): Binder() {
        val self: LocationUpdatesService = self_

        fun getService(): LocationUpdatesService {
            return self
        }
    }

    /**
     * Returns true if this is a foreground service.
     *
     * @param context The {@link Context}.
     */
    fun serviceIsRunningInForeground( context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val services = manager.getRunningServices(Integer.MAX_VALUE)
        for (service in services) {
            if (LocationUpdatesService::class.java.name == service.service.className) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }

    private fun makeCredentials(): Credentials {
        Log.i(LOG_TAG, "thread start")
        var cred = getCredentials(this)
        if (cred == null) {
            Log.i(LOG_TAG, "no cred")
            val email = getString(R.string.email)
            val pass = getString(R.string.pass)
            cred = createCredentials(email, pass)
            storeCredewntials(this, cred!!)
        }
        Log.i(LOG_TAG, "gonna register")
        return cred
    }
}