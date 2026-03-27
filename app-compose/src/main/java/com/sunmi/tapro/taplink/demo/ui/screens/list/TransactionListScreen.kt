package com.sunmi.tapro.taplink.demo.ui.screens.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.components.OrderCard
import com.sunmi.tapro.taplink.demo.ui.components.PosDialog
import com.sunmi.tapro.taplink.demo.ui.screens.detail.TransactionDetailScreen
import com.sunmi.tapro.taplink.demo.ui.screens.list.components.EmptyState
import com.sunmi.tapro.taplink.demo.ui.screens.list.components.FilterType
import com.sunmi.tapro.taplink.demo.ui.screens.list.components.QuickFilterChips
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig

/**
 * Transaction List Screen - Toast POS style
 * 
 * Displays a list of orders with filtering and search capabilities.
 * Supports operations like query transaction, batch close, and standalone refund.
 * 
 * Following MVI pattern:
 * - Observes state from ViewModel
 * - Sends intents to ViewModel for user actions
 * - Handles navigation events
 * 
 * Design features:
 * - Order-centric layout (not transaction-centric)
 * - Compact spacing for receipt-like appearance
 * - Responsive button layout (vertical < 600dp, horizontal >= 600dp)
 * - Toast POS style visual hierarchy
 * 
 * @param viewModel Transaction List ViewModel
 * @param onNavigateToDetail Callback to navigate to transaction detail screen
 * @param onNavigateBack Callback to navigate back to main screen
 * @param onNavigateToProgress Callback to navigate to transaction progress screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    viewModel: TransactionListViewModel = viewModel(
        factory = TransactionListViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    ),
    onNavigateToDetail: (String) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToProgress: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    var topBarMenuExpanded by remember { mutableStateOf(false) }
    val screenConfig = rememberScreenConfig()
    var selectedTransactionId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { Text("Orders") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.handleIntent(TransactionListIntent.NavigateBack) }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (screenConfig.isLandscape) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { viewModel.handleIntent(TransactionListIntent.ShowQueryDialog) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Query", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = { viewModel.handleIntent(TransactionListIntent.ShowBatchCloseDialog) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.DoneAll, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Batch", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = { viewModel.handleIntent(TransactionListIntent.ShowStandaloneRefundDialog) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Refund", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = { viewModel.handleIntent(TransactionListIntent.ShowClearAllDialog) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    enabled = state.transactions.isNotEmpty()
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            IconButton(onClick = { topBarMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = topBarMenuExpanded,
                                onDismissRequest = { topBarMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Query") },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        viewModel.handleIntent(TransactionListIntent.ShowQueryDialog)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Batch close") },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        viewModel.handleIntent(TransactionListIntent.ShowBatchCloseDialog)
                                    },
                                    leadingIcon = { Icon(Icons.Default.DoneAll, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Standalone refund") },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        viewModel.handleIntent(TransactionListIntent.ShowStandaloneRefundDialog)
                                    },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear all") },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        viewModel.handleIntent(TransactionListIntent.ShowClearAllDialog)
                                    },
                                    leadingIcon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                                    enabled = state.transactions.isNotEmpty()
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                QuickFilterChips(
                    selectedFilter = state.selectedFilter,
                    onFilterSelected = { filter ->
                        viewModel.handleIntent(TransactionListIntent.SelectFilter(filter))
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            @Composable
            fun ListContentColumn() {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (state.isEmpty()) {
                        EmptyState(
                            icon = Icons.AutoMirrored.Filled.List,
                            title = "No orders yet",
                            subtitle = "Orders will appear here after you process payments",
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = state.getDisplayList(),
                                key = { orderGroup -> orderGroup.orderId }
                            ) { orderGroup ->
                                val isSelected = screenConfig.isLandscape &&
                                    selectedTransactionId != null &&
                                    orderGroup.transactions.any { it.transactionRequestId == selectedTransactionId }
                                OrderCard(
                                    orderGroup = orderGroup,
                                    onTransactionClick = { transactionRequestId ->
                                        if (screenConfig.isLandscape) {
                                            selectedTransactionId = if (selectedTransactionId == transactionRequestId) null
                                            else transactionRequestId
                                        } else {
                                            viewModel.handleIntent(
                                                TransactionListIntent.NavigateToDetail(transactionRequestId)
                                            )
                                        }
                                    },
                                    isSelected = isSelected
                                )
                            }
                        }
                    }
                }
            }

            if (screenConfig.isLandscape && selectedTransactionId != null) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxSize()
                    ) {
                        ListContentColumn()
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
                        TransactionDetailScreen(
                            transactionId = selectedTransactionId!!,
                            onNavigateBack = { selectedTransactionId = null },
                            onNavigateToProgress = onNavigateToProgress,
                            embeddedInMasterDetail = true,
                            onCloseDetail = { selectedTransactionId = null }
                        )
                    }
                }
            } else {
                ListContentColumn()
            }

            // Message card overlay - floats on top
            state.message?.let { message ->
                MessageCard(
                    message = message,
                    onDismiss = { viewModel.handleIntent(TransactionListIntent.DismissMessage) },
                    onAction = { action ->
                        when (action) {
                            MessageAction.RETRY -> {
                                viewModel.handleIntent(TransactionListIntent.RefreshTransactions)
                            }
                            else -> {}
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }
    }
    
    // Query Transaction Dialog
    if (state.showQueryDialog) {
        QueryTransactionDialog(
            onDismiss = { viewModel.handleIntent(TransactionListIntent.HideQueryDialog) },
            onQuery = { queryId ->
                viewModel.handleIntent(TransactionListIntent.QueryTransaction(queryId))
            }
        )
    }
    
    // Batch Close Confirmation Dialog
    if (state.showBatchCloseDialog) {
        BatchCloseDialog(
            onDismiss = { viewModel.handleIntent(TransactionListIntent.HideBatchCloseDialog) },
            onConfirm = { viewModel.handleIntent(TransactionListIntent.BatchClose) }
        )
    }
    
    // Standalone Refund Dialog
    if (state.showStandaloneRefundDialog) {
        StandaloneRefundDialog(
            onDismiss = { viewModel.handleIntent(TransactionListIntent.HideStandaloneRefundDialog) },
            onRefund = { amount ->
                viewModel.handleIntent(TransactionListIntent.StandaloneRefund(amount))
            }
        )
    }
    
    // Clear All Transactions Dialog
    if (state.showClearAllDialog) {
        ClearAllTransactionsDialog(
            transactionCount = state.transactions.size,
            onDismiss = { viewModel.handleIntent(TransactionListIntent.HideClearAllDialog) },
            onConfirm = { viewModel.handleIntent(TransactionListIntent.ClearAllTransactions) }
        )
    }

    // Handle navigation events
    LaunchedEffect(state.navigationEvent) {
        state.navigationEvent?.let { event ->
            when (event) {
                is TransactionListNavigationEvent.ToMain -> onNavigateBack()
                is TransactionListNavigationEvent.ToDetail -> onNavigateToDetail(event.transactionId)
                is TransactionListNavigationEvent.ToProgress -> onNavigateToProgress(event.transactionId)
            }
            viewModel.handleIntent(TransactionListIntent.ClearNavigationEvent)
        }
    }
}

/**
 * Query Transaction Dialog - POS style
 *
 * Dialog for querying a transaction by request ID or transaction ID
 */
@Composable
private fun QueryTransactionDialog(
    onDismiss: () -> Unit,
    onQuery: (String) -> Unit
) {
    var queryId by remember { mutableStateOf("") }

    PosDialog(
        onDismissRequest = onDismiss,
        title = "Query Transaction",
        subtitle = "Enter transaction ID or request ID to look up",
        icon = Icons.Default.Search,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
        confirmText = "Query",
        onConfirm = {
            if (queryId.isNotBlank()) {
                onQuery(queryId)
            }
        },
        confirmEnabled = queryId.isNotBlank(),
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = queryId,
            onValueChange = { queryId = it },
            label = { Text("Transaction ID / Request ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
    }
}

/**
 * Batch Close Dialog - POS style
 *
 * Confirmation dialog for batch close operation
 */
@Composable
private fun BatchCloseDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PosDialog(
        onDismissRequest = onDismiss,
        title = "Batch Close",
        subtitle = "Are you sure you want to close the current batch? This will settle all pending transactions.",
        icon = Icons.Default.DoneAll,
        iconTint = MaterialTheme.colorScheme.secondary,
        iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
        confirmText = "Close Batch",
        onConfirm = onConfirm,
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        // No extra content needed for confirmation
    }
}

/**
 * Standalone Refund Dialog - POS style
 *
 * Dialog for processing a standalone refund without referencing an original transaction
 */
@Composable
private fun StandaloneRefundDialog(
    onDismiss: () -> Unit,
    onRefund: (String) -> Unit
) {
    var amount by remember { mutableStateOf("") }

    PosDialog(
        onDismissRequest = onDismiss,
        title = "Standalone Refund",
        subtitle = "This refund will not reference any original transaction",
        icon = Icons.Default.Refresh,
        iconTint = MaterialTheme.colorScheme.tertiary,
        iconBackgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
        confirmText = "Refund",
        onConfirm = {
            if (amount.isNotBlank()) {
                onRefund(amount)
            }
        },
        confirmEnabled = amount.isNotBlank(),
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    amount = newValue
                }
            },
            label = { Text("Amount") },
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
    }
}

/**
 * Clear All Transactions Dialog - POS style
 *
 * Confirmation dialog for clearing all transaction records
 */
@Composable
private fun ClearAllTransactionsDialog(
    transactionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    PosDialog(
        onDismissRequest = onDismiss,
        title = "Clear All Transactions",
        subtitle = "Are you sure you want to clear all $transactionCount transaction(s)? This action cannot be undone.",
        icon = Icons.Default.DeleteSweep,
        iconTint = MaterialTheme.colorScheme.error,
        iconBackgroundColor = MaterialTheme.colorScheme.errorContainer,
        confirmText = "Clear All",
        onConfirm = onConfirm,
        confirmColors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error
        ),
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        // No extra content needed for confirmation
    }
}
