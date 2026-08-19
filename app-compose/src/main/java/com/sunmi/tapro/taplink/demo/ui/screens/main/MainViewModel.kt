package com.sunmi.tapro.taplink.demo.ui.screens.main

import android.app.Application
import android.util.Log
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
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.service.HeadlessProgressEvent
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
import java.math.RoundingMode

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
        reloadSettings()
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
            is MainIntent.BeginCheckoutFlow -> beginCheckoutFlow()
            is MainIntent.SelectPaymentOption -> selectPaymentOption(intent.option)
            is MainIntent.StartSecondaryTipSelection -> startSecondaryTipSelection()
            is MainIntent.CancelSecondaryTipSelection -> cancelSecondaryTipSelection()
            is MainIntent.ApplyTipFromSecondary -> applyTipFromSecondary(intent.tipAmount)
            is MainIntent.NavigateToTransactionList -> navigateToTransactionList()
            is MainIntent.NavigateToSettings -> navigateToSettings()
            is MainIntent.DismissMessage -> dismissMessage()
            is MainIntent.RetryConnection -> retryConnection()
            is MainIntent.ClearNavigationEvent -> clearNavigationEvent()
            is MainIntent.ReloadSettings -> reloadSettings()
        }
    }
    
    private fun reloadSettings() {
        val context = getApplication<Application>()
        _state.update {
            it.copy(
                secondaryDisplayAdEnabled = ConnectionPreferences.isSecondaryDisplayAdEnabled(context),
                secondaryDisplayRefreshTick = it.secondaryDisplayRefreshTick + 1
            )
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
        _state.update { it.copy(manualAdditionalAmounts = amounts, showAdditionalAmountsDialog = false) }
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
     * Mark the UI as waiting for tip selection from the secondary display.
     */
    private fun startSecondaryTipSelection() {
        android.util.Log.d(TAG, "Secondary tip selection started")
        _state.update { it.copy(isAwaitingSecondaryTipSelection = true) }
    }

    /**
     * Cancel waiting state when secondary tip selection is dismissed.
     */
    private fun cancelSecondaryTipSelection() {
        android.util.Log.d(TAG, "Secondary tip selection canceled")
        _state.update { it.copy(isAwaitingSecondaryTipSelection = false) }
    }

    /**
     * Apply selected tip from the secondary display and recalculate total.
     *
     * Business rule:
     * - Tip = 0 means "No Tip", so remove tip from additional amounts.
     * - Positive tip value overwrites existing "Tip" entry.
     */
    private fun applyTipFromSecondary(tipAmount: BigDecimal) {
        val normalizedTip = tipAmount.setScale(2, RoundingMode.HALF_UP)
        val updatedAmounts = _state.value.additionalAmounts.toMutableMap()
        if (normalizedTip > BigDecimal.ZERO) {
            updatedAmounts["Tip"] = normalizedTip
        } else {
            updatedAmounts.remove("Tip")
        }

        android.util.Log.d(TAG, "Secondary tip selected: $normalizedTip")
        _state.update {
            it.copy(
                additionalAmounts = updatedAmounts,
                isAwaitingSecondaryTipSelection = false
            )
        }
        calculateTotal()
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
     * Landscape checkout flow: create a draft transaction and navigate to progress
     * screen for payment-method selection before starting SDK request.
     */
    private fun beginCheckoutFlow() {
        viewModelScope.launch {
            try {
                if (_state.value.orderItems.isEmpty()) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Empty Order",
                                content = "Please add items to the order before checkout",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }

                val timestamp = System.currentTimeMillis()
                val referenceOrderId = "${com.sunmi.tapro.taplink.demo.util.Constants.ORDER_ID_PREFIX}$timestamp"
                val transactionRequestId = "${com.sunmi.tapro.taplink.demo.util.Constants.TRANSACTION_REQUEST_ID_PREFIX}${timestamp}"

                val draftTransaction = Transaction(
                    transactionRequestId = transactionRequestId,
                    referenceOrderId = referenceOrderId,
                    type = TransactionType.SALE,
                    status = TransactionStatus.PENDING,
                    amount = _state.value.getItemsSubtotal(),
                    totalAmount = _state.value.totalAmount,
                    currency = CURRENCY,
                    timestamp = timestamp,
                    tipAmount = _state.value.additionalAmounts["Tip"],
                    taxAmount = _state.value.additionalAmounts["Tax"],
                    serviceFee = _state.value.additionalAmounts["Service Fee"],
                    surchargeAmount = _state.value.additionalAmounts["Surcharge"],
                    paymentCategory = PaymentCategory.CARD,
                    progressStatus = "READY_TO_PAY",
                    progressMessage = "Select payment method to continue"
                )

                if (!transactionRepository.addTransaction(draftTransaction)) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.ERROR,
                                title = "Checkout Error",
                                content = "Failed to create checkout session. Please try again.",
                                actions = listOf(MessageAction.DISMISS)
                            )
                        )
                    }
                    return@launch
                }

                transactionRepository.saveOrderItems(referenceOrderId, _state.value.orderItems)

                _state.update {
                    it.copy(
                        navigationEvent = NavigationEvent.ToProgress(transactionRequestId)
                    )
                }

                clearOrder()
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to begin checkout flow", e)
                _state.update {
                    it.copy(
                        message = Message(
                            type = MessageType.ERROR,
                            title = "Checkout Error",
                            content = e.message ?: "Failed to open checkout screen",
                            actions = listOf(MessageAction.DISMISS)
                        )
                    )
                }
            }
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
                val payableAmount = resolvePayableAmount(preferSubtotal = true)

                // In portrait/direct-pay mode, there may be no product rows.
                // As long as we have a positive payable amount, allow transaction start.
                if (payableAmount <= BigDecimal.ZERO) {
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
                    amount = payableAmount,
                    totalAmount = payableAmount,
                    currency = "USD",
                    timestamp = System.currentTimeMillis(),
                    tipAmount = _state.value.additionalAmounts["Tip"],
                    taxAmount = _state.value.additionalAmounts["Tax"],
                    serviceFee = _state.value.additionalAmounts["Service Fee"],
                    surchargeAmount = _state.value.additionalAmounts["Surcharge"],
                    // Persist the original payment method so a result-page retry keeps EBT/SNAP
                    // instead of falling back to the executeSale default CARD category.
                    paymentCategory = paymentCategory,
                    paymentMethodId = paymentMethodId,
                    subPaymentMethodId = subPaymentMethodId,
                    cardNetworkType = cardNetworkType
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
                    amount = payableAmount,
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
                    callback = createNavigatingPaymentCallback(transactionRequestId),
                    signatureConfig = resolveSignatureForSaleOrAuth()
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
                val payableAmount = resolvePayableAmount(preferSubtotal = false)

                if (payableAmount <= BigDecimal.ZERO) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Invalid Amount",
                                content = "Please enter a valid amount before processing payment",
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
                    amount = payableAmount,
                    totalAmount = payableAmount,
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
                    amount = payableAmount,
                    currency = CURRENCY,
                    description = "Auth - ${_state.value.orderItems.size} items",
                    callback = createNavigatingPaymentCallback(transactionRequestId),
                    signatureConfig = resolveSignatureForSaleOrAuth()
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
                val payableAmount = resolvePayableAmount(preferSubtotal = true)

                if (payableAmount <= BigDecimal.ZERO) {
                    _state.update {
                        it.copy(
                            message = Message(
                                type = MessageType.WARNING,
                                title = "Invalid Amount",
                                content = "Please enter a valid amount before processing payment",
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
                    amount = payableAmount,
                    totalAmount = payableAmount,
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
                    amount = payableAmount,
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
     * Calculate total amount.
     *
     * Uses [manualAdditionalAmounts] as the source of truth for user-set amounts.
     * If tax config is enabled and the user has not manually set "Tax", automatically
     * computes tax from the items subtotal and injects it into the displayed
     * [additionalAmounts]. The [manualAdditionalAmounts] is never modified here.
     *
     * Payment buttons are always enabled regardless of amount or connection status.
     */
    private fun calculateTotal() {
        val context = getApplication<Application>()
        val taxConfig = com.sunmi.tapro.taplink.demo.util.TaxConfigPreferences.getConfig(context)

        val itemsSubtotal = _state.value.getItemsSubtotal()
        val manual = _state.value.manualAdditionalAmounts

        // Auto-inject tax only when enabled, rate > 0, and user has not set Tax manually
        val effectiveAdditional = if (taxConfig.enabled && taxConfig.taxRate > 0 && !manual.containsKey("Tax")) {
            val autoTax = itemsSubtotal
                .multiply(java.math.BigDecimal(taxConfig.taxRate))
                .divide(java.math.BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
            manual + ("Tax" to autoTax)
        } else {
            manual
        }

        val additionalTotal = effectiveAdditional.values.fold(java.math.BigDecimal.ZERO) { acc, amount ->
            acc.add(amount)
        }

        _state.update {
            it.copy(
                totalAmount = itemsSubtotal.add(additionalTotal),
                additionalAmounts = effectiveAdditional,
                canProcessPayment = true
            )
        }
    }
    
    private fun resolveSignatureForSaleOrAuth() =
        com.sunmi.tapro.taplink.demo.util.SignatureConfigBuilder.buildFromPreferences(
            getApplication<Application>()
        )

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
                // Always navigate to progress screen and forward progress updates.
                // In Headless mode, POS needs to show real-time transaction progress.
                // In Custom mode, progress events are less frequent but navigation is still correct.
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
                    val context = getApplication<Application>()
                    com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
                        .getConnectionMode(context) == com.sunmi.tapro.taplink.demo.util.ConnectionPreferences.ConnectionMode.CLOUD
                } catch (e: Exception) { false }
                
                if (isCloudMode) {
                    val txn = transactionRepository.getTransactionByRequestId(transactionRequestId)
                    val needsPolling = txn?.type != TransactionType.VOID &&
                                      txn?.type != TransactionType.TIP_ADJUST &&
                                      txn?.type != TransactionType.QUERY &&
                                      txn?.type != TransactionType.BATCH_CLOSE
                    
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

            override fun onHeadlessProgress(event: HeadlessProgressEvent) {
                android.util.Log.d(
                    TAG,
                    "Headless progress: $transactionRequestId - ${event.eventCode}, " +
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
                manualAdditionalAmounts = emptyMap(),
                totalAmount = BigDecimal.ZERO,
                canProcessPayment = true,
                isAwaitingSecondaryTipSelection = false
            )
        }
    }

    /**
     * Resolve final payable amount for direct payment flows.
     * preferSubtotal=true keeps existing SALE/FORCED_AUTH semantics when items exist,
     * but still allows portrait direct-pay with totalAmount fallback.
     */
    private fun resolvePayableAmount(preferSubtotal: Boolean): BigDecimal {
        val subtotal = _state.value.getItemsSubtotal()
        val total = _state.value.totalAmount
        return if (preferSubtotal) {
            if (subtotal > BigDecimal.ZERO) subtotal else total
        } else {
            if (total > BigDecimal.ZERO) total else subtotal
        }
    }
}
