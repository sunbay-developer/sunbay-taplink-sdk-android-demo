package com.sunmi.tapro.taplink.demo.ui.screens.detail.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.ui.components.AmountText
import com.sunmi.tapro.taplink.demo.ui.components.AmountStyle
import com.sunmi.tapro.taplink.demo.ui.components.BadgeSize
import com.sunmi.tapro.taplink.demo.ui.components.StatusBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Transaction Header Component - Toast POS style
 *
 * Displays order header in receipt-like format.
 * [compact] = true for landscape: single horizontal strip to save space.
 */
@Composable
fun TransactionHeader(
    transaction: Transaction,
    compact: Boolean = false,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    val backgroundColor = when (transaction.status) {
        TransactionStatus.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        TransactionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
        TransactionStatus.PENDING, TransactionStatus.PROCESSING ->
            MaterialTheme.colorScheme.primaryContainer
    }

    if (compact) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${getOrderNumber(transaction)}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 100.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${transaction.getDisplayName()} • ${formatDateTime(transaction.timestamp)}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            AmountText(
                amount = transaction.getDisplayAmount(),
                style = AmountStyle.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            StatusBadge(
                status = transaction.status,
                size = BadgeSize.Small
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "#${getOrderNumber(transaction)}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = transaction.getDisplayName(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "•",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = formatDateTime(transaction.timestamp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        AmountText(
            amount = transaction.getDisplayAmount(),
            style = AmountStyle.ExtraLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        StatusBadge(
            status = transaction.status,
            size = BadgeSize.Large
        )
    }
}

/**
 * Get order number from transaction
 * Uses hash of orderId to ensure uniqueness while keeping it readable
 * Same logic as OrderGroup.getOrderNumber()
 */
private fun getOrderNumber(transaction: Transaction): String {
    val orderId = transaction.referenceOrderId ?: transaction.transactionRequestId
    // Use absolute value of hashCode to ensure positive number
    // Modulo by 1000000 to get 6-digit number (000000-999999)
    val hashNumber = orderId.hashCode().let { if (it < 0) -it else it } % 1000000
    return hashNumber.toString().padStart(6, '0')
}

/**
 * Format timestamp to date and time
 */
private fun formatDateTime(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}
