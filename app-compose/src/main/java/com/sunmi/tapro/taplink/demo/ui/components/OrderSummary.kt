package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.util.AmountFormatter
import java.math.BigDecimal

/**
 * OrderSummary component displays the current order with items, additional amounts, and total
 * Designed for portrait orientation POS interface with compact layout
 * Enhanced with modern styling and improved readability
 *
 * @param orderItems List of items in the current order
 * @param totalAmount Total amount including all items and additional amounts
 * @param additionalAmounts Map of additional amount types to their values (tip, tax, etc.)
 * @param onRemoveItem Callback when an item should be removed from the order
 * @param onAdditionalAmounts Callback when user wants to add/edit additional amounts
 * @param isEditingSubtotal Whether subtotal is being edited
 * @param onStartEditingSubtotal Callback when user starts editing subtotal
 * @param onStopEditingSubtotal Callback when user stops editing subtotal
 * @param onAddCustomAmount Callback when custom amount is added
 * @param modifier Optional modifier for the component
 */
@Composable
fun OrderSummary(
    orderItems: List<OrderItem>,
    subtotalAmount: BigDecimal,
    totalAmount: BigDecimal,
    additionalAmounts: Map<String, BigDecimal>,
    onRemoveItem: (OrderItem) -> Unit,
    onAdditionalAmounts: () -> Unit,
    isEditingSubtotal: Boolean = false,
    onStartEditingSubtotal: () -> Unit = {},
    onStopEditingSubtotal: () -> Unit = {},
    onAddCustomAmount: (BigDecimal) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(horizontal = 0.dp, vertical = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with item count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Order",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (orderItems.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${orderItems.size} items",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Order items list with scrolling (max 3 items visible)
            if (orderItems.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No items in order",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Tap products above to add",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                // Scrollable order items (compact, max 3 items visible = ~120dp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 120.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    orderItems.forEach { orderItem ->
                        OrderItemRow(
                            orderItem = orderItem,
                            onRemove = { onRemoveItem(orderItem) }
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Subtotal (items only or editable)
                val itemsSubtotal = subtotalAmount
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                
                if (isEditingSubtotal) {
                    EditableAmountRow(
                        label = "Subtotal",
                        currentAmount = itemsSubtotal,
                        onConfirm = { amount ->
                            onAddCustomAmount(amount)
                            onStopEditingSubtotal()
                        },
                        onCancel = onStopEditingSubtotal
                    )
                } else {
                    AmountRow(
                        label = "Subtotal",
                        amount = itemsSubtotal,
                        isSubtotal = true,
                        isClickable = true,
                        onClick = onStartEditingSubtotal
                    )
                }
                
                // Additional amounts section (compact)
                if (additionalAmounts.isNotEmpty()) {
                    additionalAmounts.forEach { (type, amount) ->
                        if (amount > BigDecimal.ZERO) {
                            AmountRow(
                                label = formatAdditionalAmountLabel(type),
                                amount = amount,
                                isSubtotal = false
                            )
                        }
                    }
                }
                
                // Additional amounts button (compact)
                OutlinedButton(
                    onClick = onAdditionalAmounts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add amounts",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Tip / Tax / Fees",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Total
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    thickness = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                
                AmountRow(
                    label = "Total",
                    amount = totalAmount,
                    isTotal = true
                )
            }
        }
    }
}

/**
 * OrderItemRow displays a single order item with quantity, name, price, and remove button
 * Compact version for portrait layout
 */
@Composable
private fun OrderItemRow(
    orderItem: OrderItem,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Quantity
        Text(
            text = "${orderItem.quantity}x",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(32.dp)
        )
        
        // Product name - takes available space
        Text(
            text = orderItem.product.name,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Subtotal
        Text(
            text = AmountFormatter.format(orderItem.calculateSubtotal()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 2.dp)
        )
        
        // Remove button
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Remove item",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * AmountRow displays a label and amount in a consistent format
 * Compact version for portrait layout
 */
@Composable
private fun AmountRow(
    label: String,
    amount: BigDecimal,
    isSubtotal: Boolean = false,
    isTotal: Boolean = false,
    isClickable: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        onClick = if (isClickable) onClick else { {} },
        enabled = isClickable,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        color = if (isClickable) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) 
                else MaterialTheme.colorScheme.surface,
        shape = if (isClickable) RoundedCornerShape(8.dp) else RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = if (isClickable) 12.dp else 0.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = when {
                        isTotal -> MaterialTheme.typography.titleMedium
                        isSubtotal -> MaterialTheme.typography.bodyLarge
                        else -> MaterialTheme.typography.bodyMedium
                    },
                    fontWeight = if (isTotal || isSubtotal) FontWeight.Bold else FontWeight.Normal
                )
                
                if (isClickable) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Edit amount",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Text(
                text = AmountFormatter.format(amount),
                style = when {
                    isTotal -> MaterialTheme.typography.titleMedium
                    isSubtotal -> MaterialTheme.typography.bodyLarge
                    else -> MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (isTotal || isSubtotal) FontWeight.Bold else FontWeight.Normal,
                color = if (isTotal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Format additional amount type labels for display
 */
private fun formatAdditionalAmountLabel(type: String): String {
    return when (type.lowercase()) {
        "tip" -> "Tip"
        "tax" -> "Tax"
        "servicefee" -> "Service Fee"
        else -> type.replaceFirstChar { it.uppercase() }
    }
}


/**
 * EditableAmountRow displays an editable amount input field
 * Used for inline editing of subtotal
 */
@Composable
private fun EditableAmountRow(
    label: String,
    currentAmount: BigDecimal,
    onConfirm: (BigDecimal) -> Unit,
    onCancel: () -> Unit
) {
    var amountText by remember(currentAmount) {
        val initialText = currentAmount.stripTrailingZeros().toPlainString()
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(initialText.length)
            )
        )
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        if (newValue.text.isEmpty() || newValue.text.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                            amountText = newValue
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    placeholder = { Text("0.00") },
                    leadingIcon = {
                        Text(
                            text = "$",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val amount = amountText.text.toBigDecimalOrNull()
                            if (amount != null && amount > BigDecimal.ZERO) {
                                onConfirm(amount)
                                keyboardController?.hide()
                            }
                        }
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                
                IconButton(
                    onClick = {
                        val amount = amountText.text.toBigDecimalOrNull()
                        if (amount != null && amount > BigDecimal.ZERO) {
                            onConfirm(amount)
                            keyboardController?.hide()
                        }
                    },
                    enabled = amountText.text.isNotEmpty() &&
                             amountText.text.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Confirm",
                        tint = if (amountText.text.isNotEmpty() &&
                                  amountText.text.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                IconButton(
                    onClick = {
                        onCancel()
                        keyboardController?.hide()
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
