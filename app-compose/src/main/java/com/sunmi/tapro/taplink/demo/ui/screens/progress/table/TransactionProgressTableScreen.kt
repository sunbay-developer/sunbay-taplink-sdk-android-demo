package com.sunmi.tapro.taplink.demo.ui.screens.progress.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.ui.screens.main.PaymentOption
import com.sunmi.tapro.taplink.demo.ui.screens.progress.SettleLeftPanel
import com.sunmi.tapro.taplink.demo.ui.screens.progress.SettleRightPanel
import com.sunmi.tapro.taplink.demo.ui.screens.progress.TransactionProgressState

@Composable
fun TransactionProgressTableScreen(
    state: TransactionProgressState,
    canSwitchToManualEntry: Boolean,
    showInlineManualEntry: Boolean,
    onAbort: () -> Unit,
    onSwitchToManualEntry: () -> Unit,
    onRetry: () -> Unit,
    onQuery: () -> Unit,
    onSelectPaymentOption: (PaymentOption) -> Unit,
    onStartPayment: () -> Unit,
    onToggleSplit: () -> Unit,
    onSplitAmountChange: (String) -> Unit,
    onSplitCountChange: (Int) -> Unit,
    onSplitLinePaymentOptionChange: (Int, PaymentOption) -> Unit,
    onSplitLineAmountChange: (Int, String) -> Unit,
    onStartSplitLinePayment: (Int) -> Unit,
    onCancelOrder: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.38f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            SettleLeftPanel(state = state)
        }

        Card(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.62f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            SettleRightPanel(
                state = state,
                canSwitchToManualEntry = canSwitchToManualEntry,
                showInlineManualEntry = showInlineManualEntry,
                onAbort = onAbort,
                onSwitchToManualEntry = onSwitchToManualEntry,
                onRetry = onRetry,
                onQuery = onQuery,
                onSelectPaymentOption = onSelectPaymentOption,
                onStartPayment = onStartPayment,
                onToggleSplit = onToggleSplit,
                onSplitAmountChange = onSplitAmountChange,
                onSplitCountChange = onSplitCountChange,
                onSplitLinePaymentOptionChange = onSplitLinePaymentOptionChange,
                onSplitLineAmountChange = onSplitLineAmountChange,
                onStartSplitLinePayment = onStartSplitLinePayment,
                onCancelOrder = onCancelOrder
            )
        }
    }
}
