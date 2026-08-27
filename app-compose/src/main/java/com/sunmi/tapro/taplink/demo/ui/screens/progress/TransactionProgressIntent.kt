package com.sunmi.tapro.taplink.demo.ui.screens.progress

import com.sunmi.tapro.taplink.demo.ui.screens.main.PaymentOption
import java.math.BigDecimal

/**
 * Sealed class representing all possible user intents/actions on the Transaction Progress Screen
 * Following MVI pattern for unidirectional data flow
 */
sealed class TransactionProgressIntent {
    /**
     * Load and observe transaction by ID
     */
    data class LoadTransaction(val transactionId: String) : TransactionProgressIntent()
    
    /**
     * Retry failed transaction
     */
    object RetryTransaction : TransactionProgressIntent()
    
    /**
     * Abort/cancel an ongoing transaction
     */
    object AbortTransaction : TransactionProgressIntent()
    
    /**
     * Navigate back to main screen
     */
    object NavigateBack : TransactionProgressIntent()
    
    /**
     * Navigate to transaction detail screen
     */
    object NavigateToDetail : TransactionProgressIntent()
    
    /**
     * Dismiss error message
     */
    object DismissMessage : TransactionProgressIntent()
    
    /**
     * Clear navigation event after handling
     */
    object ClearNavigationEvent : TransactionProgressIntent()
    
    /**
     * Query transaction status from payment terminal
     */
    object QueryTransaction : TransactionProgressIntent()

    /**
     * Switch current Headless transaction to manual card entry.
     */
    object SwitchToManualEntry : TransactionProgressIntent()

    /**
     * Select payment option before starting SDK request.
     */
    data class SelectPaymentOption(val option: PaymentOption) : TransactionProgressIntent()

    /**
     * Start SDK payment request with selected option.
     */
    object StartSelectedPayment : TransactionProgressIntent()

    /**
     * Toggle split mode for partial payment.
     */
    object ToggleSplitMode : TransactionProgressIntent()

    /**
     * Update split amount input (legacy single-line).
     */
    data class UpdateSplitAmount(val input: String) : TransactionProgressIntent()

    /**
     * Update split line count (+/-).
     */
    data class UpdateSplitCount(val count: Int) : TransactionProgressIntent()

    /**
     * Update the payment option for a specific split line.
     */
    data class UpdateSplitLinePaymentOption(val index: Int, val option: PaymentOption) : TransactionProgressIntent()

    /**
     * Update the amount input for a specific split line.
     */
    data class UpdateSplitLineAmount(val index: Int, val input: String) : TransactionProgressIntent()

    /**
     * Start payment for a specific split line.
     */
    data class StartSplitLinePayment(val index: Int) : TransactionProgressIntent()

    /**
     * Cancel the current order from READY_TO_PAY state, abort SDK session,
     * and navigate back to main so the user can continue editing the order.
     */
    object CancelOrder : TransactionProgressIntent()

    object StartSecondaryMemberInput : TransactionProgressIntent()

    object CancelSecondaryMemberInput : TransactionProgressIntent()

    data class ApplySecondaryMemberInput(val memberInput: String) : TransactionProgressIntent()

    object StartSecondaryTipSelection : TransactionProgressIntent()

    object CancelSecondaryTipSelection : TransactionProgressIntent()

    data class ApplySecondaryTip(val tipAmount: BigDecimal) : TransactionProgressIntent()
}
