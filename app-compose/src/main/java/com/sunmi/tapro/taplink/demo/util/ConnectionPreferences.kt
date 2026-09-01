package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Connection configuration management utility class.
 */
object ConnectionPreferences {
    private const val PREFS_NAME = "taplink_connection"
    private const val KEY_MODE = "connection_mode"
    private const val KEY_LAN_IP = "lan_ip"
    private const val KEY_LAN_PORT = "lan_port"
    private const val KEY_CABLE_PROTOCOL = "cable_protocol"
    private const val KEY_APP_TO_APP_MODE = "app_to_app_mode"
    private const val KEY_PRINT_RECEIPT = "print_receipt"
    private const val KEY_SIGNATURE_ENABLED = "signature_enabled"
    private const val KEY_SIGNATURE_USE_HOST_CONFIG = "signature_use_host_config"
    private const val KEY_SIGNATURE_MODE = "signature_mode"
    private const val KEY_SIGNATURE_THRESHOLD_ENABLED = "signature_threshold_enabled"
    private const val KEY_SIGNATURE_THRESHOLD = "signature_threshold"
    private const val KEY_SECONDARY_DISPLAY_AD_ENABLED = "secondary_display_ad_enabled"
    private const val KEY_CROSS_DEVICE_STRATEGY = "cross_device_strategy"
    private const val KEY_CROSS_DEVICE_LAN_IP = "cross_device_lan_ip"
    private const val KEY_CROSS_DEVICE_LAN_PORT = "cross_device_lan_port"
    private const val KEY_CROSS_DEVICE_CABLE_PROTOCOL = "cross_device_cable_protocol"
    private const val KEY_CROSS_DEVICE_AUTO_PREFER_LAN = "cross_device_auto_prefer_lan"

    private const val DEFAULT_MODE = "ON_DEVICE"
    private const val DEFAULT_LAN_PORT = Constants.DEFAULT_LAN_PORT
    private const val DEFAULT_CABLE_PROTOCOL = "AUTO"
    private const val DEFAULT_APP_TO_APP_MODE = "CUSTOM"
    private const val DEFAULT_PRINT_RECEIPT = "NONE"
    private const val DEFAULT_SIGNATURE_MODE = "ON_SCREEN"
    private const val DEFAULT_SIGNATURE_THRESHOLD = "5000"
    private const val DEFAULT_CROSS_DEVICE_STRATEGY = "AUTO"

    enum class ConnectionMode {
        ON_DEVICE,
        CROSS_DEVICE,
        APP_TO_APP,
        CABLE,
        LAN,
        CLOUD,
        SUB_SCREEN
    }

    enum class CableProtocol {
        AUTO,
        USB_AOA,
        USB_VSP,
        RS232
    }

    enum class AppToAppMode {
        CUSTOM,
        HEADLESS
    }

    /**
     * Cross-Device connection strategy. Mirrors `com.sunmi.tapro.taplink.sdk.enums.CrossDeviceStrategy`
     * so the settings UI/preferences layer does not need to depend on the SDK enum directly.
     */
    enum class CrossDeviceStrategy {
        AUTO,
        LAN,
        CABLE
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConnectionMode(context: Context, mode: ConnectionMode) {
        getPreferences(context).edit { putString(KEY_MODE, mode.name) }
    }

    fun getConnectionMode(context: Context): ConnectionMode {
        val modeName = getPreferences(context).getString(KEY_MODE, DEFAULT_MODE)
        return try {
            ConnectionMode.valueOf(modeName ?: DEFAULT_MODE)
        } catch (_: IllegalArgumentException) {
            ConnectionMode.ON_DEVICE
        }
    }

    fun saveLanConfig(context: Context, ip: String, port: Int) {
        getPreferences(context).edit {
            putString(KEY_LAN_IP, ip)
            putInt(KEY_LAN_PORT, port)
        }
    }

    fun getLanConfig(context: Context): Pair<String?, Int> {
        val prefs = getPreferences(context)
        return Pair(
            prefs.getString(KEY_LAN_IP, null),
            prefs.getInt(KEY_LAN_PORT, DEFAULT_LAN_PORT)
        )
    }

    fun saveCableProtocol(context: Context, protocol: CableProtocol) {
        getPreferences(context).edit { putString(KEY_CABLE_PROTOCOL, protocol.name) }
    }

    fun getCableProtocol(context: Context): CableProtocol {
        val protocolName = getPreferences(context).getString(KEY_CABLE_PROTOCOL, DEFAULT_CABLE_PROTOCOL)
        return try {
            CableProtocol.valueOf(protocolName ?: DEFAULT_CABLE_PROTOCOL)
        } catch (_: IllegalArgumentException) {
            CableProtocol.AUTO
        }
    }

    fun saveAppToAppMode(context: Context, mode: AppToAppMode) {
        getPreferences(context).edit { putString(KEY_APP_TO_APP_MODE, mode.name) }
    }

    fun getAppToAppMode(context: Context): AppToAppMode {
        val modeName = getPreferences(context).getString(KEY_APP_TO_APP_MODE, DEFAULT_APP_TO_APP_MODE)
        return try {
            AppToAppMode.valueOf(modeName ?: DEFAULT_APP_TO_APP_MODE)
        } catch (_: IllegalArgumentException) {
            AppToAppMode.CUSTOM
        }
    }

    // ── CROSS_DEVICE mode configuration ─────────────────────────────────────

    fun saveCrossDeviceStrategy(context: Context, strategy: CrossDeviceStrategy) {
        getPreferences(context).edit { putString(KEY_CROSS_DEVICE_STRATEGY, strategy.name) }
    }

    fun getCrossDeviceStrategy(context: Context): CrossDeviceStrategy {
        val strategyName = getPreferences(context).getString(KEY_CROSS_DEVICE_STRATEGY, DEFAULT_CROSS_DEVICE_STRATEGY)
        return try {
            CrossDeviceStrategy.valueOf(strategyName ?: DEFAULT_CROSS_DEVICE_STRATEGY)
        } catch (_: IllegalArgumentException) {
            CrossDeviceStrategy.AUTO
        }
    }

    /** LAN host/port used by CROSS_DEVICE's LAN and AUTO strategies (kept separate from classic LAN config). */
    fun saveCrossDeviceLanConfig(context: Context, ip: String, port: Int) {
        getPreferences(context).edit {
            putString(KEY_CROSS_DEVICE_LAN_IP, ip)
            putInt(KEY_CROSS_DEVICE_LAN_PORT, port)
        }
    }

    fun getCrossDeviceLanConfig(context: Context): Pair<String?, Int> {
        val prefs = getPreferences(context)
        return Pair(
            prefs.getString(KEY_CROSS_DEVICE_LAN_IP, null),
            prefs.getInt(KEY_CROSS_DEVICE_LAN_PORT, DEFAULT_LAN_PORT)
        )
    }

    /** Cable protocol used by CROSS_DEVICE's CABLE strategy (defaults to AUTO try-order when unset). */
    fun saveCrossDeviceCableProtocol(context: Context, protocol: CableProtocol) {
        getPreferences(context).edit { putString(KEY_CROSS_DEVICE_CABLE_PROTOCOL, protocol.name) }
    }

    fun getCrossDeviceCableProtocol(context: Context): CableProtocol {
        val protocolName = getPreferences(context).getString(KEY_CROSS_DEVICE_CABLE_PROTOCOL, DEFAULT_CABLE_PROTOCOL)
        return try {
            CableProtocol.valueOf(protocolName ?: DEFAULT_CABLE_PROTOCOL)
        } catch (_: IllegalArgumentException) {
            CableProtocol.AUTO
        }
    }

    /**
     * AUTO strategy transport priority. `true` tries LAN before CABLE (the SDK default),
     * `false` tries CABLE first.
     */
    fun saveCrossDeviceAutoPreferLan(context: Context, preferLan: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_CROSS_DEVICE_AUTO_PREFER_LAN, preferLan) }
    }

    fun isCrossDeviceAutoPreferLan(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_CROSS_DEVICE_AUTO_PREFER_LAN, true)
    }

    fun savePrintReceipt(
        context: Context,
        printReceipt: com.sunmi.tapro.taplink.demo.ui.screens.settings.PrintReceipt
    ) {
        getPreferences(context).edit { putString(KEY_PRINT_RECEIPT, printReceipt.name) }
    }

    fun getPrintReceipt(context: Context): com.sunmi.tapro.taplink.demo.ui.screens.settings.PrintReceipt {
        val printReceiptName = getPreferences(context).getString(KEY_PRINT_RECEIPT, DEFAULT_PRINT_RECEIPT)
        return try {
            com.sunmi.tapro.taplink.demo.ui.screens.settings.PrintReceipt.valueOf(
                printReceiptName ?: DEFAULT_PRINT_RECEIPT
            )
        } catch (_: IllegalArgumentException) {
            com.sunmi.tapro.taplink.demo.ui.screens.settings.PrintReceipt.NONE
        }
    }

    fun saveSignatureEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit(commit = true) { putBoolean(KEY_SIGNATURE_ENABLED, enabled) }
    }

    fun isSignatureEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SIGNATURE_ENABLED, false)
    }

    /**
     * Whether the terminal (host) signature configuration should be used instead of the
     * local per-transaction configuration. The settings UI exposes the inverse.
     */
    fun saveSignatureUseHostConfig(context: Context, useHostConfig: Boolean) {
        getPreferences(context).edit(commit = true) {
            putBoolean(KEY_SIGNATURE_USE_HOST_CONFIG, useHostConfig)
        }
    }

    fun isSignatureUseHostConfig(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SIGNATURE_USE_HOST_CONFIG, true)
    }

    fun saveSignatureMode(
        context: Context,
        mode: com.sunmi.tapro.taplink.demo.ui.screens.settings.SignatureMode
    ) {
        getPreferences(context).edit(commit = true) { putString(KEY_SIGNATURE_MODE, mode.name) }
    }

    fun getSignatureMode(context: Context): com.sunmi.tapro.taplink.demo.ui.screens.settings.SignatureMode {
        val modeName = getPreferences(context).getString(KEY_SIGNATURE_MODE, DEFAULT_SIGNATURE_MODE)
        return try {
            com.sunmi.tapro.taplink.demo.ui.screens.settings.SignatureMode.valueOf(
                modeName ?: DEFAULT_SIGNATURE_MODE
            )
        } catch (_: IllegalArgumentException) {
            com.sunmi.tapro.taplink.demo.ui.screens.settings.SignatureMode.ON_SCREEN
        }
    }

    fun saveSignatureThresholdEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit(commit = true) {
            putBoolean(KEY_SIGNATURE_THRESHOLD_ENABLED, enabled)
        }
    }

    fun isSignatureThresholdEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SIGNATURE_THRESHOLD_ENABLED, false)
    }

    fun saveSignatureThreshold(context: Context, threshold: String) {
        getPreferences(context).edit(commit = true) {
            putString(KEY_SIGNATURE_THRESHOLD, threshold)
        }
    }

    fun getSignatureThreshold(context: Context): String {
        return getPreferences(context).getString(
            KEY_SIGNATURE_THRESHOLD,
            DEFAULT_SIGNATURE_THRESHOLD
        ) ?: DEFAULT_SIGNATURE_THRESHOLD
    }

    fun saveSecondaryDisplayAdEnabled(context: Context, enabled: Boolean) {
        getPreferences(context).edit(commit = true) {
            putBoolean(KEY_SECONDARY_DISPLAY_AD_ENABLED, enabled)
        }
    }

    fun isSecondaryDisplayAdEnabled(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_SECONDARY_DISPLAY_AD_ENABLED, false)
    }
}
