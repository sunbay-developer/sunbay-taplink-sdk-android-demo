package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
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
            val secondaryDisplayEnabled = ConnectionPreferences.isSecondaryDisplayAdEnabled(context)
            Log.d(TAG, "=== Taplink SDK Initialization Started ===")
            Log.d(TAG, "=== SDK Init Request Parameters ===")
            Log.d(TAG, "App ID: ${config.appId}")
            Log.d(TAG, "Merchant ID: ${config.merchantId ?: "(not provided)"}")
            Log.d(TAG, "Secret Key: ${maskSecret(config.secretKey)}")
            val requestTimeoutMillis = config.requestTimeoutMillis
            Log.d(
                TAG,
                "Payment request timeout: " +
                    if (requestTimeoutMillis != null) "${requestTimeoutMillis}ms" else "not set"
            )
            Log.d(
                TAG,
                "TaplinkConfig timeout: " +
                    if (config.configTimeoutSeconds != null) {
                        "${config.configTimeoutSeconds}s"
                    } else {
                        "not set (SDK default)"
                    }
            )
            Log.d(TAG, "Auto open USB screen player: $secondaryDisplayEnabled")

            val taplinkConfig = (config.configTimeoutSeconds?.let { timeoutSeconds ->
                TaplinkConfig(
                    appId = config.appId,
                    merchantId = merchantId,
                    secretKey = config.secretKey,
                    timeout = timeoutSeconds
                )
            } ?: TaplinkConfig(
                appId = config.appId,
                merchantId = merchantId,
                secretKey = config.secretKey
            ))
                .setLogEnabled(true)
                .setLogLevel(LogLevel.DEBUG)
                // 副屏挂在 tapro 侧设备上时，由 SDK 在 connect 内部先拉起 tapro 的副屏播放器。
//                .setAutoOpenUsbScreenPlayer(secondaryDisplayEnabled)

            Log.d(TAG, "=== Calling TaplinkSDK.init() ===")
            TaplinkSDK.init(context, taplinkConfig)
            TaplinkPaymentService.getInstance()
                .setRequestTimeoutMillis(requestTimeoutMillis?.toLong())

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
