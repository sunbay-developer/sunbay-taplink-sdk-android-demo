package com.sunmi.tapro.taplink.demo.ui.screens.progress

import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.service.HeadlessProgressEvent
import com.sunmi.tapro.taplink.demo.ui.screens.main.PaymentOption
import java.math.BigDecimal

/**
 * Represents a single split payment line with independent payment option and amount.
 */
data class SplitLine(
    val paymentOption: PaymentOption = PaymentOption.CARD_SALE,
    val amountInput: String = "",
    val isPaid: Boolean = false
)

/**
 * Transaction Progress Screen State
 * 
 * Immutable data class representing the complete UI state of the Transaction Progress Screen.
 * Following MVI pattern for predictable state management.
 * 
 * All state changes create a new instance (copy) to ensure immutability.
 */
data class TransactionProgressState(
    // Transaction information
    val transactionId: String? = null,
    val transaction: Transaction? = null,
    val orderItems: List<OrderItem> = emptyList(),
    val transactionType: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val status: TransactionStatus = TransactionStatus.PENDING,
    
    // Progress information
    val progressStatus: String? = null,
    val progressMessage: String? = null,
    val headlessProgressEvent: HeadlessProgressEvent? = null,
    
    // UI control flags
    val isLoading: Boolean = true,
    val isQuerying: Boolean = false,
    val isAborting: Boolean = false,
    val isStartingPayment: Boolean = false,
    val isAwaitingSecondaryMemberInput: Boolean = false,
    val memberInput: String = "",
    val isAwaitingSecondaryTipSelection: Boolean = false,
    val isSplitMode: Boolean = false,
    val splitAmountInput: String = "",
    val splitCount: Int = 2,
    val splitLines: List<SplitLine> = emptyList(),
    val activeSplitLineIndex: Int? = null,
    val selectedPaymentOption: PaymentOption = PaymentOption.CARD_SALE,
    val canNavigateBack: Boolean = false,
    val canRetry: Boolean = false,
    val showViewDetailsButton: Boolean = false,
    
    // Error information
    val errorMessage: String = "",
    val message: Message? = null,
    
    // Navigation events (one-time events)
    val navigationEvent: TransactionProgressNavigationEvent? = null
) {
    /**
     * Check if transaction is pending
     */
    fun isPending(): Boolean = status == TransactionStatus.PENDING || status == TransactionStatus.PROCESSING

    /**
     * Calculate remaining amount after subtracting all split line amounts.
     */
    fun splitRemaining(): BigDecimal {
        if (!isSplitMode || splitLines.isEmpty()) return amount
        val totalSplit = splitLines
            .filterNot { it.isPaid }
            .mapNotNull { it.amountInput.toBigDecimalOrNull() }
            .fold(BigDecimal.ZERO) { acc, v -> acc + v }
        return totalSplit.coerceAtLeast(BigDecimal.ZERO)
    }
    
    /**
     * Check if transaction is successful
     */
    fun isSuccess(): Boolean = status == TransactionStatus.SUCCESS
    
    /**
     * Check if transaction failed
     */
    fun isFailed(): Boolean = status == TransactionStatus.FAILED
    
    /**
     * Check if transaction is complete (success or failed)
     */
    fun isReadyForPaymentSelection(): Boolean =
        progressStatus.equals("READY_TO_PAY", ignoreCase = true)

    fun isComplete(): Boolean = isSuccess() || isFailed()
    
    /**
     * Get status display text
     */
    fun getStatusDisplayText(): String {
        // If there's a progress message, show it instead of generic status
        if (!progressMessage.isNullOrEmpty() && isPending()) {
            return progressMessage
        }
        
        return when (status) {
            TransactionStatus.PENDING -> "Initializing..."
            TransactionStatus.PROCESSING -> "Processing..."
            TransactionStatus.SUCCESS -> "Transaction Successful"
            TransactionStatus.FAILED -> "Transaction Failed"
        }
    }
    
    /**
     * Get status color indicator
     */
    fun getStatusColor(): StatusColor {
        return when (status) {
            TransactionStatus.PENDING, TransactionStatus.PROCESSING -> StatusColor.PROCESSING
            TransactionStatus.SUCCESS -> StatusColor.SUCCESS
            TransactionStatus.FAILED -> StatusColor.ERROR
        }
    }
}

/**
 * Status color indicator for UI
 */
enum class StatusColor {
    PROCESSING,
    SUCCESS,
    ERROR,
    WARNING
}

/**
 * Navigation events for Transaction Progress Screen
 */
sealed class TransactionProgressNavigationEvent {
    /**
     * Navigate back to main screen
     */
    object ToMain : TransactionProgressNavigationEvent()
    
    /**
     * Navigate to transaction detail screen
     */
    data class ToDetail(val transactionId: String) : TransactionProgressNavigationEvent()
}
