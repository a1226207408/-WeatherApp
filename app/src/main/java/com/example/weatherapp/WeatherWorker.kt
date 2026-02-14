package com.example.weatherapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo()
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val cityName = inputData.getString("cityName") ?: "北京"
        val lat = inputData.getDouble("lat", 39.9042)
        val lon = inputData.getDouble("lon", 116.4074)

        try {
            setForeground(createForegroundInfo())
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Foreground failed", e)
        }

        val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WeatherApp:AlarmWakeLock")
        
        try {
            wakeLock.acquire(10 * 60 * 1000L)
            
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)
            val response = service.getWeather(lat, lon)
            
            val weather = response.current_weather
            val description = getWeatherDescription(weather.weathercode)
            
            val greeting = getDynamicGreeting()
            // Simplified Message
            val text = "${greeting}。${cityName}今天${description}，${weather.temperature}度。祝生活愉快。"

            Log.d("WeatherWorker", "Starting repeating TTS")
            
            // Repeat loop until worker is stopped (by Stop button)
            while (!isStopped) {
                val speakResult = speakSync(text)
                if (!speakResult) break
                
                // Gap between repeats
                delay(3000) 
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Error", e)
            Result.retry()
        } finally {
            if (wakeLock.isHeld) wakeLock.release()
            tts?.stop()
            tts?.shutdown()
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
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.let { t ->
                        t.language = Locale.CHINESE
                        // Adjust pitch and rate for more natural sound
                        t.setPitch(1.1f)     // Slightly higher pitch for "human" feel
                        t.setSpeechRate(0.95f) // Slightly slower for clarity
                        
                        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                if (continuation.isActive) continuation.resume(true)
                            }
                            override fun onError(utteranceId: String?) {
                                if (continuation.isActive) continuation.resume(false)
                            }
                        })
                        
                        val result = t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WeatherSpeak")
                        if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                } else {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
    }

    private fun createForegroundInfo(): ForegroundInfo {
        val stopIntent = Intent(applicationContext, WeatherStopReceiver::class.java)
        val stopPendingIntent = PendingIntent.getBroadcast(
            applicationContext,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("天气播报闹钟运行中")
            .setContentText("点击下方按钮停止播报")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止播报", stopPendingIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
