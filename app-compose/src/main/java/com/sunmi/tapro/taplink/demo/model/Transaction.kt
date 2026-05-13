package com.sunmi.tapro.taplink.demo.model

import java.math.BigDecimal

/**
 * Transaction data class
 * 
 * Stores complete information for a single transaction, including transaction identifiers, amount, status, etc.
 * 
 * @property transactionRequestId Transaction request ID (locally generated for idempotency control)
 * @property transactionId Nexus transaction serial number (returned by platform, may be empty)
 * @property referenceOrderId Reference order ID (unique order identifier in merchant system)
 * @property type Transaction type
 * @property amount Transaction base amount (order amount)
 * @property totalAmount Total transaction amount including all fees (transAmount from SDK)
 * @property currency Currency type
 * @property status Transaction status
 * @property timestamp Creation timestamp (milliseconds)
 * @property authCode Authorization code (returned when transaction is successful)
 * @property errorCode Error code (returned when transaction fails)
 * @property errorMessage Error message (returned when transaction fails)
 * @property originalTransactionId Original transaction ID (used for REFUND, VOID and other follow-up operations)
 * @property surchargeAmount Surcharge amount (optional)
 * @property tipAmount Tip amount (optional)
 * @property cashbackAmount Cashback amount (optional)
 * @property serviceFee Service fee amount (optional)
 * @property batchNo Batch number (for BATCH_CLOSE transactions)
 * @property batchCloseInfo Batch close information (for BATCH_CLOSE transactions)
 * @property progressStatus Current progress status (from onProgress callback)
 * @property progressMessage Current progress message (from onProgress callback)
 * @property completeTime Transaction complete time (from PaymentResult)
 * @property cardInfo Card information (from PaymentResult)
 */
data class Transaction(
    val transactionRequestId: String,
    val transactionId: String? = null,
    val referenceOrderId: String? = null,
    val type: TransactionType,
    val amount: BigDecimal,
    val totalAmount: BigDecimal? = null,
    val currency: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val authCode: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val originalTransactionId: String? = null,
    val surchargeAmount: BigDecimal? = null,
    val tipAmount: BigDecimal? = null,
    val taxAmount: BigDecimal? = null,
    val cashbackAmount: BigDecimal? = null,
    val serviceFee: BigDecimal? = null,
    val batchNo: Int? = null,
    val batchCloseInfo: BatchCloseInfo? = null,
    val progressStatus: String? = null,
    val progressMessage: String? = null,
    val completeTime: String? = null,
    val cardInfo: CardInfo? = null
) {
    /**
     * Check if transaction is successful
     */
    fun isSuccess(): Boolean = status == TransactionStatus.SUCCESS
    
    /**
     * Check if transaction failed
     */
    fun isFailed(): Boolean = status == TransactionStatus.FAILED
    
    /**
     * Check if transaction can be refunded
     * Only successful transactions of type SALE and POST_AUTH can be refunded
     */
    fun canRefund(): Boolean {
        return isSuccess() && (
            type == TransactionType.SALE ||
            type == TransactionType.POST_AUTH
        )
    }
    
    /**
     * Check if transaction can be voided
     * Only successful transactions of type SALE, AUTH, FORCED_AUTH and POST_AUTH can be voided
     */
    fun canVoid(): Boolean {
        return isSuccess() && (
            type == TransactionType.SALE ||
            type == TransactionType.AUTH ||
            type == TransactionType.REFUND ||
            type == TransactionType.POST_AUTH
        )
    }
    
    /**
     * Check if transaction can be tip adjusted
     * Only successful transactions of type SALE and POST_AUTH can be tip adjusted
     */
    fun canAdjustTip(): Boolean {
        return isSuccess() && (
            type == TransactionType.SALE ||
            type == TransactionType.POST_AUTH
        )
    }
    
    /**
     * Check if transaction can be incremental authorized
     * Only successful transactions of type AUTH and FORCED_AUTH can be incremental authorized
     */
    fun canIncrementalAuth(): Boolean {
        return isSuccess() && (
            type == TransactionType.AUTH
        )
    }
    
    /**
     * Check if transaction can be post authorized
     * Only successful transactions of type AUTH and FORCED_AUTH can be post authorized
     */
    fun canPostAuth(): Boolean {
        return isSuccess() && (
            type == TransactionType.AUTH
        )
    }
    
    /**
     * Get transaction display name
     */
    fun getDisplayName(): String = type.displayName()
    
    /**
     * Get status display name
     */
    fun getStatusDisplayName(): String {
        return when (status) {
            TransactionStatus.PENDING -> "Pending"
            TransactionStatus.PROCESSING -> "Processing"
            TransactionStatus.SUCCESS -> "Success"
            TransactionStatus.FAILED -> "Failed"
        }
    }
    
    /**
     * Check if transaction has any additional amounts
     */
    fun hasAdditionalAmounts(): Boolean {
        return surchargeAmount != null || tipAmount != null || taxAmount != null || 
               cashbackAmount != null || serviceFee != null
    }
    
    /**
     * Get total additional amount
     */
    fun getTotalAdditionalAmount(): BigDecimal {
        var total = BigDecimal.ZERO
        surchargeAmount?.let { total = total.add(it) }
        tipAmount?.let { total = total.add(it) }
        taxAmount?.let { total = total.add(it) }
        cashbackAmount?.let { total = total.add(it) }
        serviceFee?.let { total = total.add(it) }
        return total
    }

    /**
     * Amount to display in UI (list/detail/progress).
     *
     * - BATCH_CLOSE: show batch close total amount (from BatchCloseInfo) when available
     * - TIP_ADJUST: show the tip amount when available
     * - Others: show SDK transAmount when available, otherwise base amount
     */
    fun getDisplayAmount(): BigDecimal {
        return when (type) {
            TransactionType.BATCH_CLOSE -> batchCloseInfo?.totalAmount ?: totalAmount ?: amount
            TransactionType.TIP_ADJUST -> tipAmount ?: totalAmount ?: amount
            else -> totalAmount ?: amount
        }
    }
}

/**
 * Batch close information
 */
data class BatchCloseInfo(
    val totalCount: Int,
    val totalAmount: BigDecimal,
    val totalTip: BigDecimal,
    val totalTax: BigDecimal,
    val totalSurchargeAmount: BigDecimal,
    val cashDiscount: BigDecimal,
    val closeTime: String
)

/**
 * Card information from PaymentResult
 */
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