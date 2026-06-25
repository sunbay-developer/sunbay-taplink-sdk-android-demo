package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Preferences utility for persisting Tax Configuration settings.
 *
 * When enabled, the tax rate is automatically applied to the order subtotal
 * and included as the "Tax" additional amount during payment processing.
 */
object TaxConfigPreferences {
    private const val PREFS_NAME = "taplink_tax_config"
    private const val KEY_ENABLED = "tax_config_enabled"
    private const val KEY_TAX_RATE = "tax_rate"

    /** Default tax rate in percent (e.g. 8 means 8%) */
    const val DEFAULT_TAX_RATE = 0

    data class TaxConfigData(
        val enabled: Boolean = false,
        /** Tax rate as an integer percentage, e.g. 8 = 8% */
        val taxRate: Int = DEFAULT_TAX_RATE
    ) {
        fun isValid(): Boolean = taxRate in 0..100
    }

    private fun getPreferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveConfig(context: Context, config: TaxConfigData) {
        getPreferences(context).edit {
            putBoolean(KEY_ENABLED, config.enabled)
            putInt(KEY_TAX_RATE, config.taxRate)
        }
    }

    fun getConfig(context: Context): TaxConfigData {
        val prefs = getPreferences(context)
        return TaxConfigData(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            taxRate = prefs.getInt(KEY_TAX_RATE, DEFAULT_TAX_RATE)
        )
    }
}
