package com.sunmi.tapro.taplink.demo.ui.screens.main

import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Product
import java.math.BigDecimal

/**
 * Sealed class representing all possible user intents/actions on the Main Screen
 * Following MVI pattern for unidirectional data flow
 */
sealed class MainIntent {
    // Product and Order Management
    data class AddProduct(val product: Product) : MainIntent()
    data class RemoveOrderItem(val item: OrderItem) : MainIntent()
    data class AddCustomAmount(val amount: BigDecimal) : MainIntent()
    
    // Product Catalog Management
    object ShowAddProductDialog : MainIntent()
    object HideAddProductDialog : MainIntent()
    data class SaveNewProduct(val name: String, val price: BigDecimal) : MainIntent()
    
    // Additional Amounts Dialog
    object ShowAdditionalAmountsDialog : MainIntent()
    object HideAdditionalAmountsDialog : MainIntent()
    data class SetAdditionalAmounts(val amounts: Map<String, BigDecimal>) : MainIntent()
    
    // Subtotal Editing
    object StartEditingSubtotal : MainIntent()
    object StopEditingSubtotal : MainIntent()
    
    // Payment Operations
    object ProcessPayment : MainIntent()
    data class SelectPaymentOption(val option: PaymentOption) : MainIntent()
    
    // Navigation
    object NavigateToTransactionList : MainIntent()
    object NavigateToSettings : MainIntent()
    
    // Message Handling
    object DismissMessage : MainIntent()
    object RetryConnection : MainIntent()
    
    // Navigation Event Handling
    object ClearNavigationEvent : MainIntent()
}
