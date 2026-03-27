package com.sunmi.tapro.taplink.demo.ui.screens.detail.components

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.ui.components.AmountStyle
import com.sunmi.tapro.taplink.demo.ui.components.AmountText
import java.math.BigDecimal

/**
 * Payment Info Section Component - Toast POS style
 * 
 * Displays payment breakdown in receipt-like format
 * Compact layout with clear amount hierarchy
 */
@Composable
fun PaymentInfoSection(
    transaction: Transaction,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        // Special handling for Batch Close transactions
        if (transaction.type == TransactionType.BATCH_CLOSE && transaction.batchCloseInfo != null) {
            val info = transaction.batchCloseInfo

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Batch Close Summary",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                InfoRow(label = "Total Count", value = info.totalCount.toString())

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                AmountRow(
                    label = "Total Amount",
                    amount = info.totalAmount,
                    style = AmountStyle.Medium,
                    isSubtotal = false
                )

                if (info.totalTip > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AmountRow(
                        label = "Total Tip",
                        amount = info.totalTip,
                        style = AmountStyle.Small,
                        isSubtotal = true
                    )
                }

                if (info.totalTax > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AmountRow(
                        label = "Total Tax",
                        amount = info.totalTax,
                        style = AmountStyle.Small,
                        isSubtotal = true
                    )
                }

                if (info.cashDiscount > BigDecimal.ZERO) {
                    Spacer(modifier = Modifier.height(6.dp))
                    AmountRow(
                        label = "Cash Discount",
                        amount = info.cashDiscount,
                        style = AmountStyle.Small,
                        isSubtotal = true
                    )
                }

                if (info.closeTime.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    InfoRow(label = "Close Time", value = info.closeTime)
                }
            }

            return@Card
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Section title - smaller, less prominent
            Text(
                text = "Payment Breakdown",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Base Amount - compact
            AmountRow(
                label = "Subtotal",
                amount = transaction.amount,
                style = AmountStyle.Small,
                isSubtotal = true
            )
            
            // Additional amounts - compact spacing
            if (transaction.hasAdditionalAmounts()) {
                transaction.tipAmount?.let { tip ->
                    if (tip > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AmountRow(
                            label = "Tip",
                            amount = tip,
                            style = AmountStyle.Small,
                            isSubtotal = true
                        )
                    }
                }
                
                transaction.taxAmount?.let { tax ->
                    if (tax > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AmountRow(
                            label = "Tax",
                            amount = tax,
                            style = AmountStyle.Small,
                            isSubtotal = true
                        )
                    }
                }
                
                transaction.cashbackAmount?.let { cashback ->
                    if (cashback > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AmountRow(
                            label = "Cashback",
                            amount = cashback,
                            style = AmountStyle.Small,
                            isSubtotal = true
                        )
                    }
                }
                
                transaction.serviceFee?.let { serviceFee ->
                    if (serviceFee > BigDecimal.ZERO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        AmountRow(
                            label = "Service Fee",
                            amount = serviceFee,
                            style = AmountStyle.Small,
                            isSubtotal = true
                        )
                    }
                }
            }
            
            // Total Amount (if different from base amount)
            transaction.totalAmount?.let { total ->
                if (total != transaction.amount) {
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Divider before total - receipt style
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    // Total Amount - prominent
                    AmountRow(
                        label = "Total",
                        amount = total,
                        style = AmountStyle.Medium,
                        isSubtotal = false
                    )
                }
            }
        }
    }
}

/**
 * Amount Row Component - Toast POS style
 * 
 * Compact label-amount pair with receipt-like styling
 */
@Composable
private fun AmountRow(
    label: String,
    amount: BigDecimal,
    style: AmountStyle,
    isSubtotal: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label - left aligned
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = if (isSubtotal) 13.sp else 15.sp,
            fontWeight = if (isSubtotal) FontWeight.Normal else FontWeight.SemiBold,
            color = if (isSubtotal) 
                MaterialTheme.colorScheme.onSurfaceVariant 
            else 
                MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        
        // Amount - right aligned
        AmountText(
            amount = amount,
            style = style,
            color = if (isSubtotal) 
                MaterialTheme.colorScheme.onSurface 
            else 
                MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
