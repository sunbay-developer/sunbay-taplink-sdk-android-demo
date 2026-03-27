package com.sunmi.tapro.taplink.demo.ui.screens.list

import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.OrderGroup
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.ui.screens.list.components.FilterType

/**
 * Navigation events for Transaction List Screen
 */
sealed class TransactionListNavigationEvent {
    /**
     * Navigate back to main screen
     */
    object ToMain : TransactionListNavigationEvent()
    
    /**
     * Navigate to transaction detail screen
     */
    data class ToDetail(val transactionId: String) : TransactionListNavigationEvent()
    
    /**
     * Navigate to transaction progress screen (for new operations)
     */
    data class ToProgress(val transactionId: String) : TransactionListNavigationEvent()
}

/**
 * Transaction List Screen State
 * 
 * Immutable data class representing the complete UI state of the Transaction List Screen.
 * Following MVI pattern for predictable state management.
 * 
 * All state changes create a new instance (copy) to ensure immutability.
 */
data class TransactionListState(
    // Transaction list data
    val transactions: List<Transaction> = emptyList(),
    val filteredTransactions: List<Transaction> = emptyList(),
    
    // Order groups (transactions grouped by referenceOrderId)
    val orderGroups: List<OrderGroup> = emptyList(),
    val filteredOrderGroups: List<OrderGroup> = emptyList(),
    
    // Loading and refresh states
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    
    // Filter options
    val selectedFilter: FilterType = FilterType.ALL,
    val filterByType: TransactionType? = null,
    val filterByStatus: TransactionStatus? = null,
    val searchQuery: String = "",
    
    // Dialog states
    val showQueryDialog: Boolean = false,
    val showBatchCloseDialog: Boolean = false,
    val showStandaloneRefundDialog: Boolean = false,
    val showClearAllDialog: Boolean = false,
    
    // Query transaction state
    val queryInProgress: Boolean = false,
    val queryResult: Transaction? = null,
    
    // Batch close state
    val batchCloseInProgress: Boolean = false,
    
    // Standalone refund state
    val standaloneRefundInProgress: Boolean = false,
    
    // Error information
    val message: Message? = null,
    
    // Navigation events (one-time events)
    val navigationEvent: TransactionListNavigationEvent? = null
)

/**
 * Extension functions for TransactionListState
 */

/**
 * Check if order list is empty
 * When filter is active, check filtered list; otherwise check all orders
 */
fun TransactionListState.isEmpty(): Boolean {
    return if (selectedFilter != FilterType.ALL) {
        filteredOrderGroups.isEmpty()
    } else {
        orderGroups.isEmpty()
    }
}

/**
 * Check if filtered list is empty
 */
fun TransactionListState.isFilteredEmpty(): Boolean = filteredTransactions.isEmpty()

/**
 * Check if any filter is active
 */
fun TransactionListState.hasActiveFilters(): Boolean {
    return selectedFilter != FilterType.ALL || filterByType != null || filterByStatus != null || searchQuery.isNotEmpty()
}

/**
 * Get total transaction count
 */
fun TransactionListState.getTotalCount(): Int = transactions.size

/**
 * Get filtered transaction count
 */
fun TransactionListState.getFilteredCount(): Int = filteredTransactions.size

/**
 * Get successful transaction count
 */
fun TransactionListState.getSuccessCount(): Int {
    return transactions.count { it.status == TransactionStatus.SUCCESS }
}

/**
 * Get failed transaction count
 */
fun TransactionListState.getFailedCount(): Int {
    return transactions.count { it.status == TransactionStatus.FAILED }
}

/**
 * Get pending transaction count
 */
fun TransactionListState.getPendingCount(): Int {
    return transactions.count { 
        it.status == TransactionStatus.PENDING || it.status == TransactionStatus.PROCESSING 
    }
}

/**
 * Check if any operation is in progress
 */
fun TransactionListState.isAnyOperationInProgress(): Boolean {
    return isLoading || isRefreshing || queryInProgress || 
           batchCloseInProgress || standaloneRefundInProgress
}

/**
 * Get display list (filtered or all) - returns OrderGroups
 */
fun TransactionListState.getDisplayList(): List<OrderGroup> {
    return if (selectedFilter != FilterType.ALL) {
        filteredOrderGroups
    } else if (hasActiveFilters()) {
        filteredOrderGroups
    } else {
        orderGroups
    }
}
