package com.sunmi.tapro.taplink.demo.ui.screens.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Product
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.ConnectionManager
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.service.PaymentCallback
import com.sunmi.tapro.taplink.demo.service.PaymentResult
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Main Screen ViewModel
 * 
 * Implements MVI (Model-View-Intent) architecture pattern for the Main Screen.
 * Manages UI state, handles user intents, and coordinates business logic.
 * 
 * Key Responsibilities:
 * - Product catalog management
 * - Order management (add/remove items)
 * - Payment processing (SALE, AUTH, FORCED_AUTH)
 * - Connection status monitoring
 * - Error handling with Message model
 * - Navigation event management
 * 
 * MVI Pattern:
 * - Intent: User actions (MainIntent sealed class)
 * - Model: UI state (MainState data class)
 * - View: Composable UI that observes state
 * 
 * State Management:
 * - Immutable state updates using copy()
 * - Single source of truth (StateFlow)
 * - Unidirectional data flow
 */
class MainViewModel(
    application: Application,
    private val connectionManager: ConnectionManager = DependencyProvider.connectionManager,
    private val transactionRepository: TransactionRepository = DependencyProvider.transactionRepository
) : AndroidViewModel(application) {

    private val paymentService: PaymentService
        get() = DependencyProvider.paymentService
    
    companion object {
        private const val TAG = "MainViewModel"
        private const val CURRENCY = "USD"
        private const val SUBTOTAL_ADJUSTMENT_PRODUCT_ID = "subtotal_adjustment"
        private const val SUBTOTAL_ADJUSTMENT_PRODUCT_NAME = "Custom Amount"
    }
    
    // Product preferences for custom products
    private val productPreferences = com.sunmi.tapro.taplink.demo.util.ProductPreferences(application)
    
    // MVI State
    private val _state = MutableStateFlow(MainState())
    val state: StateFlow<MainState> = _state.asStateFlow()
    
    init {
        loadProducts()
        observeConnectionStatus()
    }
    
    /**
     * Handle user intents
     * 
     * Central intent handler following MVI pattern.
     * All user actions are routed through this method.
     * 
     * @param intent User intent to handle
     */
    fun handleIntent(intent: MainIntent) {
        when (intent) {
            is MainIntent.AddProduct -> addProduct(intent.product)
            is MainIntent.AddCustomAmount -> addCustomAmount(intent.amount)
            is MainIntent.RemoveOrderItem -> removeOrderItem(intent.item)
            is MainIntent.ShowAdditionalAmountsDialog -> showAdditionalAmountsDialog()
            is MainIntent.HideAdditionalAmountsDialog -> hideAdditionalAmountsDialog()
            is MainIntent.SetAdditionalAmounts -> setAdditionalAmounts(intent.amounts)
            is MainIntent.ShowAddProductDialog -> showAddProductDialog()
            is MainIntent.HideAddProductDialog -> hideAddProductDialog()
            is MainIntent.SaveNewProduct -> saveNewProduct(intent.name, intent.price)
            is MainIntent.StartEditingSubtotal -> startEditingSubtotal()
            is MainIntent.StopEditingSubtotal -> stopEditingSubtotal()
            is MainIntent.ProcessPayment -> processPayment()
            is MainIntent.SelectPaymentOption -> selectPaymentOption(intent.option)
            is MainIntent.NavigateToTransactionList -> navigateToTransactionList()
            is MainIntent.NavigateToSettings -> navigateToSettings()
            is MainIntent.DismissMessage -> dismissMessage()
            is MainIntent.RetryConnection -> retryConnection()
            is MainIntent.ClearNavigationEvent -> clearNavigationEvent()
        }
    }
    
    /**
     * Load product catalog
     * 
     * Initializes the product list with sample products and custom products from cache.
     * In a real application, this would load from a database or API.
     */
    private fun loadProducts() {
        val defaultProducts = listOf(
            Product(id = "1", name = "Espresso", price = BigDecimal("3.50")),
            Product(id = "2", name = "Cappuccino", price = BigDecimal("4.75")),
            Product(id = "3", name = "Latte", price = BigDecimal("4.50")),
            Product(id = "4", name = "Americano", price = BigDecimal("3.25")),
            Product(id = "5", name = "Mocha", price = BigDecimal("5.25")),
            Product(id = "6", name = "Croissant", price = BigDecimal("3.25")),
            Product(id = "7", name = "Muffin", price = BigDecimal("3.75")),
            Product(id = "8", name = "Bagel", price = BigDecimal("2.95")),
            Product(id = "9", name = "Sandwich", price = BigDecimal("8.50")),
            Product(id = "10", name = "Salad Bowl", price = BigDecimal("9.95")),
            Product(id = "11", name = "Soup", price = BigDecimal("6.50")),
            Product(id = "12", name = "Cake Slice", price = BigDecimal("4.95"))
        )
        
        // Load custom products from preferences
        val customProducts = productPreferences.getCustomProducts()
        
        // Combine default and custom products
        val allProducts = defaultProducts + customProducts
        
        _state.update { it.copy(products = allProducts) }
    }
    
    /**
     * Observe connection status changes
     * 
     * Monitors ConnectionManager state and updates UI accordingly.
     * Handles connection errors by displaying appropriate messages.
     */
    private fun observeConnectionStatus() {
        viewModelScope.launch {
            connectionManager.connectionState.collect { connectionState ->
                when (connectionState) {
                    is ConnectionState.Connected -> {
                        _state.update { currentState ->
                            currentState.copy(
                                connectionStatus = connectionState,
                                canProcessPayment = true,
                                message = null
                            )
                        }
                    }
                    is ConnectionState.Error -> {
                        _state.update { currentState ->
                            currentState.copy(
                                connectionStatus = connectionState,
                                canProcessPayment = true,
                                message = Message(
                                    type = MessageType.ERROR,
                                    title = "Connection Error",
                                    content = connectionState.message,
                                    actions = listOf(
                                        MessageAction.RETRY,
                                        MessageAction.SETTINGS,
                                        MessageAction.DISMISS
                                    )
                                )
                            )
                        }
                    }
                    is ConnectionState.Disconnected -> {
                        _state.update { currentState ->
                            currentState.copy(
                                connectionStatus = connectionState,
                                canProcessPayment = true,
                                message = Message(
                                    type = MessageType.WARNING,
                                    title = "Disconnected",
                                    content = "Please connect to payment terminal",
                                    actions = listOf(
                                        MessageAction.SETTINGS,
                                        MessageAction.DISMISS
                                    )
                                )
                            )
                        }
                    }
                    is ConnectionState.Connecting -> {
                        _state.update { currentState ->
                            currentState.copy(
                                connectionStatus = connectionState,
                                canProcessPayment = true,
                                message = Message(
                                    type = MessageType.INFO,
                                    title = "Connecting",
                                    content = "Connecting to payment terminal...",
                                    actions = emptyList()
                                )
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Intent Handlers
    
    /**
     * Add product to order
     * 
     * Adds a product to the current order. If the product already exists,
     * increments its quantity. Otherwise, adds it as a new order item.
     * 
     * @param product Product to add
     */
    private fun addProduct(product: Product) {
        val currentItems = _state.value.orderItems.toMutableList()
        val existingItem = currentItems.find { it.product.id == product.id }
        
        if (existingItem != null) {
            // Product already in order, increment quantity
            val index = currentItems.indexOf(existingItem)
            currentItems[index] = existingItem.copy(quantity = existingItem.quantity + 1)
        } else {
            // New product, add to order
            currentItems.add(OrderItem(product, 1))
        }
        
        val filteredItems = currentItems.filterNot { it.product.id == SUBTOTAL_ADJUSTMENT_PRODUCT_ID }
        _state.update { it.copy(orderItems = filteredItems) }
        calculateTotal()
    }
    
    /**
     * Remove order item
     * 
     * Removes an item from the current order. If quantity > 1, decrements quantity.
     * If quantity = 1, removes the item completely.
     * 
     * @param item Order item to remove
     */
    private fun removeOrderItem(item: OrderItem) {
        val currentItems = _state.value.orderItems.toMutableList()
        val existingItem = currentItems.find { it.product.id == item.product.id }
        
        if (existingItem != null) {
            if (existingItem.quantity > 1) {
                // Decrement quantity
                val index = currentItems.indexOf(existingItem)
                currentItems[index] = existingItem.copy(quantity = existingItem.quantity - 1)
            } else {
                // Remove item completely
                currentItems.remove(existingItem)
            }
        }
        
        val filteredItems = currentItems.filterNot { it.product.id == SUBTOTAL_ADJUSTMENT_PRODUCT_ID }
        _state.update { it.copy(orderItems = filteredItems) }
        calculateTotal()
    }
    
    private fun showAdditionalAmountsDialog() {
        _state.update { it.copy(showAdditionalAmountsDialog = true) }
    }
    
    private fun hideAdditionalAmountsDialog() {
        _state.update { it.copy(showAdditionalAmountsDialog = false) }
    }
    
    private fun setAdditionalAmounts(amounts: Map<String, BigDecimal>) {
        _state.update { it.copy(additionalAmounts = amounts, showAdditionalAmountsDialog = false) }
        calculateTotal()
    }
    
    private fun showAddProductDialog() {
        _state.update { it.copy(showAddProductDialog = true) }
    }
    
    private fun hideAddProductDialog() {
        _state.update { it.copy(showAddProductDialog = false) }
    }
    
    /**
     * Save new product to catalog
     * 
     * Adds a new custom product to the catalog and persists it to SharedPreferences.
     * 
     * @param name Product name
     * @param price Product price
     */
    private fun saveNewProduct(name: String, price: BigDecimal) {
        try {
            // Save to preferences
            val newProduct = productPreferences.addProduct(name, price)
            
            // Reload products to include the new one
            loadProducts()
            
            // Hide dialog
            _state.update { it.copy(showAddProductDialog = false) }
            
            // Show success message
            _state.update {
                it.copy(
                    message = Message(
                        type = MessageType.SUCCESS,
                        title = "Product Added",
                        content = "$name has been added to the catalog",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save product", e)
            _state.update {
                it.copy(
                    showAddProductDialog = false,
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Save Failed",
                        content = "Failed to save product: ${e.message}",
                        actions = listOf(MessageAction.DISMISS)
                    )
                )
            }
        }
    }
    
    private fun startEditingSubtotal() {
        _state.update { it.copy(isEditingSubtotal = true) }
    }
    
    private fun stopEditingSubtotal() {
        _state.update { it.copy(isEditingSubtotal = false) }
    }
    
    private fun addCustomAmount(amount: BigDecimal) {
        if (amount <= BigDecimal.ZERO) {
            return
        }

        val baseItems = _state.value.orderItems
            .filterNot { it.product.id == SUBTOTAL_ADJUSTMENT_PRODUCT_ID }
        val baseSubtotal = baseItems.fold(BigDecimal.ZERO) { acc, item ->
            acc.add(item.calculateSubtotal())
        }

        val adjustment = amount.subtract(baseSubtotal)
        val updatedItems = baseItems.toMutableList()

        if (adjustment.compareTo(BigDecimal.ZERO) != 0) {
            val customProduct = Product(
                id = SUBTOTAL_ADJUSTMENT_PRODUCT_ID,
                name = SUBTOTAL_ADJUSTMENT_PRODUCT_NAME,
                price = adjustment
            )
            updatedItems.add(OrderItem(customProduct, 1))
        }

        _state.update { it.copy(orderItems = updatedItems) }
        calculateTotal()
    }
    
    /**
     * Select payment option (Card-Sale, EBT-SNAP, etc.)
     */
    private fun selectPaymentOption(option: PaymentOption) {
        _state.update { it.copy(selectedPaymentOption = option) }
    }
    
    /**
     * Process payment based on selected payment option
     */
    private fun processPayment() {
        val option = _state.value.selectedPaymentOption
        when (option.transactionType) {
            PaymentOption.TransactionType.SALE -> processSale()
            PaymentOption.TransactionType.AUTH -> processAuth()
            PaymentOption.TransactionType.FORCED_AUTH -> processForcedAuth()
        }
    }
    
    /**
     * Process SALE transaction
     * 
     * Initiates a SALE transaction with the current order.
     * Creates a transaction record, calls payment service, and navigates to progress screen.
     * 
     * Handles errors by displaying appropriate messages.
     */
    private fun processSale() {
        val option = _state.value.selectedPaymentOption
        processSaleWithCategory(option.category, option.paymentMethodId, option.subPaymentMethodId, option.cardNetworkType)
    }

    private fun processSaleWithCategory(
        paymentCategory: PaymentCategory,
        paymentMethodId: String? = null,
        subPaymentMethodId: String? = null,
        cardNetworkType: String? = null
    ) {
        viewModelScope.launch {
            try {
                // Validate order
                if (_state.value.orderItems.isEmpty()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Empty Order",
                                content = "Please add items to the order before processing payment",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }
                
                // Generate IDs using same format as dev branch
                val timestamp = System.currentTimeMillis()
                val referenceOrderId = "${com.sunmi.tapro.taplink.demo.util.Constants.ORDER_ID_PREFIX}$timestamp"
                val transactionRequestId = "${com.sunmi.tapro.taplink.demo.util.Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"
                
                // Create transaction record
                val transaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    type = TransactionType.SALE,
                    status = TransactionStatus.PENDING,
                    amount = _state.value.getItemsSubtotal(),
                    totalAmount = _state.value.totalAmount,
                    currency = "USD",
                    timestamp = System.currentTimeMillis(),
                    tipAmount = _state.value.additionalAmounts["Tip"],
                    taxAmount = _state.value.additionalAmounts["Tax"],
                    serviceFee = _state.value.additionalAmounts["Service Fee"],
                    surchargeAmount = _state.value.additionalAmounts["Surcharge"]
                )
                
                // Add to repository
                transactionRepository.addTransaction(transaction)
                // Persist order items for this order (POS: show "what was ordered" in detail)
                transactionRepository.saveOrderItems(referenceOrderId, _state.value.orderItems)

                // Show loading state on payment button
                _state.update { it.copy(isInitiatingPayment = true) }

                // Execute payment — navigation happens on first SDK callback
                val tipConfig = com.sunmi.tapro.taplink.demo.util.TipConfigBuilder.buildFromPreferences(
                    getApplication()
                )
                paymentService.executeSale(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = _state.value.getItemsSubtotal(),
                    currency = CURRENCY,
                    description = "Sale - ${_state.value.orderItems.size} items",
                    paymentCategory = paymentCategory,
                    paymentMethodId = paymentMethodId,
                    subPaymentMethodId = subPaymentMethodId,
                    cardNetworkType = cardNetworkType,
                    tipAmount = _state.value.additionalAmounts["Tip"],
                    taxAmount = _state.value.additionalAmounts["Tax"],
                    serviceFee = _state.value.additionalAmounts["Service Fee"],
                    surchargeAmount = _state.value.additionalAmounts["Surcharge"],
                    tipConfig = tipConfig,
                    callback = createNavigatingPaymentCallback(transactionRequestId)
                )
                
                // Clear order after initiating payment
                clearOrder()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to process SALE", e)
                _state.update {
                    it.copy(
                        isInitiatingPayment = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Payment Error",
                            content = e.message ?: "Failed to process payment",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Process AUTH (pre-authorization) transaction
     * 
     * Initiates an AUTH transaction with the current order.
     * Creates a transaction record, calls payment service, and navigates to progress screen.
     * 
     * Handles errors by displaying appropriate messages.
     */
    private fun processAuth() {
        viewModelScope.launch {
            try {
                // Validate order
                if (_state.value.orderItems.isEmpty()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Empty Order",
                                content = "Please add items to the order before processing payment",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }
                
                // Generate IDs using same format as dev branch
                val timestamp = System.currentTimeMillis()
                val referenceOrderId = "${com.sunmi.tapro.taplink.demo.util.Constants.ORDER_ID_PREFIX}$timestamp"
                val transactionRequestId = "${com.sunmi.tapro.taplink.demo.util.Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"
                
                // Create transaction record
                val transaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    type = TransactionType.AUTH,
                    status = TransactionStatus.PENDING,
                    amount = _state.value.totalAmount,
                    totalAmount = _state.value.totalAmount,
                    currency = "USD",
                    timestamp = System.currentTimeMillis()
                )
                
                // Add to repository
                transactionRepository.addTransaction(transaction)
                // Persist order items for this order (POS: show "what was ordered" in detail)
                transactionRepository.saveOrderItems(referenceOrderId, _state.value.orderItems)

                // Show loading state on payment button
                _state.update { it.copy(isInitiatingPayment = true) }

                // Execute payment — navigation happens on first SDK callback
                paymentService.executeAuth(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = _state.value.totalAmount,
                    currency = CURRENCY,
                    description = "Auth - ${_state.value.orderItems.size} items",
                    callback = createNavigatingPaymentCallback(transactionRequestId)
                )
                
                // Clear order after initiating payment
                clearOrder()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to process AUTH", e)
                _state.update {
                    it.copy(
                        isInitiatingPayment = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Payment Error",
                            content = e.message ?: "Failed to process payment",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    /**
     * Process FORCED_AUTH transaction
     * 
     * Initiates a FORCED_AUTH transaction with the current order.
     * Creates a transaction record, calls payment service, and navigates to progress screen.
     * 
     * Handles errors by displaying appropriate messages.
     */
    private fun processForcedAuth() {
        viewModelScope.launch {
            try {
                // Validate order
                if (_state.value.orderItems.isEmpty()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Empty Order",
                                content = "Please add items to the order before processing payment",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }
                
                // Generate IDs using same format as dev branch
                val timestamp = System.currentTimeMillis()
                val referenceOrderId = "${com.sunmi.tapro.taplink.demo.util.Constants.ORDER_ID_PREFIX}$timestamp"
                val transactionRequestId = "${com.sunmi.tapro.taplink.demo.util.Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"
                
                // Create transaction record
                val transaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    type = TransactionType.FORCED_AUTH,
                    status = TransactionStatus.PENDING,
                    amount = _state.value.getItemsSubtotal(),
                    totalAmount = _state.value.totalAmount,
                    currency = "USD",
                    timestamp = System.currentTimeMillis(),
                    tipAmount = _state.value.additionalAmounts["Tip"],
                    taxAmount = _state.value.additionalAmounts["Tax"]
                )
                
                // Add to repository
                transactionRepository.addTransaction(transaction)
                // Persist order items for this order (POS: show "what was ordered" in detail)
                transactionRepository.saveOrderItems(referenceOrderId, _state.value.orderItems)

                // Show loading state on payment button
                _state.update { it.copy(isInitiatingPayment = true) }

                // Execute payment — navigation happens on first SDK callback
                paymentService.executeForcedAuth(
                    referenceOrderId = referenceOrderId,
                    transactionRequestId = transactionRequestId,
                    amount = _state.value.getItemsSubtotal(),
                    currency = CURRENCY,
                    description = "Forced Auth - ${_state.value.orderItems.size} items",
                    tipAmount = _state.value.additionalAmounts["Tip"],
                    taxAmount = _state.value.additionalAmounts["Tax"],
                    callback = createNavigatingPaymentCallback(transactionRequestId)
                )
                
                // Clear order after initiating payment
                clearOrder()
                
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to process FORCED_AUTH", e)
                _state.update {
                    it.copy(
                        isInitiatingPayment = false,
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Payment Error",
                            content = e.message ?: "Failed to process payment",
                            actions = listOf(MessageAction.RETRY, MessageAction.DISMISS)
                        )
                    )
                }
            }
        }
    }
    
    private fun navigateToTransactionList() {
        _state.update { it.copy(navigationEvent = NavigationEvent.ToTransactionList) }
    }
    
    private fun navigateToSettings() {
        _state.update { it.copy(navigationEvent = NavigationEvent.ToSettings) }
    }
    
    private fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }
    
    private fun retryConnection() {
        dismissMessage()
        connectionManager.reconnect()
    }
    
    private fun clearNavigationEvent() {
        _state.update { it.copy(navigationEvent = null) }
    }
    
    /**
     * Calculate total amount
     * 
     * Calculates the total amount including items subtotal and additional amounts.
     * Payment buttons are always enabled regardless of amount or connection status.
     */
    private fun calculateTotal() {
        val itemsSubtotal = _state.value.getItemsSubtotal()
        val additionalTotal = _state.value.additionalAmounts.values.fold(BigDecimal.ZERO) { acc, amount ->
            acc.add(amount)
        }
        val total = itemsSubtotal.add(additionalTotal)
        
        _state.update {
            it.copy(
                totalAmount = total,
                canProcessPayment = true
            )
        }
    }
    
    /**
     * Create a payment callback that navigates to the progress screen and resets
     * the isInitiatingPayment loading state.
     *
     * Behaviour depends on connection mode:
     * - **App-to-App**: The SDK call launches the Tapro app which handles the
     *   entire payment flow. We stay on MainScreen (showing loading) until the
     *   final result (onSuccess / onFailure) comes back, then navigate to the
     *   progress screen to display the result. onProgress callbacks only update
     *   the repository without triggering navigation.
     * - **Other modes** (Cable / LAN / Cloud): Navigate on the first callback
     *   (onProgress / onSuccess / onFailure) so the user sees real-time progress.
     */
    private fun createNavigatingPaymentCallback(transactionRequestId: String): PaymentCallback {
        val inner = createPaymentCallback(transactionRequestId)
        var navigated = false

        val isAppToApp = try {
            val context = getApplication<Application>()
            com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                .getConnectionMode(context) == com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.APP_TO_APP
        } catch (e: Exception) { false }

        val navigateOnce = {
            if (!navigated) {
                navigated = true
                _state.update {
                    it.copy(
                        isInitiatingPayment = false,
                        navigationEvent = NavigationEvent.ToProgress(transactionRequestId)
                    )
                }
            }
        }
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                navigateOnce()
                inner.onSuccess(result)
            }
            override fun onFailure(code: String, message: String) {
                navigateOnce()
                inner.onFailure(code, message)
            }
            override fun onProgress(status: String, message: String) {
                if (isAppToApp) {
                    // App-to-App: skip progress updates entirely — do not write
                    // intermediate messages (e.g. "waiting for card") to the repository,
                    // so the progress screen won't flash stale progress on navigation.
                    return
                }
                navigateOnce()
                inner.onProgress(status, message)
            }
        }
    }

    /**
     * Create payment callback
     * 
     * Creates a PaymentCallback for handling payment results.
     * Updates transaction repository based on success/failure.
     * 
     * @param transactionRequestId Transaction request ID
     * @return PaymentCallback instance
     */
    private fun createPaymentCallback(transactionRequestId: String): PaymentCallback {
        return object : PaymentCallback {
            override fun onSuccess(result: PaymentResult) {
                android.util.Log.d(TAG, "Payment callback result: $transactionRequestId, status=${result.transactionStatus}, code=${result.code}")
                
                // Cloud mode: transaction creation success != transaction complete.
                // Only types pushed to terminal need polling. Types processed directly
                // in cloud (VOID, TIP_ADJUST, QUERY, BATCH_CLOSE) are final on API success.
                val isCloudMode = try {
                    val context = getApplication<android.app.Application>()
                    com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                        .getConnectionMode(context) == com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CLOUD
                } catch (e: Exception) { false }
                
                if (isCloudMode) {
                    val txn = transactionRepository.getTransactionByRequestId(transactionRequestId)
                    val needsPolling = txn?.type != com.sunmi.tapro.taplink.demo.model.TransactionType.VOID &&
                                      txn?.type != com.sunmi.tapro.taplink.demo.model.TransactionType.TIP_ADJUST &&
                                      txn?.type != com.sunmi.tapro.taplink.demo.model.TransactionType.QUERY &&
                                      txn?.type != com.sunmi.tapro.taplink.demo.model.TransactionType.BATCH_CLOSE
                    
                    if (result.isFailed()) {
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.FAILED,
                            transactionId = result.transactionId ?: result.originalTransactionId,
                            errorCode = result.transactionResultCode ?: result.code,
                            errorMessage = result.message
                        )
                        return
                    }

                    if (needsPolling && !result.isTerminal()) {
                        android.util.Log.d(TAG, "Cloud mode: marking transaction as PROCESSING for polling")
                        transactionRepository.updateTransactionStatus(
                            transactionRequestId = transactionRequestId,
                            status = TransactionStatus.PROCESSING,
                            transactionId = result.transactionId ?: result.originalTransactionId
                        )
                        return
                    }
                }

                if (result.isFailed()) {
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.FAILED,
                        transactionId = result.transactionId ?: result.originalTransactionId,
                        errorCode = result.transactionResultCode ?: result.code,
                        errorMessage = result.message
                    )
                    return
                }

                if (result.isProcessing()) {
                    transactionRepository.updateTransactionStatus(
                        transactionRequestId = transactionRequestId,
                        status = TransactionStatus.PROCESSING,
                        transactionId = result.transactionId ?: result.originalTransactionId
                    )
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
                
                // Non-cloud mode: update transaction with success result directly
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
                android.util.Log.e(TAG, "Communication error: $transactionRequestId - $code: $message")
                
                // onFailure = communication/technical error (connection lost, timeout, etc.)
                // NOT a transaction decline — declines arrive via onSuccess with isFailed().
                transactionRepository.updateTransactionStatus(
                    transactionRequestId = transactionRequestId,
                    status = TransactionStatus.FAILED,
                    errorCode = code,
                    errorMessage = message
                )
            }
            
            override fun onProgress(status: String, message: String) {
                android.util.Log.d(TAG, "Payment progress: $transactionRequestId - $status: $message")
                
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
     * Clear current order
     * 
     * Clears order items and additional amounts after payment is initiated.
     * Payment buttons remain enabled to allow new transactions.
     */
    private fun clearOrder() {
        _state.update {
            it.copy(
                orderItems = emptyList(),
                additionalAmounts = emptyMap(),
                totalAmount = BigDecimal.ZERO,
                canProcessPayment = true
            )
        }
    }
}
