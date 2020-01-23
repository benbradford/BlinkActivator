package com.bradope.blinkactivator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bradope.blinkactivator.blink.*

class ForegroundService : Service(), BlinkAccessListener {

    private val NOTIFICATION_ID = 123
    private val CHANNEL_ID = "ForegroundService Kotlin"
    private var lastStatus: BlinkArmState = BlinkArmState.UNKNOWN

    private val id = nextId()
    companion object {
        private var isServiceRunning = false
        private val LOG_TAG = "bradope_log_service"
        private var ID = 1
        fun nextId() = ID++
        fun startService(context: Context, message: String) {
            if (isServiceRunning) return
            Log.i(LOG_TAG, "STARTSERVICE")
            isServiceRunning = true
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)

        }
        fun stopService(context: Context) {
            if (!isServiceRunning) return
            Log.i(LOG_TAG, "STOPSERVICE")
            isServiceRunning = false
            val stopIntent = Intent(context, ForegroundService::class.java)
            context.stopService(stopIntent)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        blinkRecreateLocationRequestClient(this)
        Log.i(LOG_TAG, "onStartCommand for ${id}")
        blinkSetListener(this)
        lastStatus = blinkGetLastBlinkState()
        //do heavy work on a background thread
        val input = intent?.getStringExtra("inputExtra")
        createNotificationChannel()
        val notificationIntent = Intent(this, BlinkActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0, notificationIntent, 0
        )
        val deleteIntent = PendingIntent.getActivity(
            this,
            1, notificationIntent, 0
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blink Activator")
            .setContentText(input)
            .setSmallIcon(R.drawable.blink_green)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(deleteIntent)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        //stopSelf();
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(LOG_TAG, "onDestroy $id")
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i(LOG_TAG, "on bind")
        return null
    }

    override fun onConnectToBlink(success: Boolean) {
        Log.i(LOG_TAG, " service on connect to blink $success")
    }

    override fun onStatusChange() {
        val newArmState = blinkGetLastBlinkState()

        if (lastStatus != BlinkArmState.UNKNOWN && newArmState != lastStatus) {
            val text = "$lastStatus -> $newArmState"
            Log.i(LOG_TAG, " sending notification $text")
            val notificationIntent = Intent(this, BlinkActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0, notificationIntent, 0
            )
            val deleteIntent = PendingIntent.getActivity(
                this,
                1, notificationIntent, 0
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Blink Status Changed!")
                .setContentText(text)
                .setSmallIcon(R.drawable.blink_green)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(deleteIntent)
                .build()

            with(NotificationManagerCompat.from(this)) {
                // notificationId is a unique int for each notification that you must define
                notify(NOTIFICATION_ID, notification)
            }
        }
        lastStatus = newArmState
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)

        }
    }
}