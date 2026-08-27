package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import androidx.core.content.edit
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo

object StaffInfoPreferences {
    private const val PREFS_NAME = "taplink_staff_info"
    private const val KEY_OPERATOR_ID = "operator_id"
    private const val KEY_TIP_RECIPIENT_ID = "tip_recipient_id"

    data class StaffInfoConfig(
        val operatorId: String = "",
        val tipRecipientId: String = ""
    )

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(context: Context): StaffInfoConfig {
        val prefs = preferences(context)
        return StaffInfoConfig(
            operatorId = prefs.getString(KEY_OPERATOR_ID, "").orEmpty(),
            tipRecipientId = prefs.getString(KEY_TIP_RECIPIENT_ID, "").orEmpty()
        )
    }

    fun saveConfig(context: Context, config: StaffInfoConfig) {
        preferences(context).edit {
            putString(KEY_OPERATOR_ID, config.operatorId.trim())
            putString(KEY_TIP_RECIPIENT_ID, config.tipRecipientId.trim())
        }
    }

    fun buildSdkStaffInfo(context: Context): StaffInfo? {
        val config = getConfig(context)
        if (config.operatorId.isBlank() && config.tipRecipientId.isBlank()) return null
        return StaffInfo(
            operatorId = config.operatorId.trim(),
            tipRecipientId = config.tipRecipientId.trim()
        )
    }
}
