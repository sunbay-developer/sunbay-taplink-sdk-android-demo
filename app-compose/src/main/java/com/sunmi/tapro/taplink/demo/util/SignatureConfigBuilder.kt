package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import com.sunmi.tapro.taplink.sdk.enums.SignatureEntryLocation
import com.sunmi.tapro.taplink.sdk.model.common.SignatureConfig
import java.math.BigDecimal

object SignatureConfigBuilder {

    fun buildFromPreferences(context: Context): SignatureConfig? {
        if (!ConnectionPreferences.isSignatureEnabled(context)) {
            return null
        }

        val entryLocation = SignatureEntryLocation.valueOf(
            ConnectionPreferences.getSignatureMode(context).name
        )
        if (entryLocation == SignatureEntryLocation.NONE) {
            return SignatureConfig.none()
        }
        if (!ConnectionPreferences.isSignatureThresholdEnabled(context)) {
            return SignatureConfig(entryLocation = entryLocation)
        }

        val thresholdText = ConnectionPreferences.getSignatureThreshold(context)
        val threshold = thresholdText.toBigDecimalOrNull()
        require(threshold != null && threshold >= BigDecimal.ZERO) {
            "Signature threshold must be a non-negative integer in the smallest currency unit"
        }
        require(threshold.stripTrailingZeros().scale() <= 0) {
            "Signature threshold must be an integer in the smallest currency unit"
        }
        return SignatureConfig(
            entryLocation = entryLocation,
            threshold = threshold
        )
    }
}
