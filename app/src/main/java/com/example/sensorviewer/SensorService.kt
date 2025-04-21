package com.example.sensorviewer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

class SensorService : Service() {
    companion object {
        const val CHANNEL_ID = "monitor_service_channel"
        const val NOTIFICATION_ID = 1

        const val EXTRA_AX = "extra_ax"
        const val EXTRA_AY = "extra_ay"
        const val EXTRA_AZ = "extra_az"
        const val EXTRA_PERIOD = "extra_period"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )
        serviceChannel.description = "Channel for accelerometer monitor service"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Accelerometer Monitor")
            .setContentText("Monitoring thresholds...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val threshold = listOf(
                it.getFloatExtra(EXTRA_AX, 0f),
                it.getFloatExtra(EXTRA_AY, 0f),
                it.getFloatExtra(EXTRA_AZ, 0f),
            )

            val period = it.getFloatExtra(EXTRA_PERIOD, 1f).roundToLong() * 1000
            var prevTime = 0L

            serviceScope.launch {
                this@SensorService
                    .accelerometerFlow()
                    .collect {
                        val now = System.currentTimeMillis()
                        if (now - prevTime < period) return@collect

                        if (it.zip(threshold).any { (c, t) -> c > t }) {
                            prevTime = now
                            CoroutineScope(Dispatchers.Main).launch {
                                Toast.makeText(
                                    this@SensorService, "Threshold exceeded: $threshold",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.coroutineContext.cancelChildren()
    }
}