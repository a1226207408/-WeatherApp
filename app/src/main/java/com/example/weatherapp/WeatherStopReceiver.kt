package com.example.weatherapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager

class WeatherStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Cancel all weather broadcast tasks to stop the repeating TTS
        WorkManager.getInstance(context).cancelAllWorkByTag("WeatherAlarmTask")
    }
}
