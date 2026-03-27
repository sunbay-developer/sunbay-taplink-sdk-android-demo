package com.sunmi.tapro.taplink.demo.ui.screens.detail.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sunmi.tapro.taplink.demo.model.TransactionType

/**
 * Operation Panel - Order-detail style
 *
 * Shows a single "More actions" entry; refund/void/tip/query are in a dialog
 * so the main screen stays receipt-focused like a real ordering app.
 */
@Composable
fun OperationPanel(
    availableOperations: List<TransactionType>,
    isQuerying: Boolean,
    isEnabled: Boolean,
    showQueryButton: Boolean = true,
    onPerformOperation: (TransactionType) -> Unit,
    onQueryClick: () -> Unit,
    @SuppressLint("ModifierParameter") modifier: Modifier = Modifier
) {
    var showActionsDialog by remember { mutableStateOf(false) }

    // Calculate total available actions
    val totalActions = availableOperations.size + if (showQueryButton) 1 else 0

    // If only one action available, show it directly
    val showDirectButton = totalActions == 1

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (showDirectButton) {
                    if (availableOperations.size == 1) {
                        OutlinedButton(
                            onClick = { onPerformOperation(availableOperations[0]) },
                            enabled = isEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(availableOperations[0].displayName())
                        }
                    } else if (showQueryButton) {
                        OutlinedButton(
                            onClick = onQueryClick,
                            enabled = isEnabled,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isQuerying) "Querying…" else "Query")
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showActionsDialog = true },
                        enabled = isEnabled,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("More actions")
                    }
                }
            }
        }
    }

    // POS-style operations dialog
    if (showActionsDialog && !showDirectButton) {
        Dialog(
            onDismissRequest = { showActionsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(0.92f),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
//                    Text(
//                        text = "Operations",
//                        style = MaterialTheme.typography.titleLarge,
//                        fontWeight = FontWeight.Bold,
//                        color = MaterialTheme.colorScheme.onSurface
//                    )
//                    Spacer(modifier = Modifier.height(4.dp))
//                    Text(
//                        text = "Select an operation to perform",
//                        style = MaterialTheme.typography.bodyMedium,
//                        color = MaterialTheme.colorScheme.onSurfaceVariant
//                    )
//                    Spacer(modifier = Modifier.height(16.dp))
//                    HorizontalDivider(
//                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
//                    )
//                    Spacer(modifier = Modifier.height(8.dp))

                    availableOperations.forEach { operationType ->
                        OperationActionRow(
                            label = operationType.displayName(),
                            onClick = {
                                showActionsDialog = false
                                onPerformOperation(operationType)
                            }
                        )
                    }
                    if (showQueryButton) {
                        OperationActionRow(
                            label = if (isQuerying) "Querying…" else "Query",
                            icon = Icons.Default.Search,
                            enabled = isEnabled,
                            onClick = {
                                showActionsDialog = false
                                onQueryClick()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showActionsDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/**
 * Single action row inside the operations dialog
 */
@Composable
private fun OperationActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    val tint = MaterialTheme.colorScheme.primary

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        tint.copy(alpha = 0.12f * contentAlpha),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint.copy(alpha = contentAlpha),
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = label.take(1),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint.copy(alpha = contentAlpha)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
            )
        }
    }
}
