package com.sunmi.tapro.taplink.demo.ui.secondary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

// ─── Tip Selection ────────────────────────────────────────────────────────────

/**
 * Customer-facing tip selection UI for the secondary display.
 *
 * Presents a list of common tip percentages (No Tip, 10%, 15%, 20%) and a Cancel
 * button. Intended for landscape or small external screens where simplicity matters.
 */
@Composable
internal fun SecondaryTipSelectionContent(
    subtotalAmount: BigDecimal,
    currency: String,
    onTipSelected: (BigDecimal) -> Unit,
    onCancelled: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Select Tip",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Subtotal: ${formatCurrency(subtotalAmount, currency)}",
            style = MaterialTheme.typography.titleMedium
        )

        TipActionButton(label = "No Tip") { onTipSelected(BigDecimal.ZERO) }
        TipActionButton(label = "10%") { onTipSelected(calculateTip(subtotalAmount, BigDecimal("0.10"))) }
        TipActionButton(label = "15%") { onTipSelected(calculateTip(subtotalAmount, BigDecimal("0.15"))) }
        TipActionButton(label = "20%") { onTipSelected(calculateTip(subtotalAmount, BigDecimal("0.20"))) }

        OutlinedButton(
            onClick = onCancelled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Cancel")
        }

    }
}

@Composable
private fun TipActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

@Composable
internal fun SecondaryMemberInputContent(
    onConfirmed: (String) -> Unit,
    onCancelled: () -> Unit
) {
    var memberInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Member Entry",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Please enter your member ID",
            style = MaterialTheme.typography.titleMedium
        )
        OutlinedTextField(
            value = memberInput,
            onValueChange = { memberInput = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Member ID") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancelled,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onConfirmed(memberInput.trim()) },
                enabled = memberInput.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Confirm")
            }
        }
    }
}

// ─── Ad Carousel ──────────────────────────────────────────────────────────────

/**
 * Idle advertisement carousel content for the secondary display.
 *
 * Layout rules:
 * - When [order] contains items: left 30% shows the order list, right 70% shows the ad carousel.
 * - Otherwise: the ad carousel fills the entire screen.
 */
@Composable
internal fun SecondaryAdCarouselContent(
    @DrawableRes images: List<Int>,
    order: SecondaryOrderState?
) {
    if (order != null && order.hasItems()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.3f)
            ) {
                SecondaryOrderListContent(order = order)
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.7f)
            ) {
                AdCarousel(images = images)
            }
        }
    } else {
        AdCarousel(images = images)
    }
}

/**
 * Reusable advertisement carousel that loops through [images] at 5-second intervals.
 */
@Composable
internal fun AdCarousel(@DrawableRes images: List<Int>) {
    if (images.isEmpty()) return

    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(images) {
        while (true) {
            delay(5_000L)
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    Card(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = images[currentIndex]),
            contentDescription = "Advertisement ${currentIndex + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

// ─── Order List ───────────────────────────────────────────────────────────────

/**
 * Customer-facing order list shown on the left portion of the secondary screen.
 *
 * Displays each product's name, quantity, unit price, and subtotal, with a
 * summary of item count and total amount at the bottom.
 */
@Composable
internal fun SecondaryOrderListContent(order: SecondaryOrderState) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Order",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(top = 8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(order.lines) { line ->
                    SecondaryOrderLineRow(line = line, currency = order.currency)
                }
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(top = 8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items: ${order.totalItemCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCurrency(order.totalAmount, order.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun SecondaryOrderLineRow(line: SecondaryOrderLine, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${line.quantity} × ${formatCurrency(line.unitPrice, currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatCurrency(line.subtotal, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

internal fun calculateTip(subtotalAmount: BigDecimal, rate: BigDecimal): BigDecimal {
    return subtotalAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
}

internal fun formatCurrency(amount: BigDecimal, currency: String): String {
    return "$currency ${amount.setScale(2, RoundingMode.HALF_UP)}"
}
