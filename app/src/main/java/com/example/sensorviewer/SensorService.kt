package com.example.sensorviewer

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.app.*
import android.content.pm.PackageManager
import android.hardware.*
import android.location.*
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.abs

var accelThresholdX = 20f
var accelThresholdY = 20f
var accelThresholdZ = 20f
var gyroThresholdX = 20f
var gyroThresholdY = 20f
var gyroThresholdZ = 20f

class SensorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private var latestSensorData = SensorData()
    private var latestGpsData = GpsData()

    private var samplingJob: Job? = null
    private var period: Float = 1f

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestGpsData = GpsData(location.latitude, location.longitude)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locationListener)
        }

        createNotificationChannel()

        startForegroundCompat()
    }

    private fun startForegroundCompat() {
        val serviceChannel = NotificationChannel(
            "sensor_service_channel",
            "Sensor Monitoring",
            NotificationManager.IMPORTANCE_LOW
        )

        val notification = NotificationCompat.Builder(this, "sensor_service_channel")
            .setContentTitle("Sensor Viewer")
            .setContentText("Monitoring in background...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                "sensor_channel_id",
                "Sensor Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createForegroundNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, "sensor_service_channel")
            .setContentTitle("Sensor Viewer")
            .setContentText("Monitoring in background...")
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        period = intent?.getFloatExtra("period", 1f) ?: 1f

        accelThresholdX = intent?.getFloatExtra("accelThresholdX", 20f) ?: 20f
        accelThresholdY = intent?.getFloatExtra("accelThresholdY", 20f) ?: 20f
        accelThresholdZ = intent?.getFloatExtra("accelThresholdZ", 20f) ?: 20f
        gyroThresholdX = intent?.getFloatExtra("gyroThresholdX", 20f) ?: 20f
        gyroThresholdY = intent?.getFloatExtra("gyroThresholdY", 20f) ?: 20f
        gyroThresholdZ = intent?.getFloatExtra("gyroThresholdZ", 20f) ?: 20f

        val notification = createForegroundNotification()
        startForeground(1, notification)

        samplingJob?.cancel()
        samplingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                sendBroadcastData()
                delay((period * 1000).toLong())
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        samplingJob?.cancel()
        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(locationListener)
    }

    private fun sendBroadcastData() {
        val intent = Intent("com.example.sensorviewer.SENSOR_UPDATE").apply {
            putExtra("accelX", latestSensorData.accelX)
            putExtra("accelY", latestSensorData.accelY)
            putExtra("accelZ", latestSensorData.accelZ)
            putExtra("gyroX", latestSensorData.gyroX)
            putExtra("gyroY", latestSensorData.gyroY)
            putExtra("gyroZ", latestSensorData.gyroZ)
            putExtra("lat", latestGpsData.latitude)
            putExtra("lon", latestGpsData.longitude)
        }
        sendBroadcast(intent)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                latestSensorData = latestSensorData.copy(
                    accelX = event.values[0],
                    accelY = event.values[1],
                    accelZ = event.values[2]
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                latestSensorData = latestSensorData.copy(
                    gyroX = event.values[0],
                    gyroY = event.values[1],
                    gyroZ = event.values[2]
                )
            }
        }

        checkThresholds(event.sensor.type, event.values)
    }

    private fun checkThresholds(sensorType: Int, values: FloatArray) {
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            if (abs(values[0]) > accelThresholdX ||
                abs(values[1]) > accelThresholdY ||
                abs(values[2]) > accelThresholdZ) {

                sendThresholdBroadcast("AccelThreshold", values)
            }
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            if (abs(values[0]) > gyroThresholdX ||
                abs(values[1]) > gyroThresholdY ||
                abs(values[2]) > gyroThresholdZ) {

                sendThresholdBroadcast("GyroThreshold", values)
            }
        }
    }

    private fun sendThresholdBroadcast(type: String, values: FloatArray) {
//        Log.d("SensorService", "Threshold exceeded! Sending broadcast: $type")
        val intent = Intent("com.example.THRESHOLD_TRIGGERED")
        intent.setPackage(applicationContext.packageName)
        intent.apply {
            putExtra("type", type)
            putExtra("x", values[0])
            putExtra("y", values[1])
            putExtra("z", values[2])
        }
        sendBroadcast(intent)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}