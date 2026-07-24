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
import com.sunmi.tapro.taplink.demo.service.HeadlessProgressEvent
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
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
            is TransactionListIntent.DismissBatchCloseResult -> dismissBatchCloseResult()
            is TransactionListIntent.StandaloneRefund -> standaloneRefund(
                amount = intent.amount,
                paymentCategory = intent.paymentCategory,
                cardNetworkType = intent.cardNetworkType
            )
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
     *
     * In App-to-App mode, stays on the list screen until the final result arrives,
     * then navigates to the progress screen.
     */
    private fun batchClose() {
        // 防重复批结：已有批结进行中时直接忽略本次请求，避免重复结算（财务风险）。
        // 这是权威守卫——即使 UI 未及时禁用导致连点，也不会触发第二次结算。
        if (_state.value.batchCloseInProgress) {
            android.util.Log.w(TAG, "Batch close already in progress, ignoring duplicate request")
            return
        }
        // 同步置位（在启动协程之前）：handleIntent 在主线程同步执行，连点时第二次会在此读到
        // batchCloseInProgress=true 而被守卫拦截，避免「两次都在协程置位前通过守卫」的竞态。
        // 立即弹出结果弹框（进行中状态），结果回来后在弹框内更新成功/失败，不再跳转进度页。
        _state.update {
            it.copy(
                batchCloseInProgress = true,
                showBatchCloseDialog = false,
                showBatchCloseResultDialog = true,
                batchCloseResult = null,
                batchCloseResultBatchNo = null,
                batchCloseError = null
            )
        }
        viewModelScope.launch {
            try {
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

                // Execute batch close; the dialog stays on the list screen and is updated on result.
                paymentService.executeBatchClose(
                    transactionRequestId = transactionRequestId,
                    description = "Batch Close",
                    callback = createBatchCloseCallback(transactionRequestId)
                )
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to execute batch close", e)
                _state.update {
                    it.copy(
                        batchCloseInProgress = false,
                        showBatchCloseResultDialog = true,
                        batchCloseError = e.message ?: "Failed to execute batch close"
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
     * In App-to-App mode, stays on the list screen until the final result arrives,
     * then navigates to the progress screen.
     * 
     * @param amount Refund amount as string
     * @param paymentCategory Payment category (CARD / EBT)
     * @param cardNetworkType Card network type (CREDIT / DEBIT), null means auto
     */
    private fun standaloneRefund(
        amount: String,
        paymentCategory: PaymentCategory,
        cardNetworkType: String?
    ) {
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

                // Check connection mode
                val (isAppToApp, isHeadlessAppToApp) = try {
                    val context = getApplication<android.app.Application>()
                    val connectionMode = com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                        .getConnectionMode(context)
                    val appToAppMode = com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                        .getAppToAppMode(context)
                    val appToApp = connectionMode ==
                        com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.APP_TO_APP
                    val headlessAppToApp = appToApp &&
                        appToAppMode == com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.AppToAppMode.HEADLESS
                    appToApp to headlessAppToApp
                } catch (e: Exception) {
                    false to false
                }
                
                // Execute refund (unreferenced refund: both original IDs empty, referenceOrderId required)
                // In App-to-App mode, callback triggers navigation on final result
                paymentService.executeRefund(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    originalTransactionId = "",
                    originalTransactionRequestId = "",
                    amount = refundAmount,
                    currency = CURRENCY,
                    description = "Standalone Refund",
                    reason = "Standalone refund",
                    paymentCategory = paymentCategory,
                    cardNetworkType = cardNetworkType,
                    callback = createNavigatingStandaloneRefundCallback(
                        transactionRequestId = transactionRequestId,
                        isAppToApp = isAppToApp,
                        isHeadlessAppToApp = isHeadlessAppToApp
                    )
                )
                
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
        // 操作进行中（含批结/查询/退款）时不弹确认框，从源头避免重复结算入口。
        if (_state.value.isAnyOperationInProgress()) {
            android.util.Log.w(TAG, "An operation is in progress, ignoring batch close request")
            return
        }
        _state.update { it.copy(showBatchCloseDialog = true) }
    }
    
    private fun hideBatchCloseDialog() {
        _state.update { it.copy(showBatchCloseDialog = false) }
    }

    /**
     * Dismiss the batch close result dialog and clear its data.
     */
    private fun dismissBatchCloseResult() {
        _state.update {
            it.copy(
                showBatchCloseResultDialog = false,
                batchCloseResult = null,
                batchCloseResultBatchNo = null,
                batchCloseError = null
            )
        }
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
                when {
                    result.isFailed() -> {
                        android.util.Log.e(TAG, "Query failed: $queryId - ${result.code}: ${result.message}")
                        _state.update {
                            it.copy(
                                queryInProgress = false,
                                message = Message(
                                    type = MessageType.ERROR,
                                    title = "Query Failed",
                                    content = "${result.code}: ${result.message}",
                                    actions = listOf(MessageAction.DISMISS)
                                )
                            )
                        }
                    }
                    result.isProcessing() -> {
                        android.util.Log.d(TAG, "Query result - transaction processing: $queryId")
                        _state.update {
                            it.copy(
                                queryInProgress = false,
                                message = Message(
                                    type = MessageType.INFO,
                                    title = "Transaction Processing",
                                    content = "Transaction is still being processed: ${result.transactionId}",
                                    actions = listOf(MessageAction.DISMISS)
                                )
                            )
                        }
                        // Update existing transaction status if found
                        val requestId = result.transactionRequestId ?: queryId
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = requestId,
                            status = TransactionStatus.PROCESSING,
                            transactionId = result.transactionId
                        )
                    }
                    result.isSuccess() -> {
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
                        
                        val requestId = result.transactionRequestId ?: queryId
                        val existingTransaction = transactionRepository.getTransactionByRequestId(requestId)
                        
                        if (existingTransaction != null) {
                            transactionRepository.updateTransactionWithAmounts(
                                transactionRequestId = requestId,
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
                        } else {
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
                                status = TransactionStatus.SUCCESS,
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
                    else -> {
                        android.util.Log.w(TAG, "Query unknown status: $queryId, status=${result.transactionStatus}")
                        _state.update {
                            it.copy(
                                queryInProgress = false,
                                message = Message(
                                    type = MessageType.ERROR,
                                    title = "Query Failed",
                                    content = "${result.code}: ${result.message ?: "Unknown status"}",
                                    actions = listOf(MessageAction.DISMISS)
                                )
                            )
                        }
                    }
                }
            }
            
            override fun onFailure(code: String, message: String) {
                android.util.Log.e(TAG, "Query communication error: $queryId - $code: $message")
                
                // onFailure = communication/technical error �?query could not reach terminal.
                _state.update {
                    it.copy(
                        queryInProgress = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Communication Error",
                            content = "Unable to reach terminal: $code - $message",
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
                when {
                    result.isFailed() -> {
                        android.util.Log.e(TAG, "Batch close failed: $transactionRequestId - ${result.code}: ${result.message}")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            errorCode = result.transactionResultCode ?: result.code,
                            errorMessage = result.message
                        )
                        _state.update {
                            it.copy(
                                showBatchCloseResultDialog = true,
                                batchCloseResult = null,
                                batchCloseError = result.message ?: "Batch close failed"
                            )
                        }
                    }
                    result.isProcessing() -> {
                        android.util.Log.d(TAG, "Batch close processing: $transactionRequestId")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.PROCESSING,
                            transactionId = result.transactionId
                        )
                    }
                    result.isSuccess() -> {
                        android.util.Log.d(TAG, "Batch close success: $transactionRequestId")
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
                        // lib_service 已将金额从分换算为元，这里直接使用，避免重复换算导致显示偏小 100 倍。
                        val batchCloseInfo = result.batchCloseInfo?.let {
                            com.sunmi.tapro.taplink.demo.model.BatchCloseInfo(
                                totalCount = it.totalCount,
                                totalAmount = it.totalAmount,
                                totalTip = it.totalTip,
                                totalTax = it.totalTax,
                                cashDiscount = it.cashDiscount,
                                totalSurchargeAmount = it.totalSurchargeAmount,
                                totalServiceFee = it.totalServiceFee,
                                closeTime = it.closeTime
                            )
                        }
                        transactionRepository.updateTransactionWithAmounts(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.SUCCESS,
                            transactionId = result.transactionId,
                            authCode = result.authCode,
                            batchNo = result.batchNo,
                            batchCloseInfo = batchCloseInfo,
                            completeTime = result.completeTime,
                            cardInfo = cardInfo
                        )
                        // Update the in-progress dialog to show the settlement summary.
                        _state.update {
                            it.copy(
                                showBatchCloseResultDialog = true,
                                batchCloseResult = batchCloseInfo,
                                batchCloseResultBatchNo = result.batchNo?.toString(),
                                batchCloseError = null
                            )
                        }
                    }
                    else -> {
                        android.util.Log.w(TAG, "Batch close unknown status: $transactionRequestId, status=${result.transactionStatus}")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            errorCode = result.transactionResultCode ?: result.code,
                            errorMessage = result.message ?: "Unknown transaction status"
                        )
                        _state.update {
                            it.copy(
                                showBatchCloseResultDialog = true,
                                batchCloseResult = null,
                                batchCloseError = result.message ?: "Unknown transaction status"
                            )
                        }
                    }
                }
                _state.update { it.copy(batchCloseInProgress = false) }
            }
            
            override fun onFailure(code: String, message: String) {
                android.util.Log.e(TAG, "Batch close communication error: $transactionRequestId - $code: $message")
                
                // onFailure = communication/technical error (connection lost, timeout, etc.)
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
                
                _state.update {
                    it.copy(
                        batchCloseInProgress = false,
                        showBatchCloseResultDialog = true,
                        batchCloseResult = null,
                        batchCloseError = message
                    )
                }
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
     * Create a standalone refund callback that navigates to the progress screen on final result
     * when in App-to-App mode. Wraps the standard createStandaloneRefundCallback.
     */
    private fun createNavigatingStandaloneRefundCallback(
        transactionRequestId: String,
        isAppToApp: Boolean,
        isHeadlessAppToApp: Boolean
    ): PaymentCallback {
        val inner = createStandaloneRefundCallback(transactionRequestId)
        var navigated = false
        val navigateOnce = {
            if (!navigated) {
                navigated = true
                _state.update {
                    it.copy(
                        navigationEvent = TransactionListNavigationEvent.ToProgress(transactionRequestId)
                    )
                }
            }
        }
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                if (isAppToApp && !isHeadlessAppToApp) navigateOnce()
                inner.onSuccess(result)
            }
            override fun onFailure(code: String, message: String) {
                if (isAppToApp && !isHeadlessAppToApp) navigateOnce()
                inner.onFailure(code, message)
            }
            override fun onProgress(status: String, message: String) {
                if (isAppToApp && !isHeadlessAppToApp) {
                    // App-to-App: skip progress updates entirely �?do not write
                    // intermediate messages to the repository, so the progress
                    // screen won't flash stale progress on navigation.
                    return
                }
                navigateOnce()
                inner.onProgress(status, message)
            }

            override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                navigateOnce()
                inner.onHeadlessProgress(event)
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
                android.util.Log.d(TAG, "Standalone refund result: $transactionRequestId, status=${result.transactionStatus}")
                
                // Cloud mode: API success only means request accepted, not transaction complete.
                val isCloudMode = try {
                    val context = getApplication<android.app.Application>()
                    com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.getConnectionMode(context) ==
                        com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CLOUD
                } catch (e: Exception) { false }
                
                when {
                    result.isFailed() -> {
                        android.util.Log.e(TAG, "Standalone refund failed: $transactionRequestId - ${result.code}: ${result.message}")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            transactionId = result.transactionId ?: result.originalTransactionId,
                            errorCode = result.transactionResultCode ?: result.code,
                            errorMessage = result.message
                        )
                    }
                    result.isProcessing() || (isCloudMode && !result.isTerminal()) -> {
                        android.util.Log.d(TAG, "Standalone refund processing: $transactionRequestId")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.PROCESSING,
                            transactionId = result.transactionId ?: result.originalTransactionId
                        )
                    }
                    result.isSuccess() -> {
                        android.util.Log.d(TAG, "Standalone refund success: $transactionRequestId")
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
                    }
                    else -> {
                        android.util.Log.w(TAG, "Standalone refund unknown status: $transactionRequestId, status=${result.transactionStatus}")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            transactionId = result.transactionId ?: result.originalTransactionId,
                            errorCode = result.transactionResultCode ?: result.code,
                            errorMessage = result.message ?: "Unknown transaction status"
                        )
                    }
                }
                _state.update { it.copy(standaloneRefundInProgress = false) }
            }
            
            override fun onFailure(code: String, message: String) {
                android.util.Log.e(TAG, "Refund communication error: $transactionRequestId - $code: $message")
                
                // onFailure = communication/technical error (connection lost, timeout, etc.)
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

            override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                android.util.Log.d(
                    TAG,
                    "Standalone refund headless progress: $transactionRequestId - ${event.eventCode}, " +
                        "id=${event.eventId}, waitingCardTypes=${event.waitingCardTypes}"
                )
                transactionRepository.updateTransactionProgress(
                    transactionRequestId = transactionRequestId,
                    progressStatus = event.eventCode,
                    progressMessage = event.eventMessage,
                    headlessProgressEvent = event
                )
            }
        }
    }
}
