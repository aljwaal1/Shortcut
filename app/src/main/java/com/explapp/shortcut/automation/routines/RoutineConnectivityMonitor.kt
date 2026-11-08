package com.explapp.shortcut.automation.routines

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Process-lifetime connectivity monitoring. Android force-stop and process death
 * prevent events from being observed; no persistent monitoring is implied.
 */
object RoutineConnectivityMonitor {
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Synchronized fun start(context: Context) {
        if (callback != null) return
        val app = context.applicationContext
        val manager = app.getSystemService(ConnectivityManager::class.java)
        val lock = Any()
        var lastState: Boolean? = null

        fun update(connected: Boolean) {
            val event = synchronized(lock) {
                val previous = lastState
                lastState = connected
                if (previous == null || previous == connected) null
                else if (connected) RoutineTriggerType.INTERNET_CONNECTED
                else RoutineTriggerType.INTERNET_DISCONNECTED
            }
            if (event != null) {
                Thread { runCatching { RoutineWork.enqueueMatching(app, RoutineEvent(event)) } }.start()
            }
        }

        val observer = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Wait for validated capabilities rather than assuming internet works.
            }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                if (manager.activeNetwork == network) {
                    update(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                }
            }
            override fun onLost(network: Network) {
                val active = manager.activeNetwork
                val internet = active?.let {
                    manager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                } ?: false
                update(internet)
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(observer) }
            .onSuccess { callback = observer }
    }
}
