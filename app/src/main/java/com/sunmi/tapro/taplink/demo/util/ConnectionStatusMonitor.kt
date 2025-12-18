package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connection status monitor for cross-device connection modes
 * 
 * Provides basic network connectivity monitoring:
 * - Network connectivity monitoring for LAN mode
 * - LAN device reachability checks
 * - Real-time connection status updates
 */
class ConnectionStatusMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "ConnectionStatusMonitor"
        private const val PING_TIMEOUT_MS = 3000
    }
    
    private var isMonitoring = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var statusListener: ConnectionStatusListener? = null
    
    /**
     * Interface for connection status updates
     */
    interface ConnectionStatusListener {
        fun onNetworkAvailable(isWifi: Boolean)
        fun onNetworkLost()
        fun onLanDeviceReachable(ip: String, responseTime: Long)
        fun onLanDeviceUnreachable(ip: String)
    }
    

    
    /**
     * Start monitoring connection status
     */
    fun startMonitoring(listener: ConnectionStatusListener) {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring connection status")
            return
        }
        
        this.statusListener = listener
        isMonitoring = true
        
        Log.d(TAG, "Starting connection status monitoring")
        
        // Register network callback for connectivity changes
        registerNetworkCallback()
    }
    
    /**
     * Stop monitoring connection status
     */
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        Log.d(TAG, "Stopping connection status monitoring")
        
        isMonitoring = false
        statusListener = null
        
        // Unregister network callback
        unregisterNetworkCallback()
    }
    
    /**
     * Check LAN device reachability
     */
    suspend fun checkLanDeviceReachability(ip: String, port: Int = 8443): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                
                // Try to connect to the device
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), PING_TIMEOUT_MS)
                    val responseTime = System.currentTimeMillis() - startTime
                    
                    Log.d(TAG, "LAN device reachable - IP: $ip, Response time: ${responseTime}ms")
                    statusListener?.onLanDeviceReachable(ip, responseTime)
                    
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "LAN device unreachable - IP: $ip, Error: ${e.message}")
                statusListener?.onLanDeviceUnreachable(ip)
                return@withContext false
            }
        }
    }
    

    
    /**
     * Check if device is connected to WiFi
     */
    fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
    

    
    /**
     * Register network connectivity callback
     */
    private fun registerNetworkCallback() {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.d(TAG, "Network available")
                
                val isWifi = isWifiConnected()
                statusListener?.onNetworkAvailable(isWifi)
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Network lost")
                statusListener?.onNetworkLost()
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
    }
    
    /**
     * Unregister network connectivity callback
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let { callback ->
            try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback: ${e.message}")
            }
        }
        networkCallback = null
    }
    

}