package com.sunmi.tapro.taplink.demo.ui.screens.detail.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.model.Transaction

/**
 * Card Info Section Component - Toast POS style
 * 
 * Displays card information in receipt-like format with collapsible functionality
 * Compact layout with clear visual hierarchy
 * Does not render if cardInfo is null
 */
@Composable
fun CardInfoSection(
    transaction: Transaction,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Only render if card info is available
    val cardInfo = transaction.cardInfo ?: return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleExpanded() },
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
            // Header with expand/collapse icon - Toast POS style
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Card Details",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Always show: Masked PAN (Card Number)
            cardInfo.maskedPan?.let {
                CardDetailRow(
                    label = "Card Number",
                    value = it
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            
            // Always show: Payment Method ID (Card Brand)
            cardInfo.paymentMethodId?.let {
                CardDetailRow(
                    label = "Card Brand",
                    value = it
                )
            }
            
            // Expanded content with animation (200ms)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(200)),
                exit = shrinkVertically(animationSpec = tween(200))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    // Card Network Type
                    cardInfo.cardNetworkType?.let {
                        CardDetailRow(
                            label = "Card Type",
                            value = it
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    
                    // Cardholder Name
                    cardInfo.cardholderName?.let {
                        CardDetailRow(
                            label = "Cardholder",
                            value = it.trim()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    
                    // Expiry Date
                    cardInfo.expiryDate?.let {
                        CardDetailRow(
                            label = "Expiry",
                            value = it
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    
                    // Entry Mode
                    cardInfo.entryMode?.let {
                        CardDetailRow(
                            label = "Entry Mode",
                            value = it
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    
                    // Authentication Method
                    cardInfo.authenticationMethod?.let {
                        CardDetailRow(
                            label = "Auth Method",
                            value = it
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                    
                    // Issuer Bank
                    cardInfo.issuerBank?.let {
                        CardDetailRow(
                            label = "Issuer",
                            value = it
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card Detail Row Component - Toast POS style
 * 
 * Compact label-value pair with receipt-like styling
 */
@Composable
private fun CardDetailRow(
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
