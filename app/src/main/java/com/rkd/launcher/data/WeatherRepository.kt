package com.rkd.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherNow(val temperature: Int, val description: String)

/** Foreground-only, approximate location and a cached current forecast from Open-Meteo. */
object WeatherRepository {
    @Volatile private var cached: Pair<Long, WeatherNow>? = null

    fun hasAccess(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(context: Context): WeatherNow? {
        if (!hasAccess(context)) return null
        val now = System.currentTimeMillis()
        cached?.takeIf { now - it.first < 30 * 60_000L }?.let { return it.second }
        val location = location(context) ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,weather_code")
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
                    WeatherNow(current.getDouble("temperature_2m").roundToInt(), describe(current.getInt("weather_code")))
                        .also { cached = System.currentTimeMillis() to it }
                } finally { connection.disconnect() }
            }.getOrNull()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun location(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        val last = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        if (last != null && System.currentTimeMillis() - last.time < 60 * 60_000L) return last
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return last
        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                try {
                    manager.getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { fix ->
                        if (continuation.isActive) continuation.resume(fix ?: last)
                    }
                } catch (_: Exception) {
                    if (continuation.isActive) continuation.resume(last)
                }
            }
        } ?: last
    }

    internal fun describe(code: Int): String = when (code) {
        0 -> "Clear"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        in 51..57 -> "Drizzle"
        in 61..67, in 80..82 -> "Rain"
        in 71..77, 85, 86 -> "Snow"
        in 95..99 -> "Thunderstorm"
        else -> "Weather"
    }
}
