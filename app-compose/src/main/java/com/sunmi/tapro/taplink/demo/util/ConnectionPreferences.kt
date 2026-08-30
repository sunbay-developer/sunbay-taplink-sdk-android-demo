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
    private const val KEY_PINPAD_STRATEGY = "pinpad_strategy"
    private const val KEY_PINPAD_LAN_IP = "pinpad_lan_ip"
    private const val KEY_PINPAD_LAN_PORT = "pinpad_lan_port"
    private const val KEY_PINPAD_CABLE_PROTOCOL = "pinpad_cable_protocol"
    private const val KEY_PINPAD_AUTO_PREFER_LAN = "pinpad_auto_prefer_lan"

    private const val DEFAULT_MODE = "ON_DEVICE"
    private const val DEFAULT_LAN_PORT = Constants.DEFAULT_LAN_PORT
    private const val DEFAULT_CABLE_PROTOCOL = "AUTO"
    private const val DEFAULT_APP_TO_APP_MODE = "CUSTOM"
    private const val DEFAULT_PRINT_RECEIPT = "NONE"
    private const val DEFAULT_SIGNATURE_MODE = "ON_SCREEN"
    private const val DEFAULT_SIGNATURE_THRESHOLD = "5000"
    private const val DEFAULT_PINPAD_STRATEGY = "AUTO"

    enum class ConnectionMode {
        ON_DEVICE,
        PINPAD,
        APP_TO_APP,
        CABLE,
        LAN,
        CLOUD,
        SECONDARY_DISPLAY
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
     * PinPad connection strategy. Mirrors `com.sunmi.tapro.taplink.sdk.enums.PinpadStrategy`
     * so the settings UI/preferences layer does not need to depend on the SDK enum directly.
     */
    enum class PinpadStrategy {
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

    // ── PINPAD mode configuration ───────────────────────────────────────────

    fun savePinpadStrategy(context: Context, strategy: PinpadStrategy) {
        getPreferences(context).edit { putString(KEY_PINPAD_STRATEGY, strategy.name) }
    }

    fun getPinpadStrategy(context: Context): PinpadStrategy {
        val strategyName = getPreferences(context).getString(KEY_PINPAD_STRATEGY, DEFAULT_PINPAD_STRATEGY)
        return try {
            PinpadStrategy.valueOf(strategyName ?: DEFAULT_PINPAD_STRATEGY)
        } catch (_: IllegalArgumentException) {
            PinpadStrategy.AUTO
        }
    }

    /** LAN host/port used by PINPAD's LAN and AUTO strategies (kept separate from classic LAN config). */
    fun savePinpadLanConfig(context: Context, ip: String, port: Int) {
        getPreferences(context).edit {
            putString(KEY_PINPAD_LAN_IP, ip)
            putInt(KEY_PINPAD_LAN_PORT, port)
        }
    }

    fun getPinpadLanConfig(context: Context): Pair<String?, Int> {
        val prefs = getPreferences(context)
        return Pair(
            prefs.getString(KEY_PINPAD_LAN_IP, null),
            prefs.getInt(KEY_PINPAD_LAN_PORT, DEFAULT_LAN_PORT)
        )
    }

    /** Cable protocol used by PINPAD's CABLE strategy (defaults to AUTO try-order when unset). */
    fun savePinpadCableProtocol(context: Context, protocol: CableProtocol) {
        getPreferences(context).edit { putString(KEY_PINPAD_CABLE_PROTOCOL, protocol.name) }
    }

    fun getPinpadCableProtocol(context: Context): CableProtocol {
        val protocolName = getPreferences(context).getString(KEY_PINPAD_CABLE_PROTOCOL, DEFAULT_CABLE_PROTOCOL)
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
    fun savePinpadAutoPreferLan(context: Context, preferLan: Boolean) {
        getPreferences(context).edit { putBoolean(KEY_PINPAD_AUTO_PREFER_LAN, preferLan) }
    }

    fun isPinpadAutoPreferLan(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_PINPAD_AUTO_PREFER_LAN, true)
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
