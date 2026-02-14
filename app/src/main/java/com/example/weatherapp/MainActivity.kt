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
import androidx.compose.material.icons.filled.LocationOn
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
import java.util.concurrent.TimeUnit
import java.util.*

data class City(val name: String, val lat: Double, val lon: Double)

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

        val cityData = workDataOf(
            "cityName" to city.name,
            "lat" to city.lat,
            "lon" to city.lon
        )

        val weatherRequest = PeriodicWorkRequestBuilder<WeatherWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .setInputData(cityData)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag("DailyWeatherBroadcast")
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "DailyWeatherBroadcast",
            ExistingPeriodicWorkPolicy.REPLACE,
            weatherRequest
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun WeatherDashboard() {
        var selectedHour by remember { mutableIntStateOf(8) }
        var selectedMinute by remember { mutableIntStateOf(0) }
        var selectedCity by remember { mutableStateOf(presetCities[0]) }
        
        val timePickerState = rememberTimePickerState(initialHour = selectedHour, initialMinute = selectedMinute)
        var showTimeDialog by remember { mutableStateOf(false) }
        var showCityDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF1E3C72), Color(0xFF2A5298))
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "智能天气闹钟",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 40.dp, bottom = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // City Selection Card
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showCityDialog = true },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("当前选择城市", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        Text(selectedCity.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Time Selection Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("提醒时间", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                    
                    Text(
                        text = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute),
                        color = Color.White,
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light,
                        modifier = Modifier.clickable { showTimeDialog = true }
                    )

                    Button(
                        onClick = {
                            scheduleWeatherTask(timePickerState.hour, timePickerState.minute, selectedCity)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("保存每日闹钟", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = {
                    val cityData = workDataOf(
                        "cityName" to selectedCity.name,
                        "lat" to selectedCity.lat,
                        "lon" to selectedCity.lon
                    )
                    val oneTimeRequest = OneTimeWorkRequestBuilder<WeatherWorker>()
                        .setInputData(cityData)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()
                    WorkManager.getInstance(applicationContext).enqueue(oneTimeRequest)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("立即测试播报", fontWeight = FontWeight.SemiBold)
            }
        }

        // Time Picker Dialog
        if (showTimeDialog) {
            AlertDialog(
                onDismissRequest = { showTimeDialog = false },
                confirmButton = {
                    TextButton(onClick = { showTimeDialog = false }) { Text("确定") }
                },
                text = { TimePicker(state = timePickerState) }
            )
        }

        // City Selector Dialog
        if (showCityDialog) {
            AlertDialog(
                onDismissRequest = { showCityDialog = false },
                title = { Text("选择播报城市") },
                text = {
                    LazyColumn {
                        items(presetCities) { city ->
                            ListItem(
                                headlineContent = { Text(city.name) },
                                modifier = Modifier.clickable {
                                    selectedCity = city
                                    showCityDialog = false
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
fun WeatherAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4FC3F7),
            surface = Color(0xFF1E3C72)
        ),
        content = content
    )
}
