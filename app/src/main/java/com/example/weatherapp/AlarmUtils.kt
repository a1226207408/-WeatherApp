package com.example.weatherapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.*

object AlarmStorage {
    private const val PREF_NAME = "WeatherAlarms"
    private const val KEY_SCHEDULES = "schedules"

    fun saveSchedules(context: Context, schedules: List<AlarmSchedule>) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(schedules)
        prefs.edit().putString(KEY_SCHEDULES, json).apply()
    }

    fun getSchedules(context: Context): List<AlarmSchedule> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_SCHEDULES, null) ?: return emptyList()
        val type = object : TypeToken<List<AlarmSchedule>>() {}.type
        return Gson().fromJson(json, type)
    }
}

fun setAlarm(context: Context, schedule: AlarmSchedule) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    // Check if we can schedule exact alarms (Android 12+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                // Background start might fail on some OEMs
            }
            // Even if we can't schedule "exact", we should still try to set an alarm
            // or we just return. For an alarm app, exact is better.
        }
    }

    val intent = Intent(context, WeatherAlarmReceiver::class.java).apply {
        putExtra("cityName", schedule.city.name)
        putExtra("lat", schedule.city.lat)
        putExtra("lon", schedule.city.lon)
        putExtra("hour", schedule.hour)
        putExtra("minute", schedule.minute)
        putExtra("alarmId", schedule.id.hashCode())
    }

    val pendingIntent = PendingIntent.getBroadcast(
        context,
        schedule.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, schedule.hour)
        set(Calendar.MINUTE, schedule.minute)
        set(Calendar.SECOND, 0)
        if (before(Calendar.getInstance())) {
            add(Calendar.DATE, 1)
        }
    }

    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        calendar.timeInMillis,
        pendingIntent
    )
}

fun cancelAlarm(context: Context, schedule: AlarmSchedule) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, WeatherAlarmReceiver::class.java)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        schedule.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmManager.cancel(pendingIntent)
}
