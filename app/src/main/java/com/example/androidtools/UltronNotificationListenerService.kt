package com.example.androidtools

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timeLabel: String
)

class UltronNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
        runCatching {
            val existing = activeNotifications?.take(15)?.mapNotNull { it.toCaptured() }.orEmpty()
            _recentNotifications.value = existing
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val captured = sbn?.toCaptured() ?: return
        val current = _recentNotifications.value.toMutableList()
        current.removeAll { it.key == captured.key }
        current.add(0, captured)
        _recentNotifications.value = current.take(25)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        _recentNotifications.value = _recentNotifications.value.filterNot { it.key == key }
    }

    private fun StatusBarNotification.toCaptured(): CapturedNotification? {
        if (packageName == applicationContext.packageName) return null
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return CapturedNotification(
            key = key,
            packageName = packageName,
            title = title.ifBlank { packageName },
            text = text,
            timeLabel = timeFmt.format(Date(postTime))
        )
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _recentNotifications = MutableStateFlow<List<CapturedNotification>>(emptyList())
        val recentNotifications: StateFlow<List<CapturedNotification>> = _recentNotifications.asStateFlow()
    }
}
