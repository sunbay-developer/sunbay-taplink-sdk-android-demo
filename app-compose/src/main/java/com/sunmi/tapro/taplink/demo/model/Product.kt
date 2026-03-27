package com.sunmi.tapro.taplink.demo.model

import java.math.BigDecimal

/**
 * Product data model representing an item available for sale
 *
 * @property id Unique identifier for the product
 * @property name Display name of the product
 * @property price Price of the product in the base currency
 * @property imageUrl Optional URL to product image
 */
data class Product(
    val id: String,
    val name: String,
    val price: BigDecimal,
    val imageUrl: String? = null
)
