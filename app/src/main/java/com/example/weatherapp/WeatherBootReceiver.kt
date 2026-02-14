package com.example.weatherapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WeatherBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val schedules = AlarmStorage.getSchedules(context)
            for (schedule in schedules) {
                setAlarm(context, schedule)
            }
        }
    }
}
