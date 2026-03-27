package com.sunmi.tapro.taplink.demo.service

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.service.cloud.CloudPaymentService
import com.sunmi.tapro.taplink.demo.util.CloudPreferences
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.ErrorMessageConverter
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkInitializer
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkPreferences
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Connection Manager
 * 
 * Manages the connection lifecycle with the payment terminal.
 * Provides reactive connection state updates through StateFlow.
 * Supports all connection modes: App-to-App, Cable, LAN.
 * 
 * Key Features:
 * - Reactive state management using StateFlow
 * - Automatic reconnection support
 * - Connection state observers
 * - Thread-safe operations using coroutines
 */
class ConnectionManager(
    private val context: Context,
    private val paymentServiceProvider: (Context) -> PaymentService
) {
    companion object {
        private const val TAG = "ConnectionManager"
    }
    
    // Coroutine scope for connection operations
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Connection state flow
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    // Current connection mode
    private var currentMode: ConnectionMode? = null

    /**
     * Connection generation counter.
     *
     * Incremented each time a new connection is initiated (connect/switchToCloud).
     * Every ConnectionListener captures its generation at registration time and
     * ignores callbacks if the generation has since advanced — this prevents stale
     * async SDK callbacks (e.g. LAN WebSocket close arriving after we have already
     * switched to Cloud) from overwriting a newer connection state.
     */
    private var connectionGeneration = 0
    
    /**
     * Connect to payment terminal
     * 
     * Initiates connection based on saved preferences.
     * Updates connection state through StateFlow.
     * Handles all exceptions to prevent app crashes.
     */
    fun connect() {
        scope.launch {
            try {
                Log.d(TAG, "Starting connection...")
                _connectionState.value = ConnectionState.Connecting

                // Capture the current generation so that callbacks registered below
                // can detect whether they have been superseded by a newer connection.
                val generation = ++connectionGeneration
                
                // Get connection mode from preferences
                val mode = getConnectionModeFromPreferences()
                currentMode = mode
                
                // Get the appropriate payment service for current mode
                val paymentService = paymentServiceProvider(context)
                
                // Cloud mode: initialize and connect entirely on IO thread
                if (mode == ConnectionMode.CLOUD && paymentService is CloudPaymentService) {
                    connectCloud(paymentService)
                    return@launch
                }
                
                // Create connection config based on mode
                val config = createConnectionConfig(mode)
                
                // Connect using payment service
                paymentService.connect(config, object : ConnectionListener {
                    override fun onConnected(deviceId: String, taproVersion: String) {
                        // Ignore if a newer connection has been initiated
                        if (generation != connectionGeneration) {
                            Log.d(TAG, "Ignoring stale onConnected (gen $generation, current $connectionGeneration)")
                            return
                        }
                        Log.d(TAG, "Connected successfully - Device: $deviceId, Version: $taproVersion")
                        _connectionState.value = ConnectionState.Connected(
                            mode = mode,
                            deviceId = deviceId,
                            version = taproVersion
                        )
                    }
                    
                    override fun onDisconnected(reason: String) {
                        // Ignore if a newer connection has been initiated (e.g. switched to Cloud)
                        if (generation != connectionGeneration) {
                            Log.d(TAG, "Ignoring stale onDisconnected (gen $generation, current $connectionGeneration) - Reason: $reason")
                            return
                        }
                        Log.d(TAG, "Disconnected - Reason: $reason")
                        _connectionState.value = ConnectionState.Disconnected
                        currentMode = null
                    }
                    
                    override fun onError(code: String, message: String) {
                        // Ignore if a newer connection has been initiated
                        if (generation != connectionGeneration) {
                            Log.d(TAG, "Ignoring stale onError (gen $generation, current $connectionGeneration) - Code: $code")
                            return
                        }
                        Log.e(TAG, "Connection error - Code: $code, Message: $message")
                        _connectionState.value = ConnectionState.Error(
                            code = code,
                            message = message
                        )
                    }
                })
                
            } catch (e: NoSuchMethodError) {
                // Handle SDK version mismatch or API incompatibility
                Log.e(TAG, "SDK API incompatibility detected", e)
                _connectionState.value = ConnectionState.Error(
                    code = "SDK_INCOMPATIBLE",
                    message = "SDK version mismatch. Please check SDK integration."
                )
            } catch (e: IllegalStateException) {
                // Handle configuration errors (e.g., missing LAN IP)
                Log.e(TAG, "Configuration error", e)
                _connectionState.value = ConnectionState.Error(
                    code = "CONFIG_ERROR",
                    message = e.message ?: "Configuration error"
                )
            } catch (e: Exception) {
                // Handle any other unexpected errors
                Log.e(TAG, "Connection failed with exception", e)
                _connectionState.value = ConnectionState.Error(
                    code = "EXCEPTION",
                    message = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Connect Cloud mode.
     *
     * Validates that the configuration is present, ensures the local Taplink SDK
     * is disconnected, and marks the state as Connected. CloudHttpClient is lazily
     * initialized when the first transaction is executed.
     */
    private suspend fun connectCloud(paymentService: CloudPaymentService) {
        try {
            val cloudConfig = CloudPreferences.getConfig(context)

            // Validate required config
            if (cloudConfig.apiKey.isBlank()) {
                _connectionState.value = ConnectionState.Error(
                    code = "CLOUD_CONFIG_ERROR",
                    message = "API Key is required for Cloud mode"
                )
                return
            }
            if (cloudConfig.baseUrl.isBlank()) {
                _connectionState.value = ConnectionState.Error(
                    code = "CLOUD_CONFIG_ERROR",
                    message = "Base URL is required for Cloud mode"
                )
                return
            }

            // Ensure local Taplink SDK is disconnected
            try {
                com.sunmi.tapro.taplink.demo.di.DependencyProvider.taplinkPaymentService.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to disconnect Taplink SDK (may not have been connected)", e)
            }

            // Store config in CloudPaymentService for lazy CloudHttpClient creation
            val sdkConfig = TaplinkSdkPreferences.getConfig(context, TaplinkSdkInitializer.DEFAULT_CONFIG)
            val merchantId = cloudConfig.merchantId.ifBlank { sdkConfig.merchantId }
            val appId = cloudConfig.appId.ifBlank { sdkConfig.appId }
            paymentService.initialize(
                apiKey = cloudConfig.apiKey,
                baseUrl = cloudConfig.baseUrl,
                appId = appId,
                merchantId = merchantId,
                terminalSn = cloudConfig.terminalSn
            )

            // Sync cached preferences to CloudPaymentService so they are
            // available even if the user never opens the Settings screen.
            paymentService.setNotifyUrl(cloudConfig.notifyUrl)
            paymentService.setPushToTerminal(cloudConfig.pushToTerminal)
            val printReceipt = ConnectionPreferences.getPrintReceipt(context)
            paymentService.setPrintReceipt(printReceipt.name)

            // Report connected — CloudHttpClient will be initialized lazily on first transaction
            Log.d(TAG, "Cloud connected (config validated): terminalSn=${cloudConfig.terminalSn}, appId=$appId")
            _connectionState.value = ConnectionState.Connected(
                mode = ConnectionMode.CLOUD,
                deviceId = cloudConfig.terminalSn.ifBlank { "Cloud" },
                version = "Cloud"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cloud connection failed", e)
            _connectionState.value = ConnectionState.Error(
                code = "CLOUD_ERROR",
                message = e.message ?: "Failed to configure cloud connection"
            )
        }
    }
    
    /**
     * Disconnect from payment terminal
     * 
     * Cleanly terminates the connection and updates state.
     * For Cloud mode, shuts down CloudHttpClient without touching Taplink SDK.
     */
    fun disconnect() {
        scope.launch {
            try {
                Log.d(TAG, "Disconnecting...")
                val paymentService = paymentServiceProvider(context)
                if (paymentService is CloudPaymentService) {
                    // Cloud mode: shut down CloudHttpClient, skip Taplink SDK
                    paymentService.shutdown()
                } else {
                    paymentService.disconnect()
                }
                _connectionState.value = ConnectionState.Disconnected
                currentMode = null
            } catch (e: Exception) {
                Log.e(TAG, "Disconnect failed with exception", e)
                // Still update state to disconnected even if error occurs
                _connectionState.value = ConnectionState.Disconnected
                currentMode = null
            }
        }
    }
    
    /**
     * Switch directly to Cloud mode without emitting intermediate Disconnected/Connecting states.
     *
     * When the user configures Cloud mode from Settings, calling reconnect() would briefly
     * set ConnectionState.Disconnected before Cloud connects, causing the Main screen to show
     * a spurious "Disconnected" notification. This method skips that transient state by
     * going directly from whatever the current state is to Connected (Cloud), with no
     * Disconnected or Connecting emissions in between.
     *
     * connectCloud() already handles disconnecting the old Taplink SDK service internally.
     */
    fun switchToCloud() {
        scope.launch {
            // Advance the generation so that any in-flight ConnectionListener callbacks
            // from the previous SDK connection (e.g. LAN WebSocket close) are treated
            // as stale and will not overwrite the Cloud Connected state we are about to set.
            connectionGeneration++
            currentMode = null
            connectCloud(com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService)
        }
    }

    /**
     * Reconnect to payment terminal
     * 
     * Disconnects current connection and reconnects using saved preferences.
     * Detects mode switching and handles cross-mode transitions properly.
     */
    fun reconnect() {
        scope.launch {
            Log.d(TAG, "Reconnecting...")
            val previousMode = currentMode

            // Disconnect the previous service properly
            if (previousMode != null) {
                try {
                    if (previousMode == ConnectionMode.CLOUD) {
                        // Previous was Cloud: shut down CloudHttpClient
                        val cloudService = com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService
                        cloudService.shutdown()
                    } else {
                        // Previous was Taplink: disconnect via Taplink SDK
//                        com.sunmi.tapro.taplink.demo.di.DependencyProvider.taplinkPaymentService.disconnect()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error disconnecting previous mode ($previousMode)", e)
                }
            }

            _connectionState.value = ConnectionState.Disconnected
            currentMode = null

            // Small delay to ensure clean disconnection
            kotlinx.coroutines.delay(500)
            connect()
        }
    }
    
    /**
     * Get connection mode from preferences
     */
    private fun getConnectionModeFromPreferences(): ConnectionMode {
        val prefMode = ConnectionPreferences.getConnectionMode(context)
        return when (prefMode) {
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> ConnectionMode.APP_TO_APP
            ConnectionPreferences.ConnectionMode.CABLE -> ConnectionMode.CABLE
            ConnectionPreferences.ConnectionMode.LAN -> ConnectionMode.LAN
            ConnectionPreferences.ConnectionMode.CLOUD -> ConnectionMode.CLOUD
        }
    }
    
    /**
     * Create connection config based on mode
     */
    private fun createConnectionConfig(mode: ConnectionMode): ConnectionConfig {
        return when (mode) {
            ConnectionMode.APP_TO_APP -> {
                Log.d(TAG, "Creating App-to-App connection config")
                ConnectionConfig.createAppMode()
            }
            ConnectionMode.CABLE -> {
                Log.d(TAG, "Creating Cable connection config")
                val protocol = ConnectionPreferences.getCableProtocol(context)
                val sdkProtocol = when (protocol) {
                    ConnectionPreferences.CableProtocol.AUTO -> 
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.AUTO
                    ConnectionPreferences.CableProtocol.USB_AOA -> 
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA
                    ConnectionPreferences.CableProtocol.USB_VSP -> 
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_VSP
                    ConnectionPreferences.CableProtocol.RS232 -> 
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.RS232
                }
                ConnectionConfig.createCableMode(sdkProtocol)
            }
            ConnectionMode.LAN -> {
                Log.d(TAG, "Creating LAN connection config")
                val (ip, port) = ConnectionPreferences.getLanConfig(context)
                if (ip.isNullOrBlank()) {
                    throw IllegalStateException("LAN IP address not configured")
                }
                ConnectionConfig.createLanMode(ip, port)
            }
            ConnectionMode.CLOUD -> {
                // Cloud mode uses its own initialization via CloudPaymentService.initialize()
                // Return a placeholder App mode config since ConnectionConfig is not used for Cloud
                Log.d(TAG, "Creating Cloud connection config (placeholder)")
                ConnectionConfig.createAppMode()
            }
        }
    }
    
    /**
     * Check if currently connected
     */
    fun isConnected(): Boolean {
        return _connectionState.value is ConnectionState.Connected
    }
    
    /**
     * Get current connection mode
     */
    fun getCurrentMode(): ConnectionMode? {
        return currentMode
    }
    
    /**
     * Convert current error state to Message
     * 
     * @return Message object if in error state, null otherwise
     */
    fun getErrorMessage(): Message? {
        val state = _connectionState.value
        return if (state is ConnectionState.Error) {
            ErrorMessageConverter.fromConnectionError(state.code, state.message)
        } else {
            null
        }
    }
}

/**
 * Connection State sealed class
 * 
 * Represents all possible connection states in the application.
 * Used with StateFlow for reactive state management.
 */
sealed class ConnectionState {
    /**
     * Disconnected state
     * 
     * No active connection to payment terminal.
     */
    object Disconnected : ConnectionState()
    
    /**
     * Connecting state
     * 
     * Connection attempt in progress.
     */
    object Connecting : ConnectionState()
    
    /**
     * Connected state
     * 
     * Successfully connected to payment terminal.
     * 
     * @param mode Connection mode used
     * @param deviceId Connected device identifier
     * @param version Tapro application version
     */
    data class Connected(
        val mode: ConnectionMode,
        val deviceId: String,
        val version: String
    ) : ConnectionState()
    
    /**
     * Error state
     * 
     * Connection error occurred.
     * 
     * @param code Error code
     * @param message Error message
     */
    data class Error(
        val code: String,
        val message: String
    ) : ConnectionState() {
        /**
         * Convert error state to Message object
         * 
         * @return Message object for display in MessageCard
         */
        fun toMessage(): Message {
            return ErrorMessageConverter.fromConnectionError(code, message)
        }
    }
}
