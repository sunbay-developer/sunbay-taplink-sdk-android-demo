package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preferences utility for persisting Tip Configuration settings.
 *
 * TipConfig is only applicable to SALE and POST_AUTH transactions.
 * When enabled and tipAmount is null, the terminal will display an on-screen tip prompt.
 */
object TipConfigPreferences {
    private const val PREFS_NAME = "taplink_tip_config"
    private const val KEY_ENABLED = "tip_config_enabled"
    private const val KEY_ON_SCREEN_TIP = "on_screen_tip"
    private const val KEY_TIP_MODE = "tip_mode"
    private const val KEY_TIP_WITH_TAX = "tip_with_tax"
    private const val KEY_SUGGESTIONS_ENABLED = "suggestions_enabled"
    private const val KEY_FEE_MODE = "fee_mode"
    private const val KEY_SUGGESTION_VALUE_1 = "suggestion_value_1"
    private const val KEY_SUGGESTION_VALUE_2 = "suggestion_value_2"
    private const val KEY_SUGGESTION_VALUE_3 = "suggestion_value_3"

    /** Tip mode: ON_SALE or AFTER_SALE */
    enum class TipMode {
        ON_SALE,
        AFTER_SALE
    }

    /** Fee mode for suggestions: RATE (percentage) or AMOUNT (fixed) */
    enum class FeeMode {
        RATE,
        AMOUNT
    }

    data class TipConfigData(
        val enabled: Boolean = false,
        val onScreenTip: Boolean = true,
        val tipMode: TipMode = TipMode.ON_SALE,
        val tipWithTax: Boolean = false,
        val suggestionsEnabled: Boolean = false,
        val feeMode: FeeMode = FeeMode.RATE,
        val suggestionValue1: Int = 15,
        val suggestionValue2: Int = 18,
        val suggestionValue3: Int = 20
    ) {
        /** Validate suggestion values: must be non-negative */
        fun areSuggestionsValid(): Boolean {
            return suggestionValue1 >= 0 && suggestionValue2 >= 0 && suggestionValue3 >= 0
        }

        /** Get suggestion values as a list */
        fun getSuggestionValues(): List<Int> {
            return listOf(suggestionValue1, suggestionValue2, suggestionValue3)
        }
    }

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveConfig(context: Context, config: TipConfigData) {
        getPreferences(context).edit {
            putBoolean(KEY_ENABLED, config.enabled)
            putBoolean(KEY_ON_SCREEN_TIP, config.onScreenTip)
            putString(KEY_TIP_MODE, config.tipMode.name)
            putBoolean(KEY_TIP_WITH_TAX, config.tipWithTax)
            putBoolean(KEY_SUGGESTIONS_ENABLED, config.suggestionsEnabled)
            putString(KEY_FEE_MODE, config.feeMode.name)
            putInt(KEY_SUGGESTION_VALUE_1, config.suggestionValue1)
            putInt(KEY_SUGGESTION_VALUE_2, config.suggestionValue2)
            putInt(KEY_SUGGESTION_VALUE_3, config.suggestionValue3)
        }
    }

    fun getConfig(context: Context): TipConfigData {
        val prefs = getPreferences(context)
        return TipConfigData(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            onScreenTip = prefs.getBoolean(KEY_ON_SCREEN_TIP, true),
            tipMode = try {
                TipMode.valueOf(prefs.getString(KEY_TIP_MODE, TipMode.ON_SALE.name) ?: TipMode.ON_SALE.name)
            } catch (_: IllegalArgumentException) {
                TipMode.ON_SALE
            },
            tipWithTax = prefs.getBoolean(KEY_TIP_WITH_TAX, false),
            suggestionsEnabled = prefs.getBoolean(KEY_SUGGESTIONS_ENABLED, false),
            feeMode = try {
                FeeMode.valueOf(prefs.getString(KEY_FEE_MODE, FeeMode.RATE.name) ?: FeeMode.RATE.name)
            } catch (_: IllegalArgumentException) {
                FeeMode.RATE
            },
            suggestionValue1 = prefs.getInt(KEY_SUGGESTION_VALUE_1, 15),
            suggestionValue2 = prefs.getInt(KEY_SUGGESTION_VALUE_2, 18),
            suggestionValue3 = prefs.getInt(KEY_SUGGESTION_VALUE_3, 20)
        )
    }
}
