package com.example.security

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.example.androidtools.UltronAccessibilityService
import com.example.androidtools.UltronNotificationListenerService

data class PermissionStatusSnapshot(
    val microphoneGranted: Boolean,
    val cameraGranted: Boolean,
    val notificationsGranted: Boolean,
    val writeSettingsGranted: Boolean,
    val notificationListenerEnabled: Boolean,
    val accessibilityServiceEnabled: Boolean
)

class PermissionManager(private val context: Context) {

    fun getStatusSnapshot(): PermissionStatusSnapshot {
        val mic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val cam = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val writeSettings = Settings.System.canWrite(context)

        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ).orEmpty()
        val listenerComponent = ComponentName(
            context,
            UltronNotificationListenerService::class.java
        ).flattenToString()
        val listenerEnabled = enabledListeners.contains(listenerComponent)

        val enabledAccessibility = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ).orEmpty()
        val accComponent = ComponentName(
            context,
            UltronAccessibilityService::class.java
        ).flattenToString()
        val accessibilityEnabled = enabledAccessibility.contains(accComponent, ignoreCase = true)

        return PermissionStatusSnapshot(
            microphoneGranted = mic,
            cameraGranted = cam,
            notificationsGranted = notif,
            writeSettingsGranted = writeSettings,
            notificationListenerEnabled = listenerEnabled,
            accessibilityServiceEnabled = accessibilityEnabled
        )
    }

    fun createAppDetailsIntent(): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun createWriteSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun createNotificationListenerSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun createAccessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
