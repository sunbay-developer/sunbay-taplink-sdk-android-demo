package com.sunmi.tapro.taplink.demo.ui.screens.detail

import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType

/**
 * Transaction Detail Screen State
 * 
 * Immutable data class representing the complete UI state of the Transaction Detail Screen.
 * Following MVI pattern for predictable state management.
 * 
 * All state changes create a new instance (copy) to ensure immutability.
 */
data class TransactionDetailState(
    // Transaction data
    val transactionId: String? = null,
    val transaction: Transaction? = null,
    /** Order items (what was ordered) for POS-style order detail */
    val orderItems: List<OrderItem> = emptyList(),

    // Available operations based on transaction type and status
    val availableOperations: List<TransactionType> = emptyList(),
    
    // UI control flags
    val isLoading: Boolean = true,
    val isQuerying: Boolean = false,
    val showOperationDialog: Boolean = false,
    val currentOperationType: TransactionType? = null,
    val isCardInfoExpanded: Boolean = false,
    
    // Error/Info messages
    val message: Message? = null,
    
    // Navigation events (one-time events)
    val navigationEvent: TransactionDetailNavigationEvent? = null
) {
    /**
     * Check if transaction data is loaded
     */
    fun isTransactionLoaded(): Boolean = transaction != null
    
    /**
     * Check if any operations are available
     */
    fun hasAvailableOperations(): Boolean = availableOperations.isNotEmpty()
    
    /**
     * Check if a specific operation is available
     */
    fun isOperationAvailable(operationType: TransactionType): Boolean {
        return availableOperations.contains(operationType)
    }
    
    /**
     * Get transaction type display name
     */
    fun getTransactionTypeDisplay(): String {
        return transaction?.type?.name ?: "Unknown"
    }
    
    /**
     * Get transaction status display name
     */
    fun getTransactionStatusDisplay(): String {
        return transaction?.status?.name ?: "Unknown"
    }
    
    /**
     * Check if transaction has additional amounts
     */
    fun hasAdditionalAmounts(): Boolean {
        return transaction?.hasAdditionalAmounts() == true
    }
    
    /**
     * Check if operation requires amount input
     */
    fun operationRequiresInput(operationType: TransactionType): Boolean {
        return when (operationType) {
            TransactionType.REFUND,
            TransactionType.TIP_ADJUST,
            TransactionType.INCREMENT_AUTH,
            TransactionType.POST_AUTH -> true
            TransactionType.SALE,
            TransactionType.AUTH,
            TransactionType.FORCED_AUTH,
            TransactionType.VOID,
            TransactionType.QUERY,
            TransactionType.BATCH_CLOSE -> false
        }
    }
    
    companion object {
        /**
         * Calculate available follow-up operations based on transaction type and status
         * 
         * Business rules:
         * - SALE transaction (SUCCESS): REFUND, VOID, TIP_ADJUST
         * - AUTH transaction (SUCCESS): VOID, POST_AUTH, INCREMENT_AUTH
         * - FORCED_AUTH transaction (SUCCESS): VOID
         * - REFUND transaction (SUCCESS): VOID
         * - POST_AUTH transaction (SUCCESS): VOID, REFUND, TIP_ADJUST
         * - Other transaction types or non-SUCCESS status: No follow-up operations
         */
        fun calculateAvailableOperations(transaction: Transaction?): List<TransactionType> {
            if (transaction == null || transaction.status != TransactionStatus.SUCCESS) {
                return emptyList()
            }
            
            return when (transaction.type) {
                TransactionType.SALE -> listOf(
                    TransactionType.REFUND,
                    TransactionType.VOID,
                    TransactionType.TIP_ADJUST
                )
                TransactionType.AUTH -> listOf(
                    TransactionType.VOID,
                    TransactionType.POST_AUTH,
                    TransactionType.INCREMENT_AUTH
                )
                TransactionType.FORCED_AUTH -> listOf(
                    TransactionType.VOID
                )
                TransactionType.REFUND -> listOf(
                    TransactionType.VOID
                )
                TransactionType.POST_AUTH -> listOf(
                    TransactionType.VOID,
                    TransactionType.REFUND,
                    TransactionType.TIP_ADJUST
                )
                else -> emptyList()
            }
        }
    }
}

/**
 * Navigation events for Transaction Detail Screen
 */
sealed class TransactionDetailNavigationEvent {
    /**
     * Navigate back to previous screen
     */
    object Back : TransactionDetailNavigationEvent()
    
    /**
     * Navigate to transaction progress screen for follow-up operation
     */
    data class ToProgress(val transactionId: String) : TransactionDetailNavigationEvent()
}
