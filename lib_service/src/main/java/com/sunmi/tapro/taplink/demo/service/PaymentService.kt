package com.sunmi.tapro.taplink.demo.service

import com.sunmi.tapro.taplink.sdk.config.ConnectionConfig
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import com.sunmi.tapro.taplink.sdk.model.common.StaffInfo
import java.math.BigDecimal

/**
 * Payment Service Interface
 *
 * Defines the contract for payment-related operations in the Taplink Demo application.
 * This interface abstracts the underlying SDK implementation and provides a clean API
 * for payment operations, connection management, and transaction processing.
 *
 * The interface supports multiple connection modes (App-to-App, Cable, LAN) and
 * various transaction types (SALE, AUTH, REFUND, etc.) while maintaining consistent
 * error handling and callback patterns.
 */
interface PaymentService {

    /**
     * Establish connection to payment terminal using specified configuration
     */
    fun connect(connectionConfig: ConnectionConfig, listener: ConnectionListener)

    /**
     * Disconnect from the payment terminal
     */
    fun disconnect()

    /**
     * Get the identifier of the currently connected device
     */
    fun getConnectedDeviceId(): String?

    /**
     * Get the version of the connected Tapro application
     */
    fun getTaproVersion(): String?

    fun executeSale(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        paymentCategory: PaymentCategory = PaymentCategory.CARD,
        paymentMethodId: String? = null,
        subPaymentMethodId: String? = null,
        cardNetworkType: String? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        staffInfo: StaffInfo? = null,
        callback: PaymentCallback
    )

    fun executeAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    )

    fun executeForcedAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        callback: PaymentCallback
    )

    /**
     * Execute refund transaction.
     *
     * Referenced refund: provide originalTransactionId or originalTransactionRequestId (at least one).
     *   referenceOrderId is NOT required — the server auto-associates the original order.
     *
     * Unreferenced refund: both originalTransactionId and originalTransactionRequestId must be empty.
     *   referenceOrderId is REQUIRED.
     *
     * @see <a href="https://docs.sunbay.dev/zh/refspec/transaction/refund">Refund API</a>
     */
    fun executeRefund(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        originalTransactionRequestId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    )

    fun executeVoid(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        description: String,
        reason: String?,
        callback: PaymentCallback
    )

    fun executePostAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        callback: PaymentCallback
    )

    fun executeIncrementalAuth(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        amount: BigDecimal,
        currency: String,
        description: String,
        callback: PaymentCallback
    )

    fun executeTipAdjust(
        referenceOrderId: String,
        transactionRequestId: String,
        originalTransactionId: String,
        originalTransactionRequestId: String,
        tipAmount: BigDecimal,
        description: String,
        callback: PaymentCallback
    )

    fun executeQuery(
        transactionRequestId: String,
        callback: PaymentCallback
    )

    fun executeQueryByTransactionId(
        transactionId: String,
        callback: PaymentCallback
    )

    fun executeBatchClose(
        transactionRequestId: String,
        description: String,
        callback: PaymentCallback
    )

    fun executeAbort(
        originalTransactionId: String?,
        originalTransactionRequestId: String?,
        description: String?,
        callback: PaymentCallback
    )
}

/**
 * Connection status listener
 */
interface ConnectionListener {
    fun onConnected(deviceId: String, taproVersion: String)
    fun onDisconnected(reason: String)
    fun onError(code: String, message: String)
}

/**
 * Payment callback
 */
interface PaymentCallback {
    fun onSuccess(result: PaymentResult)
    fun onFailure(code: String, message: String)
    fun onProgress(status: String, message: String) {}
}

/**
 * Payment result
 */
data class PaymentResult(
    val code: String,
    val message: String,
    val traceId: String?,
    val transactionId: String?,
    val referenceOrderId: String?,
    val transactionRequestId: String?,
    val transactionStatus: String?,
    val transactionType: String?,
    val amount: TransactionAmount?,
    val createTime: String?,
    val completeTime: String?,
    val cardInfo: CardInfo?,
    val batchNo: Int?,
    val voucherNo: String?,
    val stan: String?,
    val rrn: String?,
    val authCode: String?,
    val transactionResultCode: String?,
    val transactionResultMsg: String?,
    val description: String?,
    val attach: String?,
    val batchCloseInfo: BatchCloseInfo?,
    val tipAmount: BigDecimal?,
    val totalAuthorizedAmount: BigDecimal?,
    val merchantRefundNo: String?,
    val originalTransactionId: String?,
    val originalTransactionRequestId: String?
) {
    /**
     * Cloud API returns single-letter status codes: I, P, S, F, C
     * TaplinkPaymentService (App-to-App) uses full names: SUCCESS, PROCESSING, FAILED
     * These helpers support both formats.
     *
     * API status reference (https://docs.sunbay.dev/zh/resources/reference/transaction-status):
     *   I = Initial (created, not yet processing) - non-terminal
     *   P = Processing - non-terminal
     *   S = Success - terminal
     *   F = Failed - terminal
     *   C = Closed - terminal
     */
    fun isSuccess(): Boolean = transactionStatus == "S" || transactionStatus == "SUCCESS"
    fun isProcessing(): Boolean = transactionStatus == "P" || transactionStatus == "I" || transactionStatus == "PROCESSING"
    fun isFailed(): Boolean = transactionStatus == "F" || transactionStatus == "FAILED"
    fun isClosed(): Boolean = transactionStatus == "C"
    fun isTerminal(): Boolean = isSuccess() || isFailed() || isClosed()
}

data class TransactionAmount(
    val priceCurrency: String?,
    val transAmount: BigDecimal?,
    val orderAmount: BigDecimal?,
    val taxAmount: BigDecimal?,
    val serviceFee: BigDecimal?,
    val tipAmount: BigDecimal?,
    val cashbackAmount: BigDecimal?
)

data class CardInfo(
    val maskedPan: String?,
    val cardNetworkType: String?,
    val paymentMethodId: String?,
    val subPaymentMethodId: String?,
    val entryMode: String?,
    val authenticationMethod: String?,
    val cardholderName: String?,
    val expiryDate: String?,
    val issuerBank: String?,
    val cardBrand: String?
)

data class BatchCloseInfo(
    val totalCount: Int,
    val totalAmount: BigDecimal,
    val totalTip: BigDecimal,
    val totalTax: BigDecimal,
    val totalServiceFee: BigDecimal,
    val cashDiscount: BigDecimal,
    val closeTime: String
)
