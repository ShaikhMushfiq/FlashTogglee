package com.flashtoggle.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class FlashlightService : Service() {

    companion object {
        var isRunning = false
        var DOUBLE_PRESS_WINDOW_MS: Long = 500L
        const val CHANNEL_ID = "flashtoggle_channel"
        const val NOTIF_ID = 1
        private const val TAG = "FlashlightService"
    }

    private var lastScreenOffTime = 0L
    private var isFlashOn = false
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_SCREEN_ON  -> { /* optional: turn off flash on screen on */ }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        isRunning = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        Log.d(TAG, "Service started. Listening for double power press.")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) { }
        // Turn off flash when service is killed
        turnFlash(false)
        Log.d(TAG, "Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleScreenOff() {
        val now = System.currentTimeMillis()
        val delta = now - lastScreenOffTime
        Log.d(TAG, "Screen OFF — delta: ${delta}ms (window: ${DOUBLE_PRESS_WINDOW_MS}ms)")

        if (delta in 1 until DOUBLE_PRESS_WINDOW_MS) {
            // Double press detected!
            isFlashOn = !isFlashOn
            turnFlash(isFlashOn)
            Log.d(TAG, "Double press! Flash -> $isFlashOn")
            lastScreenOffTime = 0L  // reset so triple press doesn't re-trigger
        } else {
            lastScreenOffTime = now
        }
    }

    private fun turnFlash(on: Boolean) {
        try {
            cameraId?.let { cameraManager.setTorchMode(it, on) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flash: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FlashToggle Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps FlashToggle running in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FlashToggle Active")
            .setContentText("Double-press power button to toggle flashlight")
            .setSmallIcon(R.drawable.ic_flash)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
