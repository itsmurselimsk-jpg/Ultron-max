package com.example.androidtools

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

class UltronAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
        _isConnected.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        _activeWindowPackage.value = pkg
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        super.onDestroy()
        _isConnected.value = false
        instanceRef = null
    }

    fun executeApprovedAction(actionName: String): String {
        return when (actionName.uppercase()) {
            "BACK" -> if (performGlobalAction(GLOBAL_ACTION_BACK)) "Executed global Back." else "Failed Back action."
            "HOME" -> if (performGlobalAction(GLOBAL_ACTION_HOME)) "Executed global Home." else "Failed Home action."
            "RECENTS" -> if (performGlobalAction(GLOBAL_ACTION_RECENTS)) "Opened Recents." else "Failed Recents action."
            "NOTIFICATIONS" -> if (performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)) "Opened Notification Shade." else "Failed Notification Shade action."
            "SCREEN_SUMMARY" -> summarizeActiveWindow()
            else -> "Unsupported accessibility action: $actionName"
        }
    }

    private fun summarizeActiveWindow(): String {
        val root = rootInActiveWindow ?: return "No active window root available."
        val items = mutableListOf<String>()
        collectTextNodes(root, items, maxNodes = 30)
        return if (items.isEmpty()) {
            "Active package: ${root.packageName ?: "unknown"} (no readable text nodes)."
        } else {
            "Active package ${root.packageName ?: "unknown"}:\n" + items.joinToString(" | ")
        }
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo?, out: MutableList<String>, maxNodes: Int) {
        if (node == null || out.size >= maxNodes) return
        val label = node.text?.toString()?.trim().takeUnless { it.isNullOrBlank() }
            ?: node.contentDescription?.toString()?.trim().takeUnless { it.isNullOrBlank() }
        if (!label.isNullOrBlank() && label.length <= 120) {
            out.add(label)
        }
        for (i in 0 until node.childCount) {
            if (out.size >= maxNodes) break
            collectTextNodes(node.getChild(i), out, maxNodes)
        }
    }

    companion object {
        private var instanceRef: WeakReference<UltronAccessibilityService>? = null

        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

        private val _activeWindowPackage = MutableStateFlow("None")
        val activeWindowPackage: StateFlow<String> = _activeWindowPackage.asStateFlow()

        fun performActionIfActive(actionName: String): String {
            val service = instanceRef?.get()
                ?: return "Ultron Accessibility Service is not enabled. Enable it in System Settings after reviewing disclosure."
            return service.executeApprovedAction(actionName)
        }
    }
}
