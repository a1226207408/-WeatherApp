package com.example.weatherapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*

class WeatherAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cityName = intent.getStringExtra("cityName") ?: "北京"
        val lat = intent.getDoubleExtra("lat", 39.9042)
        val lon = intent.getDoubleExtra("lon", 116.4074)

        Log.d("WeatherAlarmReceiver", "Alarm received for city: $cityName")

        // 1. Show an immediate notification to let the user know the alarm fired
        // This also helps keeping the process alive for a moment on some devices
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val initialNotification = NotificationCompat.Builder(context, "WeatherBroadcastChannel")
            .setContentTitle("天气闹钟已触发")
            .setContentText("正在准备为您播报 ${cityName} 的天气...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        
        try {
            notificationManager.notify(102, initialNotification)
        } catch (e: Exception) {
            Log.e("WeatherAlarmReceiver", "Failed to show initial notification", e)
        }

        val cityData = workDataOf(
            "cityName" to cityName,
            "lat" to lat,
            "lon" to lon
        )

        try {
            // 2. Trigger the worker with Expedited policy
            val workRequest = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setInputData(cityData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("WeatherTask_${System.currentTimeMillis()}")
                .build()

            WorkManager.getInstance(context).enqueue(workRequest)
            Log.d("WeatherAlarmReceiver", "Work enqueued successfully")
        } catch (e: Exception) {
            Log.e("WeatherAlarmReceiver", "Failed to enqueue work", e)
        }

        // 3. Reschedule for tomorrow
        val schedules = AlarmStorage.getSchedules(context)
        val alarmId = intent.getIntExtra("alarmId", 0)
        val hour = intent.getIntExtra("hour", -1)
        val minute = intent.getIntExtra("minute", -1)
        
        schedules.find { it.id.hashCode() == alarmId || (it.hour == hour && it.minute == minute) }?.let {
            setAlarm(context, it)
            Log.d("WeatherAlarmReceiver", "Alarm rescheduled for tomorrow")
        }
    }
}
