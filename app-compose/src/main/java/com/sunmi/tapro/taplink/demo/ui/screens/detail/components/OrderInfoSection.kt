package com.sunmi.tapro.taplink.demo.ui.screens.detail.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.util.AmountFormatter

/**
 * Order Info Section Component - POS style
 *
 * Displays order content (what was ordered) and order/transaction metadata
 * in receipt-like format. When orderItems is non-empty, shows "Order content" first.
 */
@Composable
fun OrderInfoSection(
    transaction: Transaction,
    orderItems: List<OrderItem> = emptyList(),
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Order content (POS: what was ordered) - show first when available
            if (orderItems.isNotEmpty()) {
                Text(
                    text = "Order content",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                orderItems.forEach { item ->
                    OrderItemRow(item = item)
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Section title - transaction/order details
            Text(
                text = "Order Details",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Request ID - full
            DetailRow(
                label = "Request ID",
                value = transaction.transactionRequestId
            )
            
            // Transaction ID - full
            if (transaction.transactionId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                DetailRow(
                    label = "Transaction ID",
                    value = transaction.transactionId
                )
            }
            
            // Order ID
            if (transaction.referenceOrderId != null) {
                Spacer(modifier = Modifier.height(6.dp))
                DetailRow(
                    label = "Order ID",
                    value = transaction.referenceOrderId
                )
            }
            
            // Auth Code
            if (transaction.authCode != null) {
                Spacer(modifier = Modifier.height(6.dp))
                DetailRow(
                    label = "Auth Code",
                    value = transaction.authCode
                )
            }
            
            // Batch Number
            if (transaction.batchNo != null) {
                Spacer(modifier = Modifier.height(6.dp))
                DetailRow(
                    label = "Batch #",
                    value = transaction.batchNo.toString()
                )
            }
            
            // Original Transaction ID (for follow-up operations)
            if (transaction.originalTransactionId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow(
                    label = "Original Txn",
                    value = transaction.originalTransactionId
                )
            }
        }
    }
}

/**
 * Single order item row - name x qty, subtotal (POS receipt style)
 */
@Composable
private fun OrderItemRow(
    item: OrderItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${item.product.name} × ${item.quantity}",
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = AmountFormatter.format(item.calculateSubtotal()),
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Detail Row Component - Toast POS style
 *
 * Compact label-value pair with receipt-like styling
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth()
    ) {
        // Label - left aligned, lighter
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        
        // Value - right aligned, darker
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.6f)
        )
    }
}
