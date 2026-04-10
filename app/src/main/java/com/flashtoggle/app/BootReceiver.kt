package com.flashtoggle.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("flashtoggle", Context.MODE_PRIVATE)
            // Auto-start only if it was running before reboot
            if (prefs.getBoolean("auto_start", true)) {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, FlashlightService::class.java)
                )
            }
        }
    }
}
