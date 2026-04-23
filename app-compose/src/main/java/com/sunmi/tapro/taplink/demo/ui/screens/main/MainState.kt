package com.sunmi.tapro.taplink.demo.ui.screens.main

import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Product
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import java.math.BigDecimal

/**
 * Main Screen State
 * 
 * Immutable data class representing the complete UI state of the Main Screen.
 * Following MVI pattern for predictable state management.
 * 
 * All state changes create a new instance (copy) to ensure immutability.
 */
data class MainState(
    // Product catalog
    val products: List<Product> = emptyList(),
    
    // Current order
    val orderItems: List<OrderItem> = emptyList(),
    
    // Amount calculations
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val additionalAmounts: Map<String, BigDecimal> = emptyMap(),
    
    // Connection status
    val connectionStatus: ConnectionState = ConnectionState.Disconnected,
    
    // Payment option selection (Card-Sale, Card-Auth, EBT-SNAP, etc.)
    val selectedPaymentOption: PaymentOption = PaymentOption.CARD_SALE,
    
    // UI control flags
    val canProcessPayment: Boolean = true,
    val showAdditionalAmountsDialog: Boolean = false,
    val showAddProductDialog: Boolean = false,
    val isEditingSubtotal: Boolean = false,
    val isLoading: Boolean = false,
    val isInitiatingPayment: Boolean = false,
    
    // Message display (errors, warnings, info, success)
    val message: Message? = null,
    
    // Navigation events (one-time events)
    val navigationEvent: NavigationEvent? = null
) {
    /**
     * Check if order has items
     */
    fun hasItems(): Boolean = orderItems.isNotEmpty()
    
    /**
     * Check if order has additional amounts
     */
    fun hasAdditionalAmounts(): Boolean = additionalAmounts.isNotEmpty()
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = connectionStatus is ConnectionState.Connected
    
    /**
     * Get total item count
     */
    fun getTotalItemCount(): Int = orderItems.sumOf { it.quantity }
    
    /**
     * Get items subtotal (without additional amounts)
     */
    fun getItemsSubtotal(): BigDecimal {
        return orderItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.calculateSubtotal()) }
    }
}
