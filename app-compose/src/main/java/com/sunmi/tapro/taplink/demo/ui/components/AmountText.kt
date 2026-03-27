package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.DecimalFormat

/**
 * Amount style enum for AmountText component
 * 
 * Defines font sizes and weights for different amount display contexts:
 * - Small: 16sp, Normal weight (for additional amounts like tip, tax)
 * - Medium: 20sp, Medium weight (for subtotals)
 * - Large: 24sp, Bold weight (for totals in cards)
 * - ExtraLarge: 40sp, Bold weight (for prominent header amounts)
 */
enum class AmountStyle(
    val fontSize: Int,
    val fontWeight: FontWeight
) {
    Small(16, FontWeight.Normal),
    Medium(20, FontWeight.Medium),
    Large(24, FontWeight.Bold),
    ExtraLarge(40, FontWeight.Bold)
}

/**
 * Amount text component for displaying formatted currency amounts
 * 
 * @param amount Amount to display
 * @param modifier Modifier for customization
 * @param style Amount style (Small, Medium, Large, or ExtraLarge)
 * @param color Text color
 */
@Composable
fun AmountText(
    amount: BigDecimal,
    modifier: Modifier = Modifier,
    style: AmountStyle = AmountStyle.Medium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val formattedAmount = formatAmount(amount)
    
    Text(
        text = formattedAmount,
        modifier = modifier,
        color = color,
        fontSize = style.fontSize.sp,
        fontWeight = style.fontWeight
    )
}

/**
 * Format amount with currency prefix, decimal places, and thousand separators
 * 
 * Rules:
 * - Prefix: "$"
 * - Decimal places: 2
 * - Thousand separator: ","
 * 
 * Examples:
 * - 0.01 -> "$0.01"
 * - 999.99 -> "$999.99"
 * - 1000.00 -> "$1,000.00"
 * - 999999.99 -> "$999,999.99"
 */
private fun formatAmount(amount: BigDecimal): String {
    val formatter = DecimalFormat("#,##0.00")
    return "$" + formatter.format(amount)
}
