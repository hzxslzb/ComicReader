package com.arnolee.moye

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

data class PreloadPreferences(val enabled: Boolean=true,val pages: Int=5,val wifiOnly: Boolean=true) {
    fun allowed(wifi: Boolean)=enabled && (!wifiOnly || wifi)
    fun save(prefs: SharedPreferences) {
        prefs.edit().putBoolean("preloadEnabled",enabled).putInt("preloadPages",pages.coerceIn(3,5))
            .putBoolean("preloadWifiOnly",wifiOnly).apply()
    }
    companion object {
        fun load(prefs: SharedPreferences)=PreloadPreferences(prefs.getBoolean("preloadEnabled",true),
            prefs.getInt("preloadPages",5).coerceIn(3,5),prefs.getBoolean("preloadWifiOnly",true))
    }
}

class ReaderNetwork(context: Context) {
    private val manager=context.getSystemService(ConnectivityManager::class.java)
    fun isWifi(): Boolean {
        val capabilities=manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    val wifiChanges=callbackFlow {
        val callback=object: ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(isWifi()) }
            override fun onCapabilitiesChanged(network: Network,capabilities: NetworkCapabilities) { trySend(isWifi()) }
            override fun onLost(network: Network) { trySend(if(manager.activeNetwork==network) false else isWifi()) }
        }
        manager.registerDefaultNetworkCallback(callback)
        trySend(isWifi())
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
