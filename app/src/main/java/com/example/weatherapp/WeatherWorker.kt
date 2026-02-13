package com.example.weatherapp

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private var tts: TextToSpeech? = null

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // In a real app, you'd get actual GPS coordinates here
            // For simplicity, using a default (e.g., Beijing) or checking if provided
            val lat = 39.9042
            val lon = 116.4074

            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(WeatherService::class.java)
            val response = service.getWeather(lat, lon)
            
            val weather = response.current_weather
            val description = getWeatherDescription(weather.weathercode)
            val text = "早安！现在为您播报天气。当前位置，天气$description，温度${weather.temperature}度，风速每小时${weather.windspeed}公里。祝您今天心情愉快！"

            speak(text)

            Result.success()
        } catch (e: Exception) {
            Log.e("WeatherWorker", "Error fetching weather", e)
            Result.retry()
        }
    }

    private fun speak(text: String) {
        tts = TextToSpeech(applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "WeatherSpeak")
            }
        }
    }
}
