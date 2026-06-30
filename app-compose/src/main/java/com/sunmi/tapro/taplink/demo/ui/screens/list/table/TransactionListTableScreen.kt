package com.sunmi.tapro.taplink.demo.ui.screens.list.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TransactionListTableScreen(
    selectedTransactionId: String?,
    listContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
    detailContent: @Composable BoxScope.(String) -> Unit
) {
    if (selectedTransactionId != null) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxSize()
            ) {
                listContent()
            }
            VerticalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
            Box(
                modifier = Modifier
                    .weight(0.60f)
                    .fillMaxSize()
            ) {
                detailContent(selectedTransactionId)
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            listContent()
        }
    }
}
