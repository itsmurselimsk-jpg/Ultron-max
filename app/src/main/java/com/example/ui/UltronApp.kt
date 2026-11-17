package com.example.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.UltronContainer
import com.example.UltronViewModel
import com.example.auth.AuthManager
import com.example.ui.components.RiskConfirmationDialog
import com.example.ui.components.TacticalStatusChip
import com.example.ui.screens.AgentScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CoreVoiceScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.MonitorSecurityScreen
import com.example.ui.screens.ResearchDevScreen
import com.example.ui.screens.VisionScreen
import com.example.ui.theme.CharcoalDeep
import com.example.ui.theme.CharcoalElevated
import com.example.ui.theme.CrimsonCore
import com.example.ui.theme.CrimsonDarkContainer
import com.example.ui.theme.CrimsonLaser
import com.example.ui.theme.EmberAmber
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable
data object CoreRoute

@Serializable
data object AgentsRoute

@Serializable
data object VisionRoute

@Serializable
data object MemoryRoute

@Serializable
data object LabRoute

@Serializable
data object SecurityRoute

private data class UltronNavItem(
    val route: Any,
    val label: String,
    val icon: ImageVector,
    val tag: String
)

private val ULTRON_DESTINATIONS = listOf(
    UltronNavItem(CoreRoute, "Core", Icons.Default.GraphicEq, "nav_core"),
    UltronNavItem(AgentsRoute, "Agents", Icons.Default.AutoAwesome, "nav_agents"),
    UltronNavItem(VisionRoute, "Vision", Icons.Default.CameraAlt, "nav_vision"),
    UltronNavItem(MemoryRoute, "Vault", Icons.Default.Memory, "nav_memory"),
    UltronNavItem(LabRoute, "Lab", Icons.Default.Code, "nav_lab"),
    UltronNavItem(SecurityRoute, "Command", Icons.Default.Security, "nav_security")
)

@Composable
fun UltronApp(
    container: UltronContainer
) {
    val context = LocalContext.current
    val currentUser by AuthManager.authStateFlow().collectAsStateWithLifecycle(
        initialValue = runCatching { Firebase.auth.currentUser }.getOrNull()
    )

    val user = currentUser
    if (user == null) {
        AuthScreen(onAuthSuccess = {})
        return
    }

    key(user.uid) {
        val viewModel: UltronViewModel = viewModel(
            key = user.uid,
            factory = viewModelFactory {
                initializer {
                    UltronViewModel(
                        appContext = context.applicationContext,
                        container = container,
                        currentUserId = user.uid
                    )
                }
            }
        )

        val prefs by viewModel.preferences.collectAsStateWithLifecycle()
        val pendingAgentConfirm by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
        val pendingChatConfirm by viewModel.chatToolConfirmation.collectAsStateWithLifecycle()
        val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
        val brightnessRequest by viewModel.windowBrightnessRequest.collectAsStateWithLifecycle()

        var biometricUnlocked by rememberSaveable(user.uid) { mutableStateOf(false) }
        var biometricError by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(brightnessRequest) {
            val req = brightnessRequest
            if (req != null) {
                context.findActivity()?.window?.let { win ->
                    val lp = win.attributes
                    lp.screenBrightness = req
                    win.attributes = lp
                }
            }
        }

        if (prefs.biometricLockEnabled && !biometricUnlocked) {
            BiometricGateScreen(
                container = container,
                errorText = biometricError,
                onUnlocked = {
                    biometricError = null
                    biometricUnlocked = true
                },
                onError = { err -> biometricError = err }
            )
        } else {
            val snackbarHostState = remember { SnackbarHostState() }
            LaunchedEffect(statusBanner) {
                val msg = statusBanner
                if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg)
                    viewModel.clearBanner()
                }
            }

            val activeConfirmation = pendingAgentConfirm ?: pendingChatConfirm
            if (activeConfirmation != null) {
                RiskConfirmationDialog(
                    request = activeConfirmation,
                    biometricAuthManager = container.biometricAuthManager,
                    onResolve = { approved ->
                        if (pendingAgentConfirm != null) {
                            viewModel.resolveAgentConfirmation(approved)
                        } else {
                            viewModel.resolveChatToolConfirmation(approved)
                        }
                    },
                    onError = { err -> biometricError = err }
                )
            }

            val navController = rememberNavController()
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination

            val onNavigate: (Any) -> Unit = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ObsidianVoid)
            ) {
                val useRail = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = CharcoalDeep,
                            contentColor = TextSecondary,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            ULTRON_DESTINATIONS.forEach { item ->
                                val selected = currentDestination?.hasRoute(item.route::class) == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { onNavigate(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = item.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = CrimsonLaser,
                                        selectedTextColor = TextPrimary,
                                        indicatorColor = CrimsonDarkContainer,
                                        unselectedIconColor = TextMuted,
                                        unselectedTextColor = TextMuted
                                    ),
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag(item.tag)
                                )
                            }
                        }
                    }

                    Scaffold(
                        containerColor = ObsidianVoid,
                        contentWindowInsets = WindowInsets.safeDrawing,
                        snackbarHost = {
                            SnackbarHost(hostState = snackbarHostState) { data ->
                                Snackbar(
                                    snackbarData = data,
                                    containerColor = CharcoalElevated,
                                    contentColor = TextPrimary,
                                    actionColor = CrimsonLaser
                                )
                            }
                        },
                        bottomBar = {
                            if (!useRail) {
                                NavigationBar(
                                    containerColor = CharcoalDeep,
                                    contentColor = TextSecondary,
                                    modifier = Modifier.navigationBarsPadding()
                                ) {
                                    ULTRON_DESTINATIONS.forEach { item ->
                                        val selected = currentDestination?.hasRoute(item.route::class) == true
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { onNavigate(item.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = item.icon,
                                                    contentDescription = item.label
                                                )
                                            },
                                            label = {
                                                Text(
                                                    text = item.label,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = CrimsonLaser,
                                                selectedTextColor = TextPrimary,
                                                indicatorColor = CrimsonDarkContainer,
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted
                                            ),
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .testTag(item.tag)
                                        )
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = CoreRoute,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            composable<CoreRoute> {
                                CoreVoiceScreen(viewModel = viewModel)
                            }
                            composable<AgentsRoute> {
                                AgentScreen(viewModel = viewModel)
                            }
                            composable<VisionRoute> {
                                VisionScreen(viewModel = viewModel, container = container)
                            }
                            composable<MemoryRoute> {
                                MemoryScreen(viewModel = viewModel)
                            }
                            composable<LabRoute> {
                                ResearchDevScreen(viewModel = viewModel)
                            }
                            composable<SecurityRoute> {
                                MonitorSecurityScreen(
                                    viewModel = viewModel,
                                    isKeystoreActive = container.cryptoManager.isHardwareKeystoreActive
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BiometricGateScreen(
    container: UltronContainer,
    errorText: String?,
    onUnlocked: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val activity = context.findActivity() as? FragmentActivity
    val canBiometric = remember(activity) {
        activity != null && container.biometricAuthManager.canAuthenticate(activity)
    }

    LaunchedEffect(canBiometric) {
        if (canBiometric && activity != null) {
            container.biometricAuthManager.authenticate(
                activity = activity,
                title = "Unlock Ultron Omega",
                subtitle = "Verify identity to access encrypted vault and command systems",
                onSuccess = onUnlocked,
                onError = onError
            )
        }
    }

    Scaffold(containerColor = ObsidianVoid) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid)
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = CharcoalElevated,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HairlineBorder),
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    TacticalStatusChip(label = "BIOMETRIC VAULT LOCK", color = EmberAmber)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "ULTRON OMEGA LOCKED",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (canBiometric) {
                            "Authenticate with device biometrics or screen lock credential to proceed."
                        } else {
                            "Biometric/credential lock is not enrolled on this device."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (canBiometric && activity != null) {
                                container.biometricAuthManager.authenticate(
                                    activity = activity,
                                    title = "Unlock Ultron Omega",
                                    subtitle = "Verify identity to access encrypted vault",
                                    onSuccess = onUnlocked,
                                    onError = onError
                                )
                            } else {
                                onUnlocked()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonCore),
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("biometric_unlock_button")
                    ) {
                        Text(if (canBiometric) "Authenticate & Unlock" else "Continue")
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { AuthManager.signOut(credentialManager, scope) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("biometric_sign_out_button")
                    ) {
                        Text("Sign Out", color = TextSecondary)
                    }

                    if (!errorText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = CrimsonLaser,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
