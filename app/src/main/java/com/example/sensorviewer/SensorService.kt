package com.example.sensorviewer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToLong

class SensorService : Service() {
    companion object {
        const val CHANNEL_ID = "monitor_service_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP_SERVICE = "com.example.sensorviewer.ACTION_STOP_SERVICE"

        const val EXTRA_DATA = "extra_data"
        const val EXTRA_PERIOD = "extra_period"
        const val EXTRA_SENSOR = "extra_sensor"
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Sensor Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Channel for sensor monitor service" }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(serviceChannel)

        startForeground(
            NOTIFICATION_ID, NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Monitor")
                .setContentText("Monitoring thresholds...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .addAction(
                    R.drawable.ic_launcher_foreground, "Stop", PendingIntent.getService(
                        this, 0, Intent(
                            this, SensorService::class.java
                        ).apply { action = ACTION_STOP_SERVICE },
                        PendingIntent.FLAG_IMMUTABLE
                    )
                )
                .build()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_SERVICE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        intent?.let {
            val threshold = it.getFloatArrayExtra(EXTRA_DATA)!!.toList()
            val period = it.getFloatExtra(EXTRA_PERIOD, 1f).roundToLong() * 1000
            val sensor = it.getStringExtra(EXTRA_SENSOR)!!
            var prevTime = 0L

            val flow: Flow<List<Float>> = when (sensor) {
                Screen.Accelerometer.name -> this@SensorService.accelerometerFlow()
                Screen.Gyroscope.name -> this@SensorService.gyroscopeFlow()
                Screen.GPS.name -> this@SensorService.gpsFlow(period)
                else -> throw UnsupportedOperationException()
            }

            val comp: (List<Float>, List<Float>) -> Boolean = when (sensor) {
                Screen.Accelerometer.name, Screen.Gyroscope.name -> { a, b ->
                    a.zip(b).any { (c, t) -> c.absoluteValue > t }
                }

                Screen.GPS.name -> { a, b -> a == b }

                else -> throw UnsupportedOperationException()
            }

            serviceScope.launch {
                flow.collect {
                    val now = System.currentTimeMillis()
                    if ((sensor == Screen.GPS.name || now - prevTime >= period) && comp(
                            it, threshold
                        )
                    ) {
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