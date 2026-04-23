package com.sunmi.tapro.taplink.demo.ui.screens.settings

import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.TipConfigPreferences

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

    // Tip Configuration (SALE and POST_AUTH only)
    data class UpdateTipConfigEnabled(val enabled: Boolean) : SettingsIntent()
    data class UpdateTipOnScreenTip(val enabled: Boolean) : SettingsIntent()
    data class UpdateTipMode(val mode: TipConfigPreferences.TipMode) : SettingsIntent()
    data class UpdateTipWithTax(val enabled: Boolean) : SettingsIntent()
    data class UpdateTipSuggestionsEnabled(val enabled: Boolean) : SettingsIntent()
    data class UpdateTipFeeMode(val mode: TipConfigPreferences.FeeMode) : SettingsIntent()
    data class UpdateTipSuggestionValue(val index: Int, val value: Int) : SettingsIntent()
    object SaveTipConfig : SettingsIntent()
    
    // Actions
    object TestConnection : SettingsIntent()
    object ExitApplication : SettingsIntent()
    
    // Message Handling
    object DismissMessage : SettingsIntent()
    
    // Navigation event consumed
    object NavigationConsumed : SettingsIntent()
}
