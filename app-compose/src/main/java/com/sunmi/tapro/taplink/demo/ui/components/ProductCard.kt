package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.model.Product
import com.sunmi.tapro.taplink.demo.util.AmountFormatter

/**
 * ProductCard component displays a single product in a clean card format
 * Designed for cafe/retail POS with simple, elegant styling
 * Features:
 * - Compact size optimized for 3-column grid (115dp height)
 * - Clean white background with subtle shadow
 * - Clear product name and price display
 * - Responsive press animation
 *
 * @param product The product to display
 * @param onClick Callback when the card is clicked
 * @param modifier Optional modifier for the card
 */
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(115.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp,
            hoveredElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Product name
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Product price
            Text(
                text = AmountFormatter.format(product.price),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp
                ),
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
