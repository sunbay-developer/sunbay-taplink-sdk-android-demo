package com.sunmi.tapro.taplink.demo.service

import android.content.Context
import com.sunmi.tapro.taplink.demo.BuildConfig
import com.sunmi.tapro.taplink.demo.service.cloud.CloudPaymentService
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.PrintReceiptMapping

object PaymentServiceProvider {
    private val cloudPaymentService = CloudPaymentService()

    fun get(context: Context): PaymentService {
        val mode = ConnectionPreferences.getConnectionMode(context)
        return get(context, mode)
    }

    fun get(context: Context, mode: ConnectionPreferences.ConnectionMode): PaymentService {
        return when (mode) {
            ConnectionPreferences.ConnectionMode.CLOUD -> configureCloudService(context)
            else -> {
                cloudPaymentService.shutdown()
                TaplinkPaymentService.getInstance()
            }
        }
    }

    private fun configureCloudService(context: Context): PaymentService {
        val config = ConnectionPreferences.getCloudConfig(context)
        if (config.apiKey.isBlank() || config.baseUrl.isBlank() || config.terminalSn.isBlank()) {
            cloudPaymentService.shutdown()
            return cloudPaymentService
        }

        val merchantId = config.merchantId.ifBlank { BuildConfig.APP_TAPLINK_MERCHANT_ID }
        val appId = config.appId.ifBlank { BuildConfig.APP_TAPLINK_APP_ID }

        cloudPaymentService.initialize(
            apiKey = config.apiKey,
            baseUrl = config.baseUrl,
            appId = appId,
            merchantId = merchantId,
            terminalSn = config.terminalSn
        )
        cloudPaymentService.setNotifyUrl(config.notifyUrl)
        cloudPaymentService.setPrintReceipt(
            PrintReceiptMapping.toSdk(ConnectionPreferences.getPrintReceipt(context)).name
        )
        return cloudPaymentService
    }
}