# UGREEN Studio Pro - ANC Icon Integration Guide

## 🎯 Quick ANC Toggle Button (Next Step)

The `toggleAncMode()` method is ready to be called. Here's how to integrate it into the UI.

---

## Option 1: Device Activity Action Button (Recommended)

In the device activity (typically `DeviceActivity.java` or similar), add an ANC toggle button next to the battery display:

```kotlin
// In your device activity layout binding
binding.ancToggleButton.setOnClickListener {
    val support = mDeviceSupport as? UgreenStudioProDeviceSupport
    support?.toggleAncMode()
    // Optional: Show toast with current mode
    updateAncModeDisplay()
}

private fun updateAncModeDisplay() {
    val prefs = mGBDevice.devicePrefs
    val currentMode = prefs.getString(PREF_UGREEN_ANC_MODE, "off") ?: "off"
    binding.ancModeText.text = "ANC: ${currentMode.uppercase()}"
}
```

### XML Layout Addition:
```xml
<!-- Add to device activity layout, next to battery icon -->
<Button
    android:id="@+id/anc_toggle_button"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:background="@drawable/ic_noise_control_on"
    android:contentDescription="@string/toggle_anc_mode"
    android:elevation="4dp"
    android:layout_marginStart="8dp"
    style="@style/Widget.AppCompat.Button.Borderless" />

<TextView
    android:id="@+id/anc_mode_text"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textSize="12sp"
    android:layout_marginStart="4dp"
    android:text="ANC: OFF" />
```

---

## Option 2: Device Card Quick Controls (Material Design)

If using Material Design device cards:

```kotlin
// In DeviceCardActivity or similar
private fun setupAncButton() {
    val ancButton = ChipGroup() // Or use MaterialButton
    ancButton.text = "🔊 ANC"
    ancButton.setOnClickListener {
        val support = getCurrentDeviceSupport() as? UgreenStudioProDeviceSupport
        support?.toggleAncMode()
        
        // Update display after toggle
        Handler(Looper.getMainLooper()).postDelayed({
            updateAncDisplay()
        }, 500)
    }
    
    binding.quickControlsContainer.addView(ancButton)
}

private fun updateAncDisplay() {
    val prefs = mDevice.devicePrefs
    val mode = prefs.getString(PREF_UGREEN_ANC_MODE, "off") ?: "off"
    
    // Update button color/text based on mode
    val colorRes = when(mode) {
        "off" -> R.color.gray
        "deep" -> R.color.red
        "moderate" -> R.color.orange
        "mild" -> R.color.yellow
        "transparent" -> R.color.green
        else -> R.color.gray
    }
    
    binding.ancButton.setChipBackgroundColorResource(colorRes)
    binding.ancButton.text = "🔊 ANC: ${mode.uppercase()}"
}
```

---

## Option 3: Notification Quick Action (Advanced)

For Android 11+, add a persistent notification with quick toggle:

```kotlin
private fun createAncQuickAction(): NotificationCompat.Action {
    val intent = Intent(context, AncToggleService::class.java).apply {
        putExtra("device_mac", mDevice.address)
        action = "TOGGLE_ANC"
    }
    
    val pendingIntent = PendingIntent.getService(
        context, 
        ANC_TOGGLE_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    
    return NotificationCompat.Action.Builder(
        R.drawable.ic_noise_control_on,
        "ANC",
        pendingIntent
    ).build()
}

// In your notification builder:
notificationBuilder.addAction(createAncQuickAction())
```

---

## 🎨 Icon Recommendations

### Use Existing Gadgetbridge Icons:
```
ic_noise_control_on.xml        ← Primary ANC icon (already used)
ic_noise_control_off.xml       ← Alternative when ANC is off
```

### Or create a custom icon:
```xml
<!-- res/drawable/ic_anc_toggle.xml -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8zM12,7c-2.76,0 -5,2.24 -5,5s2.24,5 5,5 5,-2.24 5,-5 -2.24,-5 -5,-5z" />
</vector>
```

### Color Coding by Mode:
```kotlin
fun getAncModeColor(mode: String): Int {
    return when(mode) {
        "off" -> Color.GRAY           // #808080
        "deep" -> Color.RED           // #FF0000 (maximum noise reduction)
        "moderate" -> 0xFFFF8C00      // Dark Orange (medium noise reduction)
        "mild" -> Color.YELLOW        // #FFFF00 (light noise reduction)
        "transparent" -> Color.GREEN  // #00FF00 (ambient sound passthrough)
        "auto" -> Color.CYAN          // #00FFFF (automatic mode)
        else -> Color.GRAY
    }
}
```

---

## 📍 Display Mode Information

You can show the current ANC mode with an info card:

```kotlin
private fun createAncModeCard(): CardView {
    val modes = listOf(
        "OFF" to "No noise cancellation",
        "MILD" to "Light noise reduction",
        "MODERATE" to "Medium noise reduction",
        "DEEP" to "Maximum noise reduction",
        "TRANSPARENT" to "Ambient sound passthrough",
        "AUTO" to "Automatic mode switching"
    )
    
    val prefs = mDevice.devicePrefs
    val currentMode = prefs.getString(PREF_UGREEN_ANC_MODE, "off")?.uppercase() ?: "OFF"
    
    binding.ancInfoText.text = modes.find { it.first == currentMode }?.second
        ?: "Current mode: $currentMode"
        
    return CardView(context).apply {
        radius = 8f
        cardElevation = 4f
        addView(binding.ancInfoText)
    }
}
```

---

## 🔄 Listening for ANC Changes

To update the UI when ANC is changed from the headphone buttons:

```kotlin
// Register a BroadcastReceiver for device events
private val ancModeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_DEVICE_PREFERENCES_CHANGED) {
            val key = intent.getStringExtra("key")
            if (key == PREF_UGREEN_ANC_MODE) {
                updateAncDisplay() // Refresh UI
            }
        }
    }
}

override fun onResume() {
    super.onResume()
    val filter = IntentFilter(ACTION_DEVICE_PREFERENCES_CHANGED)
    registerReceiver(ancModeReceiver, filter)
}

override fun onPause() {
    super.onPause()
    unregisterReceiver(ancModeReceiver)
}
```

---

## ⚙️ Settings Menu Integration

Already done! Just make sure the ANC dropdown in preferences calls:
```kotlin
// In preferences activity when user selects ANC mode
onSendConfiguration(PREF_UGREEN_ANC_MODE)
```

This will trigger:
1. `UgreenStudioProProtocol.encodeSendConfiguration()`
2. → `UgreenStudioProDeviceSupport.sendCommand()`
3. → Bluetooth write to device
4. → Device responds with confirmation
5. → Preference updated in UI

---

## 🧪 Testing the Toggle

```kotlin
// Quick test in your activity
fun testAncToggle() {
    val support = mDeviceSupport as? UgreenStudioProDeviceSupport
    for (i in 0..6) {
        Handler(Looper.getMainLooper()).postDelayed({
            support?.toggleAncMode()
            Log.d("ANC", "Toggle #${i+1}")
        }, i * 1000L)
    }
}
```

---

## 🔐 Permission Notes

No special permissions needed. The `toggleAncMode()` method:
- Checks Bluetooth connection state
- Reads existing preferences (already accessible)
- Writes to device (uses existing RFCOMM socket)
- Logs changes (optional, for debugging)

---

## 📦 Strings Resource

Add to `res/values/strings.xml`:

```xml
<string name="toggle_anc_mode">Toggle ANC Mode</string>
<string name="anc_off">ANC Off</string>
<string name="anc_deep">Deep - Maximum Noise Reduction</string>
<string name="anc_moderate">Moderate - Standard Noise Reduction</string>
<string name="anc_mild">Mild - Light Noise Reduction</string>
<string name="anc_transparent">Transparent - Ambient Sound</string>
<string name="anc_auto">Auto - Automatic Mode</string>
```

---

## 🚀 Implementation Priority

1. **Phase 1 (Easy)**: Add settings dropdown with ANC cycle ✅ Already done
2. **Phase 2 (Medium)**: Add quick-toggle button in device activity (Next)
3. **Phase 3 (Advanced)**: Add notification quick-action tile
4. **Phase 4 (Nice-to-have)**: Add color coding, animations, preference shortcuts

---

**Ready to integrate?** Start with Option 1 (Device Activity Button) - it's the cleanest approach! 🎉
