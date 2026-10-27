package com.sunmi.tapro.taplink.demo.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.util.StaffInfoPreferences
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.HeadlessProgressEvent
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.demo.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.random.Random

/**
 * TransactionDetailViewModel
 * 
 * ViewModel for Transaction Detail Screen following MVI (Model-View-Intent) pattern.
 * Manages transaction detail display and follow-up operations (REFUND, VOID, TIP_ADJUST, etc.)
 * 
 * Responsibilities:
 * - Load and display transaction details
 * - Calculate available follow-up operations based on transaction type and status
 * - Execute follow-up operations (REFUND, VOID, TIP_ADJUST, POST_AUTH, INCREMENT_AUTH)
 * - Handle errors with Message model for consistent UX
 * - Manage navigation events
 * 
 * MVI Pattern:
 * - Intent: User actions (LoadTransaction, PerformOperation, etc.)
 * - Model: TransactionDetailState (immutable state)
 * - View: Observes state and renders UI
 */
class TransactionDetailViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    private val paymentService: PaymentService
        get() = DependencyProvider.paymentService
    
    private val _state = MutableStateFlow(TransactionDetailState())
    val state: StateFlow<TransactionDetailState> = _state.asStateFlow()
    
    /**
     * Handle user intents
     * Central dispatcher for all user actions following MVI pattern
     */
    fun handleIntent(intent: TransactionDetailIntent) {
        when (intent) {
            is TransactionDetailIntent.LoadTransaction -> loadTransaction(intent.transactionId)
            is TransactionDetailIntent.PerformOperation -> performOperation(intent.operationType)
            is TransactionDetailIntent.ShowOperationDialog -> showOperationDialog(intent.operationType)
            is TransactionDetailIntent.HideOperationDialog -> hideOperationDialog()
            is TransactionDetailIntent.ConfirmOperation -> confirmOperation(
                operationType = intent.operationType,
                amountString = intent.amount,
                tipAmountString = intent.tipAmount,
                taxAmountString = intent.taxAmount,
                surchargeAmountString = intent.surchargeAmount,
                requestCount = intent.requestCount
            )
            is TransactionDetailIntent.NavigateBack -> navigateBack()
            is TransactionDetailIntent.DismissMessage -> dismissMessageHandler()
            is TransactionDetailIntent.ClearNavigationEvent -> clearNavigationEventHandler()
            is TransactionDetailIntent.RefreshTransaction -> refreshTransactionHandler()
            is TransactionDetailIntent.QueryTransaction -> queryTransaction()
            is TransactionDetailIntent.ToggleCardInfoExpanded -> toggleCardInfoExpanded()
        }
    }
    
    /**
     * Load transaction by ID
     * Fetches transaction from repository and calculates available operations
     */
    private fun loadTransaction(transactionId: String) {
        _state.update { it.copy(isLoading = true, transactionId = transactionId) }
        
        viewModelScope.launch {
            try {
                // Try to find by transaction request ID first
                var transaction = transactionRepository.getTransactionByRequestId(transactionId)
                
                // If not found, try by Nexus transaction ID
                if (transaction == null) {
                    transaction = transactionRepository.getTransactionById(transactionId)
                }
                
                if (transaction != null) {
                    val availableOperations = TransactionDetailState.calculateAvailableOperations(transaction)
                    val orderItems = transaction.referenceOrderId?.let { orderId ->
                        transactionRepository.getOrderItems(orderId) ?: emptyList()
                    } ?: emptyList()

                    _state.update {
                        it.copy(
                            transaction = transaction,
                            orderItems = orderItems,
                            availableOperations = availableOperations,
                            isLoading = false,
                            message = null
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Transaction Not Found",
                                content = "Could not find transaction with ID: $transactionId",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Load Error",
                            content = "Failed to load transaction: ${e.message}",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Perform follow-up operation
     * Executes operations that don't require additional input (VOID)
     */
    private fun performOperation(operationType: TransactionType) {
        // Check if transaction exists
        if (_state.value.transaction == null) return
        
        // Check if operation requires input
        if (_state.value.operationRequiresInput(operationType)) {
            showOperationDialog(operationType)
            return
        }
        
        // Execute operation without input (e.g., VOID)
        executeOperation(operationType, null)
    }
    
    /**
     * Show operation input dialog
     * For operations that require amount input (REFUND, TIP_ADJUST, etc.)
     */
    private fun showOperationDialog(operationType: TransactionType) {
        _state.update {
            it.copy(
                showOperationDialog = true,
                currentOperationType = operationType
            )
        }
    }
    
    /**
     * Hide operation input dialog
     */
    private fun hideOperationDialog() {
        _state.update {
            it.copy(
                showOperationDialog = false,
                currentOperationType = null
            )
        }
    }
    
    /**
     * Confirm operation with input amount
     * Called when user confirms operation in dialog
     */
    private fun confirmOperation(
        operationType: TransactionType,
        amountString: String,
        tipAmountString: String? = null,
        taxAmountString: String? = null,
        surchargeAmountString: String? = null,
        requestCount: Int = 1
    ) {
        hideOperationDialog()
        
        // Parse amount
        val amount = try {
            BigDecimal(amountString)
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Invalid Amount",
                        content = "Please enter a valid amount",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
            return
        }
        
        // Validate amount
        if (amount <= BigDecimal.ZERO) {
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Invalid Amount",
                        content = "Amount must be greater than zero",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
            return
        }

        // Parse optional additional amounts for POST_AUTH
        val tipAmount = tipAmountString?.takeIf { it.isNotBlank() }?.let { BigDecimal(it) }
        val taxAmount = taxAmountString?.takeIf { it.isNotBlank() }?.let { BigDecimal(it) }
        val surchargeAmount = surchargeAmountString?.takeIf { it.isNotBlank() }?.let { BigDecimal(it) }
        
        if (operationType == TransactionType.REFUND && requestCount > 1) {
            executeRefundBatch(amount, requestCount)
        } else {
            executeOperation(operationType, amount, tipAmount, taxAmount, surchargeAmount)
        }
    }

    private fun executeRefundBatch(amount: BigDecimal, requestCount: Int) {
        val originalTransaction = _state.value.transaction ?: return
        if (requestCount !in 2..10) {
            showInvalidRefundBatchMessage("Refund request count must be between 2 and 10")
            return
        }

        val connectionMode = try {
            com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.getConnectionMode(
                DependencyProvider.requireContext()
            )
        } catch (e: Exception) {
            null
        }
        if (connectionMode !=
            com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CLOUD
        ) {
            showInvalidRefundBatchMessage("Multiple refund requests are available in Cloud mode only")
            return
        }

        val originalAmount = originalTransaction.getDisplayAmount()
        val committedRefundAmount = originalTransaction.referenceOrderId
            ?.let(transactionRepository::getTransactionsByOrderId)
            .orEmpty()
            .filter {
                it.type == TransactionType.REFUND &&
                    it.status != TransactionStatus.FAILED
            }
            .sumOf(Transaction::getDisplayAmount)
        val remainingRefundAmount = originalAmount.subtract(committedRefundAmount)
            .coerceAtLeast(BigDecimal.ZERO)
        val totalRequested = amount.multiply(BigDecimal(requestCount))
        if (totalRequested > remainingRefundAmount) {
            showInvalidRefundBatchMessage(
                "Total refund amount cannot exceed the remaining refundable amount: " +
                    "$requestCount × $amount > $remainingRefundAmount"
            )
            return
        }

        _state.update {
            it.copy(
                isLoading = true,
                message = Message(
                    type = MessageType.INFO,
                    title = "Submitting Refunds",
                    content = "Submitting $requestCount independent refund requests...",
                    actions = emptyList()
                )
            )
        }

        viewModelScope.launch {
            val referenceOrderId = originalTransaction.referenceOrderId
                ?: "${Constants.ORDER_ID_PREFIX}${System.currentTimeMillis()}"
            val refundPaymentCategory = resolveRefundPaymentCategory(originalTransaction)
            val staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext())
            var submittedCount = 0
            val requestIds = buildList {
                repeat(requestCount) { index ->
                    val transactionRequestId = generateUniqueTransactionRequestId()
                    val description = "Refund ${index + 1}/$requestCount for ${originalTransaction.type.name}"
                    val refundTransaction = Transaction(
                        transactionRequestId = transactionRequestId,
                        referenceOrderId = referenceOrderId,
                        type = TransactionType.REFUND,
                        amount = amount,
                        currency = originalTransaction.currency,
                        status = TransactionStatus.PENDING,
                        timestamp = System.currentTimeMillis() + index,
                        originalTransactionId = originalTransaction.transactionId.orEmpty(),
                        originalTransactionRequestId = originalTransaction.transactionRequestId,
                        description = description,
                        reason = "Cloud multi-refund test",
                        paymentCategory = refundPaymentCategory,
                        cardNetworkType = originalTransaction.cardNetworkType
                    )
                    transactionRepository.addTransaction(refundTransaction)
                    add(transactionRequestId)
                    try {
                        paymentService.executeRefund(
                            referenceOrderId = referenceOrderId,
                            transactionRequestId = transactionRequestId,
                            originalTransactionId = originalTransaction.transactionId.orEmpty(),
                            originalTransactionRequestId = originalTransaction.transactionRequestId,
                            amount = amount,
                            currency = originalTransaction.currency,
                            description = description,
                            reason = "Cloud multi-refund test",
                            paymentCategory = refundPaymentCategory,
                            cardNetworkType = originalTransaction.cardNetworkType,
                            staffInfo = staffInfo,
                            callback = createPaymentCallback(transactionRequestId)
                        )
                        submittedCount++
                    } catch (e: Exception) {
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            errorCode = Constants.ErrorCodes.REQUEST_SEND_FAILED,
                            errorMessage = e.message ?: "Failed to submit refund request"
                        )
                    }
                }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    message = Message(
                        type = if (submittedCount == requestCount) {
                            MessageType.SUCCESS
                        } else {
                            MessageType.WARNING
                        },
                        title = "Refund Requests Submitted",
                        content = "$submittedCount of $requestCount requests submitted for the same order.\n" +
                            requestIds.joinToString(separator = "\n"),
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
        }
    }

    private fun generateUniqueTransactionRequestId(): String {
        var candidate: String
        do {
            candidate = buildString {
                append(Constants.TRANSACTION_REQUEST_ID_PREFIX)
                append(System.currentTimeMillis())
                append('_')
                append(Random.nextInt(100_000, 1_000_000))
            }
        } while (transactionRepository.isTransactionRequestIdExists(candidate))
        return candidate
    }

    private fun showInvalidRefundBatchMessage(content: String) {
        _state.update {
            it.copy(
                isLoading = false,
                message = Message(
                    type = MessageType.ERROR,
                    title = "Invalid Refund Batch",
                    content = content,
                    actions = listOf(MessageAction.DISMISS)
                )
            )
        }
    }
    
    /**
     * Execute follow-up operation
     * Creates new transaction and calls payment service.
     *
     * In App-to-App mode the SDK launches the Tapro app for the full payment flow,
     * so we stay on the detail screen (showing a loading indicator) until the final
     * result (onSuccess / onFailure) comes back, then navigate to the progress screen.
     * In other modes we navigate immediately after calling the SDK.
     */
    private fun executeOperation(
        operationType: TransactionType,
        amount: BigDecimal?,
        tipAmount: BigDecimal? = null,
        taxAmount: BigDecimal? = null,
        surchargeAmount: BigDecimal? = null
    ) {
        val originalTransaction = _state.value.transaction ?: return
        // Use display amount (order + tip etc.) so progress screen and APIs use correct total
        val originalDisplayAmount = originalTransaction.getDisplayAmount()
        
        _state.update {
            it.copy(
                isLoading = true,
                message = Message(
                    type = MessageType.INFO,
                    title = "Processing",
                    content = "Processing ${operationType.name} operation...",
                    actions = emptyList()
                )
            )
        }
        
        viewModelScope.launch {
            try {
                // Generate transaction request ID for this new operation
                val timestamp = System.currentTimeMillis()
                val transactionRequestId = "${Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"
                val referenceOrderId = originalTransaction.referenceOrderId ?: "${Constants.ORDER_ID_PREFIX}$timestamp"
                
                // For follow-up operations, we need both the original transaction ID and original transaction request ID
                val originalTransactionId = originalTransaction.transactionId ?: ""
                val originalTransactionRequestId = originalTransaction.transactionRequestId
                
                // Amount for this operation: VOID/REFUND use display total (incl. tip); others use amount or original base
                val operationAmount = when (operationType) {
                    TransactionType.VOID -> originalDisplayAmount
                    TransactionType.REFUND -> amount ?: originalDisplayAmount
                    else -> amount ?: originalTransaction.amount
                }
                // POST_AUTH 需要在本地展示层就把“输入的附加金额”计入总额，
                // 避免进度事件页在终端最终回包前只显示 base amount。
                val postAuthAdditionalTotal = listOf(
                    tipAmount,
                    taxAmount,
                    surchargeAmount
                ).fold(BigDecimal.ZERO) { acc, value ->
                    acc.add(value ?: BigDecimal.ZERO)
                }
                val postAuthDisplayTotal = if (operationType == TransactionType.POST_AUTH) {
                    operationAmount.add(postAuthAdditionalTotal)
                } else {
                    operationAmount
                }
                // Record amount for display: VOID = full amount; REFUND = user-entered refund amount only
                val recordAmount = when (operationType) {
                    TransactionType.VOID -> operationAmount
                    TransactionType.REFUND -> amount ?: originalDisplayAmount
                    else -> amount ?: originalTransaction.amount
                }
                val operationPaymentCategory = if (operationType == TransactionType.REFUND) {
                    resolveRefundPaymentCategory(originalTransaction)
                } else {
                    originalTransaction.paymentCategory
                }
                
                // Create new transaction record
                val newTransaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    type = operationType,
                    amount = recordAmount,
                    totalAmount = when (operationType) {
                        TransactionType.VOID -> recordAmount
                        TransactionType.POST_AUTH -> postAuthDisplayTotal
                        else -> null
                    },
                    currency = originalTransaction.currency,
                    status = TransactionStatus.PENDING,
                    timestamp = System.currentTimeMillis(),
                    originalTransactionId = originalTransactionId,
                    originalTransactionRequestId = originalTransactionRequestId,
                    description = when (operationType) {
                        TransactionType.REFUND -> "Refund for ${originalTransaction.type.name}"
                        TransactionType.VOID -> "Void for ${originalTransaction.type.name}"
                        TransactionType.POST_AUTH -> "Post-authorization completion"
                        TransactionType.TIP_ADJUST -> "Tip adjustment"
                        else -> operationType.name
                    },
                    reason = when (operationType) {
                        TransactionType.REFUND -> "Customer request"
                        TransactionType.VOID -> "Transaction cancellation"
                        else -> null
                    },
                    paymentCategory = operationPaymentCategory,
                    cardNetworkType = originalTransaction.cardNetworkType,
                    tipAmount = tipAmount,
                    taxAmount = taxAmount,
                    // Preserve the surcharge field in the local capture record.
                    surchargeAmount = surchargeAmount
                )
                
                transactionRepository.addTransaction(newTransaction)

                // App-to-App mode: wait for final result before navigating to progress screen.
                // Other modes: navigate immediately so user sees real-time progress.
                val isAppToApp = try {
                    val context = DependencyProvider.requireContext()
                    com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                        .getConnectionMode(context) == com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.APP_TO_APP
                } catch (e: Exception) { false }

                // Create payment callback — in App-to-App mode it also triggers navigation on final result
                val callback = createNavigatingPaymentCallback(transactionRequestId, isAppToApp)
                
                // Execute operation based on type
                when (operationType) {
                    TransactionType.REFUND -> {
                        // Determine payment category from original transaction's card info.
                        // QR payment methods (WECHAT_PAY, ALIPAY, DANA) need QR_CPM category
                        // so the terminal routes to the QR refund flow.
                        val refundPaymentCategory = resolveRefundPaymentCategory(originalTransaction)

                        paymentService.executeRefund(
                            referenceOrderId = referenceOrderId,
                            transactionRequestId = transactionRequestId,
                            originalTransactionId = originalTransactionId,
                            originalTransactionRequestId = originalTransactionRequestId,
                            amount = operationAmount,
                            currency = originalTransaction.currency,
                            description = "Refund for ${originalTransaction.type.name}",
                            reason = "Customer request",
                            paymentCategory = refundPaymentCategory,
                            staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                            callback = callback
                        )
                    }
                    TransactionType.VOID -> {
                        paymentService.executeVoid(
                            referenceOrderId = referenceOrderId,
                            transactionRequestId = transactionRequestId,
                            originalTransactionId = originalTransactionId,
                            description = "Void for ${originalTransaction.type.name}",
                            reason = "Transaction cancellation",
                            callback = callback
                        )
                    }
                    TransactionType.TIP_ADJUST -> {
                        paymentService.executeTipAdjust(
                            referenceOrderId = referenceOrderId,
                            transactionRequestId = transactionRequestId,
                            originalTransactionId = originalTransactionId,
                            originalTransactionRequestId = originalTransactionRequestId,
                            tipAmount = amount ?: BigDecimal.ZERO,
                            description = "Tip adjustment",
                            callback = callback
                        )
                    }
                    TransactionType.POST_AUTH -> {
                        val tipConfig = com.sunmi.tapro.taplink.demo.util.TipConfigBuilder.buildFromPreferences(
                            DependencyProvider.requireContext()
                        )
                        paymentService.executePostAuth(
                            referenceOrderId = referenceOrderId,
                            transactionRequestId = transactionRequestId,
                            originalTransactionId = originalTransactionId,
                            amount = amount ?: originalTransaction.amount,
                            currency = originalTransaction.currency,
                            description = "Post-authorization completion",
                            tipAmount = tipAmount,
                            taxAmount = taxAmount,
                            surchargeAmount = surchargeAmount,
                            staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                            tipConfig = tipConfig,
                            callback = callback
                        )
                    }
                    TransactionType.INCREMENT_AUTH -> {
                        paymentService.executeIncrementalAuth(
                            referenceOrderId = referenceOrderId,
                            transactionRequestId = transactionRequestId,
                            originalTransactionId = originalTransactionId,
                            amount = amount ?: BigDecimal.ZERO,
                            currency = originalTransaction.currency,
                            description = "Incremental authorization",
                            staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                            callback = callback
                        )
                    }
                    else -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                message = Message(
                                    type = MessageType.ERROR,
                                    title = "Unsupported Operation",
                                    content = "Operation ${operationType.name} is not supported",
                                    actions = listOf(MessageAction.DISMISS)
                                )
                            )
                        }
                        return@launch
                    }
                }
                
                // Non-App-to-App: navigate to progress screen immediately
                if (!isAppToApp) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            message = null,
                            navigationEvent = TransactionDetailNavigationEvent.ToProgress(transactionRequestId)
                        )
                    }
                }
                
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Operation Failed",
                            content = "Failed to execute ${operationType.name}: ${e.message}",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Create a payment callback that, in App-to-App mode, navigates to the progress
     * screen only when the final result (onSuccess / onFailure) arrives.
     * In non-App-to-App mode the navigation is handled by the caller, so this
     * simply delegates to createPaymentCallback.
     */
    private fun createNavigatingPaymentCallback(
        transactionRequestId: String,
        isAppToApp: Boolean
    ): PaymentCallback {
        val inner = createPaymentCallback(transactionRequestId)
        var navigated = false
        val navigateOnce = {
            if (!navigated) {
                navigated = true
                _state.update {
                    it.copy(
                        isLoading = false,
                        message = null,
                        navigationEvent = TransactionDetailNavigationEvent.ToProgress(transactionRequestId)
                    )
                }
            }
        }
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                if (isAppToApp) navigateOnce()
                inner.onSuccess(result)
            }
            override fun onFailure(code: String, message: String) {
                if (isAppToApp) navigateOnce()
                inner.onFailure(code, message)
            }
            override fun onProgress(status: String, message: String) {
                if (isAppToApp) {
                    // App-to-App: skip progress updates entirely — do not write
                    // intermediate messages to the repository, so the progress
                    // screen won't flash stale progress on navigation.
                    return
                }
                inner.onProgress(status, message)
            }

            override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                if (isAppToApp) navigateOnce()
                inner.onHeadlessProgress(event)
            }
        }
    }

    /**
     * Create payment callback for follow-up operations
     */
    private fun createPaymentCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                viewModelScope.launch {
                    // Cloud mode: transaction creation success != transaction complete.
                    // Only types pushed to terminal need polling. Types processed directly
                    // in cloud (VOID, TIP_ADJUST, QUERY, BATCH_CLOSE) are final on API success.
                    val isCloudMode = try {
                        val context = com.sunmi.tapro.taplink.demo.di.DependencyProvider.requireContext()
                        com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                            .getConnectionMode(context) == com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CLOUD
                    } catch (e: Exception) { false }
                    
                    val txn = transactionRepository.getTransactionByRequestId(transactionRequestId)
                    val needsPolling = txn?.type != TransactionType.VOID &&
                                      txn?.type != TransactionType.TIP_ADJUST &&
                                      txn?.type != TransactionType.QUERY &&
                                      txn?.type != TransactionType.BATCH_CLOSE

                    when {
                        result.isFailed() -> {
                            transactionRepository.updateTransactionStatus(
                                transactionRequestId = transactionRequestId,
                                status = TransactionStatus.FAILED,
                                transactionId = result.transactionId ?: result.originalTransactionId,
                                errorCode = result.transactionResultCode ?: result.code,
                                errorMessage = result.message
                            )
                        }
                        result.isProcessing() || (isCloudMode && needsPolling && !result.isTerminal()) -> {
                            transactionRepository.updateTransactionStatus(
                                transactionRequestId = transactionRequestId,
                                status = TransactionStatus.PROCESSING,
                                transactionId = result.transactionId ?: result.originalTransactionId
                            )
                        }
                        result.isSuccess() -> {
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
                            transactionRepository.updateTransactionStatus(
                                transactionRequestId = transactionRequestId,
                                status = TransactionStatus.FAILED,
                                transactionId = result.transactionId ?: result.originalTransactionId,
                                errorCode = result.transactionResultCode ?: result.code,
                                errorMessage = result.message ?: "Unknown transaction status"
                            )
                        }
                    }
                }
            }
            
            override fun onFailure(code: String, message: String) {
                viewModelScope.launch {
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.FAILED,
                        errorCode = code,
                        errorMessage = message
                    )
                }
            }
            
            override fun onProgress(status: String, message: String) {
                viewModelScope.launch {
                    transactionRepository.updateTransactionProgress(
                        transactionRequestId = transactionRequestId,
                        progressStatus = status,
                        progressMessage = message
                    )
                }
            }

            override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                viewModelScope.launch {
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
    
    /**
     * Navigate back to previous screen
     */
    private fun navigateBack() {
        _state.update {
            it.copy(navigationEvent = TransactionDetailNavigationEvent.Back)
        }
    }
    
    /**
     * Dismiss error/info message
     */
    private fun dismissMessageHandler() {
        _state.update { it.copy(message = null) }
    }
    
    /**
     * Clear navigation event after handling
     */
    private fun clearNavigationEventHandler() {
        _state.update { it.copy(navigationEvent = null) }
    }
    
    /**
     * Refresh transaction data
     */
    private fun refreshTransactionHandler() {
        val transactionId = _state.value.transactionId
        if (transactionId != null) {
            loadTransaction(transactionId)
        }
    }
    
    /**
     * Query transaction status from payment terminal
     * Uses the transaction request ID to query the current status
     */
    private fun queryTransaction() {
        val transaction = _state.value.transaction ?: return
        
        _state.update {
            it.copy(
                isQuerying = true,
                message = Message(
                    type = MessageType.INFO,
                    title = "Querying",
                    content = "Querying transaction status from payment terminal...",
                    actions = emptyList()
                )
            )
        }
        
        viewModelScope.launch {
            try {
                val callback = object : PaymentCallback {
                    override fun onSuccess(result: PaymentResult) {
                        viewModelScope.launch {
                            // If the query itself was rejected (e.g. API not permitted),
                            // do NOT update the original transaction status
                            if (result.isFailed() && result.transactionId == null) {
                                _state.update {
                                    it.copy(
                                        isQuerying = false,
                                        message = Message(
                                            type = MessageType.ERROR,
                                            title = "Query Failed",
                                            content = "${result.code}: ${result.message}",
                                            actions = listOf(MessageAction.DISMISS)
                                        )
                                    )
                                }
                                return@launch
                            }

                            // Update transaction with query result
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
                            
                            val status = when (result.transactionStatus) {
                                "S", "SUCCESS" -> TransactionStatus.SUCCESS
                                "F", "FAILED" -> TransactionStatus.FAILED
                                "P", "PROCESSING" -> TransactionStatus.PROCESSING
                                "C" -> TransactionStatus.FAILED
                                "I" -> TransactionStatus.PENDING
                                else -> TransactionStatus.PENDING
                            }
                            
                            transactionRepository.updateTransactionWithAmounts(
                                transactionRequestId = transaction.transactionRequestId,
                                status = status,
                                transactionId = result.transactionId,
                                authCode = result.authCode,
                                orderAmount = result.amount?.orderAmount,
                                totalAmount = result.amount?.transAmount,
                                tipAmount = result.amount?.tipAmount,
                                taxAmount = result.amount?.taxAmount,
                                cashbackAmount = result.amount?.cashbackAmount,
                                serviceFee = result.amount?.serviceFee,
                                completeTime = result.completeTime,
                                cardInfo = cardInfo,
                                transactionBatchStatus = result.transactionBatchStatus,
                                relatedTransactionStatus = result.relatedTransactionStatus
                            )
                            
                            // Reload transaction to show updated data
                            loadTransaction(transaction.transactionRequestId)
                            
                            _state.update {
                                it.copy(
                                    isQuerying = false,
                                    message = Message(
                                        type = MessageType.SUCCESS,
                                        title = "Query Successful",
                                        content = "Transaction status updated: ${status.name}",
                                        actions = listOf(MessageAction.DISMISS)
                                    )
                                )
                            }
                        }
                    }
                    
                    override fun onFailure(code: String, message: String) {
                        viewModelScope.launch {
                            _state.update {
                                it.copy(
                                    isQuerying = false,
                                    message = Message(
                                        type = MessageType.ERROR,
                                        title = "Query Failed",
                                        content = "Failed to query transaction: $message (Code: $code)",
                                        actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                                    )
                                )
                            }
                        }
                    }
                    
                    override fun onProgress(status: String, message: String) {
                        viewModelScope.launch {
                            _state.update {
                                it.copy(
                                    message = Message(
                                        type = MessageType.INFO,
                                        title = "Querying",
                                        content = message,
                                        actions = emptyList()
                                    )
                                )
                            }
                        }
                    }
                }
                
                // Query using transaction request ID
                paymentService.executeQuery(
                    transactionRequestId = transaction.transactionRequestId,
                    callback = callback
                )
                
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isQuerying = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Query Error",
                            content = "Failed to query transaction: ${e.message}",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Toggle card information expanded state
     */
    private fun toggleCardInfoExpanded() {
        _state.update { it.copy(isCardInfoExpanded = !it.isCardInfoExpanded) }
    }

    /**
     * Determine the payment category for a refund from the original transaction.
     */
    private fun resolveRefundPaymentCategory(
        originalTransaction: Transaction
    ): com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory? {
        val originalCategory = originalTransaction.paymentCategory
        if (originalCategory == com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory.QR_CPM ||
            originalCategory == com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory.QR_MPM
        ) {
            return originalCategory
        }

        return when (originalTransaction.cardInfo?.paymentMethodId?.uppercase()) {
            "CPM", "QR_CPM", "WECHAT_PAY", "ALIPAY", "DANA" ->
                com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory.QR_CPM
            "MPM", "QR_MPM" ->
                com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory.QR_MPM
            else -> null
        }
    }
}
