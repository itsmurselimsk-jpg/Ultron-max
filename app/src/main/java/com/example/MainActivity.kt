package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.example.security.configureAppCheck
import com.example.ui.UltronApp
import com.example.ui.theme.UltronOmegaTheme

class MainActivity : FragmentActivity() {

    private val container: UltronContainer by lazy {
        UltronContainer(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must configure Firebase App Check before Compose/ViewModel initialization
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            UltronOmegaTheme {
                UltronApp(container = container)
            }
        }
    }
}
