package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.model.Product
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import java.math.BigDecimal

/**
 * ComponentsPreviews
 * 
 * Comprehensive preview of all reusable components.
 * Used for visual verification during development.
 */
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AllComponentsPreview() {
    TaplinkTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Section: MessageCard Components
                Text(
                    text = "MessageCard Components",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                MessageCard(
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Connection Error",
                        content = "Failed to connect to payment terminal.",
                        actions = listOf(MessageAction.RETRY, MessageAction.SETTINGS)
                    ),
                    onDismiss = {},
                    onAction = {}
                )
                
                MessageCard(
                    message = Message(
                        type = MessageType.WARNING,
                        title = "Low Battery",
                        content = "Payment terminal battery is low.",
                        actions = listOf(MessageAction.DISMISS)
                    ),
                    onDismiss = {},
                    onAction = {}
                )
                
                MessageCard(
                    message = Message(
                        type = MessageType.INFO,
                        title = "Processing",
                        content = "Please wait while we process your payment.",
                        actions = listOf(MessageAction.CANCEL)
                    ),
                    onDismiss = {},
                    onAction = {}
                )
                
                MessageCard(
                    message = Message(
                        type = MessageType.SUCCESS,
                        title = "Payment Successful",
                        content = "Transaction completed successfully.",
                        actions = listOf(MessageAction.DETAILS, MessageAction.DISMISS)
                    ),
                    onDismiss = {},
                    onAction = {}
                )
                
                HorizontalDivider()
                
                // Section: ConnectionStatusIndicator Components
                Text(
                    text = "ConnectionStatusIndicator Components",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ConnectionStatusIndicator(
                            connectionState = ConnectionState.Disconnected
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectionStatusIndicator(
                            connectionState = ConnectionState.Connecting
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectionStatusIndicator(
                            connectionState = ConnectionState.Connected(
                                mode = ConnectionMode.APP_TO_APP,
                                deviceId = "DEVICE123",
                                version = "1.0.0"
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ConnectionStatusIndicator(
                            connectionState = ConnectionState.Error(
                                code = "TIMEOUT",
                                message = "Connection timeout"
                            )
                        )
                    }
                }
                
                HorizontalDivider()
                
                // Section: LoadingIndicator Components
                Text(
                    text = "LoadingIndicator Components",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        LoadingIndicator(message = "Processing payment...")
                        Spacer(modifier = Modifier.height(16.dp))
                        LoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        InlineLoadingIndicator(message = "Loading transactions...")
                        Spacer(modifier = Modifier.height(8.dp))
                        InlineLoadingIndicator()
                    }
                }
            }
        }
    }
}

/**
 * ProductCard preview
 */
@Preview(showBackground = true, widthDp = 150, heightDp = 150)
@Composable
fun ProductCardPreview() {
    TaplinkTheme {
        ProductCard(
            product = Product(
                id = "1",
                name = "Burger",
                price = BigDecimal("12.99")
            ),
            onClick = {}
        )
    }
}

/**
 * ProductGrid preview - Phone size
 */
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
fun ProductGridPhonePreview() {
    TaplinkTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ProductGrid(
                products = listOf(
                    Product("1", "Burger", BigDecimal("12.99")),
                    Product("2", "Pizza", BigDecimal("15.99")),
                    Product("3", "Salad", BigDecimal("8.99")),
                    Product("4", "Fries", BigDecimal("4.99")),
                    Product("5", "Soda", BigDecimal("2.99")),
                    Product("6", "Coffee", BigDecimal("3.99")),
                    Product("7", "Ice Cream", BigDecimal("5.99")),
                    Product("8", "Sandwich", BigDecimal("9.99")),
                    Product("9", "Pasta", BigDecimal("13.99"))
                ),
                onProductClick = {},
                onAddProductClick = {}
            )
        }
    }
}

/**
 * ProductGrid preview - Tablet size
 */
@Preview(showBackground = true, widthDp = 800, heightDp = 1280)
@Composable
fun ProductGridTabletPreview() {
    TaplinkTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            ProductGrid(
                products = listOf(
                    Product("1", "Burger", BigDecimal("12.99")),
                    Product("2", "Pizza", BigDecimal("15.99")),
                    Product("3", "Salad", BigDecimal("8.99")),
                    Product("4", "Fries", BigDecimal("4.99")),
                    Product("5", "Soda", BigDecimal("2.99")),
                    Product("6", "Coffee", BigDecimal("3.99")),
                    Product("7", "Ice Cream", BigDecimal("5.99")),
                    Product("8", "Sandwich", BigDecimal("9.99")),
                    Product("9", "Pasta", BigDecimal("13.99")),
                    Product("10", "Steak", BigDecimal("24.99")),
                    Product("11", "Chicken", BigDecimal("16.99")),
                    Product("12", "Fish", BigDecimal("18.99"))
                ),
                onProductClick = {},
                onAddProductClick = {}
            )
        }
    }
}

/**
 * Dark theme preview
 */
@Preview(showBackground = true, showSystemUi = true)
@Composable
fun AllComponentsDarkPreview() {
    TaplinkTheme(darkTheme = true) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Dark Theme Components",
                    style = MaterialTheme.typography.headlineSmall
                )
                
                MessageCard(
                    message = Message(
                        type = MessageType.ERROR,
                        title = "Connection Error",
                        content = "Failed to connect to payment terminal.",
                        actions = listOf(MessageAction.RETRY, MessageAction.SETTINGS)
                    ),
                    onDismiss = {},
                    onAction = {}
                )
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ConnectionStatusIndicator(
                            connectionState = ConnectionState.Connected(
                                mode = ConnectionMode.LAN,
                                deviceId = "DEVICE456",
                                version = "1.0.0"
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        LoadingIndicator(message = "Processing...")
                    }
                }
            }
        }
    }
}
