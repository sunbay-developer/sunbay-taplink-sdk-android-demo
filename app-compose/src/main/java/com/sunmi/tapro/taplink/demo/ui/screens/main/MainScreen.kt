package com.sunmi.tapro.taplink.demo.ui.screens.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.model.*
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.ui.components.*
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig
import java.math.BigDecimal

/**
 * MainScreen - Primary screen for the Taplink POS application
 * Optimized for portrait orientation devices
 * 
 * Layout structure (top to bottom):
 * - Top bar with connection status, settings, and transaction list buttons
 * - Message card for errors and notifications (when present)
 * - Product grid (2 columns, scrollable) - takes most space
 * - Order summary (compact, scrollable items) - shows current order
 * - Payment buttons (fixed at bottom) - SALE, AUTH, FORCED_AUTH
 * 
 * Features:
 * - Product selection grid for building orders
 * - Compact order summary with item management
 * - Payment buttons (SALE, AUTH, FORCED_AUTH)
 * - Connection status indicator
 * - Error/message display with MessageCard
 * - Additional amounts dialog
 * - Navigation to settings and transaction list
 * 
 * Follows MVI architecture pattern with MainViewModel
 * 
 * @param viewModel MainViewModel instance for state management
 * @param onNavigateToTransactionList Callback to navigate to transaction list
 * @param onNavigateToSettings Callback to navigate to settings
 * @param onNavigateToProgress Callback to navigate to transaction progress screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(
        factory = MainViewModelFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    ),
    onNavigateToTransactionList: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProgress: (String) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val screenConfig = rememberScreenConfig()

    Scaffold(
        topBar = {
            ModernTopBar(
                connectionState = state.connectionStatus,
                onSettingsClick = { viewModel.handleIntent(MainIntent.NavigateToSettings) },
                onHistoryClick = { viewModel.handleIntent(MainIntent.NavigateToTransactionList) }
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (screenConfig.isLandscape) {
                // Landscape: left 60% product grid, right 40% order summary + payment buttons
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    Box(modifier = Modifier.fillMaxHeight().weight(0.6f)) {
                        ProductGrid(
                            products = state.products,
                            onProductClick = { product ->
                                viewModel.handleIntent(MainIntent.AddProduct(product))
                            },
                            onAddProductClick = {
                                viewModel.handleIntent(MainIntent.ShowAddProductDialog)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.4f)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            EnhancedOrderSummary(
                                orderItems = state.orderItems,
                                subtotalAmount = state.getItemsSubtotal(),
                                totalAmount = state.totalAmount,
                                additionalAmounts = state.additionalAmounts,
                                isEditingSubtotal = state.isEditingSubtotal,
                                onRemoveItem = { item ->
                                    viewModel.handleIntent(MainIntent.RemoveOrderItem(item))
                                },
                                onAdditionalAmounts = {
                                    viewModel.handleIntent(MainIntent.ShowAdditionalAmountsDialog)
                                },
                                onStartEditingSubtotal = {
                                    viewModel.handleIntent(MainIntent.StartEditingSubtotal)
                                },
                                onStopEditingSubtotal = {
                                    viewModel.handleIntent(MainIntent.StopEditingSubtotal)
                                },
                                onAddCustomAmount = { amount ->
                                    viewModel.handleIntent(MainIntent.AddCustomAmount(amount))
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ModernPaymentButton(
                            enabled = state.canProcessPayment,
                            isLoading = state.isInitiatingPayment,
                            selectedOption = state.selectedPaymentOption,
                            onPay = { viewModel.handleIntent(MainIntent.ProcessPayment) },
                            onSelectOption = { option -> viewModel.handleIntent(MainIntent.SelectPaymentOption(option)) }
                        )
                    }
                }
            } else {
                // Portrait: unchanged layout (column with grid, summary, buttons)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        ProductGrid(
                            products = state.products,
                            onProductClick = { product ->
                                viewModel.handleIntent(MainIntent.AddProduct(product))
                            },
                            onAddProductClick = {
                                viewModel.handleIntent(MainIntent.ShowAddProductDialog)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    EnhancedOrderSummary(
                        orderItems = state.orderItems,
                        subtotalAmount = state.getItemsSubtotal(),
                        totalAmount = state.totalAmount,
                        additionalAmounts = state.additionalAmounts,
                        isEditingSubtotal = state.isEditingSubtotal,
                        onRemoveItem = { item ->
                            viewModel.handleIntent(MainIntent.RemoveOrderItem(item))
                        },
                        onAdditionalAmounts = {
                            viewModel.handleIntent(MainIntent.ShowAdditionalAmountsDialog)
                        },
                        onStartEditingSubtotal = {
                            viewModel.handleIntent(MainIntent.StartEditingSubtotal)
                        },
                        onStopEditingSubtotal = {
                            viewModel.handleIntent(MainIntent.StopEditingSubtotal)
                        },
                        onAddCustomAmount = { amount ->
                            viewModel.handleIntent(MainIntent.AddCustomAmount(amount))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ModernPaymentButton(
                        enabled = state.canProcessPayment,
                        isLoading = state.isInitiatingPayment,
                        selectedOption = state.selectedPaymentOption,
                        onPay = { viewModel.handleIntent(MainIntent.ProcessPayment) },
                        onSelectOption = { option -> viewModel.handleIntent(MainIntent.SelectPaymentOption(option)) }
                    )
                }
            }

            // Message card overlay - floats on top
            state.message?.let { message ->
                MessageCard(
                    message = message,
                    onDismiss = { viewModel.handleIntent(MainIntent.DismissMessage) },
                    onAction = { action ->
                        when (action) {
                            MessageAction.RETRY -> viewModel.handleIntent(MainIntent.RetryConnection)
                            MessageAction.SETTINGS -> onNavigateToSettings()
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
    
    // Additional amounts dialog
    if (state.showAdditionalAmountsDialog) {
        AdditionalAmountsDialog(
            onDismiss = { viewModel.handleIntent(MainIntent.HideAdditionalAmountsDialog) },
            onConfirm = { amounts ->
                viewModel.handleIntent(MainIntent.SetAdditionalAmounts(amounts))
            }
        )
    }
    
    // Add product dialog
    if (state.showAddProductDialog) {
        AddProductDialog(
            onDismiss = { viewModel.handleIntent(MainIntent.HideAddProductDialog) },
            onConfirm = { name, price ->
                viewModel.handleIntent(MainIntent.SaveNewProduct(name, price))
            }
        )
    }
    
    // Handle navigation effects
    LaunchedEffect(state.navigationEvent) {
        state.navigationEvent?.let { event ->
            when (event) {
                is NavigationEvent.ToTransactionList -> onNavigateToTransactionList()
                is NavigationEvent.ToSettings -> onNavigateToSettings()
                is NavigationEvent.ToProgress -> onNavigateToProgress(event.transactionId)
            }
            viewModel.handleIntent(MainIntent.ClearNavigationEvent)
        }
    }
}

/**
 * ModernTopBar - Enhanced top bar with gradient background and modern styling
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModernTopBar(
    connectionState: ConnectionState,
    onSettingsClick: () -> Unit,
    onHistoryClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp),
        color = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = "TapLink POS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                // Connection status
                ConnectionStatusIndicator(connectionState = connectionState)
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                
                // Settings button
                IconButton(
                    onClick = onSettingsClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings"
                    )
                }
                
                // History button
                IconButton(
                    onClick = onHistoryClick,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = "Transaction History"
                    )
                }
            }
        }
    }
}

/**
 * EnhancedOrderSummary - Modern order summary with improved styling
 */
@Composable
private fun EnhancedOrderSummary(
    orderItems: List<OrderItem>,
    subtotalAmount: BigDecimal,
    totalAmount: BigDecimal,
    additionalAmounts: Map<String, BigDecimal>,
    isEditingSubtotal: Boolean,
    onRemoveItem: (OrderItem) -> Unit,
    onAdditionalAmounts: () -> Unit,
    onStartEditingSubtotal: () -> Unit,
    onStopEditingSubtotal: () -> Unit,
    onAddCustomAmount: (BigDecimal) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        OrderSummary(
            orderItems = orderItems,
            subtotalAmount = subtotalAmount,
            totalAmount = totalAmount,
            additionalAmounts = additionalAmounts,
            onRemoveItem = onRemoveItem,
            onAdditionalAmounts = onAdditionalAmounts,
            isEditingSubtotal = isEditingSubtotal,
            onStartEditingSubtotal = onStartEditingSubtotal,
            onStopEditingSubtotal = onStopEditingSubtotal,
            onAddCustomAmount = onAddCustomAmount,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * ModernPaymentButton - Single Pay button with dropdown to select payment option.
 * Options are grouped by Card (Sale/Auth/Forced Auth) and EBT (SNAP/VOUCHER; BENEFIT hidden, logic retained).
 * Default: Card - Sale.
 */
@Composable
private fun ModernPaymentButton(
    enabled: Boolean,
    isLoading: Boolean,
    selectedOption: PaymentOption,
    onPay: () -> Unit,
    onSelectOption: (PaymentOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // Hide EBT - BENEFIT from UI for now; logic kept for later
    val groupedOptions = PaymentOption.entries
        .filter { it != PaymentOption.EBT_BENEFIT }
        .groupBy { it.group }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Main Pay button
                Button(
                    onClick = onPay,
                    enabled = enabled && !isLoading,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                        .shadow(if (enabled && !isLoading) 4.dp else 0.dp, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Initiating...",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = selectedOption.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Dropdown toggle
                Button(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = Modifier
                        .height(56.dp)
                        .shadow(if (enabled) 4.dp else 0.dp, RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Select payment option"
                    )
                }
            }

            // Dropdown menu with grouped options
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                var isFirstGroup = true
                groupedOptions.forEach { (group, options) ->
                    if (!isFirstGroup) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                    isFirstGroup = false

                    // Group header
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
                                    fontWeight = if (option == selectedOption) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onSelectOption(option)
                                expanded = false
                            },
                            leadingIcon = if (option == selectedOption) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun MainScreenPreview() {
    TaplinkTheme {
        MainScreen(
            onNavigateToTransactionList = {},
            onNavigateToSettings = {},
            onNavigateToProgress = {}
        )
    }
}
