package com.sunmi.tapro.taplink.demo.util

import com.sunmi.tapro.taplink.demo.BuildConfig

/**
 * Centralized environment defaults used by app-compose.
 */
object EnvironmentDefaults {

    object Prod {
        val CLOUD_BASE_URL: String = BuildConfig.PROD_CLOUD_BASE_URL
        val CLOUD_API_KEY: String = BuildConfig.PROD_CLOUD_API_KEY
        val CLOUD_APP_ID: String = BuildConfig.PROD_CLOUD_APP_ID
        val WEBHOOK_KEY: String = BuildConfig.PROD_WEBHOOK_KEY
        val SDK_APP_ID: String = BuildConfig.PROD_SDK_APP_ID
        val SDK_MERCHANT_ID: String = BuildConfig.PROD_SDK_MERCHANT_ID
        val SDK_AUTH_KEY: String = BuildConfig.PROD_SDK_AUTH_KEY
        val CLOUD_TERMINAL_SN: String = BuildConfig.PROD_CLOUD_TERMINAL_SN
    }

    object Uat {
        val SDK_APP_ID: String = BuildConfig.UAT_SDK_APP_ID
        val SDK_MERCHANT_ID: String = BuildConfig.UAT_SDK_MERCHANT_ID
        val SDK_AUTH_KEY: String = BuildConfig.UAT_SDK_AUTH_KEY
        val CLOUD_BASE_URL: String = BuildConfig.UAT_CLOUD_BASE_URL
        val CLOUD_API_KEY: String = BuildConfig.UAT_CLOUD_API_KEY
        val CLOUD_APP_ID: String = BuildConfig.UAT_CLOUD_APP_ID
        val CLOUD_TERMINAL_SN: String = BuildConfig.UAT_CLOUD_TERMINAL_SN
    }
}
