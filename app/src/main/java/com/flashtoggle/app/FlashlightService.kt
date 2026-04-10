package com.flashtoggle.app

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                Intent.ACTION_USER_PRESENT -> {
                    if (isFlashOn) { isFlashOn = false; turnFlash(false) }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = findFlashCameraId()
        createNotificationChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FlashToggle::WakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            isRunning = false
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        isRunning = true

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenReceiver, filter)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        turnFlash(false)
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val i = Intent(applicationContext, FlashlightService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            applicationContext.startForegroundService(i)
        else applicationContext.startService(i)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun handleScreenOff() {
        try { if (wakeLock?.isHeld == false) wakeLock?.acquire(3000L) } catch (_: Exception) {}

        val now = System.currentTimeMillis()
        val delta = now - lastScreenOffTime

        if (delta in 50 until DOUBLE_PRESS_WINDOW_MS) {
            isFlashOn = !isFlashOn
            turnFlash(isFlashOn)
            lastScreenOffTime = 0L
        } else {
            lastScreenOffTime = now
        }
    }

    private fun turnFlash(on: Boolean) {
        try {
            cameraId?.let { cameraManager.setTorchMode(it, on) }
        } catch (e: Exception) {
            Log.e(TAG, "Flash error: ${e.message}")
            isFlashOn = false
        }
    }

    private fun findFlashCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) { null }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "FlashToggle Service",
                NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false); enableLights(false); enableVibration(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, FlashlightService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FlashToggle Active")
            .setContentText("Double-press power button to toggle flash")
            .setSmallIcon(R.drawable.ic_flash)
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true).setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
