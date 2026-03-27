package com.sunmi.tapro.taplink.demo.util

import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Utility object for formatting and parsing monetary amounts
 * 
 * Provides consistent formatting with $ prefix for all amounts displayed in the UI.
 * Supports bidirectional conversion between BigDecimal and formatted string.
 */
object AmountFormatter {
    
    private val formatter = DecimalFormat("$#,##0.00")
    
    /**
     * Format a BigDecimal amount as a currency string with $ prefix
     * 
     * @param amount The amount to format, or null
     * @return Formatted string like "$10.00" or "$1,234.56", or "$0.00" if amount is null
     */
    fun format(amount: BigDecimal?): String {
        return if (amount != null) {
            formatter.format(amount)
        } else {
            "$0.00"
        }
    }
    
    /**
     * Parse a formatted currency string back to BigDecimal
     * 
     * Removes $ prefix and commas before parsing.
     * 
     * @param amountString The formatted string to parse (e.g., "$10.00" or "$1,234.56")
     * @return BigDecimal representation of the amount, or null if parsing fails
     */
    fun parse(amountString: String): BigDecimal? {
        return try {
            val cleaned = amountString.replace("$", "").replace(",", "")
            BigDecimal(cleaned)
        } catch (e: Exception) {
            null
        }
    }
}
