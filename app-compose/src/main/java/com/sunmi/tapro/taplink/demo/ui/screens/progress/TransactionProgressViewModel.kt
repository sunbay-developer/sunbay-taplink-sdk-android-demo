package com.sunmi.tapro.taplink.demo.ui.screens.progress

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sunmi.tapro.taplink.demo.model.CardInfo
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.ErrorHandler
import com.sunmi.tapro.taplink.demo.util.RetryManager
import com.sunmi.tapro.taplink.demo.util.RetryManagerFactory
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.math.BigDecimal
import kotlin.coroutines.resume

/**
 * Transaction Progress ViewModel (MVI Pattern)
 * 
 * Manages the state and business logic for the Transaction Progress Screen.
 * Follows MVI (Model-View-Intent) architecture pattern for predictable state management.
 * 
 * Responsibilities:
 * - Observe transaction status changes from repository
 * - Handle retry logic for failed transactions
 * - Manage navigation events
 * - Provide error handling with Message model
 * - Update UI state based on transaction progress
 * 
 * @property transactionRepository Repository for transaction data access
 * @property paymentService Service for payment operations (used for retry)
 */
class TransactionProgressViewModel(
    private val transactionRepository: TransactionRepository,
    private val paymentService: PaymentService
) : ViewModel() {
    
    companion object {
        private const val TAG = "TransactionProgressVM"
        
        // Cloud mode polling configuration
        const val CLOUD_POLL_INTERVAL_MS = 3000L   // 3 seconds between polls
        const val CLOUD_POLL_MAX_ATTEMPTS = 60      // Max 60 attempts (3 minutes total)
    }
    
    // MVI State
    private val _state = MutableStateFlow(TransactionProgressState())
    val state: StateFlow<TransactionProgressState> = _state.asStateFlow()
    
    // Retry manager for handling retry logic
    private val retryManager: RetryManager = RetryManagerFactory.forPayment()
    
    // Current transaction being observed
    private var currentTransaction: Transaction? = null
    
    // Cloud mode polling job - cancelled on ViewModel clear or terminal status
    private var pollingJob: Job? = null
    
    /**
     * Handle user intents
     * Central method for processing all user actions following MVI pattern
     * 
     * @param intent User intent to handle
     */
    fun handleIntent(intent: TransactionProgressIntent) {
        when (intent) {
            is TransactionProgressIntent.LoadTransaction -> observeTransaction(intent.transactionId)
            is TransactionProgressIntent.RetryTransaction -> retryTransaction()
            is TransactionProgressIntent.AbortTransaction -> abortTransaction()
            is TransactionProgressIntent.NavigateBack -> navigateBack()
            is TransactionProgressIntent.NavigateToDetail -> navigateToDetail()
            is TransactionProgressIntent.DismissMessage -> dismissMessage()
            is TransactionProgressIntent.ClearNavigationEvent -> clearNavigationEvent()
            is TransactionProgressIntent.QueryTransaction -> queryTransaction()
        }
    }
    
    /**
     * Load and observe transaction by ID
     * Sets up observation of transaction status changes
     * 
     * @param transactionId Transaction ID to load and observe
     */
    fun observeTransaction(transactionId: String) {
        _state.update { it.copy(transactionId = transactionId, isLoading = true) }
        
        viewModelScope.launch {
            // Observe transaction changes from repository
            transactionRepository.transactionsFlow.collect { transactions ->
                val transaction = transactions.find { it.transactionRequestId == transactionId }
                
                if (transaction != null) {
                    currentTransaction = transaction
                    updateStateFromTransaction(transaction)
                } else {
                    // Transaction not found
                    _state.update {
                        it.copy(
                            isLoading = false,
                            canNavigateBack = true,
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Transaction Not Found",
                                content = "The requested transaction could not be found.",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                }
            }
        }
    }
    
    /**
     * Update state based on transaction data
     * Converts transaction model to UI state
     * 
     * @param transaction Transaction to update state from
     */
    private fun updateStateFromTransaction(transaction: Transaction) {
        val isComplete = transaction.status == TransactionStatus.SUCCESS ||
                        transaction.status == TransactionStatus.FAILED
        
        // Cloud mode: when transaction enters PROCESSING, start polling for final status.
        // pollingJob == null ensures we only start once.
        // QUERY, BATCH_CLOSE, and TIP_ADJUST return final results directly �?no polling needed.
        // All other types (SALE, AUTH, FORCED_AUTH, POST_AUTH, INCREMENTAL_AUTH, VOID, REFUND) need polling.
        if (transaction.status == TransactionStatus.PROCESSING && pollingJob == null) {
            val needsPolling = transaction.type != TransactionType.QUERY &&
                              transaction.type != TransactionType.BATCH_CLOSE &&
                              transaction.type != TransactionType.TIP_ADJUST
            if (needsPolling) {
                val isCloudMode = try {
                    val context = DependencyProvider.requireContext()
                    ConnectionPreferences.getConnectionMode(context) == ConnectionPreferences.ConnectionMode.CLOUD
                } catch (e: Exception) { false }
                
                if (isCloudMode) {
                    Log.d(TAG, "Cloud PROCESSING detected via repository observation, starting polling: ${transaction.transactionRequestId}")
                    startCloudPolling(transaction.transactionRequestId)
                }
            }
        }
        
        // Retry logic (POS style: success page has no retry, only failed)
        // - QUERY and BATCH_CLOSE cannot be retried
        // - SUCCESS: no retry button (payment succeeded)
        // - FAILED: show retry so user can try again
        val canRetry = when (transaction.type) {
            TransactionType.QUERY,
            TransactionType.BATCH_CLOSE -> false
            else -> isComplete && transaction.status != TransactionStatus.SUCCESS
        }
        
        val displayAmount = transaction.getDisplayAmount()
        
        _state.update {
            it.copy(
                transaction = transaction,
                transactionType = transaction.getDisplayName(),
                amount = displayAmount,
                status = transaction.status,
                progressStatus = transaction.progressStatus,
                progressMessage = transaction.progressMessage,
                isLoading = !isComplete,
                canNavigateBack = isComplete,
                canRetry = canRetry,
                showViewDetailsButton = transaction.status == TransactionStatus.SUCCESS,
                errorMessage = transaction.errorMessage ?: "",
                message = createMessageFromTransaction(transaction, canRetry)
            )
        }
    }
    
    /**
     * Create Message model from transaction status
     * Generates appropriate message based on transaction state
     * 
     * @param transaction Transaction to create message from
     * @param canRetry Whether the transaction can be retried
     * @return Message model or null if no message needed
     */
    private fun createMessageFromTransaction(transaction: Transaction, canRetry: Boolean): Message? {
        return when (transaction.status) {
            TransactionStatus.FAILED -> {
                // Don't show message popup for failed transactions
                // Error information is displayed directly in the UI
                null
            }
            else -> null
        }
    }
    
    /**
     * Retry transaction
     * Attempts to retry the current transaction using the same request ID
     * Supports all transaction types except QUERY and BATCH_CLOSE
     */
    private fun retryTransaction() {
        val transaction = currentTransaction ?: return
        
        // Check if transaction is complete (success or failed)
        val isComplete = transaction.status == TransactionStatus.SUCCESS ||
                        transaction.status == TransactionStatus.FAILED
        
        if (!isComplete) {
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.WARNING,
                        title = "Cannot Retry",
                        content = "Transaction is still in progress. Please wait for it to complete.",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
            return
        }
        
        // Check if transaction type supports retry
        if (transaction.type == TransactionType.QUERY ||
            transaction.type == TransactionType.BATCH_CLOSE) {
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Cannot Retry",
                        content = "${transaction.type.name} transactions cannot be retried.",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
            return
        }
        
        // Update state to show retrying
        _state.update {
            it.copy(
                isLoading = true,
                canRetry = false,
                message = Message(
                    type = MessageType.INFO,
                    title = "Retrying Transaction",
                    content = "Retrying ${transaction.getDisplayName()} with same request ID...",
                    actions = emptyList()
                )
            )
        }
        
        // Update transaction status to PROCESSING
        transactionRepository.updateTransactionStatus(
            transactionRequestId = transaction.transactionRequestId,
            status = TransactionStatus.PROCESSING
        )
        
        // Retry the transaction based on type
        viewModelScope.launch {
            try {
                retryTransactionByType(transaction)
            } catch (e: Exception) {
                // Handle retry failure
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transaction.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = "RETRY_ERROR",
                    errorMessage = "Failed to retry transaction: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Retry transaction based on its type
     * Executes the appropriate payment service method for the transaction type
     * 
     * @param transaction Transaction to retry
     */
    private fun retryTransactionByType(transaction: Transaction) {
        val callback = createPaymentCallback(transaction.transactionRequestId)
        
        when (transaction.type) {
            TransactionType.SALE -> {
                val tipConfig = com.sunmi.tapro.taplink.demo.util.TipConfigBuilder.buildFromPreferences(
                    com.sunmi.tapro.taplink.demo.di.DependencyProvider.requireContext()
                )
                paymentService.executeSale(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = "Retry: ${transaction.getDisplayName()}",
                    tipAmount = transaction.tipAmount,
                    taxAmount = transaction.taxAmount,
                    cashbackAmount = transaction.cashbackAmount,
                    serviceFee = transaction.serviceFee,
                    tipConfig = tipConfig,
                    callback = callback
                )
            }
            TransactionType.AUTH -> {
                paymentService.executeAuth(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = "Retry: ${transaction.getDisplayName()}",
                    callback = callback
                )
            }
            TransactionType.FORCED_AUTH -> {
                paymentService.executeForcedAuth(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = "Retry: ${transaction.getDisplayName()}",
                    tipAmount = transaction.tipAmount,
                    taxAmount = transaction.taxAmount,
                    callback = callback
                )
            }
            TransactionType.REFUND -> {
                paymentService.executeRefund(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    originalTransactionId = transaction.originalTransactionId ?: "",
                    originalTransactionRequestId = "",
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = "Retry: ${transaction.getDisplayName()}",
                    reason = "Retry: ${transaction.getDisplayName()}",
                    callback = callback
                )
            }
            else -> {
                // Other transaction types cannot be retried from progress screen
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transaction.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = "UNSUPPORTED_RETRY",
                    errorMessage = "This transaction type cannot be retried"
                )
            }
        }
    }
    
    /**
     * Create payment callback for transaction
     * Handles payment result and updates transaction repository
     * 
     * @param transactionRequestId Transaction request ID
     * @return PaymentCallback instance
     */
    private fun createPaymentCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                Log.d(TAG, "Payment callback result: $transactionRequestId, status=${result.transactionStatus}, code=${result.code}")
                
                // Reset retry manager on success
                retryManager.reset()
                
                // Cloud mode: transaction creation success != transaction complete.
                // Transactions pushed to terminal need polling:
                //   SALE, AUTH, FORCED_AUTH, POST_AUTH, INCREMENTAL_AUTH, REFUND �?always need polling.
                // Direct cloud-processed (no polling):
                //   VOID, TIP_ADJUST, QUERY, BATCH_CLOSE �?API success is final.
                val isCloudMode = try {
                    val context = DependencyProvider.requireContext()
                    ConnectionPreferences.getConnectionMode(context) == ConnectionPreferences.ConnectionMode.CLOUD
                } catch (e: Exception) {
                    false
                }
                
                val txnType = currentTransaction?.type
                val needsPolling = txnType == TransactionType.SALE ||
                                  txnType == TransactionType.AUTH ||
                                  txnType == TransactionType.FORCED_AUTH ||
                                  txnType == TransactionType.POST_AUTH ||
                                  txnType == TransactionType.INCREMENT_AUTH ||
                                  txnType == TransactionType.REFUND

                if (result.isFailed()) {
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.FAILED,
                        transactionId = result.transactionId ?: result.originalTransactionId,
                        errorCode = result.transactionResultCode ?: result.code,
                        errorMessage = result.message ?: "Transaction failed"
                    )
                    return
                }

                if (result.isProcessing() || (isCloudMode && needsPolling && !result.isTerminal())) {
                    Log.d(TAG, "Cloud mode: starting polling for final status: $transactionRequestId")
                    
                    // Keep transaction in PROCESSING status
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.PROCESSING,
                        transactionId = result.transactionId ?: result.originalTransactionId
                    )
                    
                    if (isCloudMode && needsPolling) {
                        // Start cloud polling only for cloud transactions that are still pending.
                        startCloudPolling(transactionRequestId)
                    }
                    return
                }
                
                // Convert SDK CardInfo to model CardInfo
                val cardInfo = result.cardInfo?.let { sdkCard ->
                    CardInfo(
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
                
                // Update transaction with success result
                // TIP_ADJUST response has tipAmount at top level (not nested in amount object),
                // so use result.tipAmount as fallback when amount.tipAmount is null.
                transactionRepository.updateTransactionWithAmounts(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.SUCCESS,
                    transactionId = result.transactionId ?: result.originalTransactionId,
                    authCode = result.authCode,
                    orderAmount = result.amount?.orderAmount,
                    totalAmount = result.amount?.transAmount,
                    tipAmount = result.amount?.tipAmount ?: result.tipAmount,
                    taxAmount = result.amount?.taxAmount,
                    cashbackAmount = result.amount?.cashbackAmount,
                    serviceFee = result.amount?.serviceFee,
                    completeTime = result.completeTime,
                    cardInfo = cardInfo
                )
            }
            
            override fun onFailure(code: String, message: String) {
                Log.e(TAG, "Communication error: $transactionRequestId - $code: $message")
                
                // onFailure = communication/technical error (connection lost, timeout, etc.)
                // NOT a transaction decline �?declines arrive via onSuccess with isFailed().
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
            }
            
            override fun onProgress(status: String, message: String) {
                Log.d(TAG, "Payment progress: $transactionRequestId - $status: $message")
                
                // Update transaction with progress information
                transactionRepository.updateTransactionProgress(
                    transactionRequestId = transactionRequestId,
                    progressStatus = status,
                    progressMessage = message
                )
            }
        }
    }

    /**
     * Start Cloud mode polling for transaction status
     * 
     * When a Cloud transaction returns PROCESSING status, this method polls the query API
     * at regular intervals until a terminal status (SUCCESS/FAILED) is received or
     * the maximum number of attempts is reached.
     * 
     * @param transactionRequestId Transaction request ID to poll
     */
    private fun startCloudPolling(transactionRequestId: String) {
        // Cancel any existing polling job
        pollingJob?.cancel()
        
        pollingJob = viewModelScope.launch {
            var attempts = 0
            Log.d(TAG, "Cloud polling started for: $transactionRequestId")
            
            _state.update {
                it.copy(
                    progressMessage = "${currentTransaction?.getDisplayName() ?: "Transaction"} processing..."
                )
            }
            
            while (attempts < CLOUD_POLL_MAX_ATTEMPTS) {
                delay(CLOUD_POLL_INTERVAL_MS)
                attempts++
                
                Log.d(TAG, "Cloud polling attempt $attempts/$CLOUD_POLL_MAX_ATTEMPTS for: $transactionRequestId")
                
                _state.update {
                    it.copy(
                        progressMessage = "${currentTransaction?.getDisplayName() ?: "Transaction"} processing..."
                    )
                }
                
                // Use suspendCancellableCoroutine to bridge callback-based API
                val pollResult = try {
                    suspendCancellableCoroutine<PollResult> { continuation ->
                        val callback = object : PaymentCallback {
                            override fun onSuccess(result: PaymentResult) {
                                if (continuation.isActive) {
                                    continuation.resume(PollResult.Success(result))
                                }
                            }
                            
                            override fun onFailure(code: String, message: String) {
                                if (continuation.isActive) {
                                    continuation.resume(PollResult.Failure(code, message))
                                }
                            }
                            
                            override fun onProgress(status: String, message: String) {
                                // Ignore progress during polling
                            }
                        }
                        
                        paymentService.executeQuery(
                            transactionRequestId = transactionRequestId,
                            callback = callback
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Cloud polling error at attempt $attempts: ${e.message}")
                    PollResult.Error(e.message ?: "Unknown error")
                }
                
                when (pollResult) {
                    is PollResult.Success -> {
                        val status = pollResult.result.transactionStatus
                        Log.d(TAG, "Cloud polling got status: $status at attempt $attempts")
                        
                        // Terminal statuses per API doc: S=Success, F=Failed, C=Closed
                        if (pollResult.result.isTerminal()) {
                            // Terminal status reached - update transaction and stop polling
                            val cardInfo = pollResult.result.cardInfo?.let { sdkCard ->
                                CardInfo(
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
                            
                            val transactionStatus = mapCloudStatus(status)
                            
                            transactionRepository.updateTransactionWithAmounts(
                                transactionRequestId = transactionRequestId,
                                status = transactionStatus,
                                transactionId = pollResult.result.transactionId,
                                authCode = pollResult.result.authCode,
                                orderAmount = pollResult.result.amount?.orderAmount,
                                totalAmount = pollResult.result.amount?.transAmount,
                                tipAmount = pollResult.result.amount?.tipAmount,
                                taxAmount = pollResult.result.amount?.taxAmount,
                                cashbackAmount = pollResult.result.amount?.cashbackAmount,
                                serviceFee = pollResult.result.amount?.serviceFee,
                                completeTime = pollResult.result.completeTime,
                                cardInfo = cardInfo
                            )
                            
                            Log.d(TAG, "Cloud polling completed with status: $status")
                            return@launch
                        }
                        // Non-terminal status (I or P) - continue polling
                    }
                    
                    is PollResult.Failure -> {
                        // Query failed but don't stop polling - the transaction may still be processing
                        Log.w(TAG, "Cloud polling query failed at attempt $attempts: ${pollResult.code} - ${pollResult.message}")
                    }
                    
                    is PollResult.Error -> {
                        // Exception occurred but don't stop polling
                        Log.w(TAG, "Cloud polling error at attempt $attempts: ${pollResult.errorMessage}")
                    }
                }
            }
            
            // Max attempts reached - timeout
            Log.w(TAG, "Cloud polling timed out after $CLOUD_POLL_MAX_ATTEMPTS attempts for: $transactionRequestId")
            
            _state.update {
                it.copy(
                    isLoading = false,
                    progressMessage = null,
                    message = Message(
                        type = MessageType.WARNING,
                        title = "Polling Timeout",
                        content = "Cloud transaction status polling timed out after ${CLOUD_POLL_MAX_ATTEMPTS * CLOUD_POLL_INTERVAL_MS / 1000} seconds. You can manually query the transaction status.",
                        actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                    )
                )
            }
        }
    }
    
    /**
     * Sealed class representing polling result types
     */
    private sealed class PollResult {
        data class Success(val result: PaymentResult) : PollResult()
        data class Failure(val code: String, val message: String) : PollResult()
        data class Error(val errorMessage: String) : PollResult()
    }

    /**
     * Abort/cancel an ongoing transaction
     * Uses PaymentService.executeAbort to cancel the current processing transaction.
     */
    private fun abortTransaction() {
        val transaction = currentTransaction ?: return

        // Only allow abort when transaction is still pending/processing
        if (transaction.status != TransactionStatus.PENDING &&
            transaction.status != TransactionStatus.PROCESSING
        ) {
            return
        }

        // Update UI state to show aborting progress
        _state.update {
            it.copy(
                isLoading = true,
                canRetry = false,
                canNavigateBack = false
            )
        }

        val callback = object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                // Mark the original transaction as failed (aborted)
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transaction.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    transactionId = result.originalTransactionId ?: transaction.transactionId
                )
            }

            override fun onFailure(code: String, message: String) {
                // Update transaction as failed when abort itself fails
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transaction.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
            }

            override fun onProgress(status: String, message: String) {
                // Optional: show abort progress messages
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.INFO,
                            title = "Aborting",
                            content = message,
                            actions = emptyList()
                        )
                    )
                }
            }
        }

        // Execute abort using original transaction identifiers
        paymentService.executeAbort(
            originalTransactionId = transaction.transactionId,
            originalTransactionRequestId = transaction.transactionRequestId,
            description = "user close the payment",
            callback = callback
        )
    }
    
    /**
     * Navigate back to main screen
     */
    private fun navigateBack() {
        _state.update {
            it.copy(navigationEvent = TransactionProgressNavigationEvent.ToMain)
        }
    }
    
    /**
     * Navigate to transaction detail screen
     */
    private fun navigateToDetail() {
        val transactionId = _state.value.transactionId ?: return
        _state.update {
            it.copy(navigationEvent = TransactionProgressNavigationEvent.ToDetail(transactionId))
        }
    }
    
    /**
     * Dismiss current message
     */
    private fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
    
    /**
     * Clear navigation event after handling
     */
    private fun clearNavigationEvent() {
        _state.update { it.copy(navigationEvent = null) }
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
                            // Update transaction with query result
                            val cardInfo = result.cardInfo?.let { sdkCard ->
                                CardInfo(
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
                            
                            val status = mapCloudStatus(result.transactionStatus)
                            
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
                                cardInfo = cardInfo
                            )
                            
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
     * Map Cloud API transaction status code to local TransactionStatus enum.
     *
     * API status reference (https://docs.sunbay.dev/zh/resources/reference/transaction-status):
     *   I = Initial (created, not yet processing) - non-terminal
     *   P = Processing - non-terminal
     *   S = Success - terminal
     *   F = Failed - terminal
     *   C = Closed - terminal
     *
     * TaplinkPaymentService (App-to-App) uses full names: SUCCESS, FAILED, PROCESSING
     * so we support both formats.
     */
    private fun mapCloudStatus(status: String?): TransactionStatus {
        return when (status) {
            "S", "SUCCESS" -> TransactionStatus.SUCCESS
            "F", "FAILED" -> TransactionStatus.FAILED
            "C" -> TransactionStatus.FAILED
            "P", "PROCESSING" -> TransactionStatus.PROCESSING
            "I" -> TransactionStatus.PENDING
            else -> TransactionStatus.PENDING
        }
    }

    /**
     * Clean up resources when ViewModel is cleared
     */
    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        pollingJob = null
        retryManager.reset()
    }
}
