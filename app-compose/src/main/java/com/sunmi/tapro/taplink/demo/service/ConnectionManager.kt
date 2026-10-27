package com.sunmi.tapro.taplink.demo.service

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.service.cloud.CloudPaymentService
import com.sunmi.tapro.taplink.demo.util.CloudPreferences
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.ErrorMessageConverter
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkInitializer
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkPreferences
import com.sunmi.tapro.taplink.demo.util.TipConfigPreferences
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.enums.AppToAppMode
import com.sunmi.tapro.taplink.sdk.enums.ConnectionStatus
import com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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

        /** 等待 SDK 真正进入 DISCONNECTED 的最长时间。 */
        private const val SDK_DISCONNECT_TIMEOUT_MS = 3_000L

        /** 轮询 SDK 连接状态的间隔。 */
        private const val SDK_DISCONNECT_POLL_INTERVAL_MS = 50L

        /** 串口/USB 句柄释放后的缓冲时间，避免立即 re-open 失败。 */
        private const val SDK_PORT_RELEASE_SETTLE_MS = 300L
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
        doConnect(skipSdkInit = false)
    }

    /**
     * Shared connection body for [connect] and [recoverConnection].
     *
     * @param skipSdkInit when true, the [ensureSdkInitializedForLocalModes] step is
     * skipped entirely. This is used by [recoverConnection]: during a reconnect for an
     * in-flight transaction the SDK has necessarily already been initialized, so a
     * second init would hit the SDK's "already initialized -> tear down + clear all
     * callbacks" path and drop the in-flight traceId -> PaymentCallback mappings. All
     * other logic (generation bump, mode read, IP validation, config creation,
     * paymentService.connect + ConnectionListener registration) is identical to a
     * normal connect.
     */
    private fun doConnect(skipSdkInit: Boolean) {
        scope.launch {
            try {
                Log.d(TAG, "Starting connection... (skipSdkInit=$skipSdkInit)")
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

                // Local SDK modes must have valid SDK credentials and a completed SDK init.
                // This prevents late E201 ("SDK not initialized") errors during connect().
                //
                // Skipped on recovery: re-initializing an already-initialized SDK tears
                // down previous callbacks, which would orphan an in-flight transaction's
                // traceId -> PaymentCallback mapping and cause Host push events to miss.
                if (!skipSdkInit && !ensureSdkInitializedForLocalModes()) {
                    return@launch
                }

                // LAN mode requires a configured IP. If it is missing (e.g. first launch
                // before the user runs auto-discovery), stay disconnected quietly instead
                // of surfacing a "LAN IP address not configured" error.
                if (mode == ConnectionMode.LAN) {
                    val (lanIp, _) = ConnectionPreferences.getLanConfig(context)
                    if (lanIp.isNullOrBlank()) {
                        Log.d(TAG, "LAN mode selected but no IP configured; skipping auto-connect")
                        _connectionState.value = ConnectionState.Disconnected
                        currentMode = null
                        return@launch
                    }
                }

                // CROSS_DEVICE LAN strategy needs a configured host, same rationale as classic LAN.
                if (mode == ConnectionMode.CROSS_DEVICE) {
                    val strategy = ConnectionPreferences.getCrossDeviceStrategy(context)
                    if (strategy == ConnectionPreferences.CrossDeviceStrategy.LAN) {
                        val (crossDeviceIp, _) = ConnectionPreferences.getCrossDeviceLanConfig(context)
                        if (crossDeviceIp.isNullOrBlank()) {
                            Log.d(TAG, "CROSS_DEVICE mode ($strategy) selected but no LAN host configured; skipping auto-connect")
                            _connectionState.value = ConnectionState.Disconnected
                            currentMode = null
                            return@launch
                        }
                    }
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
                        // 仅在 Settings 开启副屏时请求播放器就绪。
                        if (ConnectionPreferences.isSecondaryDisplayAdEnabled(context)) {
                            paymentService.ensureUsbScreenPlayerReady()
                        }
                        _connectionState.value = ConnectionState.Connected(
                            mode = mode,
                            deviceId = deviceId,
                            version = taproVersion
                        )
                    }
                    
                    override fun onDisconnected(reason: String) {
                        // 终态回调（断开/恢复失败 E213）始终投递给 UI，不按 generation 丢弃。
                        // 断开是「这条连接确实结束了」的终态通知，对用户永远有效；
                        // 真实 POS 应用必须处理它，否则会回到「连上又掉却无任何回调」的问题。
                        Log.d(TAG, "Disconnected - Reason: $reason")
                        _connectionState.value = ConnectionState.Disconnected
                        currentMode = null
                    }
                    
                    override fun onError(code: String, message: String) {
                        // 连接错误终态始终投递给 UI，不按 generation 丢弃（与 onDisconnected 同理，贴近真实 POS 应用）。
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
     * Discover LAN Taplink services via mDNS WITHOUT connecting.
     *
     * Ensures the SDK is initialized (required for discovery), then delegates to the
     * SDK's discover-only API and returns the resolved services. The caller decides
     * which service to use (typically auto-fill the address and then [connect]).
     *
     * @return List of discovered services (empty on failure or when none are found)
     */
    suspend fun discoverLanServices(): List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService> {
        if (!ensureSdkInitializedForLocalModes()) {
            return emptyList()
        }
        val paymentService =
            com.sunmi.tapro.taplink.demo.di.DependencyProvider.taplinkPaymentService
        return suspendCancellableCoroutine { cont ->
            try {
                paymentService.discoverLanServices(
                    onResult = { services ->
                        if (cont.isActive) cont.resume(services)
                    },
                    onError = { code, message ->
                        Log.w(TAG, "Discovery error [$code]: $message")
                        if (cont.isActive) cont.resume(emptyList())
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Discovery failed with exception", e)
                if (cont.isActive) cont.resume(emptyList())
            }
        }
    }

    /**
     * Result of a scan-only QR request.
     */
    sealed class ScanResult {
        data class Success(
            val services: List<com.sunmi.tapro.taplink.sdk.model.DiscoveredService>
        ) : ScanResult()

        data class Failure(val code: String, val message: String) : ScanResult()
    }

    /**
     * Launch the SDK QR scanner and return the scanned LAN host/port WITHOUT
     * connecting.
     *
     * Ensures the SDK is initialized, then delegates to the SDK's scan-only API. The
     * caller decides what to do with the result (typically auto-fill the address and
     * then [connect]).
     *
     * @return [ScanResult.Success] with the scanned service(s), or [ScanResult.Failure]
     *   carrying the SDK error code/message (e.g. no camera, permission denied, cancelled).
     */
    suspend fun scanLanQrCode(): ScanResult {
        if (!ensureSdkInitializedForLocalModes()) {
            return ScanResult.Failure("SDK_NOT_INITIALIZED", "SDK is not initialized")
        }
        val paymentService =
            com.sunmi.tapro.taplink.demo.di.DependencyProvider.taplinkPaymentService
        return suspendCancellableCoroutine { cont ->
            try {
                paymentService.scanLanQrCode(
                    onResult = { services ->
                        if (cont.isActive) cont.resume(ScanResult.Success(services))
                    },
                    onError = { code, message ->
                        Log.w(TAG, "QR scan error [$code]: $message")
                        if (cont.isActive) cont.resume(ScanResult.Failure(code, message))
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "QR scan failed with exception", e)
                if (cont.isActive) {
                    cont.resume(ScanResult.Failure("SCAN_EXCEPTION", e.message ?: "Unknown error"))
                }
            }
        }
    }

    /**
     * Launch the SDK QR scanner and connect using the scanned LAN Taplink QR code.
     *
     * Requires valid SDK credentials and a completed SDK init (same as other local
     * modes). Updates connection state through StateFlow using the generation guard
     * to ignore stale callbacks.
     */
    fun scanAndConnect() {
        scope.launch {
            try {
                Log.d(TAG, "Starting QR scan connect...")
                _connectionState.value = ConnectionState.Connecting
                val generation = ++connectionGeneration

                if (!ensureSdkInitializedForLocalModes()) {
                    return@launch
                }

                val paymentService =
                    com.sunmi.tapro.taplink.demo.di.DependencyProvider.taplinkPaymentService
                paymentService.scanAndConnect(
                    buildLanConnectionListener(generation, "QR scan connect")
                )
            } catch (e: Exception) {
                Log.e(TAG, "QR scan connect failed with exception", e)
                _connectionState.value = ConnectionState.Error(
                    code = "EXCEPTION",
                    message = e.message ?: "Unknown error"
                )
            }
        }
    }

    /**
     * Build a ConnectionListener for LAN auto-discovery / QR scan connect that
     * updates connection state while guarding against stale callbacks.
     */
    private fun buildLanConnectionListener(
        generation: Int,
        source: String
    ): ConnectionListener = object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            if (generation != connectionGeneration) {
                Log.d(TAG, "Ignoring stale onConnected from $source (gen $generation, current $connectionGeneration)")
                return
            }
            Log.d(TAG, "Connected via $source - Device: $deviceId, Version: $taproVersion")
            // 对扫码连接路径保持一致，但遵循 Settings 中的副屏开关。
            if (ConnectionPreferences.isSecondaryDisplayAdEnabled(context)) {
                com.sunmi.tapro.taplink.demo.di.DependencyProvider.paymentService.ensureUsbScreenPlayerReady()
            }
            currentMode = ConnectionMode.LAN
            _connectionState.value = ConnectionState.Connected(
                mode = ConnectionMode.LAN,
                deviceId = deviceId,
                version = taproVersion
            )
        }

        override fun onDisconnected(reason: String) {
            if (generation != connectionGeneration) {
                Log.d(TAG, "Ignoring stale onDisconnected from $source (gen $generation, current $connectionGeneration) - Reason: $reason")
                return
            }
            Log.d(TAG, "Disconnected via $source - Reason: $reason")
            _connectionState.value = ConnectionState.Disconnected
            currentMode = null
        }

        override fun onError(code: String, message: String) {
            if (generation != connectionGeneration) {
                Log.d(TAG, "Ignoring stale onError from $source (gen $generation, current $connectionGeneration) - Code: $code")
                return
            }
            Log.e(TAG, "Connection error via $source - Code: $code, Message: $message")
            _connectionState.value = ConnectionState.Error(
                code = code,
                message = message
            )
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
            paymentService.setTerminalEventNotifyUrl(cloudConfig.terminalEventNotifyUrl)
            paymentService.setPushToTerminal(cloudConfig.pushToTerminal)
            val printReceipt = ConnectionPreferences.getPrintReceipt(context)
            paymentService.setPrintReceipt(printReceipt.name)
            val tipConfig = TipConfigPreferences.getConfig(context)
            paymentService.setTipModeFieldEnabled(tipConfig.includeTipMode)

            // Report connected — CloudHttpClient will be initialized lazily on first transaction
            Log.d(TAG, "Cloud connected (config validated): terminalSn=${cloudConfig.terminalSn}, appId=$appId")
            _connectionState.value = ConnectionState.Connected(
                mode = ConnectionMode.CLOUD,
                deviceId = cloudConfig.terminalSn.ifBlank { "Cloud" },
                version = "Cloud"
            )
            // Cloud 模式也保持同一调用点，具体实现由 CloudPaymentService 忽略。
            if (ConnectionPreferences.isSecondaryDisplayAdEnabled(context)) {
                paymentService.ensureUsbScreenPlayerReady()
            }
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
                        // Previous was a Taplink SDK mode (CABLE/LAN/APP_TO_APP).
                        // Must explicitly disconnect so the SDK releases its internal
                        // session (especially the VSP serial port) before we try to
                        // reconnect. Without this, TaplinkSDK.connect() on an already-open
                        // VSP port never fires onConnected, causing a 10-second timeout.
                        com.sunmi.tapro.taplink.demo.di.DependencyProvider.taplinkPaymentService.disconnect()
                        awaitSdkDisconnected()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error disconnecting previous mode ($previousMode)", e)
                }
            }

            _connectionState.value = ConnectionState.Disconnected
            currentMode = null

            connect()
        }
    }

    /**
     * Recover a dropped connection WITHOUT calling disconnect().
     *
     * Use this when the connection has already dropped (state DISCONNECTED/Error)
     * while an in-flight transaction is still waiting for Host push events. Unlike
     * [reconnect], this does NOT call any disconnect path:
     * - It skips [PaymentService.disconnect] / TaplinkPaymentService.disconnect(),
     *   so the SDK's TaplinkApiImpl.disconnect() -> clearAllCallbacks() is never
     *   triggered.
     * - The in-flight traceId -> PaymentCallback mappings therefore survive, so when
     *   the transport link is rebuilt the Host-pushed events still match their
     *   original callback (via getAndRemoveCallbackByTraceId) and the Demo UI keeps
     *   updating.
     *
     * It simply rebuilds the transport connection through the shared [doConnect]
     * body (with skipSdkInit = true) using the last saved connection config. Because
     * the link is already DISCONNECTED here (not an open VSP/serial session),
     * connecting directly is safe and does not need the disconnect +
     * awaitSdkDisconnected() dance that [reconnect] performs for VSP port release.
     *
     * The generation counter advances inside [doConnect] as usual; that only guards
     * ConnectionListener (connection-state) callbacks and does not affect the
     * transaction callbacks routed through TransactionRepository.
     */
    fun recoverConnection() {
        Log.d(TAG, "Recovering connection without disconnect (preserving in-flight callbacks)...")
        // Skip SDK init on recovery: the SDK is already initialized (there is an
        // in-flight transaction), and re-initializing would tear down and clear all
        // registered callbacks, orphaning the in-flight traceId -> PaymentCallback
        // mapping so the Host-pushed events would miss their callback.
        doConnect(skipSdkInit = true)
    }

    /**
     * Wait until the Taplink SDK reports [ConnectionStatus.DISCONNECTED].
     *
     * SDK 的 disconnect() 同步返回，但底层资源（VSP 串口、接收协程）是在状态翻转前后
     * 陆续释放的。在状态仍为 CONNECTED/CONNECTING 时再次调用 connect()，SDK 会把
     * listener 挂到队列上或直接复用旧连接，导致 onConnected 永不触发。
     *
     * 因此这里轮询真实状态而不是固定 delay；额外留一小段时间让 USB 句柄彻底释放。
     */
    private suspend fun awaitSdkDisconnected(
        timeoutMs: Long = SDK_DISCONNECT_TIMEOUT_MS
    ) {
        val reachedDisconnected = kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            while (TaplinkSDK.getConnectionStatus() != ConnectionStatus.DISCONNECTED) {
                kotlinx.coroutines.delay(SDK_DISCONNECT_POLL_INTERVAL_MS)
            }
            true
        } != null

        if (!reachedDisconnected) {
            Log.w(
                TAG,
                "SDK did not reach DISCONNECTED within ${timeoutMs}ms " +
                    "(status=${TaplinkSDK.getConnectionStatus()}), continuing anyway"
            )
        }

        // USB/VSP 设备句柄关闭后，系统驱动需要短暂时间才允许重新 open。
        kotlinx.coroutines.delay(SDK_PORT_RELEASE_SETTLE_MS)
    }
    
    /**
     * Get connection mode from preferences
     */
    private fun getConnectionModeFromPreferences(): ConnectionMode {
        val prefMode = ConnectionPreferences.getConnectionMode(context)
        return when (prefMode) {
            ConnectionPreferences.ConnectionMode.ON_DEVICE -> ConnectionMode.ON_DEVICE
            ConnectionPreferences.ConnectionMode.CROSS_DEVICE -> ConnectionMode.CROSS_DEVICE
            ConnectionPreferences.ConnectionMode.APP_TO_APP -> ConnectionMode.APP_TO_APP
            ConnectionPreferences.ConnectionMode.CABLE -> ConnectionMode.CABLE
            ConnectionPreferences.ConnectionMode.LAN -> ConnectionMode.LAN
            ConnectionPreferences.ConnectionMode.CLOUD -> ConnectionMode.CLOUD
            ConnectionPreferences.ConnectionMode.SUB_SCREEN -> ConnectionMode.SUB_SCREEN
        }
    }
    
    /**
     * Create connection config based on mode
     */
    private fun createConnectionConfig(mode: ConnectionMode): ConnectionConfig {
        return when (mode) {
            ConnectionMode.ON_DEVICE -> {
                val appToAppMode = when (ConnectionPreferences.getAppToAppMode(context)) {
                    ConnectionPreferences.AppToAppMode.CUSTOM -> AppToAppMode.CUSTOM
                    ConnectionPreferences.AppToAppMode.HEADLESS -> AppToAppMode.HEADLESS
                }
                Log.d(TAG, "Creating On-Device connection config, mode=$appToAppMode")
                ConnectionConfig.createOnDeviceMode(appToAppMode)
            }
            ConnectionMode.CROSS_DEVICE -> {
                val strategy = ConnectionPreferences.getCrossDeviceStrategy(context)
                val sdkStrategy = when (strategy) {
                    ConnectionPreferences.CrossDeviceStrategy.LAN -> CrossDeviceStrategy.LAN
                    ConnectionPreferences.CrossDeviceStrategy.CABLE -> CrossDeviceStrategy.CABLE
                }
                val (host, port) = ConnectionPreferences.getCrossDeviceLanConfig(context)
                val cableProtocol = when (ConnectionPreferences.getCrossDeviceCableProtocol(context)) {
                    ConnectionPreferences.CableProtocol.USB_AOA ->
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_AOA
                    ConnectionPreferences.CableProtocol.USB_VSP ->
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.USB_VSP
                    ConnectionPreferences.CableProtocol.RS232 ->
                        com.sunmi.tapro.taplink.sdk.enums.CableProtocol.RS232
                }
                val autoReconnect = sdkStrategy == CrossDeviceStrategy.LAN &&
                    ConnectionPreferences.isLanAutoReconnectEnabled(context)
                Log.d(
                    TAG,
                    "Creating Cross-Device connection config, strategy=$sdkStrategy, " +
                        "host=$host, port=$port, autoReconnect=$autoReconnect"
                )
                ConnectionConfig.createCrossDeviceMode(
                    strategy = sdkStrategy,
                    host = host,
                    port = port,
                    cableProtocol = cableProtocol
                ).setAutoReconnect(autoReconnect)
            }
            ConnectionMode.APP_TO_APP -> {
                val appToAppMode = when (ConnectionPreferences.getAppToAppMode(context)) {
                    ConnectionPreferences.AppToAppMode.CUSTOM -> AppToAppMode.CUSTOM
                    ConnectionPreferences.AppToAppMode.HEADLESS -> AppToAppMode.HEADLESS
                }
                Log.d(TAG, "Creating App-to-App connection config, mode=$appToAppMode")
                ConnectionConfig.createAppMode(appToAppMode)
            }
            ConnectionMode.CABLE -> {
                Log.d(TAG, "Creating Cable connection config")
                val protocol = ConnectionPreferences.getCableProtocol(context)
                val sdkProtocol = when (protocol) {
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
                val (ip, port) = ConnectionPreferences.getLanConfig(context)
                if (ip.isNullOrBlank()) {
                    throw IllegalStateException("LAN IP address not configured")
                }
                val autoReconnect =
                    ConnectionPreferences.isLanAutoReconnectEnabled(context)
                Log.d(
                    TAG,
                    "Creating LAN connection config, host=$ip, port=$port, " +
                        "autoReconnect=$autoReconnect"
                )
                ConnectionConfig.createLanMode(ip, port)
                    .setAutoReconnect(autoReconnect)
            }
            ConnectionMode.CLOUD -> {
                // Cloud mode uses its own initialization via CloudPaymentService.initialize()
                // Return a placeholder App mode config since ConnectionConfig is not used for Cloud
                Log.d(TAG, "Creating Cloud connection config (placeholder)")
                ConnectionConfig.createAppMode()
            }
            ConnectionMode.SUB_SCREEN -> {
                Log.d(TAG, "Creating Sub-Screen connection config")
                ConnectionConfig.createSubScreenMode()
            }
        }
    }

    /**
     * Ensures Taplink SDK is initialized with valid credentials for local modes.
     *
     * Validation is executed before each non-cloud connection attempt so that
     * users receive an actionable error message (missing AppId/SecretKey) instead
     * of a delayed generic SDK error code during low-level connect.
     */
    private fun ensureSdkInitializedForLocalModes(): Boolean {
        val sdkConfig = TaplinkSdkPreferences.getConfig(context, TaplinkSdkInitializer.DEFAULT_CONFIG)
        if (sdkConfig.appId.isBlank() || sdkConfig.secretKey.isBlank()) {
            Log.e(
                TAG,
                "SDK credential missing: appIdBlank=${sdkConfig.appId.isBlank()}, " +
                    "secretKeyBlank=${sdkConfig.secretKey.isBlank()}"
            )
            _connectionState.value = ConnectionState.Error(
                code = "SDK_CONFIG_ERROR",
                message = "SDK credentials incomplete. Please set App ID and Secret Key in Settings."
            )
            return false
        }

        val initOk = TaplinkSdkInitializer.initFromPreferences(context)
        if (!initOk) {
            Log.e(TAG, "Taplink SDK initialization failed before connect")
            _connectionState.value = ConnectionState.Error(
                code = "SDK_INIT_ERROR",
                message = "SDK initialization failed. Please check SDK parameters in Settings."
            )
            return false
        }

        Log.d(TAG, "Taplink SDK initialized successfully for local connection")
        return true
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
