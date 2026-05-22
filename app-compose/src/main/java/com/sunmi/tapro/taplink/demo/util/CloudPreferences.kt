package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Persisted Cloud mode configuration.
 *
 * Stores active config values and per-field dropdown option lists in SharedPreferences.
 * Each field (apiKey, baseUrl, terminalSn, merchantId, appId, notifyUrl) has a
 * saved list of options the user can pick from or add to.
 *
 * Preset options are merged with user-added options on read.
 */
object CloudPreferences {
    private const val PREFS_NAME = "cloud_config"
    private val gson = Gson()

    // --- Active value keys ---
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_TERMINAL_SN = "terminal_sn"
    private const val KEY_MERCHANT_ID = "merchant_id"
    private const val KEY_APP_ID = "app_id"
    private const val KEY_NOTIFY_URL = "notify_url"
    private const val KEY_PUSH_TO_TERMINAL = "push_to_terminal"

    // --- Dropdown option list keys ---
    private const val KEY_OPTIONS_API_KEY = "options_api_key"
    private const val KEY_OPTIONS_BASE_URL = "options_base_url"
    private const val KEY_OPTIONS_TERMINAL_SN = "options_terminal_sn"
    private const val KEY_OPTIONS_MERCHANT_ID = "options_merchant_id"
    private const val KEY_OPTIONS_APP_ID = "options_app_id"
    private const val KEY_OPTIONS_NOTIFY_URL = "options_notify_url"

    // --- Preset values ---
    private val PRESET_BASE_URLS = listOf(
        "https://open.sunbay-uat.us" to "UAT",
        "https://open.sunbay.us" to "Prod"
    )
    private val PRESET_NOTIFY_URLS = listOf(
        "http://52.76.178.47:8880/api/notify" to "Default"
    )

    private val DEFAULT_BASE_URL = EnvironmentDefaults.Prod.CLOUD_BASE_URL
    private const val DEFAULT_NOTIFY_URL = "http://52.76.178.47:8880/api/notify"

    /**
     * A labeled option for dropdown display.
     * [value] is the actual config value; [label] is the display text (may equal value).
     */
    data class LabeledOption(val value: String, val label: String = value)

    data class CloudConfig(
        val apiKey: String,
        val baseUrl: String,
        val terminalSn: String,
        val merchantId: String = "",
        val appId: String = "",
        val notifyUrl: String = DEFAULT_NOTIFY_URL,
        val pushToTerminal: Boolean = true
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ========== Active config read/write ==========

    fun getConfig(context: Context): CloudConfig {
        val p = prefs(context)
        val presetBaseUrl = DEFAULT_BASE_URL
        val presetApiKey = EnvironmentDefaults.Prod.CLOUD_API_KEY
        val presetMerchantId = EnvironmentDefaults.Prod.SDK_MERCHANT_ID
        // Terminal SN preset: use secrets file value if provided, otherwise empty
        val presetTerminalSn = EnvironmentDefaults.Prod.CLOUD_TERMINAL_SN
        val presetAppId = EnvironmentDefaults.Prod.CLOUD_APP_ID

        return CloudConfig(
            apiKey = if (p.contains(KEY_API_KEY)) p.getString(KEY_API_KEY, "") ?: "" else presetApiKey,
            baseUrl = if (p.contains(KEY_BASE_URL)) p.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL else presetBaseUrl,
            terminalSn = if (p.contains(KEY_TERMINAL_SN)) p.getString(KEY_TERMINAL_SN, "") ?: "" else presetTerminalSn,
            merchantId = if (p.contains(KEY_MERCHANT_ID)) p.getString(KEY_MERCHANT_ID, "") ?: "" else presetMerchantId,
            appId = if (p.contains(KEY_APP_ID)) p.getString(KEY_APP_ID, "") ?: "" else presetAppId,
            notifyUrl = if (p.contains(KEY_NOTIFY_URL)) p.getString(KEY_NOTIFY_URL, DEFAULT_NOTIFY_URL) ?: DEFAULT_NOTIFY_URL else DEFAULT_NOTIFY_URL,
            pushToTerminal = p.getBoolean(KEY_PUSH_TO_TERMINAL, true)
        )
    }

    fun saveConfig(context: Context, config: CloudConfig) {
        prefs(context).edit {
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_BASE_URL, config.baseUrl)
            putString(KEY_TERMINAL_SN, config.terminalSn)
            putString(KEY_MERCHANT_ID, config.merchantId)
            putString(KEY_APP_ID, config.appId)
            putString(KEY_NOTIFY_URL, config.notifyUrl)
            putBoolean(KEY_PUSH_TO_TERMINAL, config.pushToTerminal)
        }
        // Auto-add current values to their option lists so they appear in dropdowns
        addOptionIfNew(context, KEY_OPTIONS_API_KEY, config.apiKey)
        addOptionIfNew(context, KEY_OPTIONS_BASE_URL, config.baseUrl)
        addOptionIfNew(context, KEY_OPTIONS_TERMINAL_SN, config.terminalSn)
        addOptionIfNew(context, KEY_OPTIONS_MERCHANT_ID, config.merchantId)
        addOptionIfNew(context, KEY_OPTIONS_APP_ID, config.appId)
        addOptionIfNew(context, KEY_OPTIONS_NOTIFY_URL, config.notifyUrl)
    }

    // ========== Dropdown option lists ==========

    /** Get merged options (presets + user-saved) for Base URL */
    fun getBaseUrlOptions(context: Context): List<LabeledOption> {
        val presets = PRESET_BASE_URLS.map { LabeledOption(it.first, "${it.second}: ${it.first}") }
        val custom = getCustomOptions(context, KEY_OPTIONS_BASE_URL)
            .filter { opt -> PRESET_BASE_URLS.none { it.first == opt } }
            .map { LabeledOption(it) }
        return presets + custom
    }

    /** Get merged options for Notify URL */
    fun getNotifyUrlOptions(context: Context): List<LabeledOption> {
        val presets = PRESET_NOTIFY_URLS.map { LabeledOption(it.first, "${it.second}: ${it.first}") }
        val custom = getCustomOptions(context, KEY_OPTIONS_NOTIFY_URL)
            .filter { opt -> PRESET_NOTIFY_URLS.none { it.first == opt } }
            .map { LabeledOption(it) }
        return presets + custom
    }

    /** Get saved options for a generic field (API Key, Terminal SN, Merchant ID, App ID) */
    fun getFieldOptions(context: Context, field: CloudField): List<LabeledOption> {
        val key = field.optionsKey
        return getCustomOptions(context, key).map { LabeledOption(it) }
    }

    /** Add a new custom option for a field */
    fun addOption(context: Context, field: CloudField, value: String) {
        if (value.isBlank()) return
        addOptionIfNew(context, field.optionsKey, value)
    }

    /** Add a new custom option for Base URL */
    fun addBaseUrlOption(context: Context, value: String) {
        if (value.isBlank()) return
        addOptionIfNew(context, KEY_OPTIONS_BASE_URL, value)
    }

    /** Add a new custom option for Notify URL */
    fun addNotifyUrlOption(context: Context, value: String) {
        if (value.isBlank()) return
        addOptionIfNew(context, KEY_OPTIONS_NOTIFY_URL, value)
    }

    // ========== Internal helpers ==========

    private fun getCustomOptions(context: Context, key: String): List<String> {
        val json = prefs(context).getString(key, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun addOptionIfNew(context: Context, key: String, value: String) {
        if (value.isBlank()) return
        val current = getCustomOptions(context, key).toMutableList()
        if (!current.contains(value)) {
            current.add(value)
            prefs(context).edit { putString(key, gson.toJson(current)) }
        }
    }

    /**
     * Enum mapping each Cloud config field to its options storage key.
     */
    enum class CloudField(val optionsKey: String) {
        API_KEY(KEY_OPTIONS_API_KEY),
        TERMINAL_SN(KEY_OPTIONS_TERMINAL_SN),
        MERCHANT_ID(KEY_OPTIONS_MERCHANT_ID),
        APP_ID(KEY_OPTIONS_APP_ID)
    }
}
