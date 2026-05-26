package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import java.math.BigDecimal

/**
 * Dialog for entering additional amounts (tip, tax, service fee).
 * Uses PosDialog base for consistent POS-style appearance.
 * All input fields use decimal keyboard and display $ prefix.
 * Only valid decimal values are accepted and included in the result.
 */
@Composable
fun AdditionalAmountsDialog(
    onDismiss: () -> Unit,
    onConfirm: (Map<String, BigDecimal>) -> Unit
) {
    var tip by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var serviceFee by remember { mutableStateOf("") }
    var surcharge by remember { mutableStateOf("") }

    PosDialog(
        onDismissRequest = onDismiss,
        title = "Additional Amounts",
        subtitle = "Add optional fees to this transaction",
        icon = Icons.Default.AttachMoney,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
        confirmText = "Apply",
        onConfirm = {
            val amounts = mutableMapOf<String, BigDecimal>()
            tip.toBigDecimalOrNull()?.let { if (it > BigDecimal.ZERO) amounts["Tip"] = it }
            tax.toBigDecimalOrNull()?.let { if (it > BigDecimal.ZERO) amounts["Tax"] = it }
            serviceFee.toBigDecimalOrNull()?.let { if (it > BigDecimal.ZERO) amounts["Service Fee"] = it }
            surcharge.toBigDecimalOrNull()?.let { if (it > BigDecimal.ZERO) amounts["Surcharge"] = it }
            onConfirm(amounts)
        },
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        AmountInputField(value = tip, onValueChange = { tip = it }, label = "Tip")
        Spacer(modifier = Modifier.height(12.dp))
        AmountInputField(value = tax, onValueChange = { tax = it }, label = "Tax")
        Spacer(modifier = Modifier.height(12.dp))
        AmountInputField(value = serviceFee, onValueChange = { serviceFee = it }, label = "Service Fee")
        Spacer(modifier = Modifier.height(12.dp))
        AmountInputField(value = surcharge, onValueChange = { surcharge = it }, label = "Surcharge")
    }
}

/**
 * Reusable amount input field with $ prefix and decimal validation.
 */
@Composable
private fun AmountInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        prefix = { Text("$") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Preview(showBackground = true)
@Composable
private fun AdditionalAmountsDialogPreview() {
    TaplinkTheme {
        AdditionalAmountsDialog(
            onDismiss = {},
            onConfirm = {}
        )
    }
}
