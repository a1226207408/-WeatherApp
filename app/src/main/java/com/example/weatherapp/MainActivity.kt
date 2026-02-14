package com.example.weatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.MyLocation
import androidx.core.content.ContextCompat
import java.util.concurrent.TimeUnit
import java.util.*
import kotlinx.coroutines.launch

data class City(val name: String, val lat: Double, val lon: Double)
data class AlarmSchedule(val id: String, val hour: Int, val minute: Int, val city: City)

val presetCities = listOf(
    City("北京", 39.9042, 116.4074),
    City("上海", 31.2304, 121.4737),
    City("广州", 23.1291, 113.2644),
    City("深圳", 22.5431, 114.0579),
    City("杭州", 30.2741, 120.1551),
    City("成都", 30.5728, 104.0668),
    City("南京", 32.0603, 118.7969),
    City("武汉", 30.5928, 114.3055)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WeatherAppTheme {
                WeatherDashboard()
            }
        }
    }

    private fun scheduleWeatherTask(hour: Int, minute: Int, city: City) {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, hour)
        dueDate.set(Calendar.MINUTE, minute)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis
        val cityData = workDataOf("cityName" to city.name, "lat" to city.lat, "lon" to city.lon)

        val uniqueName = "WeatherAlarm_${hour}_${minute}"
        
        val weatherRequest = PeriodicWorkRequestBuilder<WeatherWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .setInputData(cityData)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("WeatherAlarm")
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            uniqueName,
            ExistingPeriodicWorkPolicy.REPLACE,
            weatherRequest
        )
    }

    private fun getCityFromLocation(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.CHINA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            addresses?.get(0)?.locality ?: addresses?.get(0)?.subAdminArea ?: "当前位置"
        } catch (e: Exception) {
            "当前位置"
        }
    }

    private fun cancelWeatherTask(hour: Int, minute: Int) {
        val uniqueName = "WeatherAlarm_${hour}_${minute}"
        WorkManager.getInstance(applicationContext).cancelUniqueWork(uniqueName)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WeatherDashboard() {
        val schedules = remember { mutableStateListOf<AlarmSchedule>() }
        var showAddDialog by remember { mutableStateOf(false) }
        
        var tempHour by remember { mutableIntStateOf(8) }
        var tempMinute by remember { mutableIntStateOf(0) }
        var tempCity by remember { mutableStateOf(presetCities[0]) }
        val timePickerState = rememberTimePickerState(initialHour = 8, initialMinute = 0)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(Color(0xFF1E3C72), Color(0xFF2A5298))))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "智能播报管家",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 40.dp, bottom = 4.dp)
            )
            Text(
                text = "支持设置多个播报时段",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // Current Schedules List
            Box(modifier = Modifier.weight(1f)) {
                if (schedules.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("点击下方按钮添加播报计划", color = Color.White.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(schedules) { schedule ->
                            ScheduleItem(schedule = schedule, onDelete = {
                                cancelWeatherTask(schedule.hour, schedule.minute)
                                schedules.remove(schedule)
                            })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("新增播报时段", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        // Add Schedule Dialog
        if (showAddDialog) {
            var showCitySelector by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val context = this
            val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
            
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    scope.launch {
                        try {
                            val result = fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token
                            )
                            result.addOnSuccessListener { location ->
                                location?.let {
                                    val detectedCityName = getCityFromLocation(it.latitude, it.longitude)
                                    tempCity = City(detectedCityName, it.latitude, it.longitude)
                                }
                            }
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                }
            }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("新增播报") },
                text = {
                    Column {
                        TimePicker(state = timePickerState)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { showCitySelector = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.LocationOn, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(tempCity.name, maxLines = 1)
                            }
                            
                            IconButton(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                                            .addOnSuccessListener { location ->
                                                location?.let {
                                                    val name = getCityFromLocation(it.latitude, it.longitude)
                                                    tempCity = City(name, it.latitude, it.longitude)
                                                }
                                            }
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Icon(Icons.Default.MyLocation, contentDescription = "定位")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newSchedule = AlarmSchedule(
                            UUID.randomUUID().toString(),
                            timePickerState.hour,
                            timePickerState.minute,
                            tempCity
                        )
                        schedules.add(newSchedule)
                        scheduleWeatherTask(newSchedule.hour, newSchedule.minute, newSchedule.city)
                        showAddDialog = false
                    }) { Text("添加") }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) { Text("取消") }
                }
            )

            if (showCitySelector) {
                AlertDialog(
                    onDismissRequest = { showCitySelector = false },
                    title = { Text("选择城市") },
                    text = {
                        LazyColumn {
                            items(presetCities) { city ->
                                ListItem(
                                    headlineContent = { Text(city.name) },
                                    modifier = Modifier.clickable {
                                        tempCity = city
                                        showCitySelector = false
                                    }
                                )
                            }
                        }
                    },
                    confirmButton = {}
                )
            }
        }
    }

    @Composable
    fun ScheduleItem(schedule: AlarmSchedule, onDelete: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = String.format("%02d:%02d", schedule.hour, schedule.minute),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(schedule.city.name, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun WeatherAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4FC3F7),
            surface = Color(0xFF1E3C72)
        ),
        content = content
    )
}
