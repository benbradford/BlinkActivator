package com.bradope.blinkactivator

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
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
import kotlinx.android.synthetic.main.activity_maps.button
import kotlinx.android.synthetic.main.activity_maps.startStop
import kotlinx.android.synthetic.main.activity_maps.gotoYou
import kotlinx.android.synthetic.main.activity_maps.gotoHome
import kotlin.concurrent.thread
import kotlin.math.sqrt

class BlinkActivity : AppCompatActivity(), BlinkAccessListener, OnMapReadyCallback {

    private val REQUEST_PERMISSIONS_REQUEST_CODE = 34
    private val homeLocation = LatLng(51.083008, 1.161534)
    private var googleMap: GoogleMap? = null
    private var marker: Marker? = null
    private var myLocation: LatLng? = null

    override fun onMapReady(googleMap: GoogleMap) {
        Log.i("bradope_log_activity", "onmapready")
        this.googleMap = googleMap
        val homeCircle = CircleOptions()
            .center(homeLocation)
            .radius(180.0)
            .clickable(true)
            .strokeColor(Color.RED)
        googleMap!!.addCircle(homeCircle)
        googleMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(homeLocation, 16.0f))

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("bradope_log_activity", "oncreate")
            setContentView(R.layout.activity_maps)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (!checkPermissions()) {
            requestPermissions()
        }

        blinkInit(this)
        blinkSetListener(this)

        button.setOnClickListener(View.OnClickListener {
            blinkStatusRefresh()
        })
        startStop.setOnClickListener(View.OnClickListener {
            ForegroundService.stopService(this)
            blinkQuit()
            finish()
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

        thread {
            while (isDestroyed() == false) {
                Thread.sleep(1000)
                runOnUiThread {
                    showStatus()
                }
            }
            Log.i("bradope_log_activity", "thread destroyed")
        }
    }

    override fun onPause() {
        super.onPause()
        Log.i("bradope_log_activity", "pause")
            ForegroundService.startService(this, "starting foreground service")
    }

    override fun onResume() {
        super.onResume()
        Log.i("bradope_log_activity", "resume")
            blinkSetListener(this)
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

    override fun onStatusChange() {
        showStatus()
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

            val blinkStatusView: TextView = findViewById(R.id.text)
            val blinkStatus = blinkGetLastBlinkState()
            blinkStatusView.setText("Blink Status: ${blinkStatus.toString()}")

            val lastLocation = blinkGetLastLocation()
            if (lastLocation != null) {
                myLocation = LatLng(lastLocation!!.latitude, lastLocation!!.longitude)

                val locationStatusView = findViewById<TextView>(R.id.locState)
                val locationStatus = blinkGetLastLocationState()
                val distToHome = distToHome(lastLocation)
                locationStatusView.setText("${locationStatus.toString()} - ${distToHome.format(2)}")

                if (googleMap != null) {
                    // :TODO: measure distance between myLocation and lastLocation before commencing
                    val options = MarkerOptions()
                        .position(myLocation!!)
                        .icon( BitmapDescriptorFactory.defaultMarker(
                            BitmapDescriptorFactory.HUE_RED))

                    marker?.remove()
                    marker = googleMap!!.addMarker(options)
                }
            }
        })
    }

    fun distToHome(location: Location): Double{
        val lat = location.latitude - homeLocation.latitude
        val lon = location.longitude - homeLocation.longitude
        val distSquared = (lat*lat) + (lon*lon)
        return sqrt(distSquared) * 1000

    }
    fun Double.format(digits: Int) = "%.${digits}f".format(this)
}
