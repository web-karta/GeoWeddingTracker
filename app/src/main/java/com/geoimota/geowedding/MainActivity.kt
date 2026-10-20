package com.geoimota.geowedding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var pinInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private lateinit var statusText: TextView
    private var pendingRole: String? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val ok = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                 grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) requestBackgroundIfNeeded() else statusText.text = "Lokacija nije dopuštena."
    }

    private val backgroundPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { startPendingService() }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pinInput = findViewById(R.id.pinInput)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusText = findViewById(R.id.statusText)

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        startButton.setOnClickListener {
            val pin = pinInput.text.toString().trim()
            pendingRole = when(pin) {
                "2909" -> "groom"
                "2029" -> "bride"
                else -> null
            }
            if (pendingRole == null) {
                statusText.text = "Pogrešna šifra."
                return@setOnClickListener
            }
            ensureLocationPermissions()
        }

        stopButton.setOnClickListener {
            startService(Intent(this, LocationService::class.java).apply {
                action = LocationService.ACTION_STOP
            })
            getSharedPreferences("tracker", MODE_PRIVATE).edit()
                .putBoolean("tracking", false).apply()
            setUi(false, null)
        }

        val prefs = getSharedPreferences("tracker", MODE_PRIVATE)
        if (prefs.getBoolean("tracking", false)) {
            val role = prefs.getString("role", null)
            setUi(true, role)
        }
    }

    private fun ensureLocationPermissions() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            requestBackgroundIfNeeded()
        } else {
            locationPermission.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    private fun requestBackgroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= 30 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Android 11+ background access is normally granted from the app's Settings page.
            statusText.text = "Za potpuno pozadinsko praćenje odaberi Lokacija → Dopusti cijelo vrijeme, zatim se vrati u aplikaciju."
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"))
            startActivity(intent)
            // Foreground location service can still continue after Home/screen-off once started visibly.
            startPendingService()
        } else if (Build.VERSION.SDK_INT == 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            backgroundPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            startPendingService()
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingRole != null) {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                startPendingService()
            }
        }
    }

    private fun startPendingService() {
        val role = pendingRole ?: return
        pendingRole = null
        val i = Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
            putExtra(LocationService.EXTRA_ROLE, role)
        }
        ContextCompat.startForegroundService(this, i)
        getSharedPreferences("tracker", MODE_PRIVATE).edit()
            .putBoolean("tracking", true).putString("role", role).apply()
        setUi(true, role)
    }

    private fun setUi(on: Boolean, role: String?) {
        startButton.visibility = if(on) View.GONE else View.VISIBLE
        stopButton.visibility = if(on) View.VISIBLE else View.GONE
        pinInput.isEnabled = !on
        statusText.text = if(on)
            "Praćenje je aktivno: " + (if(role == "groom") "Mladoženja" else "Mlada") +
            ". Možeš zaključati zaslon ili prijeći u drugu aplikaciju."
        else "Praćenje nije aktivno."
    }
}
