package com.example.weatherapp

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("v1/forecast")
    suspend fun getWeather(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current_weather") current: Boolean = true,
        @Query("timezone") timezone: String = "auto"
    ): WeatherResponse
}

data class WeatherResponse(
    val current_weather: CurrentWeather
)

data class CurrentWeather(
    val temperature: Double,
    val windspeed: Double,
    val weathercode: Int
)

// Simple mapping for weather codes
fun getWeatherDescription(code: Int): String {
    return when (code) {
        0 -> "晴天"
        1, 2, 3 -> "多云"
        45, 48 -> "有雾"
        51, 53, 55 -> "毛毛雨"
        61, 63, 65 -> "小雨"
        71, 73, 75 -> "小雪"
        95 -> "雷阵雨"
        else -> "天气状况良好"
    }
}
