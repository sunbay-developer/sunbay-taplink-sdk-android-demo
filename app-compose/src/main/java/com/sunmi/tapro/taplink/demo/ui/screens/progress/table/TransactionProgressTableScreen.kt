package com.sunmi.tapro.taplink.demo.ui.screens.progress.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    onMemberInput: () -> Unit,
    showMemberInputButton: Boolean,
    onTipSelection: () -> Unit,
    showTipSelectionButton: Boolean,
    onDone: () -> Unit = {},
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
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SettleLeftPanel(
                    state = state,
                    modifier = Modifier.weight(1f)
                )
                if (showMemberInputButton && state.isReadyForPaymentSelection()) {
                    Button(
                        onClick = onMemberInput,
                        enabled = !state.isAwaitingSecondaryMemberInput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("Enter Member ID on Customer Display")
                    }
                }
                if (showTipSelectionButton &&
                    state.isReadyForPaymentSelection() &&
                    state.transaction?.tipAmount == null
                ) {
                    OutlinedButton(
                        onClick = onTipSelection,
                        enabled = !state.isAwaitingSecondaryTipSelection,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text("Select Tip on Customer Display")
                    }
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxHeight()
                .weight(0.62f),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                onDone = onDone,
                onCancelOrder = onCancelOrder,
                useTableSplitUi = true
            )
        }
    }
}
