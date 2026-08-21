package com.sunmi.tapro.taplink.demo.ui.screens.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.OrderItem
import com.sunmi.tapro.taplink.demo.model.Transaction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.components.CardReadAnimation
import com.sunmi.tapro.taplink.demo.ui.screens.main.PaymentOption
import com.sunmi.tapro.taplink.demo.ui.screens.progress.table.TransactionProgressTableScreen
import com.sunmi.tapro.taplink.demo.ui.secondary.SecondaryOrderLine
import com.sunmi.tapro.taplink.demo.ui.secondary.SecondaryOrderState
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig
import com.sunmi.tapro.taplink.demo.util.AmountFormatter
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.sdk.model.common.PaymentCategory
import java.math.BigDecimal

/**
 * Transaction Progress Screen
 * 
 * Displays real-time transaction progress with status indicators.
 * Shows transaction type, amount, and current status (pending, success, failed).
 * Provides navigation options based on transaction outcome.
 * 
 * Following MVI pattern:
 * - Observes state from ViewModel
 * - Sends intents for user actions
 * - Handles navigation events
 * 
 * @param transactionId Transaction ID to observe
 * @param onNavigateBack Callback to navigate back to main screen
 * @param onNavigateToDetail Callback to navigate to transaction detail screen
 * @param viewModel ViewModel instance (injected via DependencyProvider)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionProgressScreen(
    transactionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    viewModel: TransactionProgressViewModel = viewModel(
        factory = TransactionProgressViewModelFactory(
            transactionRepository = DependencyProvider.transactionRepository,
            paymentService = DependencyProvider.paymentService
        )
    )
) {
    val state by viewModel.state.collectAsState()
    var showManualEntryInputs by rememberSaveable(transactionId) { mutableStateOf(false) }
    
    // Load transaction on first composition
    LaunchedEffect(transactionId) {
        viewModel.handleIntent(TransactionProgressIntent.LoadTransaction(transactionId))
    }

    // Secondary display: show customer-facing order list when waiting for payment selection.
    val context = LocalContext.current
    val secondaryController = DependencyProvider.secondaryTipSelectionController
    val isSecondaryEnabled = ConnectionPreferences.isSecondaryDisplayAdEnabled(context)

    // Dismiss when leaving this screen so MainScreen can re-show the ad carousel.
    DisposableEffect(secondaryController) {
        onDispose {
            if (isSecondaryEnabled) {
                secondaryController.dismiss()
            }
        }
    }

    // Show or hide the customer-facing order summary on the secondary screen.
    LaunchedEffect(state.isReadyForPaymentSelection(), state.isLoading, state.orderItems) {
        if (!isSecondaryEnabled || state.isLoading) return@LaunchedEffect
        if (state.isReadyForPaymentSelection()) {
            val orderState = buildSettleSecondaryOrderState(state)
            secondaryController.showAdCarousel(orderState)
        } else {
            secondaryController.dismiss()
        }
    }

    // Handle navigation events
    LaunchedEffect(state.navigationEvent) {
        state.navigationEvent?.let { event ->
            when (event) {
                is TransactionProgressNavigationEvent.ToMain -> {
                    onNavigateBack()
                }
                is TransactionProgressNavigationEvent.ToDetail -> {
                    onNavigateToDetail(event.transactionId)
                }
            }
            viewModel.handleIntent(TransactionProgressIntent.ClearNavigationEvent)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settle") },
                navigationIcon = {
                    if (state.canNavigateBack) {
                        IconButton(
                            onClick = { viewModel.handleIntent(TransactionProgressIntent.NavigateBack) }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        val screenConfig = rememberScreenConfig()
        val isHeadlessWaitingCard = state.headlessProgressEvent?.eventCode
            ?.equals("WAITING_CARD", ignoreCase = true) == true
        val isAppToAppMode = try {
            val context = DependencyProvider.requireContext()
            ConnectionPreferences.getConnectionMode(context) == ConnectionPreferences.ConnectionMode.APP_TO_APP
        } catch (e: Exception) {
            false
        }
        val canSwitchToManualEntry = isAppToAppMode && state.isPending() &&
            (isHeadlessWaitingCard || state.progressStatus.equals("WAITING_CARD", ignoreCase = true))
        val waitingManualEntry = state.headlessProgressEvent?.eventCode
            ?.equals("WAITING_MANUAL_ENTRY", ignoreCase = true) == true ||
            state.progressStatus.equals("WAITING_MANUAL_ENTRY", ignoreCase = true)
        val showInlineManualEntry = state.isPending() && (showManualEntryInputs || waitingManualEntry)

        LaunchedEffect(state.isComplete()) {
            if (state.isComplete()) {
                showManualEntryInputs = false
            }
        }

        // Portrait safeguard: if a READY_TO_PAY draft is opened, start SDK payment immediately.
        LaunchedEffect(screenConfig.isLandscape, state.isReadyForPaymentSelection(), state.isStartingPayment) {
            if (!screenConfig.isLandscape && state.isReadyForPaymentSelection() && !state.isStartingPayment) {
                viewModel.handleIntent(TransactionProgressIntent.StartSelectedPayment)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (screenConfig.isLandscape) {
                TransactionProgressTableScreen(
                    state = state,
                    canSwitchToManualEntry = canSwitchToManualEntry,
                    showInlineManualEntry = showInlineManualEntry,
                    onAbort = { viewModel.handleIntent(TransactionProgressIntent.AbortTransaction) },
                    onSwitchToManualEntry = {
                        showManualEntryInputs = true
                        viewModel.handleIntent(TransactionProgressIntent.SwitchToManualEntry)
                    },
                    onRetry = { viewModel.handleIntent(TransactionProgressIntent.RetryTransaction) },
                    onQuery = { viewModel.handleIntent(TransactionProgressIntent.QueryTransaction) },
                    onSelectPaymentOption = { option ->
                        viewModel.handleIntent(TransactionProgressIntent.SelectPaymentOption(option))
                    },
                    onStartPayment = {
                        viewModel.handleIntent(TransactionProgressIntent.StartSelectedPayment)
                    },
                    onToggleSplit = {
                        viewModel.handleIntent(TransactionProgressIntent.ToggleSplitMode)
                    },
                    onSplitAmountChange = { input ->
                        viewModel.handleIntent(TransactionProgressIntent.UpdateSplitAmount(input))
                    },
                    onSplitCountChange = { count ->
                        viewModel.handleIntent(TransactionProgressIntent.UpdateSplitCount(count))
                    },
                    onSplitLinePaymentOptionChange = { index, option ->
                        viewModel.handleIntent(TransactionProgressIntent.UpdateSplitLinePaymentOption(index, option))
                    },
                    onSplitLineAmountChange = { index, input ->
                        viewModel.handleIntent(TransactionProgressIntent.UpdateSplitLineAmount(index, input))
                    },
                    onStartSplitLinePayment = { index ->
                        viewModel.handleIntent(TransactionProgressIntent.StartSplitLinePayment(index))
                    },
                    onCancelOrder = {
                        viewModel.handleIntent(TransactionProgressIntent.CancelOrder)
                    },
                    onDone = { viewModel.handleIntent(TransactionProgressIntent.NavigateBack) }
                )
            } else {
                PortraitProgressLayout(
                    state = state,
                    canSwitchToManualEntry = canSwitchToManualEntry,
                    showInlineManualEntry = showInlineManualEntry,
                    onAbort = { viewModel.handleIntent(TransactionProgressIntent.AbortTransaction) },
                    onSwitchToManualEntry = {
                        viewModel.handleIntent(TransactionProgressIntent.SwitchToManualEntry)
                    },
                    onSelectPaymentOption = { option ->
                        viewModel.handleIntent(TransactionProgressIntent.SelectPaymentOption(option))
                    },
                    onStartPayment = {
                        viewModel.handleIntent(TransactionProgressIntent.StartSelectedPayment)
                    },
                    onToggleSplit = {
                        viewModel.handleIntent(TransactionProgressIntent.ToggleSplitMode)
                    },
                    onSplitAmountChange = { input ->
                        viewModel.handleIntent(TransactionProgressIntent.UpdateSplitAmount(input))
                    },
                    onRetry = { viewModel.handleIntent(TransactionProgressIntent.RetryTransaction) },
                    onQuery = { viewModel.handleIntent(TransactionProgressIntent.QueryTransaction) },
                    onCheckOut = { viewModel.handleIntent(TransactionProgressIntent.NavigateBack) },
                    onDone = { viewModel.handleIntent(TransactionProgressIntent.NavigateBack) }
                )
            }

            // Message card overlay - direct child of Box so it can align to top
            state.message?.let { message ->
                MessageCard(
                    message = message,
                    onDismiss = { viewModel.handleIntent(TransactionProgressIntent.DismissMessage) },
                    onAction = { action ->
                        when (action) {
                            MessageAction.RETRY -> {
                                viewModel.handleIntent(TransactionProgressIntent.RetryTransaction)
                            }
                            MessageAction.DETAILS -> {
                                viewModel.handleIntent(TransactionProgressIntent.NavigateToDetail)
                            }
                            else -> {
                                viewModel.handleIntent(TransactionProgressIntent.DismissMessage)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun PortraitProgressLayout(
    state: TransactionProgressState,
    canSwitchToManualEntry: Boolean,
    showInlineManualEntry: Boolean,
    onAbort: () -> Unit,
    onSwitchToManualEntry: () -> Unit,
    onRetry: () -> Unit,
    onQuery: () -> Unit,
    onCheckOut: () -> Unit,
    onDone: () -> Unit,
    onSelectPaymentOption: (PaymentOption) -> Unit,
    onStartPayment: () -> Unit,
    onToggleSplit: () -> Unit,
    onSplitAmountChange: (String) -> Unit
) {
    // Portrait: Show only progress panel, no product details
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
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
                onDone = onDone,
                onCancelOrder = {},
                useTableSplitUi = false,
            )
        }
    }
}

@Composable
internal fun SettleLeftPanel(state: TransactionProgressState) {
    val goodsAmount = resolveGoodsAmount(state)
    val tipAmount = state.transaction?.tipAmount ?: BigDecimal.ZERO
    val taxAmount = state.transaction?.taxAmount ?: BigDecimal.ZERO
    val surchargeAmount = state.transaction?.surchargeAmount ?: BigDecimal.ZERO
    val valueAddedFee = state.transaction?.serviceFee ?: BigDecimal.ZERO
    val receivableAmount = state.transaction?.totalAmount ?: state.amount
    val paidAmount = if (state.isSuccess()) receivableAmount else BigDecimal.ZERO
    val remainingAmount = when {
        state.isSuccess() -> BigDecimal.ZERO
        state.isSplitMode -> state.splitRemaining()
        else -> receivableAmount
    }

    val paymentLabel = state.transaction?.headlessPaymentMethodLabel()
        .takeUnless { it.isNullOrBlank() }
        ?: state.transactionType

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp)
    ) {
        Text(
            text = "Product Details",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Item Details",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            if (state.orderItems.isEmpty()) {
                Text(
                    text = state.transactionType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                state.orderItems.forEach { item ->
                    ProductDetailRow(item = item)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Spacer(modifier = Modifier.height(6.dp))
        AmountLine("Total Price of Goods", goodsAmount)
        AmountLine("Tip", tipAmount)
        AmountLine("Tax", taxAmount)
        AmountLine("Surcharge", surchargeAmount)
        AmountLine("Value-added fee", valueAddedFee)
        AmountLine("Receivable", receivableAmount, emphasize = true)

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "Payment Records",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AmountLine(paymentLabel, paidAmount)

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        AmountLine("Remaining", remainingAmount, emphasize = true, highlight = true)
    }
}

private fun resolveGoodsAmount(state: TransactionProgressState): BigDecimal {
    if (state.orderItems.isNotEmpty()) {
        return state.orderItems.fold(BigDecimal.ZERO) { acc, item -> acc + item.calculateSubtotal() }
    }
    val transaction = state.transaction ?: return state.amount
    if (
        transaction.type != TransactionType.SALE &&
        transaction.type != TransactionType.FORCED_AUTH &&
        transaction.type != TransactionType.POST_AUTH
    ) {
        return transaction.amount
    }
    val extrasTotal = (transaction.tipAmount ?: BigDecimal.ZERO)
        .add(transaction.taxAmount ?: BigDecimal.ZERO)
        .add(transaction.serviceFee ?: BigDecimal.ZERO)
        .add(transaction.surchargeAmount ?: BigDecimal.ZERO)
    val derivedGoodsAmount = transaction.totalAmount
        ?.subtract(extrasTotal)
        ?.takeIf { it > BigDecimal.ZERO }
    return derivedGoodsAmount ?: transaction.amount
}

@Composable
private fun ProductDetailRow(item: OrderItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${AmountFormatter.format(item.product.price)} × ${item.quantity}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = AmountFormatter.format(item.calculateSubtotal()),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun AmountLine(
    label: String,
    amount: BigDecimal,
    emphasize: Boolean = false,
    highlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = AmountFormatter.format(amount),
            style = if (emphasize) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Medium,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun SettleRightPanel(
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
    onDone: () -> Unit = {},
    onCancelOrder: () -> Unit = {},
    onSplitCountChange: (Int) -> Unit = {},
    onSplitLinePaymentOptionChange: (Int, PaymentOption) -> Unit = { _, _ -> },
    onSplitLineAmountChange: (Int, String) -> Unit = { _, _ -> },
    onStartSplitLinePayment: (Int) -> Unit = {},
    useTableSplitUi: Boolean = false
) {
    val paymentLabel = state.transaction?.headlessPaymentMethodLabel()
        .takeUnless { it.isNullOrBlank() }
        ?: state.transactionType
    val successColor = Color(0xFF35A854)
    val groupedOptions = PaymentOption.entries.groupBy { it.group }
    var paymentOptionsExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (state.isReadyForPaymentSelection()) {
                Arrangement.Top
            } else {
                Arrangement.Center
            }
        ) {
            when {
                state.isReadyForPaymentSelection() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Top bar: payment option switch (left) + split toggle (right)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                OutlinedButton(
                                    onClick = { paymentOptionsExpanded = true },
                                    modifier = Modifier
                                        .width(220.dp)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                                    )
                                ) {
                                    Text(
                                        text = state.selectedPaymentOption.label,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Start,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Select payment option",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                DropdownMenu(
                                    expanded = paymentOptionsExpanded,
                                    onDismissRequest = { paymentOptionsExpanded = false }
                                ) {
                                    var isFirstGroup = true
                                    groupedOptions.forEach { (group, options) ->
                                        if (!isFirstGroup) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        isFirstGroup = false

                                        Text(
                                            text = group,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                        )

                                        options.forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        text = option.label,
                                                        fontWeight = if (option == state.selectedPaymentOption) {
                                                            FontWeight.Bold
                                                        } else {
                                                            FontWeight.Normal
                                                        }
                                                    )
                                                },
                                                onClick = {
                                                    paymentOptionsExpanded = false
                                                    onSelectPaymentOption(option)
                                                },
                                                leadingIcon = if (option == state.selectedPaymentOption) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else {
                                                    null
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            val allSplitPaid = useTableSplitUi && state.isSplitMode &&
                                state.splitLines.isNotEmpty() &&
                                state.splitLines.all { it.isPaid }
                            if (useTableSplitUi && !allSplitPaid) {
                                OutlinedButton(
                                    onClick = onToggleSplit,
                                    modifier = Modifier.height(42.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        if (state.isSplitMode) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    )
                                ) {
                                    Text(if (state.isSplitMode) "Cancel" else "Split")
                                }
                            }
                        }

                        if (useTableSplitUi && state.isSplitMode) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Split count control: - N +
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = { onSplitCountChange(state.splitCount - 1) },
                                            enabled = state.splitCount > 1
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Decrease split count",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                        Text(
                                            text = "${state.splitCount}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                        IconButton(
                                            onClick = { onSplitCountChange(state.splitCount + 1) },
                                            enabled = state.splitCount < 10
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Increase split count",
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Pay",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Split lines - each with independent payment option, amount, and charge button
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState()),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        state.splitLines.forEachIndexed { index, line ->
                                            SplitLineRow(
                                                index = index,
                                                line = line,
                                                isPaid = line.isPaid,
                                                isStartingPayment = state.isStartingPayment,
                                                onPaymentOptionChange = { option ->
                                                    onSplitLinePaymentOptionChange(index, option)
                                                },
                                                onAmountChange = { input ->
                                                    onSplitLineAmountChange(index, input)
                                                },
                                                onCharge = { onStartSplitLinePayment(index) }
                                            )
                                        }
                                    }
                                }

                                // Bottom action buttons: Cancel + Payment
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = onCancelOrder,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text(
                                            text = "Cancel",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Button(
                                        onClick = onStartPayment,
                                        enabled = !state.isStartingPayment,
                                        modifier = Modifier
                                            .weight(2f)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (state.isStartingPayment) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = if (state.isStartingPayment) "Starting..." else "Payment",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            // Non-split mode: keep portrait simple (Payment only), table shows Cancel+Payment.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                Text(
                                    text = AmountFormatter.format(state.amount),
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                                if (useTableSplitUi) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = onCancelOrder,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(52.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(
                                                text = "Cancel",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Button(
                                            onClick = onStartPayment,
                                            enabled = !state.isStartingPayment,
                                            modifier = Modifier
                                                .weight(2f)
                                                .height(52.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            if (state.isStartingPayment) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    strokeWidth = 2.dp
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Text(
                                                text = if (state.isStartingPayment) "Starting..." else "Payment",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = onStartPayment,
                                        enabled = !state.isStartingPayment,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .height(52.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        if (state.isStartingPayment) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            text = if (state.isStartingPayment) "Starting..." else "Payment",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                showInlineManualEntry -> {
                    InlineManualEntryForm()
                }

                state.isPending() -> {
                    // Show card animation when waiting for card, otherwise show spinner
                    val isWaitingCard = state.headlessProgressEvent?.eventCode
                        ?.equals("WAITING_CARD", ignoreCase = true) == true ||
                        state.progressStatus.equals("WAITING_CARD", ignoreCase = true)

                    if (isWaitingCard) {
                        val cardTypeStrings = state.headlessProgressEvent?.waitingCardTypes
                            ?: emptyList()
                        val animResId = com.sunmi.tapro.taplink.demo.device.getCardAnimationResIdFromStrings(cardTypeStrings)
                        CardReadAnimation(
                            animationResId = animResId,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = state.getStatusDisplayText(),
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(68.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = state.getStatusDisplayText(),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                state.isSuccess() -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Success",
                        tint = successColor,
                        modifier = Modifier.size(70.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Payment succeeded",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                else -> {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(70.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = state.getStatusDisplayText(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    if (state.errorMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = state.errorMessage,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        val canAbortPayment = state.isPending() && !state.isReadyForPaymentSelection()
        if (canAbortPayment || canSwitchToManualEntry) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                if (canAbortPayment) {
                    OutlinedButton(onClick = onAbort) { Text("Abort") }
                }
                if (canSwitchToManualEntry) {
                    OutlinedButton(onClick = onSwitchToManualEntry) { Text("Switch Manual") }
                }
            }
        }

        // Action buttons when transaction is complete
        if (state.isComplete()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!state.isSuccess() && state.transaction?.type != TransactionType.BATCH_CLOSE) {
                    OutlinedButton(
                        onClick = onQuery,
                        enabled = !state.isQuerying,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (state.isQuerying) "Querying..." else "Query",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (state.canRetry) {
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Retry",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Button(
                    onClick = onDone,
                    modifier = Modifier
                        .width(160.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineManualEntryForm() {
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var cvv by rememberSaveable { mutableStateOf("") }
    var zip by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Manual Entry",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        OutlinedTextField(
            value = cardNumber,
            onValueChange = { cardNumber = it },
            label = { Text("Card Number") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = it },
                label = { Text("MM/YY") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { cvv = it },
                label = { Text("CVV") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = zip,
            onValueChange = { zip = it },
            label = { Text("ZIP / Postal Code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * A single split payment line with independent payment option dropdown, amount input, and charge button.
 */
@Composable
private fun SplitLineRow(
    index: Int,
    line: SplitLine,
    isPaid: Boolean,
    isStartingPayment: Boolean,
    onPaymentOptionChange: (PaymentOption) -> Unit,
    onAmountChange: (String) -> Unit,
    onCharge: () -> Unit
) {
    val groupedOptions = PaymentOption.entries.groupBy { it.group }
    var expanded by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Payment option dropdown
        Box(modifier = Modifier.weight(0.40f)) {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = !isPaid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = line.paymentOption.label.substringBefore(" -"),
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                var isFirstGroup = true
                groupedOptions.forEach { (group, options) ->
                    if (!isFirstGroup) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    isFirstGroup = false
                    Text(
                        text = group,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    fontWeight = if (option == line.paymentOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                expanded = false
                                onPaymentOptionChange(option)
                            },
                            leadingIcon = if (option == line.paymentOption) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }

        // Amount input
        OutlinedTextField(
            value = line.amountInput,
            onValueChange = { input ->
                val sanitized = input.filterIndexed { idx, ch ->
                    ch.isDigit() || (ch == '.' && input.indexOf('.') == idx)
                }
                onAmountChange(sanitized)
            },
            enabled = !isPaid,
            modifier = Modifier
                .weight(0.32f)
                .height(50.dp),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            prefix = { Text("$") }
        )

        // Charge button
        Button(
            onClick = onCharge,
            enabled = !isStartingPayment && !isPaid,
            modifier = Modifier
                .weight(0.28f)
                .height(50.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(if (isPaid) "Paid" else "Charge", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Formats the payment method that the headless read-card UI should show to the cashier.
 *
 * The headless callback only tells the currently enabled reader card types. The demo keeps
 * the requested payment method on [Transaction], so use it to distinguish Credit/Debit/EBT
 * while Tapro is still waiting for card input.
 */
private fun Transaction.headlessPaymentMethodLabel(): String {
    return when (paymentCategory) {
        PaymentCategory.EBT -> {
            val subMethod = subPaymentMethodId.orEmpty()
            if (subMethod.isBlank()) "EBT" else "EBT $subMethod"
        }

        PaymentCategory.CARD -> {
            val method = paymentMethodId
                ?.takeIf { it.isNotBlank() }
                ?: cardNetworkType?.takeIf { it.isNotBlank() }
            method?.toCreditDebitDisplayName() ?: "Card"
        }

        PaymentCategory.QR_MPM -> "QR MPM"
        PaymentCategory.QR_CPM -> "QR CPM"
        null -> paymentMethodId
            ?.takeIf { it.isNotBlank() }
            ?.toCreditDebitDisplayName()
            .orEmpty()
    }
}

/**
 * Converts API-oriented payment method IDs into cashier-friendly labels.
 */
private fun String.toCreditDebitDisplayName(): String {
    return when (uppercase()) {
        "CREDIT" -> "Credit"
        "DEBIT" -> "Debit"
        "EBT" -> "EBT"
        "SNAP" -> "EBT SNAP"
        "BENEFIT" -> "EBT BENEFIT"
        else -> replace('_', ' ')
            .lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}

// Preview functions for different states
@Preview(showBackground = true)
@Composable
fun TransactionProgressScreenPendingPreview() {
    TaplinkTheme {
        TransactionProgressScreenPreview(
            transactionType = "SALE",
            amount = BigDecimal("25.50"),
            status = TransactionStatus.PENDING,
            canNavigateBack = false,
            canRetry = false,
            showViewDetailsButton = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionProgressScreenProcessingPreview() {
    TaplinkTheme {
        TransactionProgressScreenPreview(
            transactionType = "AUTH",
            amount = BigDecimal("100.00"),
            status = TransactionStatus.PROCESSING,
            canNavigateBack = false,
            canRetry = false,
            showViewDetailsButton = false
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionProgressScreenSuccessPreview() {
    TaplinkTheme {
        TransactionProgressScreenPreview(
            transactionType = "SALE",
            amount = BigDecimal("45.75"),
            status = TransactionStatus.SUCCESS,
            canNavigateBack = true,
            canRetry = false,
            showViewDetailsButton = true
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionProgressScreenFailedPreview() {
    TaplinkTheme {
        TransactionProgressScreenPreview(
            transactionType = "REFUND",
            amount = BigDecimal("30.00"),
            status = TransactionStatus.FAILED,
            canNavigateBack = true,
            canRetry = true,
            showViewDetailsButton = false,
            errorMessage = "Connection timeout. Please check your network connection."
        )
    }
}

@Preview(showBackground = true)
@Composable
fun TransactionProgressScreenAbortedPreview() {
    TaplinkTheme {
        TransactionProgressScreenPreview(
            transactionType = "SALE",
            amount = BigDecimal("15.25"),
            status = TransactionStatus.FAILED,
            canNavigateBack = true,
            canRetry = false,
            showViewDetailsButton = false
        )
    }
}

/**
 * Preview helper composable
 * Creates a preview state for the TransactionProgressScreen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionProgressScreenPreview(
    transactionType: String,
    amount: BigDecimal,
    status: TransactionStatus,
    canNavigateBack: Boolean,
    canRetry: Boolean,
    showViewDetailsButton: Boolean,
    errorMessage: String = ""
) {
    val previewState = TransactionProgressState(
        transactionId = "TXN123456",
        transactionType = transactionType,
        amount = amount,
        status = status,
        isLoading = status == TransactionStatus.PENDING || status == TransactionStatus.PROCESSING,
        canNavigateBack = canNavigateBack,
        canRetry = canRetry,
        showViewDetailsButton = showViewDetailsButton,
        errorMessage = errorMessage
    )
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Progress") },
                navigationIcon = {
                    if (previewState.canNavigateBack) {
                        IconButton(onClick = {}) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }

                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper section: Transaction type and amount
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = previewState.transactionType,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = AmountFormatter.format(previewState.amount),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
            
            // Middle section: Transaction status
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when {
                    previewState.isPending() -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 6.dp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = previewState.getStatusDisplayText(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    previewState.isSuccess() -> {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Success",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = previewState.getStatusDisplayText(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF4CAF50),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    previewState.isFailed() -> {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Failed",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = previewState.getStatusDisplayText(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        if (previewState.errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = previewState.errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
                }
            }
            
            // Lower section: Action buttons
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (previewState.showViewDetailsButton) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View Details",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    
                    if (previewState.canRetry) {
                        Button(
                            onClick = {},
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Retry Transaction",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convert a [TransactionProgressState] into a [SecondaryOrderState] suitable for
 * the customer-facing secondary display during the READY_TO_PAY settle phase.
 *
 * Mirrors the logic in MainScreen's [buildSecondaryOrderState] so that the
 * customer sees the same order-list format they saw while items were being added.
 *
 * Returns null when the transaction has no order items (fallback: ad carousel only).
 */
private fun buildSettleSecondaryOrderState(state: TransactionProgressState): SecondaryOrderState? {
    if (state.orderItems.isEmpty()) return null
    val lines = state.orderItems.map { item ->
        SecondaryOrderLine(
            name = item.product.name,
            quantity = item.quantity,
            unitPrice = item.product.price,
            subtotal = item.calculateSubtotal()
        )
    }
    val totalAmount = state.transaction?.totalAmount
        ?: state.orderItems.fold(BigDecimal.ZERO) { acc, item -> acc + item.calculateSubtotal() }
    return SecondaryOrderState(
        lines = lines,
        totalAmount = totalAmount,
        totalItemCount = state.orderItems.sumOf { it.quantity },
        currency = "USD"
    )
}
