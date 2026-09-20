package com.geoimota.geowedding

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LocationService : Service() {
    companion object {
        const val ACTION_START = "geo.START"
        const val ACTION_STOP = "geo.STOP"
        const val EXTRA_ROLE = "role"
        private const val CHANNEL = "wedding_tracking"
        private const val NOTIFICATION_ID = 2909

        private const val SUPABASE_URL = "https://tjorkcpxxttgalxrmdud.supabase.co"
        private const val SUPABASE_KEY = "sb_publishable_VqHZtcFyhQbpEY5k55eOpQ_Pww0aOL_"
    }

    private lateinit var fused: FusedLocationProviderClient
    private val http = OkHttpClient()
    private var role = "groom"

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { sendLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when(intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                role = intent.getStringExtra(EXTRA_ROLE)
                    ?: getSharedPreferences("tracker", MODE_PRIVATE).getString("role", "groom")!!
                getSharedPreferences("tracker", MODE_PRIVATE).edit()
                    .putBoolean("tracking", true).putString("role", role).apply()
                startForeground(NOTIFICATION_ID, notification())
                startLocationUpdates()
            }
            else -> {
                val p = getSharedPreferences("tracker", MODE_PRIVATE)
                if (p.getBoolean("tracking", false)) {
                    role = p.getString("role", "groom") ?: "groom"
                    startForeground(NOTIFICATION_ID, notification())
                    startLocationUpdates()
                } else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, LocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val who = if(role == "groom") "Mladoženja" else "Mlada"
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Svatovi uživo — $who")
            .setContentText("Dijeljenje lokacije je aktivno")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, "Zaustavi", stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            stopSelf()
            return
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateIntervalMillis(3000L)
            .setMinUpdateDistanceMeters(5f)
            .build()
        fused.removeLocationUpdates(callback)
        fused.requestLocationUpdates(request, callback, mainLooper)
    }

    private fun sendLocation(loc: Location) {
        val id = if(role == "groom") 1 else 2
        val json = JSONObject().apply {
            put("id", id)
            put("lat", loc.latitude)
            put("lng", loc.longitude)
            put("accuracy", loc.accuracy.toDouble())
            if (loc.hasSpeed()) put("speed", loc.speed.toDouble())
            if (loc.hasBearing()) put("heading", loc.bearing.toDouble())
            put("updated_at", java.time.Instant.now().toString())
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/wedding_live_location?on_conflict=id")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()
        http.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    private fun stopTracking() {
        fused.removeLocationUpdates(callback)
        getSharedPreferences("tracker", MODE_PRIVATE).edit()
            .putBoolean("tracking", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL, "Dijeljenje lokacije", NotificationManager.IMPORTANCE_LOW
        ))
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
