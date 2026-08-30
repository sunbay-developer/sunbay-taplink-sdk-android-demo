package com.sunmi.tapro.taplink.demo.ui.screens.main.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Product
import com.sunmi.tapro.taplink.demo.ui.screens.main.EnhancedOrderSummary
import com.sunmi.tapro.taplink.demo.ui.components.ProductGrid
import com.sunmi.tapro.taplink.demo.util.AmountFormatter
import com.sunmi.tapro.taplink.demo.ui.utils.rememberDebouncedClick
import java.math.BigDecimal

@Composable
fun MainTableScreen(
    products: List<Product>,
    orderItems: List<OrderItem>,
    subtotalAmount: BigDecimal,
    totalAmount: BigDecimal,
    additionalAmounts: Map<String, BigDecimal>,
    isEditingSubtotal: Boolean,
    canProcessPayment: Boolean,
    onProductClick: (Product) -> Unit,
    onAddProductClick: () -> Unit,
    onRemoveItem: (OrderItem) -> Unit,
    onAdditionalAmounts: () -> Unit,
    onStartEditingSubtotal: () -> Unit,
    onStopEditingSubtotal: () -> Unit,
    onAddCustomAmount: (BigDecimal) -> Unit,
    onPay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.62f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ProductGrid(
                        products = products,
                        onProductClick = onProductClick,
                        onAddProductClick = onAddProductClick,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.38f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    EnhancedOrderSummary(
                        orderItems = orderItems,
                        subtotalAmount = subtotalAmount,
                        totalAmount = totalAmount,
                        additionalAmounts = additionalAmounts,
                        isEditingSubtotal = isEditingSubtotal,
                        onRemoveItem = onRemoveItem,
                        onAdditionalAmounts = onAdditionalAmounts,
                        onStartEditingSubtotal = onStartEditingSubtotal,
                        onStopEditingSubtotal = onStopEditingSubtotal,
                        onAddCustomAmount = onAddCustomAmount,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Button(
                    onClick = rememberDebouncedClick(onClick = onPay),
                    enabled = canProcessPayment && totalAmount > BigDecimal.ZERO,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Pay ${AmountFormatter.format(totalAmount)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
