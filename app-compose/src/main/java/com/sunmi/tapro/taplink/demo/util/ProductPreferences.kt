package com.sunmi.tapro.taplink.demo.util

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sunmi.tapro.taplink.demo.model.Product
import java.math.BigDecimal

/**
 * ProductPreferences - Manages product catalog persistence using SharedPreferences
 * 
 * Stores custom products added by users in JSON format.
 * Provides methods to add, remove, and retrieve products.
 */
class ProductPreferences(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "product_preferences"
        private const val KEY_CUSTOM_PRODUCTS = "custom_products"
        private const val KEY_NEXT_PRODUCT_ID = "next_product_id"
        private const val CUSTOM_PRODUCT_ID_PREFIX = "custom_"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Get all custom products
     * 
     * @return List of custom products
     */
    fun getCustomProducts(): List<Product> {
        val json = prefs.getString(KEY_CUSTOM_PRODUCTS, null) ?: return emptyList()
        
        return try {
            val type = object : TypeToken<List<ProductData>>() {}.type
            val productDataList: List<ProductData> = gson.fromJson(json, type)
            productDataList.map { it.toProduct() }
        } catch (e: Exception) {
            android.util.Log.e("ProductPreferences", "Failed to parse custom products", e)
            emptyList()
        }
    }
    
    /**
     * Add a new custom product
     * 
     * @param name Product name
     * @param price Product price
     * @return The newly created Product
     */
    fun addProduct(name: String, price: BigDecimal): Product {
        val products = getCustomProducts().toMutableList()
        
        // Generate unique ID
        val nextId = prefs.getInt(KEY_NEXT_PRODUCT_ID, 1)
        val productId = "$CUSTOM_PRODUCT_ID_PREFIX$nextId"
        
        // Create new product
        val newProduct = Product(
            id = productId,
            name = name,
            price = price
        )
        
        products.add(newProduct)
        
        // Save to preferences
        saveProducts(products)
        
        // Increment next ID
        prefs.edit().putInt(KEY_NEXT_PRODUCT_ID, nextId + 1).apply()
        
        return newProduct
    }
    
    /**
     * Remove a custom product
     * 
     * @param productId Product ID to remove
     */
    fun removeProduct(productId: String) {
        val products = getCustomProducts().toMutableList()
        products.removeAll { it.id == productId }
        saveProducts(products)
    }
    
    /**
     * Clear all custom products
     */
    fun clearAllProducts() {
        prefs.edit()
            .remove(KEY_CUSTOM_PRODUCTS)
            .remove(KEY_NEXT_PRODUCT_ID)
            .apply()
    }
    
    /**
     * Save products to preferences
     */
    private fun saveProducts(products: List<Product>) {
        val productDataList = products.map { ProductData.fromProduct(it) }
        val json = gson.toJson(productDataList)
        prefs.edit().putString(KEY_CUSTOM_PRODUCTS, json).apply()
    }
    
    /**
     * ProductData - Serializable product data for JSON storage
     * 
     * BigDecimal is not directly serializable, so we store price as String
     */
    private data class ProductData(
        val id: String,
        val name: String,
        val price: String
    ) {
        fun toProduct(): Product {
            return Product(
                id = id,
                name = name,
                price = BigDecimal(price)
            )
        }
        
        companion object {
            fun fromProduct(product: Product): ProductData {
                return ProductData(
                    id = product.id,
                    name = product.name,
                    price = product.price.toPlainString()
                )
            }
        }
    }
}
