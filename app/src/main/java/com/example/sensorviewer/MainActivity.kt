package com.example.sensorviewer

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.sensorviewer.ui.theme.SensorViewerTheme
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.roundToInt

enum class Screen {
    Accelerometer,
    Gyroscope,
}

// Accelerometer

fun Context.accelerometerFlow(): Flow<List<Float>> = callbackFlow {
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val accelSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run { return@callbackFlow }

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                trySend(event.values.toList())
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_NORMAL)
    awaitClose { sensorManager.unregisterListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            SensorViewerTheme {
                val navController = rememberNavController()
                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentScreen =
                    Screen.valueOf(backStackEntry?.destination?.route ?: Screen.Accelerometer.name)

                Scaffold(
                    topBar = { TopBarDisplay(currentScreen) },
                    bottomBar = { BottomBarDisplay(navController, currentScreen) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    val ctx = LocalContext.current

                    NavHost(
                        navController = navController,
                        startDestination = Screen.Accelerometer.name,
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(horizontal = 16.dp)
                            .fillMaxSize()
                    ) {
                        composable(route = Screen.Accelerometer.name) {
                            val accelState by remember { ctx.accelerometerFlow() }.collectAsState(
                                initial = listOf(0f, 0f, 0f)
                            )

                            AccelerometerDisplay(
                                listOf("X", "Y", "Z"),
                                accelState.map { it.toString() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// Top Bar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarDisplay(screen: Screen) {
    val showDialog = remember { mutableStateOf(false) }

    if (showDialog.value) {
        DialogServiceCreate(showDialog)
    }

    TopAppBar(
        title = { Text(text = screen.name) },
        actions = {
            OutlinedButton(onClick = {
                showDialog.value = true
            }) {
                Text(text = "Service")
            }
        }
    )
}

@Composable
fun DialogServiceCreate(showDialog: MutableState<Boolean>) {
    Dialog(onDismissRequest = { showDialog.value = false }) {
        val ctx = LocalContext.current

        var ax by remember { mutableStateOf("10") }
        var ay by remember { mutableStateOf("10") }
        var az by remember { mutableStateOf("10") }

        var period by remember { mutableFloatStateOf(1f) }

        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                TextField(value = ax, onValueChange = { ax = it }, label = { Text("X") })
                TextField(value = ay, onValueChange = { ay = it }, label = { Text("Y") })
                TextField(value = az, onValueChange = { az = it }, label = { Text("Z") })
                Spacer(Modifier.height(16.dp))
                PeriodSliderDisplay { period = it }
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = {
                    showDialog.value = false
                    ctx.startForegroundService(Intent(ctx, SensorService::class.java).apply {
                        putExtra(SensorService.EXTRA_AX, ax.toFloatOrNull() ?: 0f)
                        putExtra(SensorService.EXTRA_AY, ay.toFloatOrNull() ?: 0f)
                        putExtra(SensorService.EXTRA_AZ, az.toFloatOrNull() ?: 0f)
                        putExtra(SensorService.EXTRA_PERIOD, period)
                    })

                    Toast.makeText(ctx, "Service started", Toast.LENGTH_SHORT).show()
                }) {
                    Text(text = "Set Threshold")
                }
            }
        }
    }
}

@Composable
fun PeriodSliderDisplay(
    onPeriodChange: (Float) -> Unit = {},
) {
    var sliderPosition by remember { mutableFloatStateOf(1f) }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = sliderPosition.toFloat(),
            onValueChange = {
                sliderPosition = it
                onPeriodChange(
                    sliderPosition.roundToInt().toFloat()
                ) // For correctness with the display
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.secondary,
                activeTrackColor = MaterialTheme.colorScheme.secondary,
                inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            steps = 8,
            valueRange = 1f..10f
        )
        Text(text = "Update Period: ${sliderPosition.roundToInt()}s")
    }
}

// Bottom Bar

@Composable
fun BottomBarDisplay(nav: NavHostController, screen: Screen) {
    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Rounded.Info, contentDescription = "Live") },
            label = { Text(text = Screen.Accelerometer.name) },
            selected = screen == Screen.Accelerometer,
            onClick = {
                nav.navigate(Screen.Accelerometer.name) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    }
}

@Composable
fun AccelerometerDisplay(
    labels: List<String>,
    data: List<String>,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(labels.zip(data)) { (label, data) ->
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = label,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                    Text(
                        text = data,
                        fontSize = 24.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}
