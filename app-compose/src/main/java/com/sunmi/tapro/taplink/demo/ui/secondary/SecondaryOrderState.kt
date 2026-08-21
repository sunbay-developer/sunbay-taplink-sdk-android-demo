package com.sunmi.tapro.taplink.demo.ui.secondary

import java.math.BigDecimal

/**
 * One line item rendered on the secondary-screen order list.
 *
 * @property name Product name
 * @property quantity Quantity
 * @property unitPrice Unit price
 * @property subtotal Subtotal for this line item (unit price × quantity)
 */
data class SecondaryOrderLine(
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

/**
 * Order summary snapshot pushed to the secondary display.
 *
 * Business rule:
 * - When lines is non-empty, the left 30% of the secondary screen shows the order list
 *   and the right 70% continues the ad carousel.
 * - When lines is empty (no items), the ad carousel fills the entire secondary screen.
 *
 * @property lines Order line items
 * @property totalAmount Order total amount
 * @property totalItemCount Total item count
 * @property currency Currency symbol/code
 */
data class SecondaryOrderState(
    val lines: List<SecondaryOrderLine>,
    val totalAmount: BigDecimal,
    val totalItemCount: Int,
    val currency: String
) {
    /** Whether it contains valid order content. */
    fun hasItems(): Boolean = lines.isNotEmpty()
}
