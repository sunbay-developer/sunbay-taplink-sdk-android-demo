package com.sunmi.tapro.taplink.demo.util

import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt as SdkPrintReceipt

/**
 * Maps app module's stored print receipt string (from ConnectionPreferences)
 * to SDK PrintReceipt for the service layer.
 * Values stored: "NONE", "MERCHANT", "CUSTOMER", "BOTH".
 */
object PrintReceiptMapping {

    fun toSdk(value: String): SdkPrintReceipt {
        return try {
            SdkPrintReceipt.valueOf(value.uppercase())
        } catch (e: IllegalArgumentException) {
            SdkPrintReceipt.NONE
        }
    }
}
