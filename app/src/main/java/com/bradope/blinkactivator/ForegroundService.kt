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
import com.bradope.blinkactivator.blink.BlinkAccessListener
import com.bradope.blinkactivator.blink.BlinkArmState
import com.bradope.blinkactivator.blink.blinkGetLastBlinkState
import com.bradope.blinkactivator.blink.blinkSetListener

class ForegroundService : Service(), BlinkAccessListener {

    private val CHANNEL_ID = "ForegroundService Kotlin"
    private var lastStatus: BlinkArmState = BlinkArmState.UNKNOWN

    private val id = nextId()
    companion object {
        private var ID = 1
        fun nextId() = ID++
        fun startService(context: Context, message: String) {
            Log.i("bradope_log_service", "STARTSERVICE")
            val startIntent = Intent(context, ForegroundService::class.java)
            startIntent.putExtra("inputExtra", message)
            ContextCompat.startForegroundService(context, startIntent)

        }
        fun stopService(context: Context) {
            Log.i("bradope_log_service", "STOPSERVICE")
            val stopIntent = Intent(context, ForegroundService::class.java)
            context.stopService(stopIntent)
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("bradope_log_service", "onStartCommand for ${id}")
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
        startForeground(1, notification)
        //stopSelf();
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("bradope_log_service", "onDestroy $id")
    }

    override fun onBind(intent: Intent): IBinder? {
        Log.i("bradope_log_service", "on bind")
        return null
    }

    override fun onConnectToBlink(success: Boolean) {
        Log.i("bradope_log_service", " service on connect to blink $success")
    }

    override fun onStatusChange() {
        val newArmState = blinkGetLastBlinkState()
        val notificationId = 123
        if (lastStatus != BlinkArmState.UNKNOWN && newArmState != lastStatus) {
            val text = "$lastStatus -> $newArmState"
            Log.i("bradope_log_service", " sending notification $text")
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
                notify(notificationId, notification)
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