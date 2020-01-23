package com.bradope.blinkactivator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bradope.blinkactivator.blink.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_maps.*
import java.lang.Exception
import kotlin.concurrent.thread
import kotlin.math.sqrt
import android.widget.ArrayAdapter
import android.widget.Button
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class BlinkActivity : AppCompatActivity(), BlinkAccessListener, OnMapReadyCallback {

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private lateinit var homeLocation: LatLng
    private var googleMap: GoogleMap? = null
    private var marker: Marker? = null
    private var myLocation: LatLng? = null
    private var userQuit = false

    override fun onMapReady(googleMap: GoogleMap) {
        Log.i("bradope_log_activity", "onmapready")
        this.googleMap = googleMap
        drawHomeCircle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("bradope_log_activity", "oncreate")
        ForegroundService.stopService(this)
        setContentView(R.layout.activity_maps)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        locationPrioritySpinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, LocationPriority.values()))

        if (!checkPermissions()) {
            requestPermissions()
        }

        loadSettingsFromStorage(blinkGetSettings())
        drawHomeLocation()

        blinkInit(this)
        blinkSetListener(this)

        setButtonListeners()

        setUpdateLoop()
    }

    override fun onPause() {
        super.onPause()
        Log.i("bradope_log_activity", "pause")
        if (!userQuit)
            ForegroundService.startService(this, "service is running")
    }

    override fun onResume() {
        super.onResume()
        ForegroundService.stopService(this)
        Log.i("bradope_log_activity", "resume")
        blinkRecreateLocationRequestClient(this)
        blinkSetListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("bradope_log_activity", "onDestroy")
    }

    override fun onConnectToBlink(success: Boolean) {
        runOnUiThread(java.lang.Runnable {
            Toast.makeText(
                this,
                "Registered With Blink",
                Toast.LENGTH_SHORT
            ).show()
        })
    }

    override fun onStatusChange(lastStatus: BlinkArmState, newStatus: BlinkArmState) {
        runOnUiThread(java.lang.Runnable {
            Toast.makeText(
                this,
                "BLink Status: $lastStatus -> $newStatus",
                Toast.LENGTH_LONG
            ).show()
        })
    }

    override fun onLocationChange(location: Location) {
        updateAndDrawLocation(LatLng(location.latitude, location.longitude))
    }

    private fun setUpdateLoop() = thread {
        while (isDestroyed() == false) {
            showStatus()

            Thread.sleep(1000)
        }
        Log.i("bradope_log_activity", "thread destroyed")
    }

    private fun setButtonListeners() {
        button.setOnClickListener(View.OnClickListener {
            blinkStatusRefresh()
        })
        startStop.setOnClickListener(View.OnClickListener {
            userQuit = true
            ForegroundService.stopService(this)
            blinkQuit()
            finishAndRemoveTask()
        })
        gotoHome.setOnClickListener {
            if (googleMap != null) {
                val cameraPosition = CameraPosition.Builder()
                    .target(homeLocation)
                    .zoom(16.0f)
                    .build()
                googleMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }

        gotoYou.setOnClickListener {
            if (myLocation != null && googleMap != null) {
                val cameraPosition = CameraPosition.Builder()
                    .target(myLocation!!)
                    .zoom(16.0f)
                    .build()
                googleMap!!.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
            }
        }
        settingsButton.setOnClickListener { SettingsPage() }
    }

    private fun updateAndDrawLocation(location: LatLng) {
        myLocation = location

        runOnUiThread{
            val options = MarkerOptions()
                .position(location)
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )

            marker?.remove()
            marker = googleMap!!.addMarker(options)
        }
    }

    private fun drawHomeCircle() {
        runOnUiThread {
            val homeCircle = CircleOptions()
                .center(homeLocation)
                .radius(blinkGetSettings().minDistFromHome * 100000)
                .clickable(true)
                .strokeColor(Color.RED)
            googleMap!!.addCircle(homeCircle)
            googleMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(homeLocation, 16.0f))
        }
    }

    private fun drawHomeLocation() {
        runOnUiThread {
            // add these to resources
            val lat = getString(R.string.homeLatitude).toDouble()
            val lon = getString(R.string.homeLongitude).toDouble()
            // todo only use read in location if not in settings
            homeLocation = LatLng(lat, lon)
            blinkGetSettings().homeLocation = homeLocation
        }
    }

    private fun checkPermissions(): Boolean {
        return PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this@BlinkActivity,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.INTERNET
            ),
            REQUEST_PERMISSIONS_REQUEST_CODE
        )
    }

    private fun showStatus() {
        runOnUiThread(java.lang.Runnable {
            try {
                text.setText("Blink Status: ${blinkGetLastBlinkState().toString()}")

                val lastLocation = blinkGetLastLocation()
                if (lastLocation != null) {
                    val newLoc = LatLng(lastLocation.latitude, lastLocation.longitude)

                    locState.setText("${blinkGetLastLocationState().toString()} - ${distToHome(lastLocation).format(2)}")

                    if (googleMap != null) updateAndDrawLocation(newLoc)
                }
            } catch ( e: Exception) {
                Log.e("bradope_log_activity", " e: ${e.message}")
            }
        })
    }

    private fun syncSettingsWithStorage(blinkSettings: BlinkSettings) {
        // :TODO:
    }

    private fun loadSettingsFromStorage(blinkSettings: BlinkSettings) {

    }

    private fun distToHome(location: Location): Double{
        val lat = location.latitude - homeLocation.latitude
        val lon = location.longitude - homeLocation.longitude
        val distSquared = (lat*lat) + (lon*lon)
        return sqrt(distSquared) * 1000

    }
    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    inner class SettingsPage {

        init {
            runOnUiThread {
                settings_menu.visibility = View.VISIBLE
                mainScreenLayout.visibility = View.GONE
                val blinkSettings = blinkGetSettings()

                closeSettings.setOnClickListener{ closeSettingsMenu() }
                syncLabelsWithSettings(blinkSettings)
            }

        }

        private fun syncLabelsWithSettings(blinkSettings: BlinkSettings) {
            locationPrioritySpinner.setSelection(blinkSettings.locationPriority.ordinal)

            setupTweaker(
                fastestLocationUpdateIntervalDecrease,
                fastestLocationUpdateIntervalIncrease,
                fastestLocationUpdateIntervalTextView,
                blinkSettings::fastestLocationUpdateIntervalInSeconds
            )
            setupTweaker(
                locationUpdateIntervalDecrease,
                locationUpdateIntervalIncrease,
                locationUpdateIntervalTextView,
                blinkSettings::locationUpdateIntervalInSeconds
            )
        }

        private fun setupTweaker(decrease: Button, increase: Button, textView: TextView, prop: KProperty<Int>) {
            textView.text = "${getSettingFetcher(prop)()}s"
            increase.setOnClickListener { textView.text = "${valueWithoutS(textView.text)+1}s" }
            decrease.setOnClickListener {textView.text = "${(valueWithoutS(textView.text)-1).coerceAtLeast(1)}s" }
        }

        private fun valueWithoutS(text: CharSequence) = text.substring(0, text.length - 1).toInt()
        
        private fun closeSettingsMenu() {
            runOnUiThread {
                settings_menu.visibility = View.GONE
                mainScreenLayout.visibility = View.VISIBLE
            }

            val blinkSettings = blinkGetSettings()

            var settingsSyncNeeded = changeLocationSettingsIfRequired(blinkSettings)

            // :TODO: check other settings

            if (settingsSyncNeeded) {
                syncSettingsWithStorage(blinkSettings)
            }
        }

        private fun changeLocationSettingsIfRequired(blinkSettings: BlinkSettings): Boolean {
            val selectedPriorty = LocationPriority.valueOf(locationPrioritySpinner.selectedItem.toString())
            var needToUpdateLocationSettings = syncSettingIfChanged(selectedPriorty, blinkSettings::locationPriority)
            needToUpdateLocationSettings = syncSettingIfChanged(valueWithoutS(fastestLocationUpdateIntervalTextView.text), blinkSettings::fastestLocationUpdateIntervalInSeconds) || needToUpdateLocationSettings
            needToUpdateLocationSettings = syncSettingIfChanged(valueWithoutS(locationUpdateIntervalTextView.text), blinkSettings::locationUpdateIntervalInSeconds) || needToUpdateLocationSettings

            if (needToUpdateLocationSettings) {
                blinkRecreateLocationRequestClient(this@BlinkActivity)
            }

            return needToUpdateLocationSettings
        }

    }
}
