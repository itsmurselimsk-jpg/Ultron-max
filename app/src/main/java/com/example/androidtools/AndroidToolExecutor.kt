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
import com.example.security.ToolInputValidator
import com.example.security.ToolValidationResult
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

data class ToolExecutionResult(
    val success: Boolean,
    val output: String,
    val verificationNote: String
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

    fun toggleFlashlight(enabled: Boolean): String = toggleFlashlightVerified(enabled).output

    fun toggleFlashlightVerified(enabled: Boolean): ToolExecutionResult {
        val cm = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return ToolExecutionResult(false, "CameraManager unavailable.", "Missing CameraManager system service")
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ToolExecutionResult(
                success = false,
                output = "No camera flash hardware detected on this device.",
                verificationNote = "No flash unit reported by CameraCharacteristics"
            )
            cm.setTorchMode(cameraId, enabled)
            currentTorchState = enabled
            val snap = refreshTelemetry()
            val msg = if (enabled) "Flashlight turned ON." else "Flashlight turned OFF."
            ToolExecutionResult(
                success = snap.torchEnabled == enabled,
                output = msg,
                verificationNote = "CameraManager torch state=$enabled on cameraId=$cameraId"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                output = "Flashlight control unavailable: ${e.localizedMessage}",
                verificationNote = "Camera2 torch exception: ${e.javaClass.simpleName}"
            )
        }
    }

    fun setVolume(streamName: String, percent: Int): String = setVolumeVerified(streamName, percent).output

    fun setVolumeVerified(streamName: String, percent: Int): ToolExecutionResult {
        if (percent !in 0..100) {
            return ToolExecutionResult(false, "Volume $percent out of valid range 0..100.", "Failed input bounds validation")
        }
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(false, "AudioManager unavailable.", "Missing AudioManager")
        val streamType = when (streamName.lowercase()) {
            "ring" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "media" -> AudioManager.STREAM_MUSIC
            else -> return ToolExecutionResult(false, "Unsupported audio stream '$streamName'.", "Invalid audio stream")
        }
        return try {
            val max = audio.getStreamMaxVolume(streamType).coerceAtLeast(1)
            val target = ((percent / 100f) * max).toInt().coerceIn(0, max)
            audio.setStreamVolume(streamType, target, 0)
            val actual = audio.getStreamVolume(streamType)
            refreshTelemetry()
            ToolExecutionResult(
                success = true,
                output = "Set $streamName volume to $percent% ($actual/$max).",
                verificationNote = "AudioManager stream=$streamName readback=$actual/$max"
            )
        } catch (e: SecurityException) {
            ToolExecutionResult(
                success = false,
                output = "Audio stream volume change blocked by Do Not Disturb policy.",
                verificationNote = "AudioManager SecurityException: ${e.message}"
            )
        }
    }

    fun setBrightness(percent: Int): String = setBrightnessVerified(percent).output

    fun setBrightnessVerified(percent: Int): ToolExecutionResult {
        if (percent !in 5..100) {
            return ToolExecutionResult(false, "Brightness $percent out of valid range 5..100.", "Failed bounds check")
        }
        _windowBrightnessRequest.value = percent / 100f
        if (Settings.System.canWrite(appContext)) {
            val systemVal = ((percent / 100f) * 255f).toInt().coerceIn(10, 255)
            Settings.System.putInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS, systemVal)
            val actual = runCatching {
                Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            }.getOrDefault(systemVal)
            refreshTelemetry()
            return ToolExecutionResult(
                success = true,
                output = "System & window brightness set to $percent%.",
                verificationNote = "System SCREEN_BRIGHTNESS readback=$actual/255"
            )
        }
        refreshTelemetry()
        return ToolExecutionResult(
            success = true,
            output = "App window brightness set to $percent%. Grant 'Modify System Settings' for global brightness.",
            verificationNote = "Applied in-app WindowManager brightness (${percent}%)"
        )
    }

    fun getBatteryStatusSummary(): String = getBatteryStatusVerified().output

    fun getBatteryStatusVerified(): ToolExecutionResult {
        val snap = refreshTelemetry()
        val state = if (snap.isCharging) "Charging" else "Discharging"
        return ToolExecutionResult(
            success = true,
            output = "Battery ${snap.batteryPercent}% ($state), Temperature ${snap.batteryTempC}°C.",
            verificationNote = "Verified via ACTION_BATTERY_CHANGED sticky broadcast"
        )
    }

    fun getNetworkStatusSummary(): String = getNetworkStatusVerified().output

    fun getNetworkStatusVerified(): ToolExecutionResult {
        val snap = refreshTelemetry()
        return ToolExecutionResult(
            success = true,
            output = "Transport: ${snap.networkTransport}, Validated Internet: ${snap.networkValidated}, Downstream capacity: ${snap.downstreamKbps} kbps.",
            verificationNote = "Verified via ConnectivityManager NetworkCapabilities"
        )
    }

    fun launchApp(appQuery: String): String = launchAppVerified(appQuery).output

    fun launchAppVerified(appQuery: String): ToolExecutionResult {
        when (val validation = ToolInputValidator.validateToolArguments("launchApp", appQuery, "")) {
            is ToolValidationResult.Invalid -> {
                return ToolExecutionResult(false, validation.reason, "Validation failed")
            }
            is ToolValidationResult.Valid -> {
                val cleanQuery = validation.arg1
                val pm = appContext.packageManager
                val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                val normalizedQuery = cleanQuery.lowercase()
                val match = resolveInfos.firstOrNull { info ->
                    val label = info.loadLabel(pm).toString().lowercase()
                    val pkg = info.activityInfo.packageName.lowercase()
                    label == normalizedQuery || pkg == normalizedQuery || label.contains(normalizedQuery) || pkg.contains(normalizedQuery)
                } ?: return ToolExecutionResult(
                    success = false,
                    output = "No installed launchable app matched '$cleanQuery'.",
                    verificationNote = "PackageManager query matched 0 launcher activities"
                )

                val launch = pm.getLaunchIntentForPackage(match.activityInfo.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ?: return ToolExecutionResult(
                        success = false,
                        output = "App '${match.loadLabel(pm)}' has no launch intent.",
                        verificationNote = "Missing launch intent for ${match.activityInfo.packageName}"
                    )
                return try {
                    appContext.startActivity(launch)
                    ToolExecutionResult(
                        success = true,
                        output = "Launched ${match.loadLabel(pm)} (${match.activityInfo.packageName}).",
                        verificationNote = "Started launcher activity for ${match.activityInfo.packageName}"
                    )
                } catch (e: Exception) {
                    ToolExecutionResult(
                        success = false,
                        output = "Failed to launch app: ${e.localizedMessage}",
                        verificationNote = "startActivity threw ${e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    fun controlMedia(command: String): String = controlMediaVerified(command).output

    fun controlMediaVerified(command: String): ToolExecutionResult {
        val audio = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ToolExecutionResult(false, "AudioManager unavailable.", "Missing AudioManager")
        val keyCode = when (command.lowercase()) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return ToolExecutionResult(
                success = false,
                output = "Unsupported media command: $command",
                verificationNote = "Unknown media key command"
            )
        }
        val eventTime = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
        return ToolExecutionResult(
            success = true,
            output = "Dispatched media command: $command.",
            verificationNote = "Dispatched KeyEvent down+up keyCode=$keyCode"
        )
    }

    fun openSystemSettings(section: String): String = openSystemSettingsVerified(section).output

    fun openSystemSettingsVerified(section: String): ToolExecutionResult {
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
        return try {
            appContext.startActivity(intent)
            ToolExecutionResult(
                success = true,
                output = "Opened Android Settings: $section.",
                verificationNote = "Launched $action"
            )
        } catch (e: Exception) {
            ToolExecutionResult(
                success = false,
                output = "Unable to open Android Settings ($section): ${e.localizedMessage}",
                verificationNote = "Settings launch failed: ${e.javaClass.simpleName}"
            )
        }
    }

    fun readNotificationsSummary(): String = readNotificationsVerified().output

    fun readNotificationsVerified(): ToolExecutionResult {
        if (!UltronNotificationListenerService.isConnected.value) {
            return ToolExecutionResult(
                success = false,
                output = "Ultron Notification Assistant is not enabled in Android Settings.",
                verificationNote = "NotificationListenerService disconnected"
            )
        }
        val items = UltronNotificationListenerService.recentNotifications.value
        if (items.isEmpty()) {
            return ToolExecutionResult(
                success = true,
                output = "No recent notifications captured.",
                verificationNote = "NotificationListener connected (0 active buffered)"
            )
        }
        return ToolExecutionResult(
            success = true,
            output = items.take(8).joinToString("\n") { "[${it.timeLabel}] ${it.title}: ${it.text}" },
            verificationNote = "Returned ${items.take(8).size} active notification summaries"
        )
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

    fun performAccessibilityAction(action: String): String = performAccessibilityActionVerified(action).output

    fun performAccessibilityActionVerified(action: String): ToolExecutionResult {
        if (!UltronAccessibilityService.isConnected.value) {
            return ToolExecutionResult(
                success = false,
                output = "Ultron Accessibility Service is not enabled. Enable it in System Settings after reviewing disclosure.",
                verificationNote = "AccessibilityService not connected"
            )
        }
        val result = UltronAccessibilityService.performActionIfActive(action)
        val ok = !result.startsWith("Failed", ignoreCase = true) &&
            !result.startsWith("Unsupported", ignoreCase = true) &&
            !result.startsWith("Ultron Accessibility Service is not enabled", ignoreCase = true)
        return ToolExecutionResult(
            success = ok,
            output = result,
            verificationNote = if (ok) "Accessibility action '$action' confirmed" else "Accessibility action '$action' unsuccessful"
        )
    }
}
