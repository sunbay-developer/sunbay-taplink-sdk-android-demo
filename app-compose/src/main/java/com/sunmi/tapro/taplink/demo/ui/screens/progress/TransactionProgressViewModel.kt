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
import com.sunmi.tapro.taplink.demo.service.HeadlessProgressEvent
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.demo.ui.screens.main.PaymentOption
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.Constants
import com.sunmi.tapro.taplink.demo.util.ErrorHandler
import com.sunmi.tapro.taplink.demo.util.RetryManager
import com.sunmi.tapro.taplink.demo.util.RetryManagerFactory
import com.sunmi.tapro.taplink.demo.util.StaffInfoPreferences
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
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

    // Active transaction observation job (switches when requestId changes, e.g. retry)
    private var observeTransactionJob: Job? = null

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
            is TransactionProgressIntent.SwitchToManualEntry -> switchToManualEntry()
            is TransactionProgressIntent.SelectPaymentOption -> {
                _state.update { it.copy(selectedPaymentOption = intent.option) }
            }
            is TransactionProgressIntent.StartSelectedPayment -> startSelectedPayment()
            is TransactionProgressIntent.ToggleSplitMode -> {
                _state.update { current ->
                    if (current.isSplitMode) {
                        current.copy(
                            isSplitMode = false,
                            splitAmountInput = "",
                            splitLines = emptyList(),
                            splitCount = 2,
                            activeSplitLineIndex = null
                        )
                    } else {
                        val count = 2
                        val perAmount = current.amount.divide(BigDecimal(count), 2, java.math.RoundingMode.HALF_UP)
                        val lines = List(count) { SplitLine(paymentOption = current.selectedPaymentOption, amountInput = perAmount.toPlainString()) }
                        current.copy(
                            isSplitMode = true,
                            splitAmountInput = current.amount.toPlainString(),
                            splitCount = count,
                            splitLines = lines,
                            activeSplitLineIndex = null
                        )
                    }
                }
            }
            is TransactionProgressIntent.UpdateSplitAmount -> {
                _state.update { it.copy(splitAmountInput = intent.input) }
            }
            is TransactionProgressIntent.UpdateSplitCount -> {
                _state.update { current ->
                    val newCount = intent.count.coerceIn(1, 10)
                    val perAmount = current.amount.divide(BigDecimal(newCount), 2, java.math.RoundingMode.HALF_UP)
                    val existingLines = current.splitLines
                    val newLines = List(newCount) { index ->
                        if (index < existingLines.size) {
                            existingLines[index].copy(amountInput = perAmount.toPlainString())
                        } else {
                            SplitLine(paymentOption = current.selectedPaymentOption, amountInput = perAmount.toPlainString())
                        }
                    }
                    current.copy(
                        splitCount = newCount,
                        splitLines = newLines,
                        activeSplitLineIndex = current.activeSplitLineIndex?.takeIf { it in newLines.indices }
                    )
                }
            }
            is TransactionProgressIntent.UpdateSplitLinePaymentOption -> {
                _state.update { current ->
                    val updatedLines = current.splitLines.toMutableList()
                    if (intent.index in updatedLines.indices) {
                        updatedLines[intent.index] = updatedLines[intent.index].copy(paymentOption = intent.option)
                    }
                    current.copy(splitLines = updatedLines)
                }
            }
            is TransactionProgressIntent.UpdateSplitLineAmount -> {
                _state.update { current ->
                    val updatedLines = current.splitLines.toMutableList()
                    if (intent.index in updatedLines.indices) {
                        updatedLines[intent.index] = updatedLines[intent.index].copy(amountInput = intent.input)
                    }
                    current.copy(splitLines = updatedLines)
                }
            }
            is TransactionProgressIntent.StartSplitLinePayment -> {
                startSplitLinePayment(intent.index)
            }
            is TransactionProgressIntent.CancelOrder -> cancelOrder()
            is TransactionProgressIntent.StartSecondaryMemberInput -> {
                if (_state.value.isReadyForPaymentSelection()) {
                    _state.update { it.copy(isAwaitingSecondaryMemberInput = true) }
                }
            }
            is TransactionProgressIntent.CancelSecondaryMemberInput -> {
                _state.update { it.copy(isAwaitingSecondaryMemberInput = false) }
            }
            is TransactionProgressIntent.ApplySecondaryMemberInput -> {
                val memberInput = intent.memberInput.trim()
                currentTransaction?.let { transaction ->
                    transactionRepository.updateTransaction(transaction.transactionRequestId) {
                        it.copy(attach = memberInput)
                    }
                }
                _state.update {
                    it.copy(
                        memberInput = memberInput,
                        isAwaitingSecondaryMemberInput = false
                    )
                }
            }
            is TransactionProgressIntent.StartSecondaryTipSelection -> {
                if (_state.value.isReadyForPaymentSelection()) {
                    _state.update { it.copy(isAwaitingSecondaryTipSelection = true) }
                }
            }
            is TransactionProgressIntent.CancelSecondaryTipSelection -> {
                _state.update { it.copy(isAwaitingSecondaryTipSelection = false) }
            }
            is TransactionProgressIntent.ApplySecondaryTip -> applySecondaryTip(intent.tipAmount)
        }
    }

    private fun applySecondaryTip(tipAmount: BigDecimal) {
        val transaction = currentTransaction ?: return
        val normalizedTip = tipAmount.setScale(2, java.math.RoundingMode.HALF_UP)
        val previousTip = transaction.tipAmount ?: BigDecimal.ZERO
        val currentTotal = transaction.totalAmount ?: transaction.amount
        val updatedTotal = currentTotal.subtract(previousTip).add(normalizedTip)
        transactionRepository.updateTransaction(transaction.transactionRequestId) {
            it.copy(
                tipAmount = normalizedTip,
                totalAmount = updatedTotal
            )
        }
        _state.update { it.copy(isAwaitingSecondaryTipSelection = false) }
    }

    /**
     * Cancel the current order from READY_TO_PAY state.
     * Marks the transaction as cancelled locally and navigates back immediately;
     * sends abort to SDK as fire-and-forget so the terminal session is cleaned up.
     * The underlying order (referenceOrderId) is preserved so the user can edit and retry.
     */
    private fun cancelOrder() {
        if (_state.value.isAborting) return

        val transaction = currentTransaction ?: run {
            navigateBack()
            return
        }

        _state.update { it.copy(isAborting = true) }

        // Mark cancelled locally so the repository is consistent.
        transactionRepository.updateTransactionStatus(
            transactionRequestId = transaction.transactionRequestId,
            status = TransactionStatus.FAILED,
            errorCode = "USER_CANCELLED",
            errorMessage = "Transaction cancelled by user"
        )

        // Fire-and-forget abort so the terminal/SDK cleans up without blocking navigation.
        try {
            paymentService.executeAbort(
                originalTransactionId = transaction.transactionId,
                originalTransactionRequestId = transaction.transactionRequestId,
                description = "Transaction cancelled by user",
                callback = object : PaymentCallback {
                    override fun onSuccess(result: PaymentResult) { /* no-op */ }
                    override fun onFailure(code: String, message: String) {
                        Log.w(TAG, "cancelOrder abort failed: $code $message")
                    }
                    override fun onProgress(status: String, message: String) { /* no-op */ }
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "cancelOrder abort threw: ${e.message}")
        }

        navigateBack()
    }
    
    /**
     * Load and observe transaction by ID
     * Sets up observation of transaction status changes
     * 
     * @param transactionId Transaction ID to load and observe
     */
    fun observeTransaction(transactionId: String) {
        Log.d(TAG, "Start observing transaction requestId=$transactionId")
        _state.update { it.copy(transactionId = transactionId, isLoading = true) }

        // Keep only one collector alive to avoid stale transaction updates overriding current UI state.
        observeTransactionJob?.cancel()
        observeTransactionJob = viewModelScope.launch {
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
        val orderItems = transaction.referenceOrderId
            ?.let { transactionRepository.getOrderItems(it) }
            .orEmpty()
        
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
        val isReadyToPay = transaction.progressStatus.equals("READY_TO_PAY", ignoreCase = true)

        _state.update { current ->
            val selectedOption = if (current.transactionId == transaction.transactionRequestId) {
                current.selectedPaymentOption
            } else {
                inferPaymentOption(transaction)
            }
            current.copy(
                transaction = transaction,
                orderItems = orderItems,
                transactionType = transaction.getDisplayName(),
                amount = displayAmount,
                status = transaction.status,
                isAborting = if (isComplete) false else current.isAborting,
                progressStatus = transaction.progressStatus,
                progressMessage = transaction.progressMessage,
                headlessProgressEvent = transaction.headlessProgressEvent,
                isLoading = !isComplete && !isReadyToPay,
                isStartingPayment = if (isReadyToPay) current.isStartingPayment else false,
                selectedPaymentOption = selectedOption,
                canNavigateBack = isComplete || isReadyToPay,
                canRetry = canRetry,
                showViewDetailsButton = transaction.status == TransactionStatus.SUCCESS,
                errorMessage = transaction.errorMessage ?: "",
                message = createMessageFromTransaction(transaction, canRetry)
            )
        }

        // In split mode, after a successful payment, auto-return to READY_TO_PAY
        // so the user can continue paying the remaining split lines.
        if (transaction.status == TransactionStatus.SUCCESS && _state.value.isSplitMode) {
            viewModelScope.launch {
                delay(1500L) // Brief delay to show success feedback
                resetToReadyForNextSplit(transaction)
            }
        }
    }

    /**
     * After a split line payment succeeds, reset the transaction back to READY_TO_PAY
     * so the user can continue paying the remaining split lines.
     * Updates the remaining amount and resets UI state.
     */
    private fun resetToReadyForNextSplit(transaction: Transaction) {
        val stateValue = _state.value
        val updatedLines = stateValue.splitLines.toMutableList()
        val activeIndex = stateValue.activeSplitLineIndex

        if (activeIndex != null && activeIndex in updatedLines.indices && !updatedLines[activeIndex].isPaid) {
            updatedLines[activeIndex] = updatedLines[activeIndex].copy(isPaid = true)
        }

        val remaining = updatedLines
            .filterNot { it.isPaid }
            .mapNotNull { it.amountInput.toBigDecimalOrNull() }
            .fold(BigDecimal.ZERO) { acc, value -> acc + value }

        if (remaining <= BigDecimal.ZERO) {
            // All split lines have been paid. Keep split page visible with all lines marked as paid.
            transactionRepository.updateTransaction(transaction.transactionRequestId) { txn ->
                txn.copy(
                    status = TransactionStatus.SUCCESS,
                    progressStatus = "READY_TO_PAY",
                    progressMessage = "All split orders have been paid",
                    errorMessage = null,
                    errorCode = null
                )
            }

            _state.update {
                it.copy(
                    splitLines = updatedLines,
                    status = TransactionStatus.SUCCESS,
                    progressStatus = "READY_TO_PAY",
                    progressMessage = "All split orders have been paid",
                    activeSplitLineIndex = null,
                    isStartingPayment = false,
                    canNavigateBack = true,
                    canRetry = false,
                    showViewDetailsButton = true,
                    errorMessage = ""
                )
            }
            return
        }

        // Reset the transaction progress back to READY_TO_PAY
        transactionRepository.updateTransaction(transaction.transactionRequestId) { txn ->
            txn.copy(
                status = TransactionStatus.PROCESSING,
                progressStatus = "READY_TO_PAY",
                progressMessage = "Ready for next split payment",
                errorMessage = null,
                errorCode = null
            )
        }

        _state.update {
            it.copy(
                splitLines = updatedLines,
                status = TransactionStatus.PROCESSING,
                progressStatus = "READY_TO_PAY",
                progressMessage = "Ready for next split payment",
                isStartingPayment = false,
                activeSplitLineIndex = null,
                canNavigateBack = true,
                canRetry = false,
                showViewDetailsButton = false,
                errorMessage = ""
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

    private fun inferPaymentOption(transaction: Transaction): PaymentOption {
        return PaymentOption.entries.firstOrNull { option ->
            option.transactionType.name == transaction.type.name &&
                option.category == (transaction.paymentCategory ?: PaymentCategory.CARD) &&
                option.paymentMethodId == transaction.paymentMethodId &&
                option.subPaymentMethodId == transaction.subPaymentMethodId &&
                option.cardNetworkType == transaction.cardNetworkType
        } ?: PaymentOption.CARD_SALE
    }

    /**
     * Start payment for a specific split line by temporarily overriding selectedPaymentOption
     * and splitAmountInput, then delegating to startSelectedPayment.
     */
    private fun startSplitLinePayment(index: Int) {
        val stateValue = _state.value
        val lines = stateValue.splitLines
        if (index !in lines.indices) return
        val line = lines[index]
        if (line.isPaid) return
        // Temporarily set the selected option and split amount from the specific line
        _state.update {
            it.copy(
                selectedPaymentOption = line.paymentOption,
                splitAmountInput = line.amountInput,
                activeSplitLineIndex = index
            )
        }
        startSelectedPayment()
    }

    private fun startSelectedPayment() {
        val transaction = currentTransaction ?: return
        if (!transaction.progressStatus.equals("READY_TO_PAY", ignoreCase = true)) {
            return
        }

        val selected = _state.value.selectedPaymentOption
        val stateValue = _state.value
        val splitAmount = if (stateValue.isSplitMode) {
            stateValue.splitAmountInput.toBigDecimalOrNull()
        } else {
            null
        }

        if (stateValue.isSplitMode) {
            val maxAllowed = stateValue.splitRemaining()
            if (splitAmount == null || splitAmount <= BigDecimal.ZERO || splitAmount > maxAllowed) {
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.WARNING,
                            title = "Invalid Split Amount",
                            content = "Enter a valid amount between 0 and $maxAllowed.",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
                return
            }
        }

        val extrasTotal = (transaction.tipAmount ?: BigDecimal.ZERO)
            .add(transaction.taxAmount ?: BigDecimal.ZERO)
            .add(transaction.serviceFee ?: BigDecimal.ZERO)
            .add(transaction.surchargeAmount ?: BigDecimal.ZERO)
        val saleBaseAmount = transaction.totalAmount
            ?.subtract(extrasTotal)
            ?.takeIf { it > BigDecimal.ZERO }
            ?: transaction.amount

        val requestedAmount = splitAmount ?: when (selected.transactionType) {
            // Keep checkout parity with MainScreen flows:
            // - SALE/FORCED_AUTH must send goods(subtotal) as base amount; extras are passed separately.
            //   Derive from total-extras when available to avoid subtotal/total mix-ups.
            // - AUTH keeps total amount semantics.
            PaymentOption.TransactionType.SALE,
            PaymentOption.TransactionType.FORCED_AUTH -> saleBaseAmount
            PaymentOption.TransactionType.AUTH -> transaction.totalAmount ?: transaction.amount
        }
        var transactionForRequest = transaction
        if (stateValue.isSplitMode) {
            val splitRequestId = generateRetryTransactionRequestId()
            val splitTransaction = transaction.copy(
                transactionRequestId = splitRequestId,
                status = TransactionStatus.PENDING,
                timestamp = System.currentTimeMillis(),
                transactionId = null,
                authCode = null,
                errorCode = null,
                errorMessage = null,
                progressStatus = "READY_TO_PAY",
                progressMessage = "Preparing split payment",
                headlessProgressEvent = null,
                completeTime = null,
                cardInfo = null
            )
            if (!transactionRepository.addTransaction(splitTransaction)) {
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Split Payment Error",
                            content = "Failed to create split transaction ID. Please try again.",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
                return
            }
            transactionForRequest = splitTransaction
            currentTransaction = splitTransaction
            observeTransaction(splitRequestId)
        }

        _state.update { it.copy(isStartingPayment = true) }

        transactionRepository.updateTransaction(transactionForRequest.transactionRequestId) { txn ->
            txn.copy(
                type = when (selected.transactionType) {
                    PaymentOption.TransactionType.SALE -> TransactionType.SALE
                    PaymentOption.TransactionType.AUTH -> TransactionType.AUTH
                    PaymentOption.TransactionType.FORCED_AUTH -> TransactionType.FORCED_AUTH
                },
                paymentCategory = selected.category,
                paymentMethodId = selected.paymentMethodId,
                subPaymentMethodId = selected.subPaymentMethodId,
                cardNetworkType = selected.cardNetworkType,
                amount = requestedAmount,
                totalAmount = if (stateValue.isSplitMode) {
                    requestedAmount
                } else {
                    txn.totalAmount ?: requestedAmount
                },
                tipAmount = if (stateValue.isSplitMode) null else txn.tipAmount,
                taxAmount = if (stateValue.isSplitMode) null else txn.taxAmount,
                serviceFee = if (stateValue.isSplitMode) null else txn.serviceFee,
                surchargeAmount = if (stateValue.isSplitMode) null else txn.surchargeAmount,
                attach = stateValue.memberInput.takeIf { it.isNotBlank() } ?: txn.attach,
                progressStatus = "INITIATING_PAYMENT",
                progressMessage = if (stateValue.isSplitMode) {
                    "Initiating split payment..."
                } else {
                    "Initiating payment..."
                }
            )
        }

        val callback = createPaymentCallback(transactionForRequest.transactionRequestId)

        try {
            when (selected.transactionType) {
                PaymentOption.TransactionType.SALE -> {
                    val tipConfig = com.sunmi.tapro.taplink.demo.util.TipConfigBuilder.buildFromPreferences(
                        DependencyProvider.requireContext()
                    )
                    paymentService.executeSale(
                        referenceOrderId = transactionForRequest.referenceOrderId ?: "",
                        transactionRequestId = transactionForRequest.transactionRequestId,
                        amount = requestedAmount,
                        currency = transactionForRequest.currency,
                        description = "Sale - ${_state.value.orderItems.size} items",
                        paymentCategory = selected.category,
                        paymentMethodId = selected.paymentMethodId,
                        subPaymentMethodId = selected.subPaymentMethodId,
                        cardNetworkType = selected.cardNetworkType,
                        tipAmount = if (stateValue.isSplitMode) null else transactionForRequest.tipAmount,
                        taxAmount = if (stateValue.isSplitMode) null else transactionForRequest.taxAmount,
                        serviceFee = if (stateValue.isSplitMode) null else transactionForRequest.serviceFee,
                        surchargeAmount = if (stateValue.isSplitMode) null else transactionForRequest.surchargeAmount,
                        attach = stateValue.memberInput.takeIf { it.isNotBlank() } ?: transactionForRequest.attach,
                        staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                        tipConfig = tipConfig,
                        callback = callback,
                        signatureConfig = resolveSignatureForSaleOrAuth()
                    )
                }

                PaymentOption.TransactionType.AUTH -> {
                    paymentService.executeAuth(
                        referenceOrderId = transactionForRequest.referenceOrderId ?: "",
                        transactionRequestId = transactionForRequest.transactionRequestId,
                        amount = requestedAmount,
                        currency = transactionForRequest.currency,
                        description = "Auth - ${_state.value.orderItems.size} items",
                        attach = stateValue.memberInput.takeIf { it.isNotBlank() } ?: transactionForRequest.attach,
                        staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                        callback = callback,
                        signatureConfig = resolveSignatureForSaleOrAuth()
                    )
                }

                PaymentOption.TransactionType.FORCED_AUTH -> {
                    paymentService.executeForcedAuth(
                        referenceOrderId = transactionForRequest.referenceOrderId ?: "",
                        transactionRequestId = transactionForRequest.transactionRequestId,
                        amount = requestedAmount,
                        currency = transactionForRequest.currency,
                        description = "Forced Auth - ${_state.value.orderItems.size} items",
                        tipAmount = if (stateValue.isSplitMode) null else transactionForRequest.tipAmount,
                        taxAmount = if (stateValue.isSplitMode) null else transactionForRequest.taxAmount,
                        attach = stateValue.memberInput.takeIf { it.isNotBlank() } ?: transactionForRequest.attach,
                        staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                        callback = callback
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start selected payment", e)
            _state.update {
                it.copy(
                    isStartingPayment = false,
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Payment Error",
                        content = e.message ?: "Failed to start payment",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
            transactionRepository.updateTransactionStatus(
                transactionRequestId = transactionForRequest.transactionRequestId,
                status = TransactionStatus.FAILED,
                errorCode = "START_PAYMENT_ERROR",
                errorMessage = e.message ?: "Failed to start payment"
            )
        }
    }
    
    /**
     * Retry transaction
     * Attempts to retry the current transaction using a new request ID
     * Supports all transaction types except QUERY and BATCH_CLOSE
     */
    private fun retryTransaction() {
        val transaction = currentTransaction ?: return

        // Guard: a QUERY is still in-flight — terminal is busy processing it.
        // Sending a new SALE now would race with the QUERY and trigger error 307.
        if (_state.value.isQuerying) return

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

        // Generate a new request ID to avoid duplicated-transaction rejection on terminal/API side.
        val retryTransactionRequestId = generateRetryTransactionRequestId()
        val retryTransaction = transaction.copy(
            transactionRequestId = retryTransactionRequestId,
            status = TransactionStatus.PENDING,
            timestamp = System.currentTimeMillis(),
            transactionId = null,
            authCode = null,
            errorCode = null,
            errorMessage = null,
            progressStatus = null,
            progressMessage = null,
            headlessProgressEvent = null,
            completeTime = null,
            cardInfo = null
        )
        Log.i(
            TAG,
            "Retry requested: originalRequestId=${transaction.transactionRequestId}, " +
                "newRequestId=$retryTransactionRequestId, type=${transaction.type}, " +
                "paymentCategory=${transaction.paymentCategory}, paymentMethodId=${transaction.paymentMethodId}, " +
                "subPaymentMethodId=${transaction.subPaymentMethodId}, cardNetworkType=${transaction.cardNetworkType}"
        )

        if (!transactionRepository.addTransaction(retryTransaction)) {
            Log.e(TAG, "Failed to add retry transaction, duplicated requestId=$retryTransactionRequestId")
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Retry Failed",
                        content = "Failed to create a new retry request. Please try again.",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
            return
        }

        // Switch current screen observation to the new request ID immediately.
        currentTransaction = retryTransaction
        observeTransaction(retryTransactionRequestId)

        // Update state to show retrying
        _state.update {
            it.copy(
                isLoading = true,
                canRetry = false,
                message = Message(
                    type = MessageType.INFO,
                    title = "Retrying Transaction",
                    content = "Retrying ${transaction.getDisplayName()} with new request ID: $retryTransactionRequestId",
                    actions = emptyList()
                )
            )
        }

        // Retry the transaction based on type
        viewModelScope.launch {
            try {
                retryTransactionByType(retryTransaction)
            } catch (e: Exception) {
                // Handle retry failure
                Log.e(
                    TAG,
                    "Retry dispatch failed: requestId=$retryTransactionRequestId, type=${retryTransaction.type}, error=${e.message}",
                    e
                )
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = retryTransactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = "RETRY_ERROR",
                    errorMessage = "Failed to retry transaction: ${e.message}"
                )
            }
        }
    }

    /**
     * Generate a unique transaction request ID for retry operations.
     *
     * Business rule: retry requests must use a brand-new request ID, otherwise terminal/API may
     * treat it as duplicated transaction.
     *
     * @return New unique transaction request ID.
     */
    private fun generateRetryTransactionRequestId(): String {
        var timestamp = System.currentTimeMillis()
        var candidate = "${Constants.TRANSACTION_REQUEST_ID_PREFIX}$timestamp"
        while (transactionRepository.isTransactionRequestIdExists(candidate)) {
            timestamp++
            candidate = "${Constants.TRANSACTION_REQUEST_ID_PREFIX}$timestamp"
        }
        return candidate
    }

    /**
     * Retry transaction based on its type
     * Executes the appropriate payment service method for the transaction type
     * 
     * @param transaction Transaction to retry
     */
    private fun retryTransactionByType(transaction: Transaction) {
        Log.i(
            TAG,
            "Dispatch retry payment request: requestId=${transaction.transactionRequestId}, type=${transaction.type}, referenceOrderId=${transaction.referenceOrderId}"
        )
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
                    description = transaction.description ?: "Sale - 0 items",
                    paymentCategory = transaction.paymentCategory ?: PaymentCategory.CARD,
                    paymentMethodId = transaction.paymentMethodId,
                    subPaymentMethodId = transaction.subPaymentMethodId,
                    cardNetworkType = transaction.cardNetworkType,
                    tipAmount = transaction.tipAmount,
                    taxAmount = transaction.taxAmount,
                    cashbackAmount = transaction.cashbackAmount,
                    serviceFee = transaction.serviceFee,
                    surchargeAmount = transaction.surchargeAmount,
                    attach = transaction.attach,
                    staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                    tipConfig = tipConfig,
                    callback = callback,
                    signatureConfig = resolveSignatureForSaleOrAuth()
                )
            }
            TransactionType.AUTH -> {
                paymentService.executeAuth(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = transaction.description ?: "Auth - 0 items",
                    attach = transaction.attach,
                    staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                    callback = callback,
                    signatureConfig = resolveSignatureForSaleOrAuth()
                )
            }
            TransactionType.FORCED_AUTH -> {
                paymentService.executeForcedAuth(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = transaction.description ?: "Forced Auth - 0 items",
                    tipAmount = transaction.tipAmount,
                    taxAmount = transaction.taxAmount,
                    attach = transaction.attach,
                    staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
                    callback = callback
                )
            }
            TransactionType.REFUND -> {
                paymentService.executeRefund(
                    referenceOrderId = transaction.referenceOrderId ?: "",
                    transactionRequestId = transaction.transactionRequestId,
                    originalTransactionId = transaction.originalTransactionId ?: "",
                    originalTransactionRequestId = transaction.originalTransactionRequestId ?: "",
                    amount = transaction.amount,
                    currency = transaction.currency,
                    description = transaction.description ?: "Refund",
                    reason = transaction.reason ?: "Customer request",
                    paymentCategory = transaction.paymentCategory,
                    cardNetworkType = transaction.cardNetworkType,
                    staffInfo = StaffInfoPreferences.buildSdkStaffInfo(DependencyProvider.requireContext()),
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
    
    private fun resolveSignatureForSaleOrAuth() =
        com.sunmi.tapro.taplink.demo.util.SignatureConfigBuilder.buildFromPreferences(
            DependencyProvider.requireContext()
        )

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

                if (isCloudMode) {
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

                    if (needsPolling && !result.isTerminal()) {
                        Log.d(TAG, "Cloud mode: starting polling for final status: $transactionRequestId")

                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.PROCESSING,
                            transactionId = result.transactionId ?: result.originalTransactionId
                        )

                        startCloudPolling(transactionRequestId)
                        return
                    }
                }

                if (result.isFailed() || result.isCancelled()) {
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.FAILED,
                        transactionId = result.transactionId ?: result.originalTransactionId,
                        errorCode = result.transactionResultCode ?: result.code,
                        errorMessage = if (result.isCancelled()) "Transaction cancelled" else (result.message ?: "Transaction failed")
                    )
                    return
                }

                if (result.isProcessing()) {
                    Log.d(TAG, "Transaction is processing: $transactionRequestId")

                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.PROCESSING,
                        transactionId = result.transactionId ?: result.originalTransactionId
                    )
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
                    cardInfo = cardInfo,
                    receiptJson = result.receiptJson
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

            override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                Log.d(
                    TAG,
                    "Headless progress: $transactionRequestId - ${event.eventCode}, " +
                        "eventId=${event.eventId}, waitingCardTypes=${event.waitingCardTypes}"
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
        if (_state.value.isAborting) return

        // Only allow abort when transaction is still pending/processing
        if (transaction.status != TransactionStatus.PENDING &&
            transaction.status != TransactionStatus.PROCESSING
        ) {
            return
        }

        // Headless mode provides authoritative cancelability. If the engine says
        // the stage is not cancelable, keep the UI consistent with Tapro rules.
        if (transaction.headlessProgressEvent?.cancelable == false) {
            Log.w(TAG, "abortTransaction ignored: headless stage is not cancelable")
            return
        }

        // Update UI state to show aborting progress
        _state.update {
            it.copy(
                isLoading = true,
                isAborting = true,
                canRetry = false,
                canNavigateBack = false
            )
        }

        val callback = object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                val currentTransaction = currentTransaction ?: return
                if (result.isFailed()) {
                    Log.w(TAG, "abortTransaction result rejected: code=${result.code} msg=${result.message}")
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isAborting = false,
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Abort Rejected",
                                content = result.message.ifBlank {
                                    "The transaction cannot be cancelled at this stage."
                                },
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return
                }

                Log.d(TAG, "abortTransaction result completed: code=${result.code} msg=${result.message}")
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = currentTransaction.transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = result.transactionResultCode ?: "USER_CANCELLED",
                    errorMessage = result.message ?: "Transaction cancelled by user"
                )
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAborting = false,
                        message = Message(
                            type = MessageType.INFO,
                            title = "Abort Request",
                            content = result.message.ifBlank { "Transaction cancelled by user." },
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            }

            override fun onFailure(code: String, message: String) {
                // Abort was rejected by the terminal — the transaction is still running on the
                // Tapro side. Do NOT mark as failed. Restore the waiting UI so the user can
                // see that the transaction is still in progress and wait for it to complete.
                Log.w(TAG, "abortTransaction rejected: $code $message — continuing to wait")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isAborting = false,
                        message = Message(
                            type = MessageType.WARNING,
                            title = "Abort Rejected",
                            content = message.ifBlank {
                                "The transaction cannot be cancelled at this stage. Please complete the transaction on the terminal."
                            },
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
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
        try {
            paymentService.executeAbort(
                originalTransactionId = transaction.transactionId,
                originalTransactionRequestId = transaction.transactionRequestId,
                description = "user close the payment",
                callback = callback
            )
        } catch (e: Exception) {
            Log.e(TAG, "abortTransaction failed to start", e)
            _state.update {
                it.copy(
                    isLoading = false,
                    isAborting = false,
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Abort Failed",
                        content = e.message ?: "Unable to send the abort request.",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
        }
    }

    /**
     * 请求将当前交易切换到“手输卡号”。
     *
     * 业务约束：仅在交易进行中且进度阶段为 WAITING_CARD 时触发。
     * 最终阶段校验仍由 Tapro 侧执行，ViewModel 只做前置 UI 门控。
     */
    private fun switchToManualEntry() {
        val transaction = currentTransaction ?: return
        val progressStatus = transaction.headlessProgressEvent?.eventCode?.uppercase()
            ?: transaction.progressStatus?.uppercase()
        val isWaitingCard = progressStatus == "WAITING_CARD"
        val isPendingTxn = transaction.status == TransactionStatus.PENDING ||
            transaction.status == TransactionStatus.PROCESSING
        if (!isPendingTxn || !isWaitingCard) {
            Log.w(
                TAG,
                "switchToManualEntry ignored: status=${transaction.status}, progressStatus=${transaction.progressStatus}"
            )
            return
        }

        transactionRepository.updateTransactionProgress(
            transactionRequestId = transaction.transactionRequestId,
            progressStatus = "SWITCHING_MANUAL_ENTRY",
            progressMessage = "Requesting terminal to switch to manual card entry..."
        )

        paymentService.executeSwitchToManualEntry(
            originalTransactionRequestId = transaction.transactionRequestId,
            callback = object : PaymentCallback {
                override fun onSuccess(result: PaymentResult) {
                    Log.i(TAG, "switchToManualEntry success: ${transaction.transactionRequestId}")
                }

                override fun onFailure(code: String, message: String) {
                    Log.w(TAG, "switchToManualEntry failed: $code, $message")
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Switch Failed",
                                content = "Failed to switch to manual entry: $message (Code: $code)",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                }

                override fun onProgress(status: String, message: String) {
                    // 透传阶段信息给状态栏，便于观察 WAITING_MANUAL_ENTRY/MANUAL_ENTRY_COMPLETED。
                    transactionRepository.updateTransactionProgress(
                        transactionRequestId = transaction.transactionRequestId,
                        progressStatus = status,
                        progressMessage = message
                    )
                }

                override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                    transactionRepository.updateTransactionProgress(
                        transactionRequestId = transaction.transactionRequestId,
                        progressStatus = event.eventCode,
                        progressMessage = event.eventMessage,
                        headlessProgressEvent = event
                    )
                }
            }
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
     *   C = Cancelled - terminal (transaction was cancelled by user or system)
     *
     * TaplinkPaymentService (App-to-App) uses full names: SUCCESS, FAILED, PROCESSING
     * so we support both formats.
     */
    private fun mapCloudStatus(status: String?): TransactionStatus {
        return when (status) {
            "S", "SUCCESS" -> TransactionStatus.SUCCESS
            "F", "FAILED" -> TransactionStatus.FAILED
            "C", "CANCELLED" -> TransactionStatus.FAILED
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
        observeTransactionJob?.cancel()
        observeTransactionJob = null
        pollingJob?.cancel()
        pollingJob = null
        retryManager.reset()
    }
}
