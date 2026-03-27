package com.sunmi.tapro.taplink.demo.ui.screens.settings

import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.util.CloudPreferences
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.EnvironmentDefaults

/**
 * Print receipt option enumeration
 */
enum class PrintReceipt {
    NONE,      // No receipt printing
    MERCHANT,  // Print merchant copy only
    CUSTOMER,  // Print customer copy only
    BOTH       // Print both copies
}

enum class SdkEnvironment {
    UAT,
    PROD
}

/**
 * Settings Screen State
 * 
 * Immutable data class representing the complete UI state of the Settings Screen.
 * Following MVI pattern for predictable state management.
 * 
 * All state changes create a new instance (copy) to ensure immutability.
 */
data class SettingsState(
    // Connection Mode
    val selectedMode: ConnectionMode = ConnectionMode.APP_TO_APP,
    
    // App-to-App Configuration
    val packageName: String = "",
    
    // Cable Configuration (SDK connection mode: AUTO, USB_AOA, USB_VSP, RS232)
    val cableProtocol: ConnectionPreferences.CableProtocol = ConnectionPreferences.CableProtocol.AUTO,
    
    // LAN Configuration
    val ipAddress: String = "",
    val port: String = "",
    
    // Print Receipt Configuration
    val printReceipt: PrintReceipt = PrintReceipt.NONE,

    // Cloud Configuration
    val cloudApiKey: String = EnvironmentDefaults.Prod.CLOUD_API_KEY,
    val cloudBaseUrl: String = EnvironmentDefaults.Prod.CLOUD_BASE_URL,
    val cloudTerminalSn: String = "",
    val cloudMerchantId: String = "",
    val cloudAppId: String = EnvironmentDefaults.Prod.CLOUD_APP_ID,
    val cloudNotifyUrl: String = "http://52.76.178.47:8880/api/notify",
    val cloudPushToTerminal: Boolean = true,

    // Cloud dropdown options (loaded from CloudPreferences)
    val cloudApiKeyOptions: List<CloudPreferences.LabeledOption> = emptyList(),
    val cloudBaseUrlOptions: List<CloudPreferences.LabeledOption> = emptyList(),
    val cloudTerminalSnOptions: List<CloudPreferences.LabeledOption> = emptyList(),
    val cloudMerchantIdOptions: List<CloudPreferences.LabeledOption> = emptyList(),
    val cloudAppIdOptions: List<CloudPreferences.LabeledOption> = emptyList(),
    val cloudNotifyUrlOptions: List<CloudPreferences.LabeledOption> = emptyList(),

    // SDK Init Configuration (TaplinkSDK.init)
    val sdkEnvironment: SdkEnvironment = SdkEnvironment.UAT,
    val sdkAppId: String = "",
    val sdkMerchantId: String = "",
    val sdkSecretKey: String = "",
    val sdkSecretKeyChanged: Boolean = false, // Track if user has entered a new secret key
    val hasExistingSecretKey: Boolean = false, // Track if there's a saved secret key
    val isApplyingSdkConfig: Boolean = false,
    
    // UI State
    val isLoading: Boolean = false,
    val isTesting: Boolean = false,
    
    // Connection Test Result
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    
    // Message display (errors, warnings, info, success)
    val message: Message? = null,
    
    // Navigation event: set to true when connection succeeds to navigate back
    val shouldNavigateBack: Boolean = false
) {
    /**
     * Check if current configuration is valid for the selected mode
     */
    fun isConfigurationValid(): Boolean {
        return when (selectedMode) {
            ConnectionMode.APP_TO_APP -> true // No additional configuration needed
            ConnectionMode.CABLE -> true // Cable mode only requires protocol selection
            ConnectionMode.LAN -> ipAddress.isNotBlank() && port.isNotBlank() && isValidPort()
            ConnectionMode.CLOUD -> cloudApiKey.isNotBlank() && cloudBaseUrl.isNotBlank() && cloudAppId.isNotBlank()
        }
    }
    
    /**
     * Validate port number is in valid range (1-65535)
     */
    private fun isValidPort(): Boolean {
        return try {
            val portNum = port.toInt()
            portNum in 1..65535
        } catch (e: NumberFormatException) {
            false
        }
    }
    
    /**
     * Check if IP address format is valid (basic validation)
     */
    fun isValidIpAddress(): Boolean {
        if (ipAddress.isBlank()) return false
        val parts = ipAddress.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            try {
                val num = part.toInt()
                num in 0..255
            } catch (e: NumberFormatException) {
                false
            }
        }
    }
    
    /**
     * Get configuration value for current mode
     */
    fun getCurrentConfiguration(): String {
        return when (selectedMode) {
            ConnectionMode.APP_TO_APP -> "Direct connection"
            ConnectionMode.CABLE -> cableProtocol.name
            ConnectionMode.LAN -> "$ipAddress:$port"
            ConnectionMode.CLOUD -> cloudBaseUrl
        }
    }

    /**
     * Basic validation for Taplink SDK credentials.
     *
     * Note: secretKey is allowed to be empty in some environments.
     */
    fun isSdkConfigValid(): Boolean {
        return sdkAppId.isNotBlank() && sdkMerchantId.isNotBlank()
    }
}
