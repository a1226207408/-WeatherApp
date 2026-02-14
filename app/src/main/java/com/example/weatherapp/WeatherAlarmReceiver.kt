package com.example.weatherapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*

class WeatherAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cityName = intent.getStringExtra("cityName") ?: "北京"
        val lat = intent.getDoubleExtra("lat", 39.9042)
        val lon = intent.getDoubleExtra("lon", 116.4074)

        val cityData = workDataOf(
            "cityName" to cityName,
            "lat" to lat,
            "lon" to lon
        )

        try {
            // Trigger the worker immediately when alarm goes off
            val workRequest = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setInputData(cityData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
        } catch (e: Exception) {
            Log.e("WeatherAlarmReceiver", "Failed to enqueue work", e)
        }

        // Reschedule for tomorrow
        val schedules = AlarmStorage.getSchedules(context)
        schedules.find { it.id.hashCode() == intent.getIntExtra("alarmId", 0) || (it.hour == intent.getIntExtra("hour", -1) && it.minute == intent.getIntExtra("minute", -1)) }?.let {
            setAlarm(context, it)
        }
    }
}
