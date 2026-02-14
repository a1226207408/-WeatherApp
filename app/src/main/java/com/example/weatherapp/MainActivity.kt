package com.example.weatherapp

import android.os.Bundle
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.location.LocationListener
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
        
        // Request Notifications and Exact Alarm permissions for Android 13+
        requestCriticalPermissions()
        // Ensure notification channel exists for background alarms
        createNotificationChannel()
        
        setContent {
            WeatherAppTheme {
                WeatherDashboard()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "天气播报服务"
            val descriptionText = "确保天气闹钟在后台准时运行"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel("WeatherBroadcastChannel", name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestCriticalPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                } catch (e: Exception) {}
            }
        }
    }

    private fun getCityFromLocation(lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(this, Locale.CHINA)
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                address.locality ?: address.subAdminArea ?: address.adminArea ?: "未知城市"
            } else {
                "当前位置"
            }
        } catch (e: Exception) {
            "当前位置"
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WeatherDashboard() {
        val schedules = remember { mutableStateListOf<AlarmSchedule>() }
        
        LaunchedEffect(Unit) {
            schedules.addAll(AlarmStorage.getSchedules(this@MainActivity))
        }

        var showAddDialog by remember { mutableStateOf(false) }
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
                text = "超级天气闹钟",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 40.dp, bottom = 4.dp)
            )
            Text(
                text = "系统级精准播报 · 全天候运行",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (schedules.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("点击下方按钮设置您的第一个播报计划", color = Color.White.copy(alpha = 0.5f))
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(schedules) { schedule ->
                            ScheduleItem(schedule = schedule, onDelete = {
                                cancelAlarm(this@MainActivity, schedule)
                                schedules.remove(schedule)
                                AlarmStorage.saveSchedules(this@MainActivity, schedules.toList())
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
                Text("新增精准播报", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        if (showAddDialog) {
            var showCitySelector by remember { mutableStateOf(false) }
            val context = this@MainActivity
            var isLocating by remember { mutableStateOf(false) }
            
            val locationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    performNativeLocationUpdate(context) { city ->
                        tempCity = city
                        isLocating = false
                    }
                } else {
                    isLocating = false
                }
            }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("新增播报时段") },
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
                                    isLocating = true
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        performNativeLocationUpdate(context) { city ->
                                            tempCity = city
                                            isLocating = false
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                },
                                enabled = !isLocating
                            ) {
                                if (isLocating) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.MyLocation, contentDescription = null)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val newSchedule = AlarmSchedule(UUID.randomUUID().toString(), timePickerState.hour, timePickerState.minute, tempCity)
                        schedules.add(newSchedule)
                        setAlarm(this@MainActivity, newSchedule)
                        AlarmStorage.saveSchedules(this@MainActivity, schedules.toList())
                        showAddDialog = false
                    }) { Text("保存") }
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

    private fun performNativeLocationUpdate(context: Context, onCityDetected: (City) -> Unit) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            LocationManager.NETWORK_PROVIDER
        } else if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else {
            null
        }

        if (provider != null) {
            try {
                val lastKnown = locationManager.getLastKnownLocation(provider)
                if (lastKnown != null) {
                    val name = getCityFromLocation(lastKnown.latitude, lastKnown.longitude)
                    onCityDetected(City(name, lastKnown.latitude, lastKnown.longitude))
                }

                locationManager.requestLocationUpdates(provider, 0L, 0f, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        val name = getCityFromLocation(location.latitude, location.longitude)
                        onCityDetected(City(name, location.latitude, location.longitude) )
                        locationManager.removeUpdates(this)
                    }
                    override fun onStatusChanged(p0: String?, p1: Int, p2: Bundle?) {}
                })
            } catch (e: SecurityException) {}
        }
    }

    @Composable
    fun ScheduleItem(schedule: AlarmSchedule, onDelete: () -> Unit) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = String.format("%02d:%02d", schedule.hour, schedule.minute),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(schedule.city.name, color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
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
        colorScheme = darkColorScheme(primary = Color(0xFF4FC3F7), surface = Color(0xFF1E3C72)),
        content = content
    )
}
