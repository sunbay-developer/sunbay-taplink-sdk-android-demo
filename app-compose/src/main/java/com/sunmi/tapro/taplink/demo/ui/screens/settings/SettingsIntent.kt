package com.sunmi.tapro.taplink.demo.ui.screens.settings

import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences

/**
 * Sealed class representing all possible user intents/actions on the Settings Screen
 * Following MVI pattern for unidirectional data flow
 */
sealed class SettingsIntent {
    // Connection Mode Selection
    data class SelectMode(val mode: ConnectionMode) : SettingsIntent()
    
    // Configuration Updates
    data class UpdateCableProtocol(val protocol: ConnectionPreferences.CableProtocol) : SettingsIntent()
    data class UpdateIpAddress(val ipAddress: String) : SettingsIntent()
    data class UpdatePort(val port: String) : SettingsIntent()
    data class UpdatePrintReceipt(val printReceipt: PrintReceipt) : SettingsIntent()

    // SDK Initialization Parameters
    data class UpdateSdkAppId(val appId: String) : SettingsIntent()
    data class UpdateSdkMerchantId(val merchantId: String) : SettingsIntent()
    data class UpdateSdkSecretKey(val secretKey: String) : SettingsIntent()
    data class SwitchSdkEnvironment(val environment: SdkEnvironment) : SettingsIntent()
    object ApplySdkConfig : SettingsIntent()

    // Cloud Configuration
    data class UpdateCloudApiKey(val apiKey: String) : SettingsIntent()
    data class UpdateCloudBaseUrl(val baseUrl: String) : SettingsIntent()
    data class UpdateCloudTerminalSn(val terminalSn: String) : SettingsIntent()
    data class UpdateCloudMerchantId(val merchantId: String) : SettingsIntent()
    data class UpdateCloudAppId(val appId: String) : SettingsIntent()
    data class UpdateCloudNotifyUrl(val notifyUrl: String) : SettingsIntent()
    data class UpdateCloudPushToTerminal(val enabled: Boolean) : SettingsIntent()
    /** Add a custom value to a cloud field's dropdown option list */
    data class AddCloudOption(val field: String, val value: String) : SettingsIntent()
    
    // Actions
    object TestConnection : SettingsIntent()
    object ExitApplication : SettingsIntent()
    
    // Message Handling
    object DismissMessage : SettingsIntent()
    
    // Navigation event consumed
    object NavigationConsumed : SettingsIntent()
}
