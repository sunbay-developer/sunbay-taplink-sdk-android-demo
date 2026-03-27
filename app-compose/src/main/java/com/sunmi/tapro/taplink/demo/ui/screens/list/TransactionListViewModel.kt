package com.sunmi.tapro.taplink.demo.ui.screens.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.model.groupTransactionsByOrder
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.demo.ui.screens.list.components.FilterType
import com.sunmi.tapro.taplink.demo.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.Calendar

/**
 * Transaction List Screen ViewModel
 * 
 * Implements MVI (Model-View-Intent) architecture pattern for the Transaction List Screen.
 * Manages UI state, handles user intents, and coordinates business logic.
 * 
 * Key Responsibilities:
 * - Transaction list management and display
 * - Query transaction by request ID or transaction ID
 * - Batch close operation
 * - Standalone refund operation
 * - Transaction filtering and search
 * - Error handling with Message model
 * - Navigation event management
 * 
 * MVI Pattern:
 * - Intent: User actions (TransactionListIntent sealed class)
 * - Model: UI state (TransactionListState data class)
 * - View: Composable UI that observes state
 * 
 * State Management:
 * - Immutable state updates using copy()
 * - Single source of truth (StateFlow)
 * - Unidirectional data flow
 */
class TransactionListViewModel(
    application: Application,
    private val paymentService: PaymentService = DependencyProvider.paymentService,
    private val transactionRepository: TransactionRepository = DependencyProvider.transactionRepository
) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "TransactionListViewModel"
        private const val CURRENCY = "USD"
    }
    
    // MVI State
    private val _state = MutableStateFlow(TransactionListState())
    val state: StateFlow<TransactionListState> = _state.asStateFlow()
    
    init {
        loadTransactions()
        observeTransactionRepository()
    }
    
    /**
     * Handle user intents
     * 
     * Central intent handler following MVI pattern.
     * All user actions are routed through this method.
     * 
     * @param intent User intent to handle
     */
    fun handleIntent(intent: TransactionListIntent) {
        when (intent) {
            is TransactionListIntent.LoadTransactions -> loadTransactions()
            is TransactionListIntent.RefreshTransactions -> refreshTransactions()
            is TransactionListIntent.SelectFilter -> selectFilter(intent.filter)
            is TransactionListIntent.NavigateToDetail -> navigateToDetail(intent.transactionId)
            is TransactionListIntent.NavigateBack -> navigateBack()
            is TransactionListIntent.QueryTransaction -> queryTransaction(intent.queryId)
            is TransactionListIntent.ShowQueryDialog -> showQueryDialog()
            is TransactionListIntent.HideQueryDialog -> hideQueryDialog()
            is TransactionListIntent.BatchClose -> batchClose()
            is TransactionListIntent.ShowBatchCloseDialog -> showBatchCloseDialog()
            is TransactionListIntent.HideBatchCloseDialog -> hideBatchCloseDialog()
            is TransactionListIntent.StandaloneRefund -> standaloneRefund(intent.amount)
            is TransactionListIntent.ShowStandaloneRefundDialog -> showStandaloneRefundDialog()
            is TransactionListIntent.HideStandaloneRefundDialog -> hideStandaloneRefundDialog()
            is TransactionListIntent.ClearAllTransactions -> clearAllTransactions()
            is TransactionListIntent.ShowClearAllDialog -> showClearAllDialog()
            is TransactionListIntent.HideClearAllDialog -> hideClearAllDialog()
            is TransactionListIntent.DismissMessage -> dismissMessage()
            is TransactionListIntent.ClearNavigationEvent -> clearNavigationEvent()
        }
    }
    
    // Intent Handlers
    
    /**
     * Load transactions from repository
     * 
     * Loads all transactions from the repository and updates the state.
     * Sets loading flag during the operation.
     */
    private fun loadTransactions() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isLoading = true) }
                
                val transactions = transactionRepository.getAllTransactions()
                val filtered = applyFilter(transactions, _state.value.selectedFilter)
                
                // Group transactions by order
                val orderGroups = groupTransactionsByOrder(transactions)
                val filteredOrderGroups = groupTransactionsByOrder(filtered)
                
                _state.update {
                    it.copy(
                        transactions = transactions,
                        filteredTransactions = filtered,
                        orderGroups = orderGroups,
                        filteredOrderGroups = filteredOrderGroups,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to load transactions", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Load Error",
                            content = e.message ?: "Failed to load transactions",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Refresh transactions
     * 
     * Reloads transactions from repository with refresh indicator.
     * Used for pull-to-refresh functionality.
     */
    private fun refreshTransactions() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(isRefreshing = true) }
                
                val transactions = transactionRepository.getAllTransactions()
                val filtered = applyFilter(transactions, _state.value.selectedFilter)
                
                // Group transactions by order
                val orderGroups = groupTransactionsByOrder(transactions)
                val filteredOrderGroups = groupTransactionsByOrder(filtered)
                
                _state.update {
                    it.copy(
                        transactions = transactions,
                        filteredTransactions = filtered,
                        orderGroups = orderGroups,
                        filteredOrderGroups = filteredOrderGroups,
                        isRefreshing = false
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to refresh transactions", e)
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Refresh Error",
                            content = e.message ?: "Failed to refresh transactions",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Select filter
     * 
     * Updates the selected filter and applies it to the transaction list.
     * 
     * @param filter Filter type to apply
     */
    private fun selectFilter(filter: FilterType) {
        viewModelScope.launch {
            val filtered = applyFilter(_state.value.transactions, filter)
            val filteredOrderGroups = groupTransactionsByOrder(filtered)
            _state.update {
                it.copy(
                    selectedFilter = filter,
                    filteredTransactions = filtered,
                    filteredOrderGroups = filteredOrderGroups
                )
            }
        }
    }
    
    /**
     * Apply filter to transaction list
     * 
     * Filters transactions based on the selected filter type:
     * - ALL: Show all transactions
     * - TODAY: Show transactions from today
     * - SUCCESS: Show successful transactions
     * - FAILED: Show failed transactions
     * - PENDING: Show pending/processing transactions
     * - CANCELLED: Show cancelled transactions
     * 
     * @param transactions List of transactions to filter
     * @param filter Filter type to apply
     * @return Filtered list of transactions
     */
    private fun applyFilter(transactions: List<Transaction>, filter: FilterType): List<Transaction> {
        return when (filter) {
            FilterType.ALL -> transactions
            FilterType.TODAY -> {
                val calendar = Calendar.getInstance()
                val todayStart = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val todayEnd = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis
                
                transactions.filter { transaction ->
                    transaction.timestamp in todayStart..todayEnd
                }
            }
            FilterType.SUCCESS -> {
                transactions.filter { it.status == TransactionStatus.SUCCESS }
            }
            FilterType.FAILED -> {
                transactions.filter { it.status == TransactionStatus.FAILED }
            }
            FilterType.PENDING -> {
                transactions.filter { 
                    it.status == TransactionStatus.PENDING || it.status == TransactionStatus.PROCESSING 
                }
            }
            FilterType.CANCELLED -> {
                transactions.filter { it.status == TransactionStatus.CANCELLED }
            }
        }
    }
    
    /**
     * Observe transaction repository for changes
     * 
     * Monitors the transaction repository StateFlow and updates the UI
     * whenever transactions change (add, update, delete).
     */
    private fun observeTransactionRepository() {
        viewModelScope.launch {
            transactionRepository.transactionsFlow.collect { transactions ->
                val filtered = applyFilter(transactions, _state.value.selectedFilter)
                
                // Group transactions by order
                val orderGroups = groupTransactionsByOrder(transactions)
                val filteredOrderGroups = groupTransactionsByOrder(filtered)
                
                _state.update {
                    it.copy(
                        transactions = transactions,
                        filteredTransactions = filtered,
                        orderGroups = orderGroups,
                        filteredOrderGroups = filteredOrderGroups
                    )
                }
            }
        }
    }
    
    /**
     * Query transaction by request ID or transaction ID
     * 
     * Queries a transaction from the payment service using either
     * transaction request ID or transaction ID.
     * 
     * @param queryId Transaction request ID or transaction ID to query
     */
    private fun queryTransaction(queryId: String) {
        viewModelScope.launch {
            try {
                if (queryId.isBlank()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Invalid Input",
                                content = "Please enter a transaction ID or request ID",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }
                
                _state.update { 
                    it.copy(
                        queryInProgress = true,
                        showQueryDialog = false
                    ) 
                }
                
                // Try to determine if it's a transaction ID or request ID
                val isRequestId = queryId.contains("REQ")
                
                if (isRequestId) {
                    paymentService.executeQuery(
                        transactionRequestId = queryId,
                        callback = createQueryCallback(queryId)
                    )
                } else {
                    paymentService.executeQueryByTransactionId(
                        transactionId = queryId,
                        callback = createQueryCallback(queryId)
                    )
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to query transaction", e)
                _state.update {
                    it.copy(
                        queryInProgress = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Query Error",
                            content = e.message ?: "Failed to query transaction",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Batch close operation
     * 
     * Executes a batch close operation to settle all pending transactions.
     * Creates a new transaction record for the batch close operation.
     */
    private fun batchClose() {
        viewModelScope.launch {
            try {
                _state.update { 
                    it.copy(
                        batchCloseInProgress = true,
                        showBatchCloseDialog = false
                    ) 
                }
                
                // Generate transaction request ID for batch close
                val timestamp = System.currentTimeMillis()
                val transactionRequestId = "${Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"
                
                // Create batch close transaction record
                val transaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = "BATCH-$timestamp",
                    type = TransactionType.BATCH_CLOSE,
                    status = TransactionStatus.PENDING,
                    amount = BigDecimal.ZERO,
                    totalAmount = BigDecimal.ZERO,
                    currency = "USD",
                    timestamp = System.currentTimeMillis()
                )
                
                // Add to repository
                transactionRepository.addTransaction(transaction)
                
                // Execute batch close
                paymentService.executeBatchClose(
                    transactionRequestId = transactionRequestId,
                    description = "Batch Close",
                    callback = createBatchCloseCallback(transactionRequestId)
                )
                
                // Navigate to progress screen
                _state.update { 
                    it.copy(
                        navigationEvent = TransactionListNavigationEvent.ToProgress(transactionRequestId)
                    ) 
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to execute batch close", e)
                _state.update {
                    it.copy(
                        batchCloseInProgress = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Batch Close Error",
                            content = e.message ?: "Failed to execute batch close",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Standalone refund operation
     * 
     * Executes a standalone refund operation without referencing an original transaction.
     * Creates a new transaction record for the refund.
     * 
     * @param amount Refund amount as string
     */
    private fun standaloneRefund(amount: String) {
        viewModelScope.launch {
            try {
                // Validate amount
                val refundAmount = try {
                    BigDecimal(amount)
                } catch (e: NumberFormatException) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Invalid Amount",
                                content = "Please enter a valid amount",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }
                
                if (refundAmount <= BigDecimal.ZERO) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Invalid Amount",
                                content = "Amount must be greater than zero",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }
                
                _state.update { 
                    it.copy(
                        standaloneRefundInProgress = true,
                        showStandaloneRefundDialog = false
                    ) 
                }
                
                // Generate IDs
                val timestamp = System.currentTimeMillis()
                val referenceOrderId = "REFUND-$timestamp"
                val transactionRequestId = "${Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"
                
                // Create refund transaction record
                val transaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    type = TransactionType.REFUND,
                    status = TransactionStatus.PENDING,
                    amount = refundAmount,
                    totalAmount = refundAmount,
                    currency = "USD",
                    timestamp = System.currentTimeMillis()
                )
                
                // Add to repository
                transactionRepository.addTransaction(transaction)
                
                // Execute refund (unreferenced refund: both original IDs empty, referenceOrderId required)
                paymentService.executeRefund(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    originalTransactionId = "",
                    originalTransactionRequestId = "",
                    amount = refundAmount,
                    currency = CURRENCY,
                    description = "Standalone Refund",
                    reason = "Standalone refund",
                    callback = createStandaloneRefundCallback(transactionRequestId)
                )
                
                // Navigate to progress screen
                _state.update { 
                    it.copy(
                        navigationEvent = TransactionListNavigationEvent.ToProgress(transactionRequestId)
                    ) 
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to execute standalone refund", e)
                _state.update {
                    it.copy(
                        standaloneRefundInProgress = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Refund Error",
                            content = e.message ?: "Failed to execute refund",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    private fun navigateToDetail(transactionId: String) {
        _state.update { 
            it.copy(navigationEvent = TransactionListNavigationEvent.ToDetail(transactionId)) 
        }
    }
    
    private fun navigateBack() {
        _state.update { 
            it.copy(navigationEvent = TransactionListNavigationEvent.ToMain) 
        }
    }
    
    private fun showQueryDialog() {
        _state.update { it.copy(showQueryDialog = true) }
    }
    
    private fun hideQueryDialog() {
        _state.update { it.copy(showQueryDialog = false) }
    }
    
    private fun showBatchCloseDialog() {
        _state.update { it.copy(showBatchCloseDialog = true) }
    }
    
    private fun hideBatchCloseDialog() {
        _state.update { it.copy(showBatchCloseDialog = false) }
    }
    
    private fun showStandaloneRefundDialog() {
        _state.update { it.copy(showStandaloneRefundDialog = true) }
    }
    
    private fun hideStandaloneRefundDialog() {
        _state.update { it.copy(showStandaloneRefundDialog = false) }
    }
    
    private fun showClearAllDialog() {
        _state.update { it.copy(showClearAllDialog = true) }
    }
    
    private fun hideClearAllDialog() {
        _state.update { it.copy(showClearAllDialog = false) }
    }
    
    /**
     * Clear all transactions
     * 
     * Clears all transaction records from the repository.
     * Shows a success message after clearing.
     */
    private fun clearAllTransactions() {
        viewModelScope.launch {
            try {
                _state.update { 
                    it.copy(showClearAllDialog = false) 
                }
                
                // Clear all transactions from repository
                transactionRepository.clearAllTransactions()
                
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.SUCCESS,
                            title = "Cleared",
                            content = "All transactions have been cleared",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to clear transactions", e)
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Clear Error",
                            content = e.message ?: "Failed to clear transactions",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    private fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
    
    private fun clearNavigationEvent() {
        _state.update { it.copy(navigationEvent = null) }
    }
    
    // Callback Creators
    
    /**
     * Create query callback
     * 
     * Creates a PaymentCallback for handling query transaction results.
     * Updates transaction repository and displays result to user.
     * If transaction doesn't exist in repository, adds it as a new transaction.
     * 
     * @param queryId Query ID (request ID or transaction ID)
     * @return PaymentCallback instance
     */
    private fun createQueryCallback(queryId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                android.util.Log.d(TAG, "Query success: $queryId")
                
                _state.update {
                    it.copy(
                        queryInProgress = false,
                        message = Message(
                            type = MessageType.SUCCESS,
                            title = "Query Successful",
                            content = "Transaction found: ${result.transactionId}\nStatus: ${result.transactionStatus}",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
                
                // Convert SDK CardInfo to model CardInfo
                val cardInfo = result.cardInfo?.let { sdkCard ->
                    com.sunmi.tapro.taplink.demo.model.CardInfo(
                        maskedPan = sdkCard.maskedPan,
                        cardNetworkType = sdkCard.cardNetworkType,
                        paymentMethodId = sdkCard.paymentMethodId,
                        subPaymentMethodId = sdkCard.subPaymentMethodId,
                        entryMode = sdkCard.entryMode,
                        authenticationMethod = sdkCard.authenticationMethod,
                        cardholderName = sdkCard.cardholderName,
                        expiryDate = sdkCard.expiryDate,
                        issuerBank = sdkCard.issuerBank,
                        cardBrand = sdkCard.cardBrand
                    )
                }
                
                // Check if transaction exists in repository
                val requestId = result.transactionRequestId ?: queryId
                val existingTransaction = transactionRepository.getTransactionByRequestId(requestId)
                
                if (existingTransaction != null) {
                    // Transaction exists, update it
                    transactionRepository.updateTransactionWithAmounts(
                        transactionRequestId = requestId,
                        status = when (result.transactionStatus) {
                            "SUCCESS" -> TransactionStatus.SUCCESS
                            "FAILED" -> TransactionStatus.FAILED
                            "PROCESSING" -> TransactionStatus.PROCESSING
                            else -> TransactionStatus.PENDING
                        },
                        transactionId = result.transactionId,
                        authCode = result.authCode,
                        orderAmount = result.amount?.orderAmount,
                        totalAmount = result.amount?.transAmount,
                        tipAmount = result.amount?.tipAmount,
                        taxAmount = result.amount?.taxAmount,
                        cashbackAmount = result.amount?.cashbackAmount,
                        serviceFee = result.amount?.serviceFee,
                        completeTime = result.completeTime,
                        cardInfo = cardInfo
                    )
                } else {
                    // Transaction doesn't exist, add it as a new transaction
                    val newTransaction = Transaction(
                        transactionRequestId = requestId,
                        transactionId = result.transactionId,
                        referenceOrderId = result.referenceOrderId ?: "QUERY-${System.currentTimeMillis()}",
                        type = when (result.transactionType) {
                            "SALE" -> TransactionType.SALE
                            "AUTH" -> TransactionType.AUTH
                            "REFUND" -> TransactionType.REFUND
                            "VOID" -> TransactionType.VOID
                            "POST_AUTH" -> TransactionType.POST_AUTH
                            "INCREMENTAL_AUTH" -> TransactionType.INCREMENT_AUTH
                            "TIP_ADJUST" -> TransactionType.TIP_ADJUST
                            "FORCED_AUTH" -> TransactionType.FORCED_AUTH
                            else -> TransactionType.SALE
                        },
                        status = when (result.transactionStatus) {
                            "SUCCESS" -> TransactionStatus.SUCCESS
                            "FAILED" -> TransactionStatus.FAILED
                            "PROCESSING" -> TransactionStatus.PROCESSING
                            else -> TransactionStatus.PENDING
                        },
                        amount = result.amount?.orderAmount ?: BigDecimal.ZERO,
                        totalAmount = result.amount?.transAmount,
                        tipAmount = result.amount?.tipAmount,
                        taxAmount = result.amount?.taxAmount,
                        cashbackAmount = result.amount?.cashbackAmount,
                        serviceFee = result.amount?.serviceFee,
                        currency = result.amount?.priceCurrency ?: "USD",
                        authCode = result.authCode,
                        batchNo = result.batchNo,
                        completeTime = result.completeTime,
                        cardInfo = cardInfo,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    transactionRepository.addTransaction(newTransaction)
                    
                    android.util.Log.d(TAG, "Added queried transaction to repository: $requestId")
                }
            }
            
            override fun onFailure(code: String, message: String) {
                android.util.Log.e(TAG, "Query failed: $queryId - $code: $message")
                
                _state.update {
                    it.copy(
                        queryInProgress = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Query Failed",
                            content = "$code: $message",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Create batch close callback
     * 
     * Creates a PaymentCallback for handling batch close results.
     * Updates transaction repository with batch close information.
     * 
     * @param transactionRequestId Transaction request ID
     * @return PaymentCallback instance
     */
    private fun createBatchCloseCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                android.util.Log.d(TAG, "Batch close success: $transactionRequestId")
                
                // Convert SDK CardInfo to model CardInfo
                val cardInfo = result.cardInfo?.let { sdkCard ->
                    com.sunmi.tapro.taplink.demo.model.CardInfo(
                        maskedPan = sdkCard.maskedPan,
                        cardNetworkType = sdkCard.cardNetworkType,
                        paymentMethodId = sdkCard.paymentMethodId,
                        subPaymentMethodId = sdkCard.subPaymentMethodId,
                        entryMode = sdkCard.entryMode,
                        authenticationMethod = sdkCard.authenticationMethod,
                        cardholderName = sdkCard.cardholderName,
                        expiryDate = sdkCard.expiryDate,
                        issuerBank = sdkCard.issuerBank,
                        cardBrand = sdkCard.cardBrand
                    )
                }
                
                // Update transaction in repository
                transactionRepository.updateTransactionWithAmounts(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.SUCCESS,
                    transactionId = result.transactionId,
                    authCode = result.authCode,
                    batchNo = result.batchNo,
                    batchCloseInfo = result.batchCloseInfo?.let {
                        com.sunmi.tapro.taplink.demo.model.BatchCloseInfo(
                            totalCount = it.totalCount,
                            totalAmount = it.totalAmount,
                            totalTip = it.totalTip,
                            totalTax = it.totalTax,
                            cashDiscount = it.cashDiscount,
                            totalSurchargeAmount = it.totalTax,
                            closeTime = it.closeTime
                        )
                    },
                    completeTime = result.completeTime,
                    cardInfo = cardInfo
                )
                
                _state.update { it.copy(batchCloseInProgress = false) }
            }
            
            override fun onFailure(code: String, message: String) {
                android.util.Log.e(TAG, "Batch close failed: $transactionRequestId - $code: $message")
                
                // Update transaction in repository
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
                
                _state.update { it.copy(batchCloseInProgress = false) }
            }
            
            override fun onProgress(status: String, message: String) {
                android.util.Log.d(TAG, "Batch close progress: $transactionRequestId - $status: $message")
                
                // Update transaction progress in repository
                transactionRepository.updateTransactionProgress(
                    transactionRequestId = transactionRequestId,
                    progressStatus = status,
                    progressMessage = message
                )
            }
        }
    }
    
    /**
     * Create standalone refund callback
     * 
     * Creates a PaymentCallback for handling standalone refund results.
     * Updates transaction repository with refund information.
     * 
     * @param transactionRequestId Transaction request ID
     * @return PaymentCallback instance
     */
    private fun createStandaloneRefundCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                android.util.Log.d(TAG, "Standalone refund success: $transactionRequestId")
                
                // Cloud mode: API success only means request accepted, not transaction complete.
                // Set status to PROCESSING so TransactionProgressViewModel can start polling.
                val isCloudMode = try {
                    val context = getApplication<android.app.Application>()
                    com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.getConnectionMode(context) ==
                        com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CLOUD
                } catch (e: Exception) { false }
                
                if (isCloudMode) {
                    android.util.Log.d(TAG, "Cloud mode: setting refund to PROCESSING for polling: $transactionRequestId")
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.PROCESSING,
                        transactionId = result.transactionId
                    )
                    _state.update { it.copy(standaloneRefundInProgress = false) }
                    return
                }
                
                // Convert SDK CardInfo to model CardInfo
                val cardInfo = result.cardInfo?.let { sdkCard ->
                    com.sunmi.tapro.taplink.demo.model.CardInfo(
                        maskedPan = sdkCard.maskedPan,
                        cardNetworkType = sdkCard.cardNetworkType,
                        paymentMethodId = sdkCard.paymentMethodId,
                        subPaymentMethodId = sdkCard.subPaymentMethodId,
                        entryMode = sdkCard.entryMode,
                        authenticationMethod = sdkCard.authenticationMethod,
                        cardholderName = sdkCard.cardholderName,
                        expiryDate = sdkCard.expiryDate,
                        issuerBank = sdkCard.issuerBank,
                        cardBrand = sdkCard.cardBrand
                    )
                }
                
                // Non-cloud mode: API success is final
                transactionRepository.updateTransactionWithAmounts(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.SUCCESS,
                    transactionId = result.transactionId,
                    authCode = result.authCode,
                    orderAmount = result.amount?.orderAmount,
                    totalAmount = result.amount?.transAmount,
                    tipAmount = result.amount?.tipAmount,
                    taxAmount = result.amount?.taxAmount,
                    cashbackAmount = result.amount?.cashbackAmount,
                    serviceFee = result.amount?.serviceFee,
                    completeTime = result.completeTime,
                    cardInfo = cardInfo
                )
                
                _state.update { it.copy(standaloneRefundInProgress = false) }
            }
            
            override fun onFailure(code: String, message: String) {
                android.util.Log.e(TAG, "Standalone refund failed: $transactionRequestId - $code: $message")
                
                // Update transaction in repository
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
                
                _state.update { it.copy(standaloneRefundInProgress = false) }
            }
            
            override fun onProgress(status: String, message: String) {
                android.util.Log.d(TAG, "Standalone refund progress: $transactionRequestId - $status: $message")
                
                // Update transaction progress in repository
                transactionRepository.updateTransactionProgress(
                    transactionRequestId = transactionRequestId,
                    progressStatus = status,
                    progressMessage = message
                )
            }
        }
    }
}
