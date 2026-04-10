package com.flashtoggle.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val NOTIFICATION_PERMISSION_CODE = 101
    private lateinit var statusText: TextView
    private lateinit var toggleButton: Button
    private lateinit var doublePressSensitivitySeek: SeekBar
    private lateinit var sensitivityLabel: TextView
    private val prefs by lazy { getSharedPreferences("flashtoggle", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        toggleButton = findViewById(R.id.toggleButton)
        doublePressSensitivitySeek = findViewById(R.id.sensitivitySeek)
        sensitivityLabel = findViewById(R.id.sensitivityLabel)

        val savedWindow = prefs.getInt("double_press_window", 500)
        doublePressSensitivitySeek.progress = savedWindow - 200  // range 200–800ms
        updateSensitivityLabel(savedWindow)

        doublePressSensitivitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val ms = progress + 200
                updateSensitivityLabel(ms)
                prefs.edit().putInt("double_press_window", ms).apply()
                FlashlightService.DOUBLE_PRESS_WINDOW_MS = ms.toLong()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE)
            }
        }

        updateUI()

        toggleButton.setOnClickListener {
            if (FlashlightService.isRunning) {
                stopService(Intent(this, FlashlightService::class.java))
            } else {
                val serviceIntent = Intent(this, FlashlightService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
            }
            // Small delay so the service state updates
            toggleButton.postDelayed({ updateUI() }, 300)
        }
    }

    private fun updateSensitivityLabel(ms: Int) {
        sensitivityLabel.text = "Double-press window: ${ms}ms"
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
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }
}
