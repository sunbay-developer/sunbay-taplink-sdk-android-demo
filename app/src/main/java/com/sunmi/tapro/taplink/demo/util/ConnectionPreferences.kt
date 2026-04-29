package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.sunmi.tapro.taplink.demo.util.Constants

/**
 * Connection configuration management utility class
 * 
 * Responsible for saving and loading connection mode configurations, including:
 * - Connection mode (App-to-App, Cable, LAN, Cloud)
 * - LAN mode IP address and port configuration
 * 
 *
 * Uses SharedPreferences for persistent storage
 */
object ConnectionPreferences {
    
    private const val PREFS_NAME = "taplink_connection"
    private const val KEY_MODE = "connection_mode"
    private const val KEY_LAN_IP = "lan_ip"
    private const val KEY_LAN_PORT = "lan_port"
    private const val KEY_CABLE_PROTOCOL = "cable_protocol"
    private const val KEY_PRINT_RECEIPT = "print_receipt"
    
    // Cloud config keys
    private const val KEY_CLOUD_API_KEY = "cloud_api_key"
    private const val KEY_CLOUD_BASE_URL = "cloud_base_url"
    private const val KEY_CLOUD_TERMINAL_SN = "cloud_terminal_sn"
    private const val KEY_CLOUD_MERCHANT_ID = "cloud_merchant_id"
    private const val KEY_CLOUD_APP_ID = "cloud_app_id"
    private const val KEY_CLOUD_NOTIFY_URL = "cloud_notify_url"
    
    // Default values
    private const val DEFAULT_MODE = "APP_TO_APP"
    private const val DEFAULT_LAN_PORT = Constants.DEFAULT_LAN_PORT
    private const val DEFAULT_CABLE_PROTOCOL = "AUTO"
    private const val DEFAULT_PRINT_RECEIPT = "NONE"
    
    // Default cloud values
    private const val DEFAULT_CLOUD_BASE_URL = "https://open.sunbay.us"
    private const val DEFAULT_CLOUD_NOTIFY_URL = "http://52.76.178.47:8880/api/notify"
    
    /**
     * Connection mode enumeration
     */
    enum class ConnectionMode {
        APP_TO_APP,    // Same-device integration (default)
        CABLE,         // Cross-device via cable
        LAN,           // Local Area Network
        CLOUD          // Cloud API connection
    }
    
    /**
     * Cable protocol enumeration
     */
    enum class CableProtocol {
        AUTO,          // Auto-detect protocol (default)
        USB_AOA,       // USB Android Open Accessory 2.0
        USB_VSP,       // USB Virtual Serial Port
        RS232          // Standard RS232 serial communication
    }
    
    /**
     * Get SharedPreferences instance
     */
    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Save connection mode
     * 
     * @param context Android Context
     * @param mode Connection mode
     */
    fun saveConnectionMode(context: Context, mode: ConnectionMode) {
        getPreferences(context).edit {
            putString(KEY_MODE, mode.name)
        }
    }
    
    /**
     * Get saved connection mode
     * 
     * @param context Android Context
     * @return Connection mode, default is APP_TO_APP
     */
    fun getConnectionMode(context: Context): ConnectionMode {
        val prefs = getPreferences(context)
        val modeName = prefs.getString(KEY_MODE, DEFAULT_MODE)
        return try {
            ConnectionMode.valueOf(modeName ?: DEFAULT_MODE)
        } catch (e: IllegalArgumentException) {
            // Return default value if saved value is invalid
            ConnectionMode.APP_TO_APP
        }
    }
    
    /**
     * Save LAN configuration
     * 
     * @param context Android Context
     * @param ip IP address (if empty, will remove the saved IP)
     * @param port Port number
     */
    fun saveLanConfig(context: Context, ip: String, port: Int) {
        getPreferences(context).edit {
            if (ip.isBlank()) {
                remove(KEY_LAN_IP)  // Remove saved IP if empty
            } else {
                putString(KEY_LAN_IP, ip)
            }
            putInt(KEY_LAN_PORT, port)
        }
    }

    /**
     * Clear LAN IP configuration
     * 
     * @param context Android Context
     */
    fun clearLanIp(context: Context) {
        getPreferences(context).edit {
            remove(KEY_LAN_IP)
        }
    }

    /**
     * Get complete LAN configuration
     * 
     * @param context Android Context
     * @return Pair(IP address, Port number)
     */
    fun getLanConfig(context: Context): Pair<String?, Int> {
        val prefs = getPreferences(context)
        val ip = prefs.getString(KEY_LAN_IP, null)
        val port = prefs.getInt(KEY_LAN_PORT, DEFAULT_LAN_PORT)
        return Pair(ip, port)
    }
    
    /**
     * Save Cable protocol configuration
     * 
     * @param context Android Context
     * @param protocol Cable protocol
     */
    fun saveCableProtocol(context: Context, protocol: CableProtocol) {
        getPreferences(context).edit {
            putString(KEY_CABLE_PROTOCOL, protocol.name)
        }
    }
    
    /**
     * Get Cable protocol configuration
     * 
     * @param context Android Context
     * @return Cable protocol, default is AUTO
     */
    fun getCableProtocol(context: Context): CableProtocol {
        val prefs = getPreferences(context)
        val protocolName = prefs.getString(KEY_CABLE_PROTOCOL, DEFAULT_CABLE_PROTOCOL)
        return try {
            CableProtocol.valueOf(protocolName ?: DEFAULT_CABLE_PROTOCOL)
        } catch (e: IllegalArgumentException) {
            // Return default value if saved value is invalid
            CableProtocol.AUTO
        }
    }
    
    /**
     * Save print receipt configuration
     * 
     * @param context Android Context
     * @param printReceipt Print receipt option (NONE, MERCHANT, CUSTOMER, BOTH)
     */
    fun savePrintReceipt(context: Context, printReceipt: String) {
        getPreferences(context).edit {
            putString(KEY_PRINT_RECEIPT, printReceipt)
        }
    }
    
    /**
     * Get print receipt configuration
     * 
     * @param context Android Context
     * @return Print receipt option, default is NONE
     */
    fun getPrintReceipt(context: Context): String {
        val prefs = getPreferences(context)
        return prefs.getString(KEY_PRINT_RECEIPT, DEFAULT_PRINT_RECEIPT) ?: DEFAULT_PRINT_RECEIPT
    }
    
    /**
     * Save Cloud configuration
     */
    fun saveCloudConfig(context: Context, apiKey: String, baseUrl: String, terminalSn: String, merchantId: String = "", appId: String = "", notifyUrl: String = DEFAULT_CLOUD_NOTIFY_URL) {
        getPreferences(context).edit {
            putString(KEY_CLOUD_API_KEY, apiKey)
            putString(KEY_CLOUD_BASE_URL, baseUrl)
            putString(KEY_CLOUD_TERMINAL_SN, terminalSn)
            putString(KEY_CLOUD_MERCHANT_ID, merchantId)
            putString(KEY_CLOUD_APP_ID, appId)
            putString(KEY_CLOUD_NOTIFY_URL, notifyUrl)
        }
    }
    
    /**
     * Cloud configuration data class
     */
    data class CloudConfig(
        val apiKey: String,
        val baseUrl: String,
        val terminalSn: String,
        val merchantId: String = "",
        val appId: String = "",
        val notifyUrl: String = DEFAULT_CLOUD_NOTIFY_URL
    )
    
    /**
     * Get Cloud configuration
     * 
     * @param context Android Context
     * @return CloudConfig with all cloud settings
     */
    fun getCloudConfig(context: Context): CloudConfig {
        val prefs = getPreferences(context)
        return CloudConfig(
            apiKey = prefs.getString(KEY_CLOUD_API_KEY, "") ?: "",
            baseUrl = prefs.getString(KEY_CLOUD_BASE_URL, DEFAULT_CLOUD_BASE_URL) ?: DEFAULT_CLOUD_BASE_URL,
            terminalSn = prefs.getString(KEY_CLOUD_TERMINAL_SN, "") ?: "",
            merchantId = prefs.getString(KEY_CLOUD_MERCHANT_ID, "") ?: "",
            appId = prefs.getString(KEY_CLOUD_APP_ID, "") ?: "",
            notifyUrl = prefs.getString(KEY_CLOUD_NOTIFY_URL, DEFAULT_CLOUD_NOTIFY_URL) ?: DEFAULT_CLOUD_NOTIFY_URL
        )
    }
}
