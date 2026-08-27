package com.sunmi.tapro.taplink.demo.ui.screens.detail.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.CardInfoSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.OperationPanel
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.OrderInfoSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.PaymentInfoSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.SignatureSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.TransactionHeader

@Composable
fun TransactionDetailTableScreen(
    transaction: Transaction,
    orderItems: List<com.sunmi.tapro.taplink.demo.model.OrderItem>,
    isCardInfoExpanded: Boolean,
    availableOperations: List<TransactionType>,
    isLoading: Boolean,
    isQuerying: Boolean,
    labelOverrides: Map<TransactionType, String>,
    onToggleCardInfo: () -> Unit,
    onPerformOperation: (TransactionType) -> Unit,
    onQueryTransaction: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TransactionHeader(transaction = transaction, compact = true)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier.weight(0.48f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OrderInfoSection(
                        transaction = transaction,
                        orderItems = orderItems,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )
                Column(
                    modifier = Modifier.weight(0.52f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaymentInfoSection(
                        transaction = transaction,
                        modifier = Modifier.fillMaxWidth()
                    )
                    transaction.cardInfo?.let {
                        CardInfoSection(
                            transaction = transaction,
                            isExpanded = isCardInfoExpanded,
                            onToggleExpanded = onToggleCardInfo,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    transaction.receiptJson?.let {
                        SignatureSection(
                            receiptJson = it,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    val shouldShowOperations = availableOperations.isNotEmpty() ||
                        transaction.type != TransactionType.BATCH_CLOSE
                    if (shouldShowOperations) {
                        OperationPanel(
                            availableOperations = availableOperations,
                            isQuerying = isQuerying,
                            isEnabled = !isLoading && !isQuerying,
                            showQueryButton = transaction.type != TransactionType.BATCH_CLOSE,
                            labelOverrides = labelOverrides,
                            onPerformOperation = onPerformOperation,
                            onQueryClick = onQueryTransaction,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
