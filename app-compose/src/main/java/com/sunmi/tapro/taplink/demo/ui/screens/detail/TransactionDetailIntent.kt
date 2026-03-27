package com.sunmi.tapro.taplink.demo.ui.screens.detail

import com.sunmi.tapro.taplink.demo.model.TransactionType

/**
 * Sealed class representing all possible user intents/actions on the Transaction Detail Screen
 * Following MVI pattern for unidirectional data flow
 */
sealed class TransactionDetailIntent {
    /**
     * Load transaction details by ID
     */
    data class LoadTransaction(val transactionId: String) : TransactionDetailIntent()
    
    /**
     * Perform a follow-up operation on the transaction
     * @param operationType The type of follow-up operation (REFUND, VOID, TIP_ADJUST, etc.)
     */
    data class PerformOperation(val operationType: TransactionType) : TransactionDetailIntent()
    
    /**
     * Show input dialog for operation that requires amount input
     * @param operationType The operation type that requires input
     */
    data class ShowOperationDialog(val operationType: TransactionType) : TransactionDetailIntent()
    
    /**
     * Hide operation input dialog
     */
    object HideOperationDialog : TransactionDetailIntent()
    
    /**
     * Confirm operation with input amount
     * @param operationType The operation type
     * @param amount The amount for the operation (for REFUND, TIP_ADJUST, etc.)
     */
    data class ConfirmOperation(
        val operationType: TransactionType,
        val amount: String
    ) : TransactionDetailIntent()
    
    /**
     * Navigate back to previous screen
     */
    object NavigateBack : TransactionDetailIntent()
    
    /**
     * Dismiss error/info message
     */
    object DismissMessage : TransactionDetailIntent()
    
    /**
     * Clear navigation event after handling
     */
    object ClearNavigationEvent : TransactionDetailIntent()
    
    /**
     * Refresh transaction data
     */
    object RefreshTransaction : TransactionDetailIntent()
    
    /**
     * Query transaction status from payment terminal
     */
    object QueryTransaction : TransactionDetailIntent()
    
    /**
     * Toggle card information expanded state
     */
    object ToggleCardInfoExpanded : TransactionDetailIntent()
}
