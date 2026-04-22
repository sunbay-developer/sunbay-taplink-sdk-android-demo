package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.sdk.enums.FeeMode
import com.sunmi.tapro.taplink.sdk.enums.TipMode
import com.sunmi.tapro.taplink.sdk.model.common.TipConfig
import com.sunmi.tapro.taplink.sdk.model.common.TipSuggestions

/**
 * Utility to build SDK TipConfig from saved preferences.
 * Returns null if tipConfig is not enabled.
 *
 * Reads from SharedPreferences on every call to ensure the latest
 * settings page toggle state is always reflected.
 */
object TipConfigBuilder {

    private const val TAG = "TipConfigBuilder"

    /**
     * Load tip configuration from SharedPreferences and build SDK TipConfig.
     * Returns null if tip config is disabled.
     *
     * This method reads preferences on every invocation so that changes
     * made on the Settings screen take effect immediately without restart.
     */
    fun buildFromPreferences(context: Context): TipConfig? {
        val config = TipConfigPreferences.getConfig(context)
        if (!config.enabled) {
            Log.d(TAG, "TipConfig is disabled, returning null (field will not be sent)")
            return null
        }

        val tipMode = when (config.tipMode) {
            TipConfigPreferences.TipMode.ON_SALE -> TipMode.ON_SALE
            TipConfigPreferences.TipMode.AFTER_SALE -> TipMode.AFTER_SALE
        }

        val suggestions = if (config.suggestionsEnabled) {
            val feeMode = when (config.feeMode) {
                TipConfigPreferences.FeeMode.RATE -> FeeMode.RATE
                TipConfigPreferences.FeeMode.AMOUNT -> FeeMode.AMOUNT
            }
            TipSuggestions(
                feeMode = feeMode,
                values = config.getSuggestionValues()
            )
        } else {
            null
        }

        val tipConfig = TipConfig(
            onScreenTip = config.onScreenTip,
            tipMode = tipMode,
            tipWithTax = config.tipWithTax,
            suggestions = suggestions
        )

        Log.d(TAG, "TipConfig is enabled, built: onScreenTip=${config.onScreenTip}, " +
                "tipMode=${tipMode.name}, tipWithTax=${config.tipWithTax}, " +
                "suggestions=${if (suggestions != null) "feeMode=${suggestions.feeMode.name}, values=${config.getSuggestionValues()}" else "none"}")

        return tipConfig
    }
}
