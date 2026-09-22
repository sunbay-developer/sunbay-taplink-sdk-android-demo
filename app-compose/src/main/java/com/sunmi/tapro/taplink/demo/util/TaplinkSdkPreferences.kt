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
    private const val KEY_LEGACY_REQUEST_TIMEOUT_SECONDS = "request_timeout_seconds"
    private const val KEY_CONFIG_TIMEOUT_SECONDS = "config_timeout_seconds"
    private const val KEY_LEGACY_CONFIG_TIMEOUT_MILLIS = "config_timeout_millis"

    data class SdkConfig(
        val appId: String,
        val merchantId: String,
        val secretKey: String,
        val requestTimeoutMillis: Int? = null,
        val configTimeoutSeconds: Int? = null
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
        val requestTimeoutMillis = when {
            p.contains(KEY_REQUEST_TIMEOUT_MILLIS) -> p.getInt(KEY_REQUEST_TIMEOUT_MILLIS, -1)
                .takeIf { it > 0 }
            p.contains(KEY_LEGACY_REQUEST_TIMEOUT_SECONDS) ->
                secondsToMillis(p.getInt(KEY_LEGACY_REQUEST_TIMEOUT_SECONDS, -1))
            else -> defaultConfig.requestTimeoutMillis
        }
        val configTimeoutSeconds = when {
            p.contains(KEY_CONFIG_TIMEOUT_SECONDS) ->
                p.getInt(KEY_CONFIG_TIMEOUT_SECONDS, -1).takeIf { it > 0 }
            p.contains(KEY_LEGACY_CONFIG_TIMEOUT_MILLIS) ->
                millisToSeconds(p.getInt(KEY_LEGACY_CONFIG_TIMEOUT_MILLIS, -1))
            else -> defaultConfig.configTimeoutSeconds
        }

        return SdkConfig(
            appId = appId,
            merchantId = merchantId,
            secretKey = secretKey,
            requestTimeoutMillis = requestTimeoutMillis,
            configTimeoutSeconds = configTimeoutSeconds
        )
    }

    fun saveConfig(context: Context, config: SdkConfig) {
        prefs(context).edit {
            putString(KEY_APP_ID, config.appId)
            putString(KEY_MERCHANT_ID, config.merchantId)
            putString(KEY_SECRET_KEY, config.secretKey)
            if (config.requestTimeoutMillis != null && config.requestTimeoutMillis > 0) {
                putInt(KEY_REQUEST_TIMEOUT_MILLIS, config.requestTimeoutMillis)
            } else {
                remove(KEY_REQUEST_TIMEOUT_MILLIS)
            }
            remove(KEY_LEGACY_REQUEST_TIMEOUT_SECONDS)
            if (config.configTimeoutSeconds != null && config.configTimeoutSeconds > 0) {
                putInt(KEY_CONFIG_TIMEOUT_SECONDS, config.configTimeoutSeconds)
            } else {
                remove(KEY_CONFIG_TIMEOUT_SECONDS)
            }
            remove(KEY_LEGACY_CONFIG_TIMEOUT_MILLIS)
        }
    }

    private fun secondsToMillis(seconds: Int): Int? {
        return seconds.takeIf { it > 0 }
            ?.toLong()
            ?.times(1000L)
            ?.takeIf { it <= Int.MAX_VALUE }
            ?.toInt()
    }

    private fun millisToSeconds(millis: Int): Int? {
        return millis.takeIf { it > 0 }
            ?.let { ((it.toLong() + 999L) / 1000L).toInt() }
    }
}
