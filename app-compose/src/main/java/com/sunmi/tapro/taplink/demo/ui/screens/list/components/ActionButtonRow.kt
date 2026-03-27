package com.sunmi.tapro.taplink.demo.ui.screens.list.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.ui.components.PosButton
import com.sunmi.tapro.taplink.demo.ui.components.ButtonSize

/**
 * ActionButtonRow component - Toast POS style
 *
 * Displays the Standalone Refund button. Query and Batch are in the list screen top bar.
 * Button height: 56dp.
 */
@Composable
fun ActionButtonRow(
    onRefundClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    RefundButton(
        onClick = onRefundClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(56.dp)
    )
}

/**
 * Refund button - Amber color (Standalone Refund)
 */
@Composable
private fun RefundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PosButton(
        text = "Refund",
        onClick = onClick,
        icon = Icons.Default.Refresh, // Using Refresh icon as placeholder
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFFFA726) // Amber
        ),
        size = ButtonSize.Large
    )
}
