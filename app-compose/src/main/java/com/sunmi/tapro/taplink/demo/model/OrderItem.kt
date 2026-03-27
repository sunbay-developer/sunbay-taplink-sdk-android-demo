package com.sunmi.tapro.taplink.demo.model

import com.sunmi.tapro.taplink.demo.model.Product
import java.math.BigDecimal

/**
 * OrderItem data model representing a product in the current order
 *
 * @property product The product being ordered
 * @property quantity Number of units of this product
 */
data class OrderItem(
    val product: Product,
    val quantity: Int
) {
    /**
     * Calculate the subtotal for this order item
     * @return The subtotal (price * quantity)
     */
    fun calculateSubtotal(): BigDecimal {
        return product.price.multiply(BigDecimal(quantity))
    }
}
