package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal

/**
 * AddProductDialog - Dialog for adding new products to the catalog.
 * Uses PosDialog base for consistent POS-style appearance.
 *
 * Features:
 * - Product name input with validation
 * - Price input with decimal validation
 * - Save and cancel actions
 *
 * @param onDismiss Callback when dialog is dismissed
 * @param onConfirm Callback when product is confirmed (name, price)
 */
@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, BigDecimal) -> Unit
) {
    var productName by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf(false) }
    var priceError by remember { mutableStateOf(false) }

    PosDialog(
        onDismissRequest = onDismiss,
        title = "New Product",
        subtitle = "Add a product to your catalog",
        icon = Icons.Default.AddCircle,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
        confirmText = "Save",
        onConfirm = {
            val isNameValid = productName.isNotBlank()
            val isPriceValid = priceText.isNotBlank() &&
                priceText.toBigDecimalOrNull() != null &&
                priceText.toBigDecimal() > BigDecimal.ZERO
            nameError = !isNameValid
            priceError = !isPriceValid
            if (isNameValid && isPriceValid) {
                onConfirm(productName.trim(), priceText.toBigDecimal())
            }
        },
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = productName,
            onValueChange = {
                productName = it
                nameError = false
            },
            label = { Text("Product Name") },
            placeholder = { Text("e.g. Espresso") },
            isError = nameError,
            supportingText = if (nameError) {
                { Text("Product name is required") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = priceText,
            onValueChange = {
                if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                    priceText = it
                    priceError = false
                }
            },
            label = { Text("Price") },
            placeholder = { Text("0.00") },
            isError = priceError,
            supportingText = if (priceError) {
                { Text("Valid price is required") }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            prefix = { Text("$") },
            shape = MaterialTheme.shapes.medium
        )
    }
}
