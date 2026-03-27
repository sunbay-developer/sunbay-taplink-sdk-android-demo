package com.sunmi.tapro.taplink.demo.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sunmi.tapro.taplink.demo.model.BatchCloseInfo
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

/**
 * Transaction Repository (Singleton)
 * 
 * Responsible for managing CRUD operations for transaction records, using SharedPreferences for persistence
 * 
 * Features:
 * - Add new transaction records
 * - Update transaction records
 * - Delete transaction records
 * - Query transaction records (by ID, order number, type, etc.)
 * - Get all transaction records
 * - Reactive updates via StateFlow
 * 
 * Note:
 * - Uses SharedPreferences for persistent storage
 * - Data persists across app restarts
 * - Provides StateFlow for reactive UI updates
 */
class TransactionRepository private constructor(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "transaction_repository"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val KEY_ORDER_ITEMS = "order_items"
        
        @Volatile
        private var instance: TransactionRepository? = null
        
        fun getInstance(context: Context): TransactionRepository {
            return instance ?: synchronized(this) {
                instance ?: TransactionRepository(context.applicationContext).also { instance = it }
            }
        }
        
        // Backward compatibility methods - delegate to singleton instance
        // These will be removed in Phase 11 when legacy code is deleted
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun addTransaction(transaction: Transaction): Boolean {
            return instance?.addTransaction(transaction) ?: false
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun updateTransaction(
            transactionRequestId: String,
            updater: (Transaction) -> Transaction
        ): Boolean {
            return instance?.updateTransaction(transactionRequestId, updater) ?: false
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun updateTransactionStatus(
            transactionRequestId: String,
            status: TransactionStatus,
            transactionId: String? = null,
            authCode: String? = null,
            errorCode: String? = null,
            errorMessage: String? = null
        ): Boolean {
            return instance?.updateTransactionStatus(
                transactionRequestId, status, transactionId, authCode, errorCode, errorMessage
            ) ?: false
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun updateTransactionWithAmounts(
            transactionRequestId: String,
            status: TransactionStatus,
            transactionId: String? = null,
            authCode: String? = null,
            errorCode: String? = null,
            errorMessage: String? = null,
            orderAmount: BigDecimal? = null,
            totalAmount: BigDecimal? = null,
            tipAmount: BigDecimal? = null,
            taxAmount: BigDecimal? = null,
            cashbackAmount: BigDecimal? = null,
            serviceFee: BigDecimal? = null,
            batchNo: Int? = null,
            batchCloseInfo: BatchCloseInfo? = null,
            completeTime: String? = null,
            cardInfo: com.sunmi.tapro.taplink.demo.model.CardInfo? = null
        ): Boolean {
            return instance?.updateTransactionWithAmounts(
                transactionRequestId, status, transactionId, authCode, errorCode, errorMessage,
                orderAmount, totalAmount, tipAmount, taxAmount, cashbackAmount,
                serviceFee, batchNo, batchCloseInfo, completeTime, cardInfo
            ) ?: false
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun deleteTransaction(transactionRequestId: String): Boolean {
            return instance?.deleteTransaction(transactionRequestId) ?: false
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getTransactionByRequestId(transactionRequestId: String): Transaction? {
            return instance?.getTransactionByRequestId(transactionRequestId)
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getTransactionById(transactionId: String): Transaction? {
            return instance?.getTransactionById(transactionId)
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getTransactionsByOrderId(referenceOrderId: String): List<Transaction> {
            return instance?.getTransactionsByOrderId(referenceOrderId) ?: emptyList()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getTransactionsByType(type: TransactionType): List<Transaction> {
            return instance?.getTransactionsByType(type) ?: emptyList()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getTransactionsByStatus(status: TransactionStatus): List<Transaction> {
            return instance?.getTransactionsByStatus(status) ?: emptyList()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getAllTransactions(): List<Transaction> {
            return instance?.getAllTransactions() ?: emptyList()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getSuccessfulTransactions(): List<Transaction> {
            return instance?.getSuccessfulTransactions() ?: emptyList()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getFailedTransactions(): List<Transaction> {
            return instance?.getFailedTransactions() ?: emptyList()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun getTransactionCount(): Int {
            return instance?.getTransactionCount() ?: 0
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun clearAllTransactions() {
            instance?.clearAllTransactions()
        }
        
        @Deprecated("Use getInstance(context) instead", ReplaceWith("getInstance(context)"))
        fun isTransactionRequestIdExists(transactionRequestId: String): Boolean {
            return instance?.isTransactionRequestIdExists(transactionRequestId) ?: false
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val gson = Gson()
    
    /**
     * Transaction records list (in-memory cache)
     * Using MutableList to store, sorted in reverse chronological order (newest first)
     */
    private val transactions = mutableListOf<Transaction>()
    
    /**
     * StateFlow for reactive transaction updates
     */
    private val _transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    val transactionsFlow: StateFlow<List<Transaction>> = _transactionsFlow.asStateFlow()
    
    /**
     * Order items by referenceOrderId (for POS order content display)
     */
    private val orderItemsByOrderId = mutableMapOf<String, List<OrderItem>>()

    init {
        loadTransactions()
        loadOrderItems()
    }

    /**
     * Load order items map from SharedPreferences
     */
    private fun loadOrderItems() {
        try {
            val json = sharedPreferences.getString(KEY_ORDER_ITEMS, null)
            if (json != null) {
                val type = object : TypeToken<Map<String, List<OrderItem>>>() {}.type
                val loaded: Map<String, List<OrderItem>> = gson.fromJson(json, type)
                orderItemsByOrderId.clear()
                orderItemsByOrderId.putAll(loaded)
            }
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Failed to load order items", e)
        }
    }

    /**
     * Save order items map to SharedPreferences
     */
    private fun saveOrderItemsMap() {
        try {
            val json = gson.toJson(orderItemsByOrderId)
            sharedPreferences.edit().putString(KEY_ORDER_ITEMS, json).apply()
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Failed to save order items", e)
        }
    }
    
    /**
     * Save transactions to SharedPreferences
     */
    private fun saveTransactions() {
        try {
            val json = gson.toJson(transactions)
            sharedPreferences.edit().putString(KEY_TRANSACTIONS, json).apply()
            _transactionsFlow.value = transactions.toList()
        } catch (e: Exception) {
            // Log error but don't crash
            android.util.Log.e("TransactionRepository", "Failed to save transactions", e)
        }
    }
    
    /**
     * Load transactions from SharedPreferences
     */
    private fun loadTransactions() {
        try {
            val json = sharedPreferences.getString(KEY_TRANSACTIONS, null)
            if (json != null) {
                val type = object : TypeToken<List<Transaction>>() {}.type
                val loadedTransactions: List<Transaction> = gson.fromJson(json, type)
                transactions.clear()
                transactions.addAll(loadedTransactions)
                _transactionsFlow.value = transactions.toList()
            }
        } catch (e: Exception) {
            // Log error but don't crash, start with empty list
            android.util.Log.e("TransactionRepository", "Failed to load transactions", e)
            transactions.clear()
            _transactionsFlow.value = emptyList()
        }
    }
    
    /**
     * Add transaction record
     * 
     * @param transaction Transaction record to add
     * @return Returns true if added successfully, false if transactionRequestId already exists
     */
    fun addTransaction(transaction: Transaction): Boolean {
        // Check if transactionRequestId already exists
        if (transactions.any { it.transactionRequestId == transaction.transactionRequestId }) {
            return false
        }
        
        // Add to the beginning of the list (maintain reverse chronological order)
        transactions.add(0, transaction)
        saveTransactions()
        return true
    }
    
    /**
     * Update transaction record
     * 
     * @param transactionRequestId Transaction request ID to update
     * @param updater Update function that receives the old transaction and returns the new one
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransaction(
        transactionRequestId: String,
        updater: (Transaction) -> Transaction
    ): Boolean {
        val index = transactions.indexOfFirst { it.transactionRequestId == transactionRequestId }
        if (index == -1) {
            return false
        }
        
        val oldTransaction = transactions[index]
        val newTransaction = updater(oldTransaction)
        transactions[index] = newTransaction
        saveTransactions()
        return true
    }
    
    /**
     * Update transaction status
     * 
     * @param transactionRequestId Transaction request ID
     * @param status New status
     * @param transactionId Nexus transaction serial number (optional)
     * @param authCode Authorization code (optional)
     * @param errorCode Error code (optional)
     * @param errorMessage Error message (optional)
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransactionStatus(
        transactionRequestId: String,
        status: TransactionStatus,
        transactionId: String? = null,
        authCode: String? = null,
        errorCode: String? = null,
        errorMessage: String? = null
    ): Boolean {
        return updateTransaction(transactionRequestId) { transaction ->
            transaction.copy(
                status = status,
                transactionId = transactionId ?: transaction.transactionId,
                authCode = authCode ?: transaction.authCode,
                errorCode = errorCode ?: transaction.errorCode,
                errorMessage = errorMessage ?: transaction.errorMessage
            )
        }
    }
    
    /**
     * Update transaction progress information
     * 
     * @param transactionRequestId Transaction request ID
     * @param progressStatus Progress status from onProgress callback
     * @param progressMessage Progress message from onProgress callback
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransactionProgress(
        transactionRequestId: String,
        progressStatus: String,
        progressMessage: String
    ): Boolean {
        return updateTransaction(transactionRequestId) { transaction ->
            transaction.copy(
                progressStatus = progressStatus,
                progressMessage = progressMessage
            )
        }
    }
    
    /**
     * Update transaction with complete amount information from SDK result
     * 
     * @param transactionRequestId Transaction request ID
     * @param status Transaction status
     * @param transactionId Nexus transaction serial number (optional)
     * @param authCode Authorization code (optional)
     * @param errorCode Error code (optional)
     * @param errorMessage Error message (optional)
     * @param orderAmount Order base amount from SDK (optional)
     * @param totalAmount Total transaction amount from SDK (optional)
     * @param tipAmount Tip amount from SDK (optional)
     * @param cashbackAmount Cashback amount from SDK (optional)
     * @param batchNo Batch number (optional, for BATCH_CLOSE)
     * @param batchCloseInfo Batch close information (optional, for BATCH_CLOSE)
     * @return Returns true if update successful, false if record not found
     */
    fun updateTransactionWithAmounts(
        transactionRequestId: String,
        status: TransactionStatus,
        transactionId: String? = null,
        authCode: String? = null,
        errorCode: String? = null,
        errorMessage: String? = null,
        orderAmount: BigDecimal? = null,
        totalAmount: BigDecimal? = null,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        cashbackAmount: BigDecimal? = null,
        serviceFee: BigDecimal? = null,
        batchNo: Int? = null,
        batchCloseInfo: BatchCloseInfo? = null,
        completeTime: String? = null,
        cardInfo: com.sunmi.tapro.taplink.demo.model.CardInfo? = null
    ): Boolean {
        return updateTransaction(transactionRequestId) { transaction ->
            transaction.copy(
                status = status,
                transactionId = transactionId ?: transaction.transactionId,
                authCode = authCode ?: transaction.authCode,
                errorCode = errorCode ?: transaction.errorCode,
                errorMessage = errorMessage ?: transaction.errorMessage,
                // For BatchClose transactions, keep original amount if SDK doesn't return amount info
                amount = if (orderAmount != null) orderAmount else transaction.amount,
                totalAmount = if (totalAmount != null) totalAmount else transaction.totalAmount,
                tipAmount = if (tipAmount != null) tipAmount else transaction.tipAmount,
                taxAmount = if (taxAmount != null) taxAmount else transaction.taxAmount,
                cashbackAmount = if (cashbackAmount != null) cashbackAmount else transaction.cashbackAmount,
                serviceFee = if (serviceFee != null) serviceFee else transaction.serviceFee,
                batchNo = batchNo ?: transaction.batchNo,
                batchCloseInfo = batchCloseInfo ?: transaction.batchCloseInfo,
                completeTime = completeTime ?: transaction.completeTime,
                cardInfo = cardInfo ?: transaction.cardInfo
            )
        }
    }
    
    /**
     * Delete transaction record
     * 
     * @param transactionRequestId Transaction request ID to delete
     * @return Returns true if deletion successful, false if record not found
     */
    fun deleteTransaction(transactionRequestId: String): Boolean {
        val index = transactions.indexOfFirst { it.transactionRequestId == transactionRequestId }
        if (index == -1) {
            return false
        }
        
        transactions.removeAt(index)
        saveTransactions()
        return true
    }
    
    /**
     * Query transaction by transaction request ID
     * 
     * @param transactionRequestId Transaction request ID to query
     * @return Found transaction record, or null if not found
     */
    fun getTransactionByRequestId(transactionRequestId: String): Transaction? {
        return transactions.find { it.transactionRequestId == transactionRequestId }
    }
    
    /**
     * Query transaction by Nexus transaction ID
     * 
     * @param transactionId Nexus transaction serial number
     * @return Found transaction record, or null if not found
     */
    fun getTransactionById(transactionId: String): Transaction? {
        return transactions.find { it.transactionId == transactionId }
    }
    
    /**
     * Query all transactions by order number
     * 
     * @param referenceOrderId Reference order ID
     * @return All transaction records for the order (reverse chronological order)
     */
    fun getTransactionsByOrderId(referenceOrderId: String): List<Transaction> {
        return transactions.filter { it.referenceOrderId == referenceOrderId }
    }
    
    /**
     * Query transactions by type
     * 
     * @param type Transaction type
     * @return All transactions of the type (reverse chronological order)
     */
    fun getTransactionsByType(type: TransactionType): List<Transaction> {
        return transactions.filter { it.type == type }
    }
    
    /**
     * Query transactions by status
     * 
     * @param status Transaction status
     * @return All transactions with the status (reverse chronological order)
     */
    fun getTransactionsByStatus(status: TransactionStatus): List<Transaction> {
        return transactions.filter { it.status == status }
    }
    
    /**
     * Get all transaction records
     * 
     * @return All transaction records (reverse chronological order)
     */
    fun getAllTransactions(): List<Transaction> {
        return transactions.toList()
    }
    
    /**
     * Get successful transaction records
     * 
     * @return All successful transaction records (reverse chronological order)
     */
    fun getSuccessfulTransactions(): List<Transaction> {
        return transactions.filter { it.status == TransactionStatus.SUCCESS }
    }
    
    /**
     * Get failed transaction records
     * 
     * @return All failed transaction records (reverse chronological order)
     */
    fun getFailedTransactions(): List<Transaction> {
        return transactions.filter { it.status == TransactionStatus.FAILED }
    }
    
    /**
     * Get transaction count
     * 
     * @return Total number of transaction records
     */
    fun getTransactionCount(): Int {
        return transactions.size
    }
    
    /**
     * Clear all transaction records
     */
    fun clearAllTransactions() {
        transactions.clear()
        saveTransactions()
    }
    
    /**
     * Check if transaction request ID exists (for idempotency check)
     * 
     * @param transactionRequestId Transaction request ID
     * @return True if exists, false otherwise
     */
    fun isTransactionRequestIdExists(transactionRequestId: String): Boolean {
        return transactions.any { it.transactionRequestId == transactionRequestId }
    }

    /**
     * Save order items for a given order (POS: persist what was ordered for receipt/detail)
     *
     * @param referenceOrderId Order ID (same as transaction.referenceOrderId)
     * @param items List of order items (product + quantity)
     */
    fun saveOrderItems(referenceOrderId: String, items: List<OrderItem>) {
        if (items.isEmpty()) return
        orderItemsByOrderId[referenceOrderId] = items
        saveOrderItemsMap()
    }

    /**
     * Get order items for a given order (for order detail screen)
     *
     * @param referenceOrderId Order ID
     * @return List of order items, or null if not found
     */
    fun getOrderItems(referenceOrderId: String): List<OrderItem>? {
        return orderItemsByOrderId[referenceOrderId]
    }
}
