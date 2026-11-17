package com.example.system

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.example.R
import com.example.widget.UltronGlanceWidget
import com.example.widget.UltronGlanceWidgetReceiver

object SurfaceInstaller {

    fun requestAddQuickTile(
        context: Context,
        onResult: (String) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult("Swipe down Quick Settings -> Edit -> drag 'Ultron Core' tile into active tiles.")
            return
        }
        val statusBarManager = context.getSystemService(StatusBarManager::class.java)
        if (statusBarManager == null) {
            onResult("StatusBarManager unavailable on this device.")
            return
        }
        val component = ComponentName(context, UltronQuickTileService::class.java)
        val icon = Icon.createWithResource(context, R.drawable.ic_ultron_tile)
        statusBarManager.requestAddTileService(
            component,
            context.getString(R.string.quick_tile_label),
            icon,
            ContextCompat.getMainExecutor(context)
        ) { resultCode ->
            val msg = when (resultCode) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ->
                    "Ultron Core Quick Settings tile added."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED ->
                    "Ultron Core Quick Settings tile is already active."
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                    "Quick Settings tile request dismissed."
                else -> "Quick Settings tile request finished (code $resultCode)."
            }
            onResult(msg)
        }
    }

    suspend fun requestPinHomeWidget(context: Context): String {
        return runCatching {
            val manager = GlanceAppWidgetManager(context.applicationContext)
            val accepted = manager.requestPinGlanceAppWidget(
                receiver = UltronGlanceWidgetReceiver::class.java,
                preview = UltronGlanceWidget()
            )
            if (accepted) {
                "Home Screen widget pin prompt opened."
            } else {
                "Launcher does not support programmatic pinning. Add 'Ultron Omega' from Home Screen Widgets."
            }
        }.getOrElse { err ->
            "Widget pin unavailable: ${err.localizedMessage ?: "Use launcher widget picker"}"
        }
    }
}
