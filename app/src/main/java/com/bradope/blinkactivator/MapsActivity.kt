package com.bradope.blinkactivator

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.bradope.blinkactivator.LocationUpdatesService.LocalBinder
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_maps.*
import kotlinx.coroutines.delay
import kotlin.concurrent.thread
import kotlin.math.sqrt

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var mMap: GoogleMap

    private val LOG_TAG = "bradope_log " + MapsActivity::class.java.getSimpleName()

    // Used in checking for runtime permissions.
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34

    // The BroadcastReceiver used to listen from broadcasts from the service.
    private lateinit var myReceiver: MyReceiver

    // A reference to the service used to get location updates.
    private var mService: LocationUpdatesService? = null

    // Tracks the bound state of the service.
    private var mBound = false

    // Monitors the state of the connection to the service.
    private lateinit var mServiceConnection: ServiceConnection

    private var homeLocation = LatLng(51.083008, 1.161534)
    private var homeSize = 180.0
    private var marker: Marker? = null
    private var myLocation: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LOG_TAG, "Activity onCreate")
        setContentView(R.layout.activity_maps)
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        myReceiver = MyReceiver(this)

        // Monitors the state of the connection to the service.
         mServiceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                Log.i(LOG_TAG, "onServiceConnected")
                val binder = service as LocalBinder
                val hadService = mService != null
                mService = binder.getService()


               // Thread.sleep(1000)
                if (!checkPermissions()) {
                    requestPermissions()
                }
                //Thread.sleep(1000)
                if (!hadService)
                    mService!!.requestLocationUpdates()
                mService!!.refreshBlinkState()


                mBound = true
            }

            override fun onServiceDisconnected(name: ComponentName) {
                Log.i(LOG_TAG, "onServiceDisconnected")
               // mService = null
              //  mBound = false
            }
        }


    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
                val homeCircle = CircleOptions()
                    .center(homeLocation)
                    .radius(homeSize)
                    .clickable(true)
                    .strokeColor(Color.RED)
                mMap.addCircle(homeCircle)
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(homeLocation, 16.0f))
    }


    override fun onStart() {
        super.onStart()
        Log.i(LOG_TAG, "Activity onStart")
        PreferenceManager.getDefaultSharedPreferences(this)
            .registerOnSharedPreferenceChangeListener(this)
       // mRequestLocationUpdatesButton = findViewById(R.id.request_location_updates_button) as Button
       // mRemoveLocationUpdatesButton = findViewById(R.id.remove_location_updates_button) as Button
        val button: Button = findViewById(R.id.button)
        button.setOnClickListener {

            if (!checkPermissions()) {
                requestPermissions()
            } else {
                mService!!.requestLocationUpdates()
                mService!!.refreshBlinkState()
            }

        }

        findViewById<Button>(R.id.gotoHome).setOnClickListener {
            val cameraPosition = CameraPosition.Builder()
                .target(homeLocation)
                .zoom(16.0f)
                .build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
        findViewById<Button>(R.id.gotoYou).setOnClickListener {
            if (myLocation != null) {
            val cameraPosition = CameraPosition.Builder()
                .target(myLocation!!)
                .zoom(16.0f)
                .build()
                mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
        val startStopButton = findViewById<Button>(R.id.startStop)
        if (mService != null) {
            if (mService!!.hasBlink()) {
                startStopButton.text = "Stop Blink"
            } else {
                startStopButton.text = "Start Blink"
            }
        }
        startStopButton.setOnClickListener {
            if (mService != null) {
                if (mService!!.hasBlink()) {
                    mService!!.stopBlink()
                    finish()
                } else {
                    bindService(
                        Intent(this, LocationUpdatesService::class.java), mServiceConnection,
                        Context.BIND_AUTO_CREATE
                    )
                    //mService!!.startBlink()
                    startStopButton.text = "Stop Blink"
                }
            }
        }
       /* mRemoveLocationUpdatesButton.setOnClickListener(object : OnClickListener() {
            fun onClick(view: View?) {
                mService!!.removeLocationUpdates()
            }
        })*/
        // Restore the state of the buttons when the activity (re)launches.
        setButtonsState(requestingLocationUpdates(this))

        bindService(
            Intent(this, LocationUpdatesService::class.java), mServiceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onResume() {
        super.onResume()
        Log.i(LOG_TAG, "Activity onResume")
        LocalBroadcastManager.getInstance(this).registerReceiver(
            myReceiver!!,
            IntentFilter(ACTION_BROADCAST)
        )
    }

    override fun onPause() {
        Log.i(LOG_TAG, "Activity onPause")
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver!!)
        super.onPause()
    }

    override fun onStop() {
        Log.i(LOG_TAG, "Activity onStop")
        if (mBound) { // Unbind from the service. This signals to the service that this activity is no longer
// in the foreground, and the service can respond by promoting itself to a foreground
// service.
            unbindService(mServiceConnection)
            mBound = false
        }
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    /**
     * Returns the current state of the permissions needed.
     */
    private fun checkPermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestPermissions() {
        val shouldProvideRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        // Provide an additional rationale to the user. This would happen if the user denied the
// request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(LOG_TAG, "Displaying permission rationale to provide additional context.")
            /*

            Snackbar.make(
                findViewById(com.bradope.blinkactivator.R.id.activity_maps),
                "R.string.permission_rationale",
                Snackbar.LENGTH_INDEFINITE
            )
                .setAction(R.string.ok, object : DialogInterface.OnClickListener {
                    fun onClick(view: View?) { // Request permission
                        ActivityCompat.requestPermissions(
                            this@MapsActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                            REQUEST_PERMISSIONS_REQUEST_CODE
                        )
                    }
                })
                .show()
                */
        } else {
            Log.i(LOG_TAG, "Requesting permission")
            // Request permission. It's possible this can be auto answered if device policy
// sets the permission in a given state or the user denied the permission
// previously and checked "Never ask again".
            ActivityCompat.requestPermissions(
                this@MapsActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        Log.i(LOG_TAG, "onRequestPermissionResult")
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.size <= 0) { // If user interaction was interrupted, the permission request is cancelled and you
// receive empty arrays.
                Log.i(LOG_TAG, "User interaction was cancelled.")
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) { // Permission was granted.
                mService!!.requestLocationUpdates()
            } else { // Permission denied.
                setButtonsState(false)
                Log.i(LOG_TAG, "permission denied")
                /*Snackbar.make(
                    findViewById(R.id.activity_main),
                    "R.string.permission_denied_explanation",
                    Snackbar.LENGTH_INDEFINITE
                )
                    .setAction("R.string.settings", object : View.OnClickListener {
                        override fun onClick(view: View?) { // Build intent that displays the App settings screen.
                            val intent = Intent()
                            intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            val uri: Uri = Uri.fromParts(
                                "package",
                                BuildConfig.APPLICATION_ID, null
                            )
                            intent.data = uri
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            startActivity(intent)
                        }
                    })
                    .show()
                    */

            }
        }
    }

    /**
     * Receiver for broadcasts sent by [LocationUpdatesService].
     */
    private class MyReceiver(activity: MapsActivity) : BroadcastReceiver() {
        val self  = activity

        private val homeLocation = LatLng(51.083008, 1.161534)

        fun distToHome(location: Location): Double{
            val lat = location.latitude - homeLocation.latitude
            val lon = location.longitude - homeLocation.longitude
            val distSquared = (lat*lat) + (lon*lon)
            return sqrt(distSquared) * 1000

        }
        fun Double.format(digits: Int) = "%.${digits}f".format(this)
        override fun onReceive(context: Context?, intent: Intent) {
            val location =
                intent.getParcelableExtra<Location>(EXTRA_LOCATION)
            if (location != null && self.mMap != null) {

                self.myLocation = LatLng(location.latitude, location.longitude)
                val options = MarkerOptions()
                    .position(self.myLocation!!)
                    .icon( BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED))

                self.marker?.remove()
                self.marker = self.mMap.addMarker(options)


                val locState = self.findViewById<TextView>(R.id.locState)
                var d = distToHome(location)
                if (d == 0.0) d = 0.00001
                val dist = "${d.format(2)}"
                var loc = intent.getStringExtra(EXTRA_LOCATION_STATE)

                locState.text = "$loc $dist"

            }
            val reg =intent.getBooleanExtra(EXTRA_REGISTER_STATUS, false)

            if (reg ) {
                    Toast.makeText(
                        self, "Registered With Blink",
                        Toast.LENGTH_SHORT
                    ).show()
                }


            val ref =intent.getStringExtra(EXTRA_STATUS)
            if (ref != null && ref != "") {
                if (ref != self.lastState) {
                     Toast.makeText(
                         self, "Blink Status Update: $ref",
                        Toast.LENGTH_SHORT
                     ).show()

                    val tv: TextView = self.findViewById(R.id.text)
                    tv.setText("Blink Status Update: $ref")
                }
                self.lastState = ref
            }

        }
    }
    var lastState = ""


    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        s: String
    ) { // Update the buttons state depending on whether location updates are being requested.
        if (s == KEY_REQUESTING_LOCATION_UPDATES) {
            setButtonsState(
                sharedPreferences.getBoolean(
                    KEY_REQUESTING_LOCATION_UPDATES,
                    false
                )
            )
        }
    }

    private fun setButtonsState(requestingLocationUpdates: Boolean) {
        if (requestingLocationUpdates) {
            //mRequestLocationUpdatesButton.setEnabled(false)
            //mRemoveLocationUpdatesButton.setEnabled(true)
        } else {
            //mRequestLocationUpdatesButton.setEnabled(true)
            //mRemoveLocationUpdatesButton.setEnabled(false)
        }
    }

}
