package com.example.weatherapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.util.Locale
import kotlin.coroutines.resume

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private var tts: TextToSpeech? = null
    private val CHANNEL_ID = "WeatherBroadcastChannel"
    private val NOTIFICATION_ID = 101

    // Required for Expedited Work on Android versions < 12
    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val cityName = inputData.getString("cityName") ?: "北京"
        val lat = inputData.getDouble("lat", 39.9042)
        val lon = inputData.getDouble("lon", 116.4074)

        // Force promote to foreground immediately
        try {
            setForeground(createForegroundInfo())
            Log.d("WeatherWorker", "Foreground service started successfully")
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Failed to start foreground service", e)
            // Even if setForeground fails, we might still be able to run as a background task
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeatherApp:AlarmWakeLock")
        
        try {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)
            val response = service.getWeather(lat, lon)
            
            val weather = response.current_weather
            val description = getWeatherDescription(weather.weathercode)
            
            val greeting = getDynamicGreeting()
            val text = "${greeting}！现在为您播报天气。当前位置：${cityName}，天气${description}，温度约为${weather.temperature}度。祝您今天在${cityName}拥有一份好心情！"

            Log.d("WeatherWorker", "Starting TTS: $text")
            val speakResult = speakSync(text)
            
            // Wait for speech to finish (roughly)
            delay(15000) 

            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Error in WeatherWorker execution", e)
            Result.retry()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            tts?.shutdown()
            Log.d("WeatherWorker", "Worker finished and cleaned up")
        }
    }

    private fun getDynamicGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "早安"
            in 11..13 -> "午安"
            in 14..18 -> "下午好"
            in 19..23 -> "晚上好"
            else -> "夜深了"
        }
    }

    private suspend fun speakSync(text: String): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            var initialized = false
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    // Some TTS engines take a moment to be actually ready for speech
                    val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WeatherSpeak")
                    if (result == TextToSpeech.SUCCESS) {
                        initialized = true
                        continuation.resume(true)
                    } else {
                        if (!initialized) continuation.resume(false)
                    }
                } else {
                    if (!initialized) continuation.resume(false)
                }
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val title = "智能天气播报中"
        val content = "正在为您提供实时语音天气速报"

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "天气播报服务", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "确保天气闹钟在后台准时运行"
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
