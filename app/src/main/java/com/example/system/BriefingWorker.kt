package com.example.system

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.androidtools.AndroidToolExecutor
import com.example.data.local.BriefingHistoryEntity
import com.example.data.local.UltronDatabase
import com.example.security.CryptoManager
import com.example.security.SecureCredentialStore
import com.example.widget.UltronGlanceWidget
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.flow.first
import java.util.UUID
import java.util.concurrent.TimeUnit

object BriefingScheduler {
    private const val TAG = "BriefingScheduler"
    private const val WORK_NAME = "ultron_omega_periodic_briefing"
    const val CHANNEL_ID = "ultron_tactical_briefings"
    private const val NOTIFICATION_ID = 4401

    fun schedule(context: Context, enabled: Boolean, intervalHours: Int = 12) {
        val wm = WorkManager.getInstance(context.applicationContext)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val safeHours = intervalHours.coerceIn(1, 24).toLong()
        val request = PeriodicWorkRequestBuilder<BriefingWorker>(safeHours, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    suspend fun generateAndDispatch(
        context: Context,
        userIdOverride: String? = null,
        forceNotify: Boolean = false
    ): Pair<String, String> {
        val appContext = context.applicationContext
        val store = SecureCredentialStore(appContext, CryptoManager())
        val prefs = store.preferencesFlow.first()

        val telemetry = AndroidToolExecutor(appContext).refreshTelemetry()
        val uid = userIdOverride ?: runCatching { Firebase.auth.currentUser?.uid }.getOrNull()

        var memoryCount = 0
        var pinnedCount = 0
        if (!uid.isNullOrBlank()) {
            runCatching {
                val dao = UltronDatabase.getInstance(appContext).ultronDao()
                memoryCount = dao.countMemoriesForUser(uid)
                pinnedCount = dao.countPinnedMemoriesForUser(uid)
            }
        }

        val posture = if (prefs.widgetEmergencyStop) "E-STOP ENGAGED" else "NOMINAL"
        val title = "ULTRON BRIEFING • $posture"
        val body = buildString {
            append("Power: ${telemetry.batteryPercent}%")
            if (telemetry.isCharging) append(" (Charging)")
            append(" • Net: ${telemetry.networkTransport}")
            if (telemetry.ramTotalMb > 0L) {
                val freeMb = (telemetry.ramTotalMb - telemetry.ramUsedMb).coerceAtLeast(0L)
                append(" • Free RAM: ${freeMb}MB")
            }
            if (!uid.isNullOrBlank()) {
                append(" • Vault: $memoryCount memories ($pinnedCount pinned)")
            }
            append(" • Policy: ${prefs.riskPolicy.name}")
        }

        val now = System.currentTimeMillis()
        if (!uid.isNullOrBlank()) {
            runCatching {
                val dao = UltronDatabase.getInstance(appContext).ultronDao()
                dao.insertBriefingHistory(
                    BriefingHistoryEntity(
                        id = "brf_${UUID.randomUUID().toString().replace("-", "").take(12)}",
                        userId = uid,
                        title = title,
                        body = body,
                        createdAt = now
                    )
                )
            }
        }

        store.saveBriefingSnapshot(
            title = title,
            body = body,
            timestamp = now
        )
        runCatching { UltronGlanceWidget().updateAll(appContext) }

        if (prefs.proactiveBriefingsEnabled || forceNotify) {
            postNotificationIfPermitted(appContext, title, body)
        }
        return title to body
    }

    fun postNotificationIfPermitted(context: Context, title: String, body: String) {
        val canPost = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!canPost) {
            Log.d(TAG, "Skipping briefing notification: POST_NOTIFICATIONS not granted.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.briefing_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.briefing_channel_description)
            }
            nm?.createNotificationChannel(channel)
        }

        val pendingIntent = UltronIntentActions.createLaunchPendingIntent(
            context = context,
            action = UltronIntentActions.ACTION_OPEN_BRIEFING
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ultron_tile)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }.onFailure { err ->
            Log.w(TAG, "Unable to post briefing notification", err)
        }
    }
}

class BriefingWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            BriefingScheduler.generateAndDispatch(applicationContext, forceNotify = false)
            Result.success()
        } catch (e: Exception) {
            Log.e("BriefingWorker", "Briefing worker failed", e)
            Result.retry()
        }
    }
}
