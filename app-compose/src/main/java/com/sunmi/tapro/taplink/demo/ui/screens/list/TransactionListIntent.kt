package com.sunmi.tapro.taplink.demo.ui.screens.list

import com.sunmi.tapro.taplink.demo.ui.screens.list.components.FilterType

/**
 * Sealed class representing all possible user intents/actions on the Transaction List Screen
 * Following MVI pattern for unidirectional data flow
 */
sealed class TransactionListIntent {
    /**
     * Load transaction list
     */
    object LoadTransactions : TransactionListIntent()
    
    /**
     * Refresh transaction list
     */
    object RefreshTransactions : TransactionListIntent()
    
    /**
     * Select filter for transaction list
     */
    data class SelectFilter(val filter: FilterType) : TransactionListIntent()
    
    /**
     * Navigate to transaction detail screen
     */
    data class NavigateToDetail(val transactionId: String) : TransactionListIntent()
    
    /**
     * Navigate back to main screen
     */
    object NavigateBack : TransactionListIntent()
    
    /**
     * Query transaction by request ID or transaction ID
     */
    data class QueryTransaction(val queryId: String) : TransactionListIntent()
    
    /**
     * Show query transaction dialog
     */
    object ShowQueryDialog : TransactionListIntent()
    
    /**
     * Hide query transaction dialog
     */
    object HideQueryDialog : TransactionListIntent()
    
    /**
     * Perform batch close operation
     */
    object BatchClose : TransactionListIntent()
    
    /**
     * Show batch close confirmation dialog
     */
    object ShowBatchCloseDialog : TransactionListIntent()
    
    /**
     * Hide batch close confirmation dialog
     */
    object HideBatchCloseDialog : TransactionListIntent()
    
    /**
     * Perform standalone refund operation
     */
    data class StandaloneRefund(val amount: String) : TransactionListIntent()
    
    /**
     * Show standalone refund dialog
     */
    object ShowStandaloneRefundDialog : TransactionListIntent()
    
    /**
     * Hide standalone refund dialog
     */
    object HideStandaloneRefundDialog : TransactionListIntent()
    
    /**
     * Clear all transactions
     */
    object ClearAllTransactions : TransactionListIntent()
    
    /**
     * Show clear all transactions confirmation dialog
     */
    object ShowClearAllDialog : TransactionListIntent()
    
    /**
     * Hide clear all transactions confirmation dialog
     */
    object HideClearAllDialog : TransactionListIntent()
    
    /**
     * Dismiss error message
     */
    object DismissMessage : TransactionListIntent()
    
    /**
     * Clear navigation event after handling
     */
    object ClearNavigationEvent : TransactionListIntent()
}
