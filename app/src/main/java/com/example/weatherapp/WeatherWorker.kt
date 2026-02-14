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
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private var tts: TextToSpeech? = null
    private val CHANNEL_ID = "WeatherBroadcastChannel"
    private val NOTIFICATION_ID = 101

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Prepare Foreground Service (Alarm style)
        setForeground(createForegroundInfo())

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeatherApp:AlarmWakeLock")
        
        try {
            wakeLock.acquire(10 * 60 * 1000L /*10 minutes*/)
            
            val cityName = inputData.getString("cityName") ?: "北京"
            val lat = inputData.getDouble("lat", 39.9042)
            val lon = inputData.getDouble("lon", 116.4074)

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)
            val response = service.getWeather(lat, lon)
            
            val weather = response.current_weather
            val description = getWeatherDescription(weather.weathercode)
            
            val text = "早安！现在为您播报天气。当前位置：${cityName}，天气${description}，温度约为${weather.temperature}度。祝您今天在${cityName}拥有一份好心情！"

            speakSync(text)
            
            // Wait a bit to ensure TTS finished before worker dies
            delay(15000) 

            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Error fetching weather", e)
            Result.retry()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private suspend fun speakSync(text: String) {
        withContext(Dispatchers.Main) {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.CHINESE
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WeatherSpeak")
                }
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val title = "天气正在播报"
        val content = "正在为您获取并播报今日天气..."

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "天气播报服务", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "每日天气语音播报提醒"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
