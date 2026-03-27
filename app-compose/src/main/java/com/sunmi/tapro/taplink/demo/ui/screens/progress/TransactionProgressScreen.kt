package com.sunmi.tapro.taplink.demo.ui.screens.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.model.TransactionType
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig
import com.sunmi.tapro.taplink.demo.util.AmountFormatter
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
    
    // Load transaction on first composition
    LaunchedEffect(transactionId) {
        viewModel.handleIntent(TransactionProgressIntent.LoadTransaction(transactionId))
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
                title = { Text("Transaction Progress") },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                        text = state.transactionType,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = AmountFormatter.format(state.amount),
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
                        state.isPending() -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(64.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 6.dp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = state.getStatusDisplayText(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        state.isSuccess() -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Success",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = state.getStatusDisplayText(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        
                        state.isFailed() -> {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Failed",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = state.getStatusDisplayText(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            
                            if (state.errorMessage.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = state.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 32.dp)
                                )
                            }
                        }
                        
                        state.isCancelled() -> {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = "Cancelled",
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Text(
                                text = state.getStatusDisplayText(),
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                // Lower section: Action buttons (horizontal in landscape)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (screenConfig.isLandscape) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            if (state.isPending()) {
                                OutlinedButton(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.AbortTransaction) },
                                    modifier = Modifier.height(56.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Abort Transaction",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (state.showViewDetailsButton) {
                                Button(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.NavigateToDetail) },
                                    modifier = Modifier.height(56.dp),
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
                            if (state.isComplete() && !state.isSuccess() && state.transaction?.type != TransactionType.BATCH_CLOSE) {
                                Button(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.QueryTransaction) },
                                    modifier = Modifier.height(56.dp),
                                    enabled = !state.isQuerying,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    if (state.isQuerying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (state.isQuerying) "Querying..." else "Query Transaction",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            if (state.canRetry) {
                                Button(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.RetryTransaction) },
                                    modifier = Modifier.height(56.dp),
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
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (state.isPending()) {
                                OutlinedButton(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.AbortTransaction) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Abort Transaction",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (state.showViewDetailsButton) {
                                Button(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.NavigateToDetail) },
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
                            if (state.isComplete() && !state.isSuccess() && state.transaction?.type != TransactionType.BATCH_CLOSE) {
                                Button(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.QueryTransaction) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    enabled = !state.isQuerying,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    if (state.isQuerying) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (state.isQuerying) "Querying..." else "Query Transaction",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                            if (state.canRetry) {
                                Button(
                                    onClick = { viewModel.handleIntent(TransactionProgressIntent.RetryTransaction) },
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
fun TransactionProgressScreenCancelledPreview() {
    TaplinkTheme {
        TransactionProgressScreenPreview(
            transactionType = "SALE",
            amount = BigDecimal("15.25"),
            status = TransactionStatus.CANCELLED,
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
                    
                    previewState.isCancelled() -> {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Cancelled",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = previewState.getStatusDisplayText(),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFFFF9800),
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
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
