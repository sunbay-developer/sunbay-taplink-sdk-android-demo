package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import androidx.core.content.edit

/**
 * Persisted Taplink SDK credentials/config.
 *
 * Stored in SharedPreferences so it survives app restarts.
 */
object TaplinkSdkPreferences {
    private const val PREFS_NAME = "taplink_sdk"

    private const val KEY_APP_ID = "app_id"
    private const val KEY_MERCHANT_ID = "merchant_id"
    private const val KEY_SECRET_KEY = "secret_key"
    private const val KEY_REQUEST_TIMEOUT_MILLIS = "request_timeout_millis"

    data class SdkConfig(
        val appId: String,
        val merchantId: String,
        val secretKey: String,
        val requestTimeoutMillis: Int = 180_000
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(context: Context, defaultConfig: SdkConfig): SdkConfig {
        val p = prefs(context)

        val appId =
            if (p.contains(KEY_APP_ID)) p.getString(KEY_APP_ID, "") ?: "" else defaultConfig.appId
        val merchantId =
            if (p.contains(KEY_MERCHANT_ID)) p.getString(KEY_MERCHANT_ID, "") ?: "" else defaultConfig.merchantId
        val secretKey =
            if (p.contains(KEY_SECRET_KEY)) p.getString(KEY_SECRET_KEY, "") ?: "" else defaultConfig.secretKey
        val requestTimeoutMillis = if (p.contains(KEY_REQUEST_TIMEOUT_MILLIS)) {
            p.getInt(KEY_REQUEST_TIMEOUT_MILLIS, defaultConfig.requestTimeoutMillis)
        } else {
            defaultConfig.requestTimeoutMillis
        }.coerceAtLeast(1)

        return SdkConfig(
            appId = appId,
            merchantId = merchantId,
            secretKey = secretKey,
            requestTimeoutMillis = requestTimeoutMillis
        )
    }

    fun saveConfig(context: Context, config: SdkConfig) {
        prefs(context).edit {
            putString(KEY_APP_ID, config.appId)
            putString(KEY_MERCHANT_ID, config.merchantId)
            putString(KEY_SECRET_KEY, config.secretKey)
            putInt(KEY_REQUEST_TIMEOUT_MILLIS, config.requestTimeoutMillis.coerceAtLeast(1))
        }
    }
}

