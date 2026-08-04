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
    private const val KEY_SIGNATURE_MODE = "signature_mode"
    private const val KEY_SIGNATURE_THRESHOLD_ENABLED = "signature_threshold_enabled"
    private const val KEY_SIGNATURE_THRESHOLD = "signature_threshold"

    private const val DEFAULT_MODE = "APP_TO_APP"
    private const val DEFAULT_LAN_PORT = Constants.DEFAULT_LAN_PORT
    private const val DEFAULT_CABLE_PROTOCOL = "AUTO"
    private const val DEFAULT_APP_TO_APP_MODE = "CUSTOM"
    private const val DEFAULT_PRINT_RECEIPT = "NONE"
    private const val DEFAULT_SIGNATURE_MODE = "ON_SCREEN"
    private const val DEFAULT_SIGNATURE_THRESHOLD = "5000"

    enum class ConnectionMode {
        APP_TO_APP,
        CABLE,
        LAN,
        CLOUD
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
            ConnectionMode.APP_TO_APP
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
}
