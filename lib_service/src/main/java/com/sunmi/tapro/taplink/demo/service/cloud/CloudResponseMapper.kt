package com.sunmi.tapro.taplink.demo.service.cloud

import android.util.Log
import com.google.gson.JsonObject
import com.sunmi.tapro.taplink.demo.service.util.AmountConverter
import com.sunmi.tapro.taplink.demo.service.BatchCloseInfo
import com.sunmi.tapro.taplink.demo.service.CardInfo
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.TransactionAmount
import java.math.BigDecimal

/**
 * Maps CloudResponse (JsonObject-based) to PaymentResult.
 * Reads fields directly from JsonObject — no external SDK dependency.
 */
object CloudResponseMapper {

    private const val TAG = "CloudResponseMapper"

    fun mapToPaymentResult(response: CloudResponse, transactionType: String): PaymentResult {
        Log.d(TAG, "Mapping response for $transactionType")

        val data = response.data

        return PaymentResult(
            code = response.code,
            message = response.msg,
            traceId = response.traceId,
            transactionId = data?.str("transactionId"),
            referenceOrderId = data?.str("referenceOrderId"),
            transactionRequestId = data?.str("transactionRequestId"),
            transactionStatus = data?.str("transactionStatus"),
            transactionType = data?.str("transactionType") ?: transactionType,
            amount = data?.mapAmount(),
            createTime = data?.str("createTime"),
            completeTime = data?.str("completeTime"),
            cardInfo = data?.mapCardInfo(),
            batchNo = data?.str("batchNo")?.toIntOrNull(),
            voucherNo = data?.str("voucherNo"),
            stan = data?.str("stan"),
            rrn = data?.str("rrn"),
            authCode = data?.str("authCode"),
            transactionResultCode = data?.str("transactionResultCode"),
            transactionResultMsg = data?.str("transactionResultMsg"),
            description = data?.str("description"),
            attach = data?.str("attach"),
            batchCloseInfo = data?.mapBatchCloseInfo(),
            tipAmount = data?.cents("tipAmount"),
            totalAuthorizedAmount = data?.cents("totalAuthorizedAmount"),
            merchantRefundNo = data?.str("merchantRefundNo"),
            originalTransactionId = data?.str("originalTransactionId"),
            originalTransactionRequestId = data?.str("originalTransactionRequestId")
        )
    }

    // --- Extension helpers for JsonObject ---

    private fun JsonObject.str(key: String): String? {
        val el = get(key) ?: return null
        return if (el.isJsonNull) null else el.asString
    }

    private fun JsonObject.int(key: String): Int? {
        val el = get(key) ?: return null
        return if (el.isJsonNull) null else try { el.asInt } catch (_: Exception) { null }
    }

    private fun JsonObject.cents(key: String): BigDecimal? {
        return int(key)?.let { AmountConverter.toDollars(it) }
    }

    private fun JsonObject.obj(key: String): JsonObject? {
        val el = get(key) ?: return null
        return if (el.isJsonObject) el.asJsonObject else null
    }

    // --- Nested object mappers ---

    private fun JsonObject.mapAmount(): TransactionAmount? {
        val a = obj("amount") ?: return null
        return TransactionAmount(
            priceCurrency = a.str("priceCurrency"),
            transAmount = a.cents("transAmount"),
            orderAmount = a.cents("orderAmount"),
            taxAmount = a.cents("taxAmount"),
            serviceFee = a.cents("surchargeAmount"),
            tipAmount = a.cents("tipAmount"),
            cashbackAmount = a.cents("cashbackAmount")
        )
    }

    private fun JsonObject.mapCardInfo(): CardInfo? {
        // Some APIs nest card info in a "cardInfo" object; Query API puts fields at top level.
        val c = obj("cardInfo") ?: this
        val pan = c.str("maskedPan") ?: return null
        return CardInfo(
            maskedPan = pan,
            cardNetworkType = c.str("cardNetworkType"),
            paymentMethodId = c.str("paymentMethodId"),
            subPaymentMethodId = c.str("subPaymentMethodId"),
            entryMode = c.str("entryMode"),
            authenticationMethod = c.str("authenticationMethod"),
            cardholderName = c.str("cardholderName"),
            expiryDate = c.str("expiryDate"),
            issuerBank = c.str("issuerBank"),
            cardBrand = c.str("cardBrand")
        )
    }

    private fun JsonObject.mapBatchCloseInfo(): BatchCloseInfo? {
        val count = int("transactionCount") ?: return null
        return BatchCloseInfo(
            totalCount = count,
            totalAmount = cents("netAmount") ?: BigDecimal.ZERO,
            totalTip = cents("tipAmount") ?: BigDecimal.ZERO,
            totalTax = cents("taxAmount") ?: BigDecimal.ZERO,
            totalServiceFee = cents("surchargeAmount") ?: BigDecimal.ZERO,
            cashDiscount = BigDecimal.ZERO,
            closeTime = str("batchTime") ?: ""
        )
    }
}
