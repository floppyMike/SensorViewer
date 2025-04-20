package com.example.sensorviewer

import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.sensorviewer.ui.theme.SensorViewerTheme
import androidx.core.app.ActivityCompat
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.widget.Toast
import androidx.compose.material3.TextField
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs

data class SensorData(
    val accelX: Float = 0f,
    val accelY: Float = 0f,
    val accelZ: Float = 0f,
    val gyroX: Float = 0f,
    val gyroY: Float = 0f,
    val gyroZ: Float = 0f
)

data class GpsData(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

private var latestSensorData = SensorData()
private var latestGpsData = GpsData()

class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null

    private val sensorData = mutableStateOf(SensorData())
    private val gpsData = mutableStateOf(GpsData())

    private lateinit var thresholdAlertReceiver: ThresholdAlertReceiver

    var accelThresholdX by mutableStateOf(20f)
    var accelThresholdY by mutableStateOf(20f)
    var accelThresholdZ by mutableStateOf(20f)

    var gyroThresholdX by mutableStateOf(20f)
    var gyroThresholdY by mutableStateOf(20f)
    var gyroThresholdZ by mutableStateOf(20f)

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestGpsData = GpsData(location.latitude, location.longitude)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        thresholdAlertReceiver = ThresholdAlertReceiver()
        val intentFilter = IntentFilter("com.example.THRESHOLD_TRIGGERED")
        registerReceiver(thresholdAlertReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000,
                5f,
                locationListener
            )
        }

        val filter = IntentFilter("com.example.THRESHOLD_TRIGGERED")
        registerReceiver(thresholdReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        enableEdgeToEdge()
        setContent {
            SensorViewerTheme {
                val periodState = remember { mutableFloatStateOf(1f) }
                val context = LocalContext.current
                LaunchedEffect(
                    periodState.value, accelThresholdX, accelThresholdY, accelThresholdZ,
                    gyroThresholdX, gyroThresholdY, gyroThresholdZ
                ) {
                    val intent = Intent(context, SensorService::class.java).apply {
                        putExtra("period", periodState.value)
                        putExtra("accelThresholdX", accelThresholdX)
                        putExtra("accelThresholdY", accelThresholdY)
                        putExtra("accelThresholdZ", accelThresholdZ)
                        putExtra("gyroThresholdX", gyroThresholdX)
                        putExtra("gyroThresholdY", gyroThresholdY)
                        putExtra("gyroThresholdZ", gyroThresholdZ)
                    }
                    context.startService(intent)
                }

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        PeriodSlider(
                            period = periodState.value,
                            onPeriodChange = { periodState.value = it },
                            modifier = Modifier.padding(innerPadding)
                        )
                        SensorDataCollector(
                            period = periodState.value,
                            modifier = Modifier.padding(innerPadding)
                        )
                        ThresholdInputs(
                            onAccelThresholdChange = { x, y, z ->
                                accelThresholdX = x
                                accelThresholdY = y
                                accelThresholdZ = z
                            },
                            onGyroThresholdChange = { x, y, z ->
                                gyroThresholdX = x
                                gyroThresholdY = y
                                gyroThresholdZ = z
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        gyroscope?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        checkThresholds(event.sensor.type, event.values)

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                sensorData.value = sensorData.value.copy(
                    accelX = event.values[0],
                    accelY = event.values[1],
                    accelZ = event.values[2]
                )
                latestSensorData = sensorData.value
            }

            Sensor.TYPE_GYROSCOPE -> {
                sensorData.value = sensorData.value.copy(
                    gyroX = event.values[0],
                    gyroY = event.values[1],
                    gyroZ = event.values[2]
                )
                latestSensorData = sensorData.value
            }
        }
    }

    private fun checkThresholds(sensorType: Int, values: FloatArray) {
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            if (abs(values[0]) > accelThresholdX ||
                abs(values[1]) > accelThresholdY ||
                abs(values[2]) > accelThresholdZ
            ) {

                sendThresholdBroadcast("AccelThreshold", values)
            }
        } else if (sensorType == Sensor.TYPE_GYROSCOPE) {
            if (abs(values[0]) > gyroThresholdX ||
                abs(values[1]) > gyroThresholdY ||
                abs(values[2]) > gyroThresholdZ
            ) {

                sendThresholdBroadcast("GyroThreshold", values)
            }
        }
    }

    private fun sendThresholdBroadcast(type: String, values: FloatArray) {
        val intent = Intent("com.example.THRESHOLD_TRIGGERED")
        intent.setPackage(applicationContext.packageName)
        intent.apply {
            putExtra("type", type)
            putExtra("x", values[0])
            putExtra("y", values[1])
            putExtra("z", values[2])
        }
        sendBroadcast(intent)
        showThrottledToast(this, type, values[0], values[1], values[2])
    }

    private val thresholdReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getStringExtra("type")
            val x = intent.getFloatExtra("x", 0f)
            val y = intent.getFloatExtra("y", 0f)
            val z = intent.getFloatExtra("z", 0f)

            showThrottledToast(context, type, x, y, z)
        }
    }

    private var lastToastTime = 0L
    private val toastInterval = 2000L

    fun showThrottledToast(context: Context, type: String?, x: Float, y: Float, z: Float) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastToastTime >= toastInterval) {
            Toast.makeText(context, "$type Reach! X=$x, Y=$y, Z=$z", Toast.LENGTH_SHORT).show()
            lastToastTime = currentTime
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {

    }

    override fun onDestroy() {
        super.onDestroy()

        sensorManager.unregisterListener(this)
        locationManager.removeUpdates(locationListener)

        try {
            unregisterReceiver(thresholdAlertReceiver)
        } catch (e: Exception) {
        }

        unregisterReceiver(thresholdReceiver)
    }
}

@Composable
fun PeriodSlider(
    period: Float,
    onPeriodChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = period,
            onValueChange = { newValue ->
                val clamped = if (newValue == 0f) 1f else newValue
                onPeriodChange(clamped)
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            steps = 8,
            valueRange = 1f..10f
        )
        Text(text = "Update Period: ${period.toInt()}s")
    }
}

@Composable
fun SensorDataCollector(
    period: Float,
    modifier: Modifier = Modifier
) {
    val displaySensorData = remember { mutableStateOf(SensorData()) }
    val displayGpsData = remember { mutableStateOf(GpsData()) }

    LaunchedEffect(period) {
        while (true) {
            displaySensorData.value = latestSensorData
            displayGpsData.value = latestGpsData
            delay((period * 1000).toLong())
        }
    }

    SliderAdvanced(
        modifier = modifier,
        sensorData = displaySensorData.value,
        gpsData = displayGpsData.value,
        period = period
    )
}

@Composable
fun SliderAdvanced(
    modifier: Modifier = Modifier,
    sensorData: SensorData,
    gpsData: GpsData,
    period: Float
) {
    Column(modifier = modifier.padding(16.dp)) {
        Text("Accelerometer: X=${sensorData.accelX}, Y=${sensorData.accelY}, Z=${sensorData.accelZ}")
        Text("Gyroscope: X=${sensorData.gyroX}, Y=${sensorData.gyroY}, Z=${sensorData.gyroZ}")
        Text("GPS: Lat=${gpsData.latitude}, Lon=${gpsData.longitude}")
        Text("Update Period: ${period}s")
    }
}

@Composable
fun ThresholdInputs(
    onAccelThresholdChange: (Float, Float, Float) -> Unit,
    onGyroThresholdChange: (Float, Float, Float) -> Unit
) {
    var ax by remember { mutableStateOf("20") }
    var ay by remember { mutableStateOf("20") }
    var az by remember { mutableStateOf("20") }

    var gx by remember { mutableStateOf("20") }
    var gy by remember { mutableStateOf("20") }
    var gz by remember { mutableStateOf("20") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Accelerometer Threshold")

        TextField(value = ax, onValueChange = { ax = it }, label = { Text("X") })
        TextField(value = ay, onValueChange = { ay = it }, label = { Text("Y") })
        TextField(value = az, onValueChange = { az = it }, label = { Text("Z") })

        androidx.compose.material3.Button(onClick = {
            val fx = ax.toFloatOrNull() ?: 0f
            val fy = ay.toFloatOrNull() ?: 0f
            val fz = az.toFloatOrNull() ?: 0f
            onAccelThresholdChange(fx, fy, fz)
        }) {
            Text("Set Threshold")
        }

        Text("Gyroscope Threshold")

        TextField(value = gx, onValueChange = { gx = it }, label = { Text("X") })
        TextField(value = gy, onValueChange = { gy = it }, label = { Text("Y") })
        TextField(value = gz, onValueChange = { gz = it }, label = { Text("Z") })

        androidx.compose.material3.Button(onClick = {
            val fx = gx.toFloatOrNull() ?: 0f
            val fy = gy.toFloatOrNull() ?: 0f
            val fz = gz.toFloatOrNull() ?: 0f
            onGyroThresholdChange(fx, fy, fz)
        }) {
            Text("Set Threshold")
        }
    }
}