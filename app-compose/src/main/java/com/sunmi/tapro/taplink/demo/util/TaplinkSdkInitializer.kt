package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import com.sunmi.tapro.taplink.sdk.config.TaplinkConfig
import com.sunmi.tapro.taplink.sdk.enums.LogLevel

/**
 * Centralized TaplinkSDK initialization.
 *
 * Used by Application startup and Settings screen "apply" action.
 */
object TaplinkSdkInitializer {
    private const val TAG = "TaplinkSdkInit"

    val DEFAULT_CONFIG = TaplinkSdkPreferences.SdkConfig(
        appId = EnvironmentDefaults.Prod.SDK_APP_ID,
        merchantId = EnvironmentDefaults.Prod.SDK_MERCHANT_ID,
        // Empty is allowed in some environments; keep default aligned with current demo behavior.
        secretKey = EnvironmentDefaults.Prod.SDK_AUTH_KEY
    )

    fun initFromPreferences(context: Context): Boolean {
        val config = TaplinkSdkPreferences.getConfig(context, DEFAULT_CONFIG)
        return init(context, config)
    }

    fun init(context: Context, config: TaplinkSdkPreferences.SdkConfig): Boolean {
        return try {
            val merchantId = config.merchantId.trim().ifBlank { null }
            Log.d(TAG, "=== Taplink SDK Initialization Started ===")
            Log.d(TAG, "=== SDK Init Request Parameters ===")
            Log.d(TAG, "App ID: ${config.appId}")
            Log.d(TAG, "Merchant ID: ${config.merchantId ?: "(not provided)"}")
            Log.d(TAG, "Secret Key: ${maskSecret(config.secretKey)}")

            val taplinkConfig = TaplinkConfig(
                appId = config.appId,
//                merchantId = config.merchantId,
                secretKey = config.secretKey
            )
                .setLogEnabled(true)
                .setLogLevel(LogLevel.DEBUG)

            Log.d(TAG, "=== Calling TaplinkSDK.init() ===")
            TaplinkSDK.init(context, taplinkConfig)

            Log.d(TAG, "=== Taplink SDK Initialization Response ===")
            Log.d(TAG, "Status: SUCCESS")
            Log.d(TAG, "SDK Version: ${TaplinkSDK.getVersion()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "=== Taplink SDK Initialization Response ===")
            Log.e(TAG, "Status: FAILURE")
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Exception: ", e)
            false
        }
    }

    private fun maskSecret(secretKey: String): String {
        if (secretKey.isEmpty()) return "(empty)"
        if (secretKey.length <= 6) return "***"
        return secretKey.take(2) + "***" + secretKey.takeLast(2)
    }
}

