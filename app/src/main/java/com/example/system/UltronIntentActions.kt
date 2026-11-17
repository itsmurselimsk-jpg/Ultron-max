package com.example.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.MainActivity

object UltronIntentActions {
    const val EXTRA_ULTRON_ACTION = "com.example.EXTRA_ULTRON_ACTION"

    const val ACTION_LAUNCH_VOICE_CORE = "ULTRON_ACTION_LAUNCH_VOICE_CORE"
    const val ACTION_OPEN_BRIEFING = "ULTRON_ACTION_OPEN_BRIEFING"
    const val ACTION_OPEN_SECURITY = "ULTRON_ACTION_OPEN_SECURITY"

    fun createLaunchIntent(context: Context, action: String): Intent {
        return Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_ULTRON_ACTION, action)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
    }

    fun createLaunchPendingIntent(
        context: Context,
        action: String,
        requestCode: Int = action.hashCode()
    ): PendingIntent {
        return PendingIntent.getActivity(
            context,
            requestCode,
            createLaunchIntent(context, action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
