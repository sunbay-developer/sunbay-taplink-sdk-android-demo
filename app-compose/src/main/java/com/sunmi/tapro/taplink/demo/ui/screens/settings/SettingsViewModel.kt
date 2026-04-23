package com.sunmi.tapro.taplink.demo.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.service.ConnectionManager
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.util.CloudPreferences
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.EnvironmentDefaults
import com.sunmi.tapro.taplink.demo.util.PrintReceiptMapping
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkInitializer
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkPreferences
import com.sunmi.tapro.taplink.demo.util.TipConfigPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Settings Screen ViewModel
 * 
 * Implements MVI (Model-View-Intent) architecture pattern for the Settings Screen.
 * Manages connection configuration, validates input, and handles connection testing.
 * 
 * Key Responsibilities:
 * - Connection mode selection
 * - Configuration input management
 * - Input validation (IP address, port)
 * - Settings persistence to SharedPreferences
 * - Connection testing
 * - Error handling with Message model
 * 
 * MVI Pattern:
 * - Intent: User actions (SettingsIntent sealed class)
 * - Model: UI state (SettingsState data class)
 * - View: Composable UI that observes state
 * 
 * State Management:
 * - Immutable state updates using copy()
 * - Single source of truth (StateFlow)
 * - Unidirectional data flow
 */
class SettingsViewModel(
    application: Application,
    private val connectionManager: ConnectionManager = DependencyProvider.connectionManager
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "SettingsViewModel"
        
        // Default values
        private const val DEFAULT_IP_ADDRESS = ""
        private const val DEFAULT_PORT = "8080"
    }
    
    // MVI State
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()
    
    init {
        loadSettings()
    }
    
    /**
     * Handle user intents
     * 
     * Central intent handler following MVI pattern.
     * All user actions are routed through this method.
     * 
     * @param intent User intent to handle
     */
    fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SelectMode -> selectMode(intent.mode)
            is SettingsIntent.UpdateCableProtocol -> updateCableProtocol(intent.protocol)
            is SettingsIntent.UpdateIpAddress -> updateIpAddress(intent.ipAddress)
            is SettingsIntent.UpdatePort -> updatePort(intent.port)
            is SettingsIntent.UpdatePrintReceipt -> updatePrintReceipt(intent.printReceipt)
            is SettingsIntent.UpdateSdkAppId -> updateSdkAppId(intent.appId)
            is SettingsIntent.UpdateSdkMerchantId -> updateSdkMerchantId(intent.merchantId)
            is SettingsIntent.UpdateSdkSecretKey -> updateSdkSecretKey(intent.secretKey)
            is SettingsIntent.SwitchSdkEnvironment -> switchSdkEnvironment(intent.environment)
            is SettingsIntent.ApplySdkConfig -> applySdkConfig()
            is SettingsIntent.UpdateCloudApiKey -> updateCloudApiKey(intent.apiKey)
            is SettingsIntent.UpdateCloudBaseUrl -> updateCloudBaseUrl(intent.baseUrl)
            is SettingsIntent.UpdateCloudTerminalSn -> updateCloudTerminalSn(intent.terminalSn)
            is SettingsIntent.UpdateCloudMerchantId -> updateCloudMerchantId(intent.merchantId)
            is SettingsIntent.UpdateCloudAppId -> updateCloudAppId(intent.appId)
            is SettingsIntent.UpdateCloudNotifyUrl -> updateCloudNotifyUrl(intent.notifyUrl)
            is SettingsIntent.UpdateCloudPushToTerminal -> updateCloudPushToTerminal(intent.enabled)
            is SettingsIntent.AddCloudOption -> addCloudOption(intent.field, intent.value)
            is SettingsIntent.UpdateTipConfigEnabled -> updateTipConfigEnabled(intent.enabled)
            is SettingsIntent.UpdateTipOnScreenTip -> updateTipOnScreenTip(intent.enabled)
            is SettingsIntent.UpdateTipMode -> updateTipMode(intent.mode)
            is SettingsIntent.UpdateTipWithTax -> updateTipWithTax(intent.enabled)
            is SettingsIntent.UpdateTipSuggestionsEnabled -> updateTipSuggestionsEnabled(intent.enabled)
            is SettingsIntent.UpdateTipFeeMode -> updateTipFeeMode(intent.mode)
            is SettingsIntent.UpdateTipSuggestionValue -> updateTipSuggestionValue(intent.index, intent.value)
            is SettingsIntent.SaveTipConfig -> saveTipConfig()
            is SettingsIntent.TestConnection -> testConnection()
            is SettingsIntent.ExitApplication -> exitApplication()
            is SettingsIntent.DismissMessage -> dismissMessage()
            is SettingsIntent.NavigationConsumed -> navigationConsumed()
        }
    }
    
    /**
     * Load settings from SharedPreferences
     * 
     * Loads saved connection configuration and populates the state.
     * Called during initialization.
     */
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                
                // Load connection mode
                val savedMode = ConnectionPreferences.getConnectionMode(context)
                val mode = when (savedMode) {
                    ConnectionPreferences.ConnectionMode.APP_TO_APP -> ConnectionMode.APP_TO_APP
                    ConnectionPreferences.ConnectionMode.CABLE -> ConnectionMode.CABLE
                    ConnectionPreferences.ConnectionMode.LAN -> ConnectionMode.LAN
                    ConnectionPreferences.ConnectionMode.CLOUD -> ConnectionMode.CLOUD
                }
                
                // Load Cable protocol
                val savedCableProtocol = ConnectionPreferences.getCableProtocol(context)
                
                // Load LAN configuration
                val (savedIp, savedPort) = ConnectionPreferences.getLanConfig(context)
                
                // Load print receipt configuration
                val savedPrintReceipt = ConnectionPreferences.getPrintReceipt(context)
                // Sync to payment service (from :service module)
                TaplinkPaymentService.getInstance().setPrintReceipt(PrintReceiptMapping.toSdk(savedPrintReceipt))
                // Sync to cloud payment service (uses string name directly)
                com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService.setPrintReceipt(savedPrintReceipt.name)

                // Load SDK credentials (TaplinkSDK.init params)
                val sdkConfig = TaplinkSdkPreferences.getConfig(context, TaplinkSdkInitializer.DEFAULT_CONFIG)

                // Load Cloud configuration
                val cloudConfig = CloudPreferences.getConfig(context)
                
                // Sync notifyUrl and pushToTerminal to cloud payment service
                com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService.setNotifyUrl(cloudConfig.notifyUrl)
                com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService.setPushToTerminal(cloudConfig.pushToTerminal)

                // Load dropdown options for Cloud fields
                val apiKeyOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.API_KEY)
                val baseUrlOptions = CloudPreferences.getBaseUrlOptions(context)
                val terminalSnOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.TERMINAL_SN)
                val merchantIdOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.MERCHANT_ID)
                val appIdOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.APP_ID)
                val notifyUrlOptions = CloudPreferences.getNotifyUrlOptions(context)

                // Load Tip Configuration
                val tipConfig = TipConfigPreferences.getConfig(context)
                
                // Update state with loaded values
                // For security: don't show existing secret key, only track its existence
                val sdkEnvironment = when {
                    sdkConfig.appId == EnvironmentDefaults.Uat.SDK_APP_ID &&
                        sdkConfig.merchantId == EnvironmentDefaults.Uat.SDK_MERCHANT_ID -> SdkEnvironment.UAT
                    sdkConfig.appId == EnvironmentDefaults.Prod.SDK_APP_ID &&
                        sdkConfig.merchantId == EnvironmentDefaults.Prod.SDK_MERCHANT_ID -> SdkEnvironment.PROD
                    else -> SdkEnvironment.UAT
                }
                _state.update {
                    it.copy(
                        selectedMode = mode,
                        cableProtocol = savedCableProtocol,
                        ipAddress = savedIp ?: DEFAULT_IP_ADDRESS,
                        port = savedPort.toString(),
                        printReceipt = savedPrintReceipt,
                        cloudApiKey = cloudConfig.apiKey,
                        cloudBaseUrl = cloudConfig.baseUrl,
                        cloudTerminalSn = cloudConfig.terminalSn,
                        cloudMerchantId = cloudConfig.merchantId,
                        cloudAppId = cloudConfig.appId,
                        cloudNotifyUrl = cloudConfig.notifyUrl,
                        cloudPushToTerminal = cloudConfig.pushToTerminal,
                        cloudApiKeyOptions = apiKeyOptions,
                        cloudBaseUrlOptions = baseUrlOptions,
                        cloudTerminalSnOptions = terminalSnOptions,
                        cloudMerchantIdOptions = merchantIdOptions,
                        cloudAppIdOptions = appIdOptions,
                        cloudNotifyUrlOptions = notifyUrlOptions,
                        sdkEnvironment = sdkEnvironment,
                        sdkAppId = sdkConfig.appId,
                        sdkMerchantId = sdkConfig.merchantId,
                        sdkSecretKey = "", // Don't load existing secret key for security
                        sdkSecretKeyChanged = false,
                        hasExistingSecretKey = sdkConfig.secretKey.isNotBlank(), // Track if secret key exists
                        tipConfigEnabled = tipConfig.enabled,
                        tipOnScreenTip = tipConfig.onScreenTip,
                        tipMode = tipConfig.tipMode,
                        tipWithTax = tipConfig.tipWithTax,
                        tipSuggestionsEnabled = tipConfig.suggestionsEnabled,
                        tipFeeMode = tipConfig.feeMode,
                        tipSuggestionValue1 = tipConfig.suggestionValue1,
                        tipSuggestionValue2 = tipConfig.suggestionValue2,
                        tipSuggestionValue3 = tipConfig.suggestionValue3,
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load settings", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Load Error",
                            content = "Failed to load settings: ${e.message}",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    // Intent Handlers
    
    /**
     * Select connection mode
     * 
     * Updates the selected connection mode in the state.
     * 
     * @param mode Connection mode to select
     */
    private fun selectMode(mode: ConnectionMode) {
        _state.update { it.copy(selectedMode = mode) }
    }
    
    /**
     * Update Cable connection mode (protocol) for Cable mode
     * 
     * @param protocol SDK Cable protocol: AUTO, USB_AOA, USB_VSP, RS232
     */
    private fun updateCableProtocol(protocol: ConnectionPreferences.CableProtocol) {
        _state.update { it.copy(cableProtocol = protocol) }
    }
    
    /**
     * Update IP address for LAN mode
     * 
     * @param ipAddress IP address of payment terminal
     */
    private fun updateIpAddress(ipAddress: String) {
        _state.update { it.copy(ipAddress = ipAddress) }
    }
    
    /**
     * Update port for LAN mode
     * 
     * @param port Port number for LAN connection
     */
    private fun updatePort(port: String) {
        _state.update { it.copy(port = port) }
    }
    
    /**
     * Update print receipt option
     * 
     * @param printReceipt Print receipt option
     */
    private fun updatePrintReceipt(printReceipt: PrintReceipt) {
        viewModelScope.launch {
            _state.update { it.copy(printReceipt = printReceipt) }
            val context = getApplication<Application>()
            ConnectionPreferences.savePrintReceipt(context, printReceipt)
            // Sync to payment service (from :service module)
            TaplinkPaymentService.getInstance().setPrintReceipt(PrintReceiptMapping.toSdk(printReceipt))
            // Sync to cloud payment service (uses string name directly)
            com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService.setPrintReceipt(printReceipt.name)
        }
    }

    private fun updateSdkAppId(appId: String) {
        _state.update { it.copy(sdkAppId = appId) }
    }

    private fun updateSdkMerchantId(merchantId: String) {
        _state.update { it.copy(sdkMerchantId = merchantId) }
    }

    private fun updateSdkSecretKey(secretKey: String) {
        _state.update { 
            it.copy(
                sdkSecretKey = secretKey,
                sdkSecretKeyChanged = true // Mark that user has entered a new secret key
            ) 
        }
    }

    private fun switchSdkEnvironment(environment: SdkEnvironment) {
        val appId = if (environment == SdkEnvironment.UAT) {
            EnvironmentDefaults.Uat.SDK_APP_ID
        } else {
            EnvironmentDefaults.Prod.SDK_APP_ID
        }
        val merchantId = if (environment == SdkEnvironment.UAT) {
            EnvironmentDefaults.Uat.SDK_MERCHANT_ID
        } else {
            EnvironmentDefaults.Prod.SDK_MERCHANT_ID
        }
        val secret = if (environment == SdkEnvironment.UAT) {
            EnvironmentDefaults.Uat.SDK_AUTH_KEY
        } else {
            EnvironmentDefaults.Prod.SDK_AUTH_KEY
        }
        val cloudAppId = if (environment == SdkEnvironment.UAT) {
            EnvironmentDefaults.Uat.CLOUD_APP_ID
        } else {
            EnvironmentDefaults.Prod.CLOUD_APP_ID
        }
        val cloudApiKey = if (environment == SdkEnvironment.UAT) {
            EnvironmentDefaults.Uat.CLOUD_API_KEY
        } else {
            EnvironmentDefaults.Prod.CLOUD_API_KEY
        }
        val cloudBaseUrl = if (environment == SdkEnvironment.UAT) {
            EnvironmentDefaults.Uat.CLOUD_BASE_URL
        } else {
            EnvironmentDefaults.Prod.CLOUD_BASE_URL
        }
        _state.update {
            it.copy(
                sdkEnvironment = environment,
                sdkAppId = appId,
                sdkMerchantId = merchantId,
                sdkSecretKey = secret,
                sdkSecretKeyChanged = true,
                cloudAppId = cloudAppId,
                cloudApiKey = cloudApiKey,
                cloudBaseUrl = cloudBaseUrl
            )
        }

        // Persist cloud parameters immediately even when current mode is not CLOUD.
        // This ensures SDK environment switching keeps Cloud config in sync.
        viewModelScope.launch {
            val context = getApplication<Application>()
            val current = _state.value
            CloudPreferences.saveConfig(
                context,
                CloudPreferences.CloudConfig(
                    apiKey = current.cloudApiKey.trim(),
                    baseUrl = current.cloudBaseUrl.trim(),
                    terminalSn = current.cloudTerminalSn.trim(),
                    merchantId = current.cloudMerchantId.trim(),
                    appId = current.cloudAppId.trim(),
                    notifyUrl = current.cloudNotifyUrl.trim(),
                    pushToTerminal = current.cloudPushToTerminal
                )
            )
            com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService
                .setNotifyUrl(current.cloudNotifyUrl.trim())
            com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService
                .setPushToTerminal(current.cloudPushToTerminal)
        }
    }

    private fun updateCloudApiKey(apiKey: String) {
        _state.update { it.copy(cloudApiKey = apiKey) }
    }

    private fun updateCloudBaseUrl(baseUrl: String) {
        _state.update { it.copy(cloudBaseUrl = baseUrl) }
    }

    private fun updateCloudTerminalSn(terminalSn: String) {
        _state.update { it.copy(cloudTerminalSn = terminalSn) }
    }

    private fun updateCloudMerchantId(merchantId: String) {
        _state.update { it.copy(cloudMerchantId = merchantId) }
    }

    private fun updateCloudAppId(appId: String) {
        _state.update { it.copy(cloudAppId = appId) }
    }

    private fun updateCloudNotifyUrl(notifyUrl: String) {
        _state.update { it.copy(cloudNotifyUrl = notifyUrl) }
    }

    private fun updateCloudPushToTerminal(enabled: Boolean) {
        _state.update { it.copy(cloudPushToTerminal = enabled) }
    }

    /**
     * Add a custom value to a cloud field's dropdown option list and refresh options.
     * [field] is one of: "apiKey", "baseUrl", "terminalSn", "merchantId", "appId", "notifyUrl"
     */
    private fun addCloudOption(field: String, value: String) {
        if (value.isBlank()) return
        viewModelScope.launch {
            val context = getApplication<Application>()
            when (field) {
                "apiKey" -> {
                    CloudPreferences.addOption(context, CloudPreferences.CloudField.API_KEY, value)
                    _state.update { it.copy(
                        cloudApiKey = value,
                        cloudApiKeyOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.API_KEY)
                    ) }
                }
                "baseUrl" -> {
                    CloudPreferences.addBaseUrlOption(context, value)
                    _state.update { it.copy(
                        cloudBaseUrl = value,
                        cloudBaseUrlOptions = CloudPreferences.getBaseUrlOptions(context)
                    ) }
                }
                "terminalSn" -> {
                    CloudPreferences.addOption(context, CloudPreferences.CloudField.TERMINAL_SN, value)
                    _state.update { it.copy(
                        cloudTerminalSn = value,
                        cloudTerminalSnOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.TERMINAL_SN)
                    ) }
                }
                "merchantId" -> {
                    CloudPreferences.addOption(context, CloudPreferences.CloudField.MERCHANT_ID, value)
                    _state.update { it.copy(
                        cloudMerchantId = value,
                        cloudMerchantIdOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.MERCHANT_ID)
                    ) }
                }
                "appId" -> {
                    CloudPreferences.addOption(context, CloudPreferences.CloudField.APP_ID, value)
                    _state.update { it.copy(
                        cloudAppId = value,
                        cloudAppIdOptions = CloudPreferences.getFieldOptions(context, CloudPreferences.CloudField.APP_ID)
                    ) }
                }
                "notifyUrl" -> {
                    CloudPreferences.addNotifyUrlOption(context, value)
                    _state.update { it.copy(
                        cloudNotifyUrl = value,
                        cloudNotifyUrlOptions = CloudPreferences.getNotifyUrlOptions(context)
                    ) }
                }
            }
        }
    }

    // ── Tip Configuration handlers ──────────────────────────────────────────────

    /**
     * Persist current tip config state to SharedPreferences immediately.
     * Called after every tip config field change so the setting takes effect
     * in real-time without requiring a separate "Save" action.
     */
    private fun persistTipConfig() {
        val currentState = _state.value
        val config = TipConfigPreferences.TipConfigData(
            enabled = currentState.tipConfigEnabled,
            onScreenTip = currentState.tipOnScreenTip,
            tipMode = currentState.tipMode,
            tipWithTax = currentState.tipWithTax,
            suggestionsEnabled = currentState.tipSuggestionsEnabled,
            feeMode = currentState.tipFeeMode,
            suggestionValue1 = currentState.tipSuggestionValue1,
            suggestionValue2 = currentState.tipSuggestionValue2,
            suggestionValue3 = currentState.tipSuggestionValue3
        )
        val context = getApplication<Application>()
        TipConfigPreferences.saveConfig(context, config)
        android.util.Log.d(TAG, "TipConfig persisted: enabled=${config.enabled}, onScreenTip=${config.onScreenTip}, " +
                "tipMode=${config.tipMode}, tipWithTax=${config.tipWithTax}, " +
                "suggestions=${if (config.suggestionsEnabled) "${config.feeMode}:[${config.suggestionValue1},${config.suggestionValue2},${config.suggestionValue3}]" else "disabled"}")
    }

    private fun updateTipConfigEnabled(enabled: Boolean) {
        _state.update { it.copy(tipConfigEnabled = enabled) }
        persistTipConfig()
    }

    private fun updateTipOnScreenTip(enabled: Boolean) {
        _state.update { it.copy(tipOnScreenTip = enabled) }
        persistTipConfig()
    }

    private fun updateTipMode(mode: TipConfigPreferences.TipMode) {
        _state.update { it.copy(tipMode = mode) }
        persistTipConfig()
    }

    private fun updateTipWithTax(enabled: Boolean) {
        _state.update { it.copy(tipWithTax = enabled) }
        persistTipConfig()
    }

    private fun updateTipSuggestionsEnabled(enabled: Boolean) {
        _state.update { it.copy(tipSuggestionsEnabled = enabled) }
        persistTipConfig()
    }

    private fun updateTipFeeMode(mode: TipConfigPreferences.FeeMode) {
        _state.update { it.copy(tipFeeMode = mode) }
        persistTipConfig()
    }

    private fun updateTipSuggestionValue(index: Int, value: Int) {
        _state.update {
            when (index) {
                0 -> it.copy(tipSuggestionValue1 = value)
                1 -> it.copy(tipSuggestionValue2 = value)
                2 -> it.copy(tipSuggestionValue3 = value)
                else -> it
            }
        }
        persistTipConfig()
    }

    /**
     * Explicit save action for tip config.
     * Validates suggestion values and shows confirmation message.
     * All fields are already persisted on each change; this provides
     * user feedback and an extra validation gate for suggestion values.
     */
    private fun saveTipConfig() {
        viewModelScope.launch {
            val currentState = _state.value
            val config = TipConfigPreferences.TipConfigData(
                enabled = currentState.tipConfigEnabled,
                onScreenTip = currentState.tipOnScreenTip,
                tipMode = currentState.tipMode,
                tipWithTax = currentState.tipWithTax,
                suggestionsEnabled = currentState.tipSuggestionsEnabled,
                feeMode = currentState.tipFeeMode,
                suggestionValue1 = currentState.tipSuggestionValue1,
                suggestionValue2 = currentState.tipSuggestionValue2,
                suggestionValue3 = currentState.tipSuggestionValue3
            )

            if (currentState.tipSuggestionsEnabled && !config.areSuggestionsValid()) {
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Invalid Tip Suggestions",
                            content = "Suggestion values must be non-negative numbers.",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
                return@launch
            }

            val context = getApplication<Application>()
            TipConfigPreferences.saveConfig(context, config)
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.SUCCESS,
                        title = "Tip Config Saved",
                        content = "Tip configuration has been saved and will be applied to SALE and POST_AUTH transactions.",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
        }
    }

    /**
     * Save SDK config and re-initialize TaplinkSDK immediately.
     *
     * Also triggers a reconnect so subsequent operations use the updated credentials.
     */
    private fun applySdkConfig() {
        viewModelScope.launch {
            val currentState = _state.value
            val appId = currentState.sdkAppId.trim()
            val merchantId = currentState.sdkMerchantId.trim()
            
            // Determine which secret key to use:
            // - If user entered a new key, use it
            // - Otherwise, keep the existing key from storage
            val secretKey = if (currentState.sdkSecretKeyChanged) {
                currentState.sdkSecretKey.trim()
            } else {
                // Keep existing secret key from storage
                val existingConfig = TaplinkSdkPreferences.getConfig(getApplication(), TaplinkSdkInitializer.DEFAULT_CONFIG)
                existingConfig.secretKey
            }

            if (appId.isBlank() || merchantId.isBlank()) {
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Invalid SDK Configuration",
                            content = "App ID and Merchant ID are required.",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
                return@launch
            }

            _state.update { it.copy(isApplyingSdkConfig = true, message = null) }

            try {
                val context = getApplication<Application>()
                val config = TaplinkSdkPreferences.SdkConfig(
                    appId = appId,
                    merchantId = merchantId,
                    secretKey = secretKey
                )

                TaplinkSdkPreferences.saveConfig(context, config)

                val initOk = TaplinkSdkInitializer.init(context, config)
                if (initOk) {
                    // Reconnect so the connection uses the updated SDK config
                    connectionManager.reconnect()
                }

                _state.update {
                    it.copy(
                        isApplyingSdkConfig = false,
                        sdkSecretKey = "", // Clear the secret key field after saving
                        sdkSecretKeyChanged = false,
                        hasExistingSecretKey = secretKey.isNotBlank(),
                        message = Message(
                            type = if (initOk) MessageType.SUCCESS else MessageType.ERROR,
                            title = if (initOk) "SDK Updated" else "SDK Update Failed",
                            content = if (initOk) {
                                "SDK initialized with the updated parameters and connection is being refreshed."
                            } else {
                                "Failed to initialize SDK with updated parameters. Please verify values and try again."
                            },
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to apply SDK config", e)
                _state.update {
                    it.copy(
                        isApplyingSdkConfig = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "SDK Update Error",
                            content = "Failed to apply SDK config: ${e.message}",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Connect with current configuration.
     *
     * Behaviour differs by mode:
     *
     * - CLOUD: No persistent connection is needed. Cloud uses per-transaction HTTP calls.
     *   We simply save the configuration, disconnect any previous SDK connection, and
     *   initialise the CloudPaymentService — then navigate back immediately.
     *
     * - SDK modes (App-to-App, Cable, LAN): A real connection must be established.
     *   We kick off reconnect() and observe the connectionState Flow until we see a
     *   definitive Connected or Error result, with a 10-second timeout.
     */
    private fun testConnection() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isTesting = true, testResult = null, message = null) }

                val currentState = _state.value

                if (!validateConfiguration(currentState)) {
                    _state.update { it.copy(isTesting = false) }
                    return@launch
                }

                // Save settings (mode + mode-specific config) before connecting
                saveSettingsInternal()

                // ── Cloud mode ──────────────────────────────────────────────────────
                // Cloud uses per-transaction HTTP calls — no persistent connection needed.
                // switchToCloud() disconnects the old SDK service and initialises
                // CloudPaymentService without emitting a transient Disconnected state,
                // so the Main screen never shows a spurious "Disconnected" notification.
                if (currentState.selectedMode == ConnectionMode.CLOUD) {
                    connectionManager.switchToCloud()
                    _state.update {
                        it.copy(
                            isTesting = false,
                            testSuccess = true,
                            shouldNavigateBack = true
                        )
                    }
                    return@launch
                }

                // ── SDK modes (App-to-App, Cable, LAN) ─────────────────────────────
                // A persistent connection must be established. Observe the flow and
                // wait for the first Connected or Error state that follows the initial
                // Disconnected emitted by reconnect().
                connectionManager.reconnect()

                var seenDisconnected = false
                val finalState = withTimeoutOrNull(10_000L) {
                    connectionManager.connectionState.first { state ->
                        when {
                            state is ConnectionState.Disconnected -> {
                                seenDisconnected = true
                                false
                            }
                            state is ConnectionState.Connecting -> false
                            seenDisconnected && (state is ConnectionState.Connected || state is ConnectionState.Error) -> true
                            else -> false
                        }
                    }
                }

                when (finalState) {
                    is ConnectionState.Connected -> {
                        _state.update {
                            it.copy(
                                isTesting = false,
                                testSuccess = true,
                                shouldNavigateBack = true
                            )
                        }
                    }
                    is ConnectionState.Error -> {
                        _state.update {
                            it.copy(
                                isTesting = false,
                                testSuccess = false,
                                message = Message(
                                    type = MessageType.ERROR,
                                    title = "Connection Failed",
                                    content = finalState.message,
                                    actions = listOf(MessageAction.DISMISS)
                                )
                            )
                        }
                    }
                    null -> {
                        _state.update {
                            it.copy(
                                isTesting = false,
                                testSuccess = false,
                                message = Message(
                                    type = MessageType.WARNING,
                                    title = "Connection Timeout",
                                    content = "Connection attempt timed out. Please check your configuration and try again.",
                                    actions = listOf(MessageAction.DISMISS)
                                )
                            )
                        }
                    }
                    else -> {
                        _state.update { it.copy(isTesting = false) }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e(TAG, "Connection test failed", e)
                _state.update {
                    it.copy(
                        isTesting = false,
                        testSuccess = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Test Error",
                            content = "Failed to test connection: ${e.message}",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }

    /**
     * Dismiss current message
     */
    private fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /**
     * Reset navigation flag after navigation has been performed
     */
    private fun navigationConsumed() {
        _state.update { it.copy(shouldNavigateBack = false) }
    }
    
    /**
     * Exit application
     * 
     * Terminates the application process.
     */
    private fun exitApplication() {
        viewModelScope.launch {
            try {
                android.util.Log.d(TAG, "Exiting application")
                // Clean up resources if needed
                connectionManager.disconnect()
                
                // Exit the application
                android.os.Process.killProcess(android.os.Process.myPid())
                kotlin.system.exitProcess(0)
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error during application exit", e)
            }
        }
    }
    
    // Helper Methods
    
    /**
     * Validate configuration for current mode
     * 
     * Checks if the configuration is valid for the selected connection mode.
     * Shows error message if validation fails.
     * 
     * @param state Current state to validate
     * @return true if valid, false otherwise
     */
    private fun validateConfiguration(state: SettingsState): Boolean {
        when (state.selectedMode) {
            ConnectionMode.APP_TO_APP -> {
                // No additional configuration needed for App-to-App mode
                return true
            }
            ConnectionMode.CABLE -> {
                // Cable mode only requires protocol selection (no device path)
                return true
            }
            ConnectionMode.LAN -> {
                // Validate IP address
                if (!state.isValidIpAddress()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Invalid IP Address",
                                content = "Please enter a valid IP address (e.g., 192.168.1.100)",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return false
                }
                
                // Validate port
                if (state.port.isBlank()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Invalid Port",
                                content = "Port number is required for LAN mode",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return false
                }
                
                val portNum = state.port.toIntOrNull()
                if (portNum == null || portNum !in 1..65535) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Invalid Port",
                                content = "Port must be a number between 1 and 65535",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return false
                }
            }
            ConnectionMode.CLOUD -> {
                if (state.cloudApiKey.isBlank()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Invalid Configuration",
                                content = "API Key is required for Cloud mode",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return false
                }
                if (state.cloudBaseUrl.isBlank()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Invalid Configuration",
                                content = "Base URL is required for Cloud mode",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return false
                }
            }
        }
        
        return true
    }
    
    /**
     * Save settings internally without showing success message
     * 
     * Used by testConnection to save settings before testing.
     */
    private suspend fun saveSettingsInternal() {
        try {
            val currentState = _state.value
            val context = getApplication<Application>()
            
            // Save connection mode
            val prefMode = when (currentState.selectedMode) {
                ConnectionMode.APP_TO_APP -> ConnectionPreferences.ConnectionMode.APP_TO_APP
                ConnectionMode.CABLE -> ConnectionPreferences.ConnectionMode.CABLE
                ConnectionMode.LAN -> ConnectionPreferences.ConnectionMode.LAN
                ConnectionMode.CLOUD -> ConnectionPreferences.ConnectionMode.CLOUD
            }
            
            ConnectionPreferences.saveConnectionMode(context, prefMode)
            
            // Save mode-specific configuration
            when (currentState.selectedMode) {
                ConnectionMode.CABLE -> {
                    ConnectionPreferences.saveCableProtocol(context, currentState.cableProtocol)
                }
                ConnectionMode.LAN -> {
                    val port = currentState.port.toIntOrNull() ?: return
                    ConnectionPreferences.saveLanConfig(context, currentState.ipAddress, port)
                }
                ConnectionMode.CLOUD -> {
                    CloudPreferences.saveConfig(
                        context,
                        CloudPreferences.CloudConfig(
                            apiKey = currentState.cloudApiKey.trim(),
                            baseUrl = currentState.cloudBaseUrl.trim(),
                            terminalSn = currentState.cloudTerminalSn.trim(),
                            merchantId = currentState.cloudMerchantId.trim(),
                            appId = currentState.cloudAppId.trim(),
                            notifyUrl = currentState.cloudNotifyUrl.trim(),
                            pushToTerminal = currentState.cloudPushToTerminal
                        )
                    )
                    // Sync notifyUrl and pushToTerminal to cloud payment service
                    com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService.setNotifyUrl(currentState.cloudNotifyUrl.trim())
                    com.sunmi.tapro.taplink.demo.di.DependencyProvider.cloudPaymentService.setPushToTerminal(currentState.cloudPushToTerminal)
                }
                else -> { /* App-to-App: no extra config */ }
            }
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save settings internally", e)
        }
    }
}
