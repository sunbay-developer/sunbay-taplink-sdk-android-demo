package com.sunmi.tapro.taplink.demo.model

import java.math.BigDecimal

/**
 * Order Group
 * 
 * Groups multiple transactions by the same order ID (referenceOrderId).
 * Represents a complete order that may contain multiple transaction types
 * (e.g., SALE, REFUND, VOID, TIP_ADJUST, etc.)
 * 
 * @property orderId The reference order ID that groups transactions together
 * @property transactions List of all transactions for this order (sorted by timestamp descending)
 * @property primaryTransaction The primary transaction (usually SALE or AUTH), used for display
 * @property totalAmount The net total amount for all transactions in this order
 * @property latestTimestamp The timestamp of the most recent transaction
 */
data class OrderGroup(
    val orderId: String,
    val transactions: List<Transaction>,
    val primaryTransaction: Transaction,
    val totalAmount: BigDecimal,
    val latestTimestamp: Long
) {
    /**
     * Get order number for display (short format)
     * Uses hash of orderId to ensure uniqueness while keeping it readable
     */
    fun getOrderNumber(): String {
        // Use absolute value of hashCode to ensure positive number
        // Modulo by 1000000 to get 6-digit number (000000-999999)
        val hashNumber = orderId.hashCode().let { if (it < 0) -it else it } % 1000000
        return hashNumber.toString().padStart(6, '0')
    }
    
    /**
     * Check if order has any successful transactions
     */
    fun hasSuccessfulTransaction(): Boolean {
        return transactions.any { it.isSuccess() }
    }
    
    /**
     * Check if order has any failed transactions
     */
    fun hasFailedTransaction(): Boolean {
        return transactions.any { it.isFailed() }
    }
    
    /**
     * Get overall order status based on transactions.
     *
     * Logic:
     * - Any PROCESSING → PROCESSING
     * - Any PENDING → PENDING
     * - Check the latest transaction in the series:
     *   - If the latest is FAILED and no SUCCESS after it → FAILED
     *   - If there is any SUCCESS after a FAILED → SUCCESS
     * - Any SUCCESS → SUCCESS
     * - Fallback → status of the most recent transaction
     */
    fun getOrderStatus(): TransactionStatus {
        return when {
            transactions.any { it.status == TransactionStatus.PROCESSING } -> TransactionStatus.PROCESSING
            transactions.any { it.status == TransactionStatus.PENDING } -> TransactionStatus.PENDING
            else -> {
                // Transactions are sorted by timestamp descending (newest first).
                // Only mark as FAILED when the most recent transaction in the series is FAILED
                // and there is no successful transaction after (newer than) the last failure.
                val sorted = transactions.sortedByDescending { it.timestamp }
                val latestFailedIndex = sorted.indexOfFirst { it.status == TransactionStatus.FAILED }
                val latestSuccessIndex = sorted.indexOfFirst { it.status == TransactionStatus.SUCCESS }

                when {
                    // No failures at all, but has success
                    latestFailedIndex == -1 && latestSuccessIndex != -1 -> TransactionStatus.SUCCESS
                    // Has failure, and the latest success is newer than the latest failure → SUCCESS
                    latestFailedIndex != -1 && latestSuccessIndex != -1 && latestSuccessIndex < latestFailedIndex -> TransactionStatus.SUCCESS
                    // Has failure, and no success is newer than the latest failure → FAILED
                    latestFailedIndex != -1 && (latestSuccessIndex == -1 || latestSuccessIndex > latestFailedIndex) -> TransactionStatus.FAILED
                    // Fallback
                    else -> sorted.firstOrNull()?.status ?: TransactionStatus.PENDING
                }
            }
        }
    }
    
    /**
     * Get transactions grouped by type for display
     */
    fun getTransactionsByType(): Map<TransactionType, List<Transaction>> {
        return transactions.groupBy { it.type }
    }
    
    /**
     * Check if order can be refunded
     */
    fun canRefund(): Boolean {
        return primaryTransaction.canRefund()
    }
    
    /**
     * Check if order can be voided
     */
    fun canVoid(): Boolean {
        return transactions.any { it.canVoid() }
    }
}

/**
 * Group transactions by order ID
 * 
 * @param transactions List of transactions to group
 * @return List of OrderGroup sorted by latest timestamp descending
 */
fun groupTransactionsByOrder(transactions: List<Transaction>): List<OrderGroup> {
    // Special handling for transactions without order ID (BATCH_CLOSE, standalone REFUND, etc.)
    // These are treated as individual orders
    
    val grouped = transactions.groupBy { transaction ->
        transaction.referenceOrderId ?: transaction.transactionRequestId
    }
    
    return grouped.map { (orderId, orderTransactions) ->
        // Sort transactions by timestamp descending (newest first)
        val sortedTransactions = orderTransactions.sortedByDescending { it.timestamp }
        
        // Find primary transaction: use the last (most recent) successful transaction
        // If first transaction is failed, the order is failed
        val primaryTransaction = sortedTransactions.firstOrNull { it.isSuccess() } 
            ?: sortedTransactions.first()
        
        // Determine order amount display logic:
        // 1. If has POST_AUTH (success) -> show POST_AUTH amount
        // 2. Else if has AUTH -> show AUTH amount (+ INCREMENT_AUTH if any)
        // 3. Else if has SALE -> show SALE amount
        // 4. Other types don't affect order amount
        
        val postAuthTx = sortedTransactions.find { 
            it.type == TransactionType.POST_AUTH && it.isSuccess() 
        }
        val authTx = sortedTransactions.find { 
            it.type == TransactionType.AUTH && it.isSuccess() 
        }
        val saleTx = sortedTransactions.find { 
            it.type == TransactionType.SALE && it.isSuccess() 
        }
        
        val (adjustedPrimaryTransaction, totalAmount) = when {
            // Priority 1: POST_AUTH
            postAuthTx != null -> {
                postAuthTx to postAuthTx.getDisplayAmount()
            }
            // Priority 2: AUTH (with INCREMENT_AUTH cumulative)
            authTx != null -> {
                val incrementAuthSum = sortedTransactions
                    .filter { it.type == TransactionType.INCREMENT_AUTH && it.isSuccess() }
                    .sumOf { it.getDisplayAmount() }
                
                if (incrementAuthSum > BigDecimal.ZERO) {
                    val adjustedAuth = authTx.copy(
                        totalAmount = (authTx.totalAmount ?: authTx.amount).add(incrementAuthSum)
                    )
                    adjustedAuth to adjustedAuth.getDisplayAmount()
                } else {
                    authTx to authTx.getDisplayAmount()
                }
            }
            // Priority 3: SALE
            saleTx != null -> {
                saleTx to saleTx.getDisplayAmount()
            }
            // Fallback: use primary transaction (latest successful)
            else -> {
                primaryTransaction to primaryTransaction.getDisplayAmount()
            }
        }
        
        // Use latest transaction timestamp
        val latestTimestamp = sortedTransactions.maxOf { it.timestamp }
        
        OrderGroup(
            orderId = orderId,
            transactions = sortedTransactions,
            primaryTransaction = adjustedPrimaryTransaction,
            totalAmount = totalAmount,
            latestTimestamp = latestTimestamp
        )
    }.sortedByDescending { it.latestTimestamp }
}
