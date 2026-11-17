package com.example.androidtools

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class SystemTelemetrySnapshot(
    val batteryPercent: Int = 0,
    val isCharging: Boolean = false,
    val batteryTempC: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val networkTransport: String = "Offline",
    val networkValidated: Boolean = false,
    val downstreamKbps: Int = 0,
    val torchEnabled: Boolean = false,
    val mediaVolumePercent: Int = 50,
    val brightnessPercent: Int = 60
)

class AndroidToolExecutor(private val context: Context) {

    private val appContext = context.applicationContext
    private val workspaceDir = File(appContext.filesDir, "ultron_workspace").apply { mkdirs() }

    private val _telemetry = MutableStateFlow(SystemTelemetrySnapshot())
    val telemetry: StateFlow<SystemTelemetrySnapshot> = _telemetry.asStateFlow()

    private val _windowBrightnessRequest = MutableStateFlow<Float?>(null)
    val windowBrightnessRequest: StateFlow<Float?> = _windowBrightnessRequest.asStateFlow()

    private var currentTorchState = false

    fun refreshTelemetry(): SystemTelemetrySnapshot {
        val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.coerceAtLeast(1) ?: 100
        val batteryPct = if (level >= 0) (level * 100) / scale else 0
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val tempTenths = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val totalMb = memInfo.totalMem / (1024L * 1024L)
        val availMb = memInfo.availMem / (1024L * 1024L)
        val usedMb = (totalMb - availMb).coerceAtLeast(0L)

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        val caps = cm?.getNetworkCapabilities(network)
        val transport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val downKbps = caps?.linkDownstreamBandwidthKbps ?: 0

        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxVol = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
        val curVol = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val volPct = ((curVol * 100f) / maxVol).toInt().coerceIn(0, 100)

        val sysBright = runCatching {
            Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(150)
        val brightPct = ((sysBright * 100f) / 255f).toInt().coerceIn(5, 100)

        val snapshot = SystemTelemetrySnapshot(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            batteryTempC = tempTenths / 10f,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            networkTransport = transport,
            networkValidated = validated,
            downstreamKbps = downKbps,
            torchEnabled = currentTorchState,
            mediaVolumePercent = volPct,
            brightnessPercent = brightPct
        )
        _telemetry.value = snapshot
        return snapshot
    }

    fun toggleFlashlight(enabled: Boolean): String {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return "CameraManager unavailable."
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "No camera flash hardware detected on this device."
            cm.setTorchMode(cameraId, enabled)
            currentTorchState = enabled
            refreshTelemetry()
            if (enabled) "Flashlight turned ON." else "Flashlight turned OFF."
        } catch (e: Exception) {
            "Flashlight control unavailable: ${e.localizedMessage}"
        }
    }

    fun setVolume(streamName: String, percent: Int): String {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "AudioManager unavailable."
        val streamType = when (streamName.lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            else -> AudioManager.STREAM_MUSIC
        }
        val clamped = percent.coerceIn(0, 100)
        val max = audio.getStreamMaxVolume(streamType).coerceAtLeast(1)
        val target = ((clamped / 100f) * max).toInt().coerceIn(0, max)
        audio.setStreamVolume(streamType, target, 0)
        refreshTelemetry()
        return "Set $streamName volume to $clamped% ($target/$max)."
    }

    fun setBrightness(percent: Int): String {
        val clamped = percent.coerceIn(5, 100)
        _windowBrightnessRequest.value = clamped / 100f
        if (Settings.System.canWrite(appContext)) {
            val systemVal = ((clamped / 100f) * 255f).toInt().coerceIn(10, 255)
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, systemVal)
            refreshTelemetry()
            return "System & window brightness set to $clamped%."
        }
        refreshTelemetry()
        return "App window brightness set to $clamped%. Grant 'Modify System Settings' for global brightness."
    }

    fun getBatteryStatusSummary(): String {
        val snap = refreshTelemetry()
        val state = if (snap.isCharging) "Charging" else "Discharging"
        return "Battery ${snap.batteryPercent}% ($state), Temperature ${snap.batteryTempC}°C."
    }

    fun getNetworkStatusSummary(): String {
        val snap = refreshTelemetry()
        return "Transport: ${snap.networkTransport}, Validated Internet: ${snap.networkValidated}, Downstream capacity: ${snap.downstreamKbps} kbps."
    }

    fun launchApp(appQuery: String): String {
        val pm = appContext.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        val normalizedQuery = appQuery.trim().lowercase()
        val match = resolveInfos.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            val pkg = info.activityInfo.packageName.lowercase()
            label == normalizedQuery || pkg == normalizedQuery || label.contains(normalizedQuery) || pkg.contains(normalizedQuery)
        } ?: return "No installed launchable app matched '$appQuery'."

        val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: return "App '${match.loadLabel(pm)}' has no launch intent."
        appContext.startActivity(launch)
        return "Launched ${match.loadLabel(pm)} (${match.activityInfo.packageName})."
    }

    fun controlMedia(command: String): String {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "AudioManager unavailable."
        val keyCode = when (command.lowercase()) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return "Unsupported media command: $command"
        }
        val eventTime = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return "Dispatched media command: $command."
    }

    fun openSystemSettings(section: String): String {
        val action = when (section.lowercase()) {
            "wifi" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "display" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery" -> Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
        val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
        return "Opened Android Settings: $section."
    }

    fun readNotificationsSummary(): String {
        if (!UltronNotificationListenerService.isConnected.value) {
            return "Ultron Notification Assistant is not enabled in Android Settings."
        }
        val items = UltronNotificationListenerService.recentNotifications.value
        if (items.isEmpty()) return "No recent notifications captured."
        return items.take(8).joinToString("\n") { "[${it.timeLabel}] ${it.title}: ${it.text}" }
    }

    fun listWorkspaceFiles(): String {
        val files = workspaceDir.listFiles().orEmpty()
        if (files.isEmpty()) return "Workspace directory is empty."
        return files.joinToString("\n") { "${it.name} (${it.length()} bytes)" }
    }

    fun writeWorkspaceFile(fileName: String, content: String): String {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_").take(80)
        if (safeName.isBlank()) return "Invalid file name."
        val target = File(workspaceDir, safeName)
        target.writeText(content)
        return "Saved ${target.name} (${target.length()} bytes) in Ultron workspace."
    }

    fun readWorkspaceFile(fileName: String): String {
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")
        val target = File(workspaceDir, safeName)
        if (!target.exists()) return "Workspace file '$safeName' not found."
        return target.readText().take(8000)
    }

    fun performAccessibilityAction(action: String): String {
        return UltronAccessibilityService.performActionIfActive(action)
    }
}
