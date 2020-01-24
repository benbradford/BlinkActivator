package com.bradope.blinkactivator

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
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
import com.google.gson.Gson
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.full.memberProperties

class BlinkActivity : AppCompatActivity(), BlinkAccessListener, OnMapReadyCallback {
    companion object val LOG_TAG = "bradope_log_activity"

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private lateinit var homeLocation: LatLng
    private var googleMap: GoogleMap? = null
    private var marker: Marker? = null
    var homeCircle: Circle? = null
    private var myLocation: LatLng? = null
    private var userQuit = false

    override fun onMapReady(googleMap: GoogleMap) {
        Log.i(LOG_TAG, "onmapready")
        this.googleMap = googleMap
        drawHomeCircle()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LOG_TAG, "oncreate")
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
        Log.i(LOG_TAG, "pause")
        if (!userQuit)
            ForegroundService.startService(this, "service is running")
    }

    override fun onResume() {
        super.onResume()

        ForegroundService.stopService(this)
        Log.i(LOG_TAG, "resume")
        blinkRecreateLocationRequestClient(this)
        blinkSetListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(LOG_TAG, "onDestroy")
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

    override fun onStatusChange(previousState: BlinkArmState, newState: BlinkArmState) {
        runOnUiThread{
            Toast.makeText(
                this,
                "BLink Status: $previousState -> $newState",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onLocationChange(location: Location) {
        updateAndDrawLocation(LatLng(location.latitude, location.longitude))
    }

    private fun setUpdateLoop() = thread {
        while (isDestroyed() == false) {
            showStatus()

            Thread.sleep(1000)
        }
        Log.i(LOG_TAG, "thread destroyed")
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
        settingsButton.setOnClickListener {
            SettingsPage()

        }
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
            val homeCircleOptions = CircleOptions()
                .center(homeLocation)
                .radius(blinkGetSettings().minDistFromHome.toDouble())
                .clickable(true)
                .strokeColor(Color.RED)
            homeCircle?.remove()
            homeCircle = googleMap!!.addCircle(homeCircleOptions)
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
                if (blinkIsInScheduledArm()) {
                    text.setText("Blink Status: Scheduled Arm")
                } else {
                    text.setText("Blink Status: ${blinkGetLastBlinkState().toString()}")
                }

                val lastLocation = blinkGetLastLocation()
                if (lastLocation != null) {
                    val newLoc = LatLng(lastLocation.latitude, lastLocation.longitude)

                    locState.setText("Location Status: ${blinkGetLastLocationState().toString()}")

                    if (googleMap != null) updateAndDrawLocation(newLoc)
                }
            } catch ( e: Exception) {
                Log.e(LOG_TAG, " e: ${e.message}")
            }
        })
    }

    private fun writeSettingsToStorage(blinkSettings: BlinkSettings) {
        try {
            val prefs = getSharedPreferences("blink_settings", Context.MODE_PRIVATE).edit()
            prefs.putBoolean("hasData", true)
            prefs.putString("settings", Gson().toJson(blinkSettings))
            prefs.apply()
        } catch ( e: Exception ) {
            Log.e(LOG_TAG, "Problem writing to storage " + e)
        }
    }

    private fun loadSettingsFromStorage(blinkSettings: BlinkSettings) {
        try {
            val prefs = getSharedPreferences("blink_settings", Context.MODE_PRIVATE)
            if (!prefs.getBoolean("hasData", false)) return
            val newSettings = Gson().fromJson<BlinkSettings>(
                prefs.getString("settings", ""),
                BlinkSettings::class.java
            )
            for (prop in blinkSettings::class.memberProperties) {
                val other = newSettings::class.members.find { it.name == prop.name }
                val value = other!!.call(newSettings)
                val mutableProp = (prop as KMutableProperty<R>)
                mutableProp.setter.call(blinkSettings, value)
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "unable to load settings " + e)
        }
    }

    inner class SettingsPage {

        inner class SeekBarBinder(prop: KProperty<Int>, seeker: SeekBar, val label: TextView, val postFix: String): SeekBar.OnSeekBarChangeListener {
            init {
                seeker.progress = getSettingFetcher(prop)()
                label.text = "${seeker.progress}s"
                seeker.setOnSeekBarChangeListener(this)
            }

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                label.text = "${progress}${postFix}"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}

        }
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

            SeekBarBinder(blinkSettings::fastestLocationUpdateIntervalInSeconds,
                fastestLocationUpdateSeekBar,
                fastestLocationUpdateLabel,
                "s")
            SeekBarBinder(blinkSettings::locationUpdateIntervalInSeconds,
                locationUpdateSeekBar,
                locationUpdateLabel,
                "s")
            SeekBarBinder(blinkSettings::minDistFromHome,
                homeBoundarySeekbar,
                homeBoundaryLabel,
                "m")
        }

        private fun valueWithoutS(text: CharSequence) = text.substring(0, text.length - 1).toInt()
        
        private fun closeSettingsMenu() {
            runOnUiThread {
                settings_menu.visibility = View.GONE
                mainScreenLayout.visibility = View.VISIBLE
            }

            val blinkSettings = blinkGetSettings()

            var settingsSyncNeeded = changeLocationSettingsIfRequired(blinkSettings)

            settingsSyncNeeded = changeHomeSettingsIfRequired(blinkSettings) || settingsSyncNeeded

            if (settingsSyncNeeded) {
                writeSettingsToStorage(blinkSettings)
            }
        }

        private fun changeLocationSettingsIfRequired(blinkSettings: BlinkSettings): Boolean {
            val selectedPriorty = LocationPriority.valueOf(locationPrioritySpinner.selectedItem.toString())
            var needToUpdateLocationSettings = syncSettingIfChanged(selectedPriorty, blinkSettings::locationPriority)
            needToUpdateLocationSettings = syncSettingIfChanged(fastestLocationUpdateSeekBar.progress, blinkSettings::fastestLocationUpdateIntervalInSeconds) || needToUpdateLocationSettings
            needToUpdateLocationSettings = syncSettingIfChanged(locationUpdateSeekBar.progress, blinkSettings::locationUpdateIntervalInSeconds) || needToUpdateLocationSettings

            if (needToUpdateLocationSettings) {
                blinkRecreateLocationRequestClient(this@BlinkActivity)
            }

            return needToUpdateLocationSettings
        }

        private fun changeHomeSettingsIfRequired(blinkSettings: BlinkSettings): Boolean {
            if (syncSettingIfChanged(homeBoundarySeekbar.progress, blinkSettings::minDistFromHome)) {
                drawHomeCircle()
                return true
            }
            return false
        }

    }
}
