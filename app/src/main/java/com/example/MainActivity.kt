package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.example.security.configureAppCheck
import com.example.system.UltronIntentActions
import com.example.ui.UltronApp
import com.example.ui.theme.UltronOmegaTheme

class MainActivity : FragmentActivity() {

    private val container: UltronContainer by lazy {
        UltronContainer(applicationContext)
    }

    private var pendingLaunchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must configure Firebase App Check before Compose/ViewModel initialization
        configureAppCheck(this, intent)
        pendingLaunchAction = intent?.getStringExtra(UltronIntentActions.EXTRA_ULTRON_ACTION)
        enableEdgeToEdge()
        setContent {
            UltronOmegaTheme {
                UltronApp(
                    container = container,
                    pendingLaunchAction = pendingLaunchAction,
                    onConsumeLaunchAction = { pendingLaunchAction = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingLaunchAction = intent.getStringExtra(UltronIntentActions.EXTRA_ULTRON_ACTION)
    }
}
