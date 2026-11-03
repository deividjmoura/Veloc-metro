package com.gpsspeedometer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Owns GPS collection while the app is not visible.
 * It is started explicitly by the user while the activity is in the foreground.
 */
class GpsTrackingService : Service() {
    private lateinit var locationManager: LocationManager
    private val prefs by lazy { getSharedPreferences("trip_data", Context.MODE_PRIVATE) }
    private var previousDistanceLocation: Location? = null
    private var lastNotificationAt = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (location.accuracy > 50f) {
                prefs.edit().putBoolean("has_fix", false).putFloat("speed_kmh", 0f).apply()
                updateNotification(0f)
                return
            }

            val editor = prefs.edit()
                .putBoolean("has_fix", true)
                .putFloat("current_lat", location.latitude.toFloat())
                .putFloat("current_lng", location.longitude.toFloat())

            val speedKmh = if (location.hasSpeed() && location.accuracy <= 50f) {
                (location.speed * 3.6f).coerceAtLeast(0f)
            } else {
                prefs.getFloat("speed_kmh", 0f)
            }
            val maxSpeed = maxOf(prefs.getFloat("max_speed_kmh", 0f), speedKmh)
            editor.putFloat("speed_kmh", speedKmh)
                .putFloat("max_speed_kmh", maxSpeed)

            if (location.accuracy <= 25f) {
                var startedAt = prefs.getLong("trip_started_at", 0L)
                if (startedAt == 0L) {
                    startedAt = System.currentTimeMillis()
                    editor.putLong("trip_started_at", startedAt)
                }
                previousDistanceLocation?.let { previous ->
                    val deltaKm = previous.distanceTo(location).toDouble() / 1000.0
                    // Reject GPS jumps and stationary noise instead of corrupting the odometer.
                    if (deltaKm in 0.001..0.5) {
                        val tripKm = prefs.getFloat("trip_km", 0f) + deltaKm.toFloat()
                        val totalKm = prefs.getFloat("total_km", 0f) + deltaKm.toFloat()
                        editor.putFloat("trip_km", tripKm)
                            .putFloat("total_km", totalKm)
                    }
                }
                previousDistanceLocation = Location(location)
            }

            editor.apply()
            val now = System.currentTimeMillis()
            if (now - lastNotificationAt >= 5000L) {
                updateNotification(speedKmh)
                lastNotificationAt = now
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }

        if (!hasLocationPermission()) {
            stopTracking()
            return START_NOT_STICKY
        }

        startAsForeground()
        prefs.edit().putBoolean("tracking_active", true).apply()
        try {
            locationManager.removeUpdates(listener)
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 1000L, 1f, listener, Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 2000L, 2f, listener, Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            stopTracking()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun startAsForeground() {
        val notification = buildNotification(prefs.getFloat("speed_kmh", 0f))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(speedKmh: Float) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(speedKmh))
    }

    private fun buildNotification(speedKmh: Float): Notification =
        Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("GPS Speedometer • rastreamento ativo")
            .setContentText(String.format(Locale.getDefault(), "Velocidade atual: %.0f km/h", speedKmh))
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Rastreamento GPS", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantém a medição GPS ativa durante a viagem."
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun stopTracking() {
        runCatching { locationManager.removeUpdates(listener) }
        prefs.edit()
            .putBoolean("tracking_active", false)
            .putBoolean("has_fix", false)
            .putFloat("speed_kmh", 0f)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { locationManager.removeUpdates(listener) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.gpsspeedometer.action.START_TRACKING"
        const val ACTION_STOP = "com.gpsspeedometer.action.STOP_TRACKING"
        private const val CHANNEL_ID = "gps_tracking"
        private const val NOTIFICATION_ID = 4102
    }
}
