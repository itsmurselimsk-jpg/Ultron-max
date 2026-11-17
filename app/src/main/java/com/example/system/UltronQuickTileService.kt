package com.example.system

import android.annotation.SuppressLint
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.R
import com.example.security.CryptoManager
import com.example.security.SecureCredentialStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class UltronQuickTileService : TileService() {

    private val tileScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        tileScope.launch {
            val prefs = runCatching {
                SecureCredentialStore(applicationContext, CryptoManager()).preferencesFlow.first()
            }.getOrNull()
            val tile = qsTile ?: return@launch
            val emergencyStop = prefs?.widgetEmergencyStop == true

            tile.label = getString(R.string.quick_tile_label)
            tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_ultron_tile)
            tile.state = if (emergencyStop) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (emergencyStop) "E-STOP Engaged" else "Voice Ready"
            }
            tile.contentDescription = if (emergencyStop) {
                "Ultron Core paused by Emergency Stop"
            } else {
                "Launch Ultron Omega Voice Core"
            }
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isSecure) {
            unlockAndRun { launchVoiceCore() }
        } else {
            launchVoiceCore()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launchVoiceCore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = UltronIntentActions.createLaunchPendingIntent(
                context = applicationContext,
                action = UltronIntentActions.ACTION_LAUNCH_VOICE_CORE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            val intent = UltronIntentActions.createLaunchIntent(
                context = applicationContext,
                action = UltronIntentActions.ACTION_LAUNCH_VOICE_CORE
            )
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        tileScope.cancel()
        super.onDestroy()
    }
}
