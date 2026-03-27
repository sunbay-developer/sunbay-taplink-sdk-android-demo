package com.sunmi.tapro.taplink.demo.ui.screens.progress

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
}
