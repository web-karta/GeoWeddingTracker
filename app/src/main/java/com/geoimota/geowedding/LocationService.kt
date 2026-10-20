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
import android.os.Handler
import android.os.Looper
import java.time.LocalDateTime
import java.time.ZoneId
import android.os.SystemClock
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.time.Instant

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
    private val handler = Handler(Looper.getMainLooper())
    private var lastLocation: Location? = null
    private var running = false
    private val offlinePrefs by lazy { getSharedPreferences("pending_gps", MODE_PRIVATE) }
    private var lastSentFixMs = 0L
    @Volatile private var requestInFlight = false
    // Ažuriraj i pohrani posljednju stvarnu GPS lokaciju, čak i bez interneta.
    // Ne šalji staro očitanje s novim vremenskim žigom.
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (afterEnd()) { stopTracking(); return }
            if (withinEvent()) {
                lastLocation?.let { loc ->
                    val ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L
                    if (ageMs in 0..120_000L) savePending(loc)
                }
                flushPending()
            }
            handler.postDelayed(this, 5000L)
        }
    }

    private fun fixTimestamp(loc: Location): Long {
        // Timestamp stvarnog očitanja; ograniči neispravan sat uređaja.
        val now = System.currentTimeMillis()
        return if (loc.time in (now - 24L * 60 * 60 * 1000)..(now + 5000L)) loc.time else 0L
    }

    private fun savePending(loc: Location) {
        val time = fixTimestamp(loc)
        if (time <= 0L) return
        val old = offlinePrefs.getLong("time", 0L)
        if (time <= old || time <= lastSentFixMs) return
        offlinePrefs.edit()
            .putLong("time", time)
            .putString("lat", loc.latitude.toString())
            .putString("lng", loc.longitude.toString())
            .putString("accuracy", loc.accuracy.toString())
            .apply()
    }

    private fun isConnected(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun flushPending() {
        if (requestInFlight || !isConnected() || !withinEvent()) return
        val time = offlinePrefs.getLong("time", 0L)
        if (time <= lastSentFixMs) return
        val lat = offlinePrefs.getString("lat", null)?.toDoubleOrNull() ?: return
        val lng = offlinePrefs.getString("lng", null)?.toDoubleOrNull() ?: return
        val accuracy = offlinePrefs.getString("accuracy", null)?.toDoubleOrNull() ?: return
        if (!lat.isFinite() || !lng.isFinite() || lat !in -90.0..90.0 || lng !in -180.0..180.0) return
        sendPending(lat, lng, accuracy, time)
    }

    private fun eventStart(): Long = LocalDateTime.of(2029, 9, 29, 11, 0)
        .atZone(ZoneId.of("Europe/Sarajevo")).toInstant().toEpochMilli()
    private fun eventEnd(): Long = LocalDateTime.of(2029, 9, 30, 6, 0)
        .atZone(ZoneId.of("Europe/Sarajevo")).toInstant().toEpochMilli()
    private fun withinEvent(): Boolean = System.currentTimeMillis() in eventStart() until eventEnd()
    private fun afterEnd(): Boolean = System.currentTimeMillis() >= eventEnd()


    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { loc ->
                lastLocation = loc
                if (withinEvent()) { savePending(loc); flushPending() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (afterEnd()) { stopTracking(); return START_NOT_STICKY }
        when(intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val newRole = intent.getStringExtra(EXTRA_ROLE)
                    ?: getSharedPreferences("tracker", MODE_PRIVATE).getString("role", "groom")!!
                if (role != newRole) offlinePrefs.edit().clear().apply()
                role = newRole
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
            .setMinUpdateDistanceMeters(0f)
            .setMaxUpdateDelayMillis(5000L)
            .build()
        fused.removeLocationUpdates(callback)
        fused.requestLocationUpdates(request, callback, mainLooper)
        // Početna lokacija dok čekamo prvo aktivno očitanje.
        fused.lastLocation.addOnSuccessListener { loc -> if (loc != null && lastLocation == null) lastLocation = loc }
        running = true
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun sendPending(lat: Double, lng: Double, accuracy: Double, fixTime: Long) {
        if (requestInFlight) return
        requestInFlight = true
        val id = if (role == "groom") 1 else 2
        val json = JSONObject().apply {
            put("id", id)
            put("lat", lat)
            put("lng", lng)
            put("accuracy", accuracy)
            put("updated_at", Instant.ofEpochMilli(fixTime).toString())
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/wedding_live_location?on_conflict=id")
            .header("apikey", SUPABASE_KEY)
            .header("Authorization", "Bearer $SUPABASE_KEY")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .post(body)
            .build()
        http.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                requestInFlight = false // zadrži posljednji fix za idući pokušaj
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        lastSentFixMs = maxOf(lastSentFixMs, fixTime)
                        // Ne briši novije očitanje koje je stiglo tijekom zahtjeva.
                        if (offlinePrefs.getLong("time", 0L) <= fixTime) {
                            offlinePrefs.edit().clear().apply()
                        }
                    } else {
                        android.util.Log.w("GeoWeddingTracker", "Supabase HTTP ${it.code}")
                    }
                }
                requestInFlight = false
            }
        })
    }

    private fun stopTracking() {
        running = false
        handler.removeCallbacks(tick)
        fused.removeLocationUpdates(callback)
        getSharedPreferences("tracker", MODE_PRIVATE).edit()
            .putBoolean("tracking", false).apply()
        // Zadnje ocitanje ostaje na uredaju; nakon završetka ne šalji ga izvan rasporeda.
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
        running = false
        handler.removeCallbacks(tick)
        fused.removeLocationUpdates(callback)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
