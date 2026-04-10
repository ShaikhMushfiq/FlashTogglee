package com.flashtoggle.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {

    private val NOTIFICATION_PERMISSION_CODE = 101
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var sensitivitySeek: SeekBar
    private lateinit var sensitivityLabel: TextView
    private lateinit var batteryStatusText: TextView
    private val prefs by lazy { getSharedPreferences("flashtoggle", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        sensitivitySeek = findViewById(R.id.sensitivitySeek)
        sensitivityLabel = findViewById(R.id.sensitivityLabel)
        batteryStatusText = findViewById(R.id.batteryStatusText)

        val savedWindow = prefs.getInt("double_press_window", 500)
        sensitivitySeek.progress = savedWindow - 200
        updateSensitivityLabel(savedWindow)

        sensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = progress + 200
                updateSensitivityLabel(ms)
                prefs.edit().putInt("double_press_window", ms).apply()
                FlashlightService.DOUBLE_PRESS_WINDOW_MS = ms.toLong()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        // Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE)
            }
        }

        toggleButton.setOnClickListener {
            if (FlashlightService.isRunning) {
                val stopIntent = Intent(this, FlashlightService::class.java).apply { action = "STOP" }
                startService(stopIntent)
            } else {
                ContextCompat.startForegroundService(this, Intent(this, FlashlightService::class.java))
            }
            toggleButton.postDelayed({ updateUI() }, 400)
        }

        // Battery optimization button
        findViewById<Button>(R.id.batteryOptButton).setOnClickListener {
            requestBatteryOptimizationExemption()
        }

        // OEM settings button
        findViewById<Button>(R.id.oemSettingsButton).setOnClickListener {
            showOemGuide()
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: open battery settings
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        } else {
            Toast.makeText(this, "Battery optimization already disabled ✓", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showOemGuide() {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val steps = when {
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") ->
                "iQOO / Vivo (Funtouch OS):\n\n" +
                "1. Settings → Battery → Background app management\n   → FlashToggle → No restrictions\n\n" +
                "2. Settings → Apps → FlashToggle\n   → Autostart → Enable\n\n" +
                "3. Recents screen → Long press FlashToggle\n   → Lock the app"

            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") ->
                "Xiaomi / Redmi / POCO (MIUI/HyperOS):\n\n" +
                "1. Settings → Apps → Manage apps → FlashToggle\n   → Battery saver → No restrictions\n\n" +
                "2. Same screen → Autostart → Enable\n\n" +
                "3. Recents → swipe up on FlashToggle → tap the lock 🔒"

            manufacturer.contains("huawei") || manufacturer.contains("honor") ->
                "Huawei / Honor (EMUI/MagicUI):\n\n" +
                "1. Settings → Battery → App launch\n   → FlashToggle → Manage manually\n   → Enable all three toggles\n\n" +
                "2. Recents → Lock FlashToggle card"

            manufacturer.contains("oppo") || manufacturer.contains("oneplus") || manufacturer.contains("realme") ->
                "OPPO / OnePlus / Realme (ColorOS/OxygenOS):\n\n" +
                "1. Settings → Battery → Battery optimization\n   → FlashToggle → Don't optimize\n\n" +
                "2. Settings → Apps → FlashToggle → Battery\n   → Allow background activity"

            manufacturer.contains("samsung") ->
                "Samsung (One UI):\n\n" +
                "1. Settings → Apps → FlashToggle → Battery\n   → Unrestricted\n\n" +
                "2. Settings → Device care → Battery\n   → Background usage limits\n   → Never sleeping apps → Add FlashToggle"

            else ->
                "Generic Android:\n\n" +
                "1. Settings → Apps → FlashToggle → Battery\n   → Unrestricted / Don't optimize\n\n" +
                "2. Settings → Battery → Battery optimization\n   → FlashToggle → Don't optimize"
        }

        AlertDialog.Builder(this)
            .setTitle("Allow background running")
            .setMessage(steps)
            .setPositiveButton("Open Battery Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun updateSensitivityLabel(ms: Int) {
        sensitivityLabel.text = "Double-press window: ${ms}ms"
    }

    private fun isBatteryOptimized(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return !pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun updateUI() {
        if (FlashlightService.isRunning) {
            statusText.text = "● Active — double-press power button to toggle flash"
            statusText.setTextColor(getColor(R.color.active_green))
            toggleButton.text = "Stop Service"
            toggleButton.setBackgroundColor(getColor(R.color.stop_red))
        } else {
            statusText.text = "○ Inactive — tap Start to enable"
            statusText.setTextColor(getColor(R.color.inactive_gray))
            toggleButton.text = "Start Service"
            toggleButton.setBackgroundColor(getColor(R.color.start_green))
        }

        if (isBatteryOptimized()) {
            batteryStatusText.text = "⚠ Battery optimization ON — app may be killed"
            batteryStatusText.setTextColor(getColor(R.color.warn_yellow))
        } else {
            batteryStatusText.text = "✓ Battery optimization disabled"
            batteryStatusText.setTextColor(getColor(R.color.active_green))
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
