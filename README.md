# FlashToggle 🔦

Double-press the power button to toggle your flashlight — just like Xiaomi/Redmi/POCO phones.

## How it works
- A Foreground Service listens to `ACTION_SCREEN_OFF` broadcasts (fired by the power button)
- If two screen-off events happen within the configured window (default 500ms) → flashlight toggles
- Adjustable sensitivity slider (200ms – 800ms)
- Auto-starts on reboot

## Build Instructions

### Requirements
- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Kotlin 1.9+
- A physical Android phone (emulators have no camera flash)

### Steps
1. Open Android Studio → **File → Open** → select this folder (`FlashToggle/`)
2. Wait for Gradle sync to finish
3. Plug in your phone with USB Debugging enabled
4. Click **Run ▶** or press `Shift+F10`

### After installing
1. Open the app
2. Tap **"Start Service"**
3. Grant notification permission if asked
4. **IMPORTANT**: Go to `Settings → Battery → Battery optimization → All apps → FlashToggle → Don't optimize`
   - This prevents Android from killing the service when the screen is off
5. Lock your screen and quickly press the power button twice — flash toggles!

## Troubleshooting

| Problem | Fix |
|---|---|
| Flash doesn't respond | Disable battery optimization for the app |
| Service keeps stopping | Enable "Autostart" for the app in battery settings (MIUI/OneUI) |
| Double press too sensitive | Increase the window slider in app |
| App crashes | Make sure you're on Android 8.0+ (API 26) |

## On MIUI / HyperOS (Xiaomi phones)
Xiaomi ironically restricts background apps most aggressively:
- Settings → Apps → Manage Apps → FlashToggle → Battery Saver → **No restrictions**
- Settings → Apps → Manage Apps → FlashToggle → **Autostart → Enable**
- Lock screen → recent apps → lock the app (swipe up and tap the lock icon)

## Files
```
app/src/main/java/com/flashtoggle/app/
├── MainActivity.kt       — UI + start/stop button + sensitivity slider
├── FlashlightService.kt  — Foreground service, screen-off listener, flash control
└── BootReceiver.kt       — Auto-start on device reboot
```
