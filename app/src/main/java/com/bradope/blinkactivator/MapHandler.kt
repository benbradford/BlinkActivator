package com.bradope.blinkactivator

import android.app.Activity
import android.graphics.Color
import com.bradope.blinkactivator.blink.blinkGetSettings
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*

class MapHandler (val activity: Activity, val googleMap: GoogleMap){
    private val homeLocation: LatLng
    private var marker: Marker? = null
    var homeCircle: Circle? = null
    private var myLocation: LatLng? = null

    init {
        // add these to resources
        val lat = activity.getString(R.string.homeLatitude).toDouble()
        val lon = activity.getString(R.string.homeLongitude).toDouble()
        // todo only use read in location if not in settings
        homeLocation = LatLng(lat, lon)
        blinkGetSettings().homeLocation = homeLocation

        drawHomeCircle()

    }

    fun gotoHome() {

            val cameraPosition = CameraPosition.Builder()
                .target(homeLocation)
                .zoom(16.0f)
                .build()
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))

    }

    fun gotoUser() {
        if (myLocation != null) {
            val cameraPosition = CameraPosition.Builder()
                .target(myLocation!!)
                .zoom(16.0f)
                .build()
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    fun updateAndDrawLocation(location: LatLng) {
        myLocation = location

        activity.runOnUiThread{
            val options = MarkerOptions()
                .position(location)
                .icon(
                    BitmapDescriptorFactory.defaultMarker(
                        BitmapDescriptorFactory.HUE_RED
                    )
                )

            marker?.remove()
            marker = googleMap.addMarker(options)
        }
    }

    fun drawHomeCircle() {
        activity.runOnUiThread {
            val homeCircleOptions = CircleOptions()
                .center(homeLocation)
                .radius(blinkGetSettings().minDistFromHome.toDouble())
                .clickable(true)
                .strokeColor(Color.RED)
            homeCircle?.remove()
            homeCircle = googleMap.addCircle(homeCircleOptions)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(homeLocation, 16.0f))
        }
    }

}