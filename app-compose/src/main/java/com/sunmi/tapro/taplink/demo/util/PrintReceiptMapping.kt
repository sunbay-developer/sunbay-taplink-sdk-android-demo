package com.sunmi.tapro.taplink.demo.util

import com.sunmi.tapro.taplink.demo.ui.screens.settings.PrintReceipt as UiPrintReceipt
import com.sunmi.tapro.taplink.sdk.enums.PrintReceipt as SdkPrintReceipt

/**
 * Maps app-compose UI PrintReceipt enum to SDK PrintReceipt for the service layer.
 */
object PrintReceiptMapping {
    fun toSdk(ui: UiPrintReceipt): SdkPrintReceipt = when (ui) {
        UiPrintReceipt.NONE -> SdkPrintReceipt.NONE
        UiPrintReceipt.MERCHANT -> SdkPrintReceipt.MERCHANT
        UiPrintReceipt.CUSTOMER -> SdkPrintReceipt.CUSTOMER
        UiPrintReceipt.BOTH -> SdkPrintReceipt.BOTH
    }
}
