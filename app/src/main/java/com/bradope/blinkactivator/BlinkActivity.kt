package com.bradope.blinkactivator

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.bradope.blinkactivator.blink.*
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import kotlinx.android.synthetic.main.activity_maps.*
import java.lang.Exception
import kotlin.concurrent.thread

class BlinkActivity : AppCompatActivity(), BlinkAccessListener {
    companion object val LOG_TAG = "bradope_log_activity"

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34

    private var userQuit = false
    private var map: MapHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(LOG_TAG, "oncreate")
        loadSettingsFromStorage(this, blinkGetSettings())
        ForegroundService.stopService(this)
        setContentView(R.layout.activity_maps)
        settings_menu.visibility = View.GONE
        mainScreenLayout.visibility = View.VISIBLE

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync({map = MapHandler(this, it) })

        locationPrioritySpinner.setAdapter(ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, LocationPriority.values()))

        if (!checkPermissions()) {
            requestPermissions()
        }

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
        map?.updateAndDrawLocation(LatLng(location.latitude, location.longitude))
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
           map?.gotoHome()
        }

        gotoYou.setOnClickListener {
            map?.gotoUser()
        }
        settingsButton.setOnClickListener {
            SettingsPage(this, {
                runOnUiThread {
                    settings_menu.visibility = View.GONE
                    mainScreenLayout.visibility = View.VISIBLE
                    map?.drawHomeCircle()
                }
            })

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

                    map?.updateAndDrawLocation(newLoc)
                }
            } catch ( e: Exception) {
                Log.e(LOG_TAG, " e: ${e.message}")
            }
        })
    }

}

