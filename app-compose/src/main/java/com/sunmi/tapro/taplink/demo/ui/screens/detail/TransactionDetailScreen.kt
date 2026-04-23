package com.sunmi.tapro.taplink.demo.ui.screens.detail

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.components.PosDialog
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.TransactionHeader
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.OrderInfoSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.PaymentInfoSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.CardInfoSection
import com.sunmi.tapro.taplink.demo.ui.screens.detail.components.OperationPanel
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig
import com.sunmi.tapro.taplink.demo.util.AmountFormatter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Transaction Detail Screen - Toast POS style
 *
 * Displays complete order information in receipt-like format.
 * Follows MVI pattern with TransactionDetailViewModel for state management.
 *
 * Features:
 * - Order header with prominent amount and status
 * - Order details in compact receipt format
 * - Payment breakdown with clear hierarchy
 * - Card information (collapsible)
 * - Available follow-up operations
 * - Optimized for portrait mode with smooth scrolling
 *
 * Design features:
 * - Toast POS style visual hierarchy
 * - Compact spacing (6-8dp between sections)
 * - Receipt-like information display
 * - Clear visual grouping with cards
 *
 * @param transactionId Transaction ID to display
 * @param onNavigateBack Callback to navigate back
 * @param onNavigateToProgress Callback to navigate to progress screen for follow-up operations
 * @param embeddedInMasterDetail When true, renders as a panel (e.g. right side in landscape list) with close instead of back
 * @param onCloseDetail When embedded, called when user closes the detail panel
 * @param viewModel ViewModel instance (injected via factory)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToProgress: (String) -> Unit,
    embeddedInMasterDetail: Boolean = false,
    onCloseDetail: () -> Unit = {},
    viewModel: TransactionDetailViewModel = viewModel(
        factory = TransactionDetailViewModelFactory(
            context = LocalContext.current
        )
    )
) {
    val state by viewModel.state.collectAsState()

    // Load transaction on first composition
    LaunchedEffect(transactionId) {
        viewModel.handleIntent(TransactionDetailIntent.LoadTransaction(transactionId))
    }

    // Handle navigation events
    LaunchedEffect(state.navigationEvent) {
        state.navigationEvent?.let { event ->
            when (event) {
                is TransactionDetailNavigationEvent.Back ->
                    if (embeddedInMasterDetail) onCloseDetail() else onNavigateBack()

                is TransactionDetailNavigationEvent.ToProgress -> onNavigateToProgress(event.transactionId)
            }
            viewModel.handleIntent(TransactionDetailIntent.ClearNavigationEvent)
        }
    }

    val topBar: @Composable () -> Unit = {
        TopAppBar(
            title = { Text("Order Details") },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (embeddedInMasterDetail) onCloseDetail()
                        else viewModel.handleIntent(TransactionDetailIntent.NavigateBack)
                    }
                ) {
                    Icon(
                        imageVector = if (embeddedInMasterDetail) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = if (embeddedInMasterDetail) "Close" else "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }

    if (embeddedInMasterDetail) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(modifier = Modifier.fillMaxSize()) {
                TransactionDetailScreenContent(state, viewModel)
            }
        }
    } else {
        Scaffold(topBar = topBar) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TransactionDetailScreenContent(state, viewModel)
            }
        }
    }

    // Operation input dialog
    if (state.showOperationDialog && state.currentOperationType != null) {
        OperationInputDialog(
            operationType = state.currentOperationType!!,
            onDismiss = { viewModel.handleIntent(TransactionDetailIntent.HideOperationDialog) },
            onConfirm = { amount, tipAmount, taxAmount, surchargeAmount ->
                viewModel.handleIntent(
                    TransactionDetailIntent.ConfirmOperation(
                        operationType = state.currentOperationType!!,
                        amount = amount,
                        tipAmount = tipAmount,
                        taxAmount = taxAmount,
                        surchargeAmount = surchargeAmount
                    )
                )
            }
        )
    }
}

@Composable
private fun TransactionDetailScreenContent(
    state: TransactionDetailState,
    viewModel: TransactionDetailViewModel
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> {
                // Loading indicator
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            state.transaction != null -> {
                // Transaction details content
                TransactionDetailContent(
                    state = state,
                    onPerformOperation = { operationType ->
                        viewModel.handleIntent(
                            TransactionDetailIntent.PerformOperation(
                                operationType
                            )
                        )
                    },
                    onDismissMessage = {
                        viewModel.handleIntent(TransactionDetailIntent.DismissMessage)
                    },
                    onQueryTransaction = {
                        viewModel.handleIntent(TransactionDetailIntent.QueryTransaction)
                    },
                    viewModel = viewModel
                )
            }

            else -> {
                // Error state - no order found
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Order not found",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.handleIntent(TransactionDetailIntent.NavigateBack) }) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}

/**
 * Transaction detail content - Toast POS style
 *
 * Portrait: single column, vertical scroll.
 * Landscape: compact header at top, then two columns in one scroll with divider.
 */
@Composable
private fun TransactionDetailContent(
    state: TransactionDetailState,
    onPerformOperation: (TransactionType) -> Unit,
    onDismissMessage: () -> Unit,
    onQueryTransaction: () -> Unit,
    viewModel: TransactionDetailViewModel
) {
    val transaction = state.transaction ?: return
    val screenConfig = rememberScreenConfig()

    Box(modifier = Modifier.fillMaxSize()) {
        if (screenConfig.isLandscape) {
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
                                orderItems = state.orderItems,
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
                                    isExpanded = state.isCardInfoExpanded,
                                    onToggleExpanded = {
                                        viewModel.handleIntent(
                                            TransactionDetailIntent.ToggleCardInfoExpanded
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            // Only show OperationPanel if there are operations or query button
                            val shouldShowOperations = state.availableOperations.isNotEmpty() || 
                                                      transaction.type != TransactionType.BATCH_CLOSE
                            if (shouldShowOperations) {
                                OperationPanel(
                                    availableOperations = state.availableOperations,
                                    isQuerying = state.isQuerying,
                                    isEnabled = !state.isLoading && !state.isQuerying,
                                    showQueryButton = transaction.type != TransactionType.BATCH_CLOSE,
                                    labelOverrides = buildTipLabelOverride(transaction),
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
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                TransactionHeader(transaction = transaction)
                Spacer(modifier = Modifier.height(8.dp))
                OrderInfoSection(
                    transaction = transaction,
                    orderItems = state.orderItems,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                PaymentInfoSection(
                    transaction = transaction,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                transaction.cardInfo?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    CardInfoSection(
                        transaction = transaction,
                        isExpanded = state.isCardInfoExpanded,
                        onToggleExpanded = { viewModel.handleIntent(TransactionDetailIntent.ToggleCardInfoExpanded) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Only show OperationPanel if there are operations or query button
                val shouldShowOperations = state.availableOperations.isNotEmpty() || 
                                          transaction.type != TransactionType.BATCH_CLOSE
                if (shouldShowOperations) {
                    OperationPanel(
                        availableOperations = state.availableOperations,
                        isQuerying = state.isQuerying,
                        isEnabled = !state.isLoading && !state.isQuerying,
                        showQueryButton = transaction.type != TransactionType.BATCH_CLOSE,
                        labelOverrides = buildTipLabelOverride(transaction),
                        onPerformOperation = onPerformOperation,
                        onQueryClick = onQueryTransaction,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Message card overlay - floats on top
        state.message?.let { message ->
            MessageCard(
                message = message,
                onDismiss = onDismissMessage,
                onAction = { action ->
                    when (action) {
                        MessageAction.RETRY -> {
                            // Retry logic handled by ViewModel
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

/**
 * Build label override map for TIP_ADJUST button.
 * Shows "Add Tip" when no tip exists, "Tip Adjust" when tip already present.
 */
private fun buildTipLabelOverride(transaction: com.sunmi.tapro.taplink.demo.model.Transaction): Map<TransactionType, String> {
    val hasTip = transaction.tipAmount != null && transaction.tipAmount > java.math.BigDecimal.ZERO
    return mapOf(TransactionType.TIP_ADJUST to if (hasTip) "Tip Adjust" else "Add Tip")
}

/**
 * Operation input dialog - POS style
 * For operations that require amount input (REFUND, TIP_ADJUST, etc.)
 * POST_AUTH shows additional fields for tip, tax, and surcharge amounts.
 */
@Composable
private fun OperationInputDialog(
    operationType: TransactionType,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, tipAmount: String?, taxAmount: String?, surchargeAmount: String?) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var tipAmount by remember { mutableStateOf("") }
    var taxAmount by remember { mutableStateOf("") }
    var surchargeAmount by remember { mutableStateOf("") }
    val isPostAuth = operationType == TransactionType.POST_AUTH
    val amountRegex = remember { Regex("^\\d*\\.?\\d{0,2}$") }

    PosDialog(
        onDismissRequest = onDismiss,
        title = operationType.displayName(),
        subtitle = if (isPostAuth) "Enter completion amount and optional additional amounts"
                   else "Enter the amount for this operation",
        icon = Icons.Default.AttachMoney,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
        confirmText = "Confirm",
        onConfirm = {
            onConfirm(
                amount,
                tipAmount.takeIf { isPostAuth && it.isNotBlank() },
                taxAmount.takeIf { isPostAuth && it.isNotBlank() },
                surchargeAmount.takeIf { isPostAuth && it.isNotBlank() }
            )
        },
        confirmEnabled = amount.isNotBlank(),
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.matches(amountRegex)) amount = it },
            label = { Text("Amount") },
            prefix = { Text("$") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        )
        if (isPostAuth) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = tipAmount,
                onValueChange = { if (it.isEmpty() || it.matches(amountRegex)) tipAmount = it },
                label = { Text("Tip Amount (Optional)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = taxAmount,
                onValueChange = { if (it.isEmpty() || it.matches(amountRegex)) taxAmount = it },
                label = { Text("Tax Amount (Optional)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = surchargeAmount,
                onValueChange = { if (it.isEmpty() || it.matches(amountRegex)) surchargeAmount = it },
                label = { Text("Surcharge Amount (Optional)") },
                prefix = { Text("$") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}
