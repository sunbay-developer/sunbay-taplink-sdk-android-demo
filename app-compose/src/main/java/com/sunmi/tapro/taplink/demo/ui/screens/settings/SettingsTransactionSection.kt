package com.sunmi.tapro.taplink.demo.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.ui.components.PosDialog
import com.sunmi.tapro.taplink.demo.ui.utils.rememberDebouncedClick
import com.sunmi.tapro.taplink.demo.util.TipConfigPreferences

// ─── Helper functions ──────────────────────────────────────────────────────────

internal fun getPrintReceiptLabel(option: PrintReceipt): String = when (option) {
    PrintReceipt.NONE -> "None"
    PrintReceipt.MERCHANT -> "Merchant Copy"
    PrintReceipt.CUSTOMER -> "Customer Copy"
    PrintReceipt.BOTH -> "Both Copies"
}

internal fun getPrintReceiptDescription(option: PrintReceipt): String = when (option) {
    PrintReceipt.NONE -> "No receipt will be printed"
    PrintReceipt.MERCHANT -> "Print merchant copy only"
    PrintReceipt.CUSTOMER -> "Print customer copy only"
    PrintReceipt.BOTH -> "Print both merchant and customer copies"
}

internal fun getSignatureModeLabel(option: SignatureMode): String = when (option) {
    SignatureMode.ON_SCREEN -> "ON_SCREEN"
    SignatureMode.ON_RECEIPT -> "ON_RECEIPT"
    SignatureMode.NONE -> "NONE"
}

internal fun getSignatureModeDescription(option: SignatureMode): String = when (option) {
    SignatureMode.ON_SCREEN -> "Collect signature on terminal screen"
    SignatureMode.ON_RECEIPT -> "Collect signature on printed receipt"
    SignatureMode.NONE -> "Do not collect a signature for the transaction"
}

// ─── Section content composables ──────────────────────────────────────────────

@Composable
internal fun TransactionAmountSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    TipConfigSectionContent(state = state, onIntent = onIntent)
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))
    TaxConfigSectionContent(state = state, onIntent = onIntent)
}

@Composable
internal fun ParameterConfigurationSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    PrintingSectionContent(state = state, onIntent = onIntent)
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))
    SignatureSectionContent(state = state, onIntent = onIntent)
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))
    StaffInfoSectionContent(state = state, onIntent = onIntent)
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))
    SecondaryDisplaySectionContent(state = state, onIntent = onIntent)
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))
    SdkSectionContent(state = state, onIntent = onIntent)
}

@Composable
private fun StaffInfoSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Text("Staff Information", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Optional staff identifiers included in SALE requests.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    OutlinedTextField(
        value = state.staffOperatorId,
        onValueChange = { onIntent(SettingsIntent.UpdateStaffOperatorId(it)) },
        label = { Text("Operator ID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.staffTipRecipientId,
        onValueChange = { onIntent(SettingsIntent.UpdateStaffTipRecipientId(it)) },
        label = { Text("Tip Recipient ID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun TaxConfigSectionContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit) {
    Text(
        text = "Tax Configuration",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Configure the tax rate applied automatically to each order subtotal.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))

    // Enable/Disable Tax
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable Tax", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Automatically add tax to each order based on the rate below",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = state.taxConfigEnabled,
            onCheckedChange = { onIntent(SettingsIntent.UpdateTaxConfigEnabled(it)) }
        )
    }

    AnimatedVisibility(
        visible = state.taxConfigEnabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = if (state.taxRate == 0) "" else state.taxRate.toString(),
                onValueChange = { text ->
                    val parsed = text.filter { it.isDigit() }.toIntOrNull() ?: 0
                    onIntent(SettingsIntent.UpdateTaxRate(parsed.coerceIn(0, 100)))
                },
                label = { Text("Tax Rate") },
                suffix = { Text("%") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Enter a value between 0 and 100") },
                shape = MaterialTheme.shapes.medium
            )
        }
    }
}

/**
 * Printing section: receipt copies selection as an inline radio list.
 */
@Composable
private fun PrintingSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Text(
        text = "Receipt Copies",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Select which receipt copies to print after each transaction.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))
    PrintReceipt.values().forEach { option ->
        val isSelected = state.printReceipt == option
        Surface(
            onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdatePrintReceipt(option)) },
            color = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                Color.Transparent
            },
            shape = MaterialTheme.shapes.small
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = { onIntent(SettingsIntent.UpdatePrintReceipt(option)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = getPrintReceiptLabel(option),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                    Text(
                        text = getPrintReceiptDescription(option),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SignatureSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Text(
        text = "Signature (Sale/Auth)",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Turn on the switch to include signatureConfig in Sale/Auth requests.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable Signature Configuration", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (state.signatureEnabled) {
                    "Enabled — signatureConfig will be included"
                } else {
                    "Disabled — signatureConfig will not be included"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = state.signatureEnabled,
            onCheckedChange = { onIntent(SettingsIntent.UpdateSignatureEnabled(it)) }
        )
    }

    AnimatedVisibility(
        visible = state.signatureEnabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Local Signature Configuration", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (!state.signatureUseHostConfig) {
                            "Enabled — local signature options are used for transactions"
                        } else {
                            "Disabled — terminal signature configuration is used"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = !state.signatureUseHostConfig,
                    onCheckedChange = {
                        onIntent(SettingsIntent.UpdateSignatureUseHostConfig(!it))
                    }
                )
            }

            AnimatedVisibility(
                visible = !state.signatureUseHostConfig,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        SignatureMode.values().forEach { option ->
                    val isSelected = state.signatureMode == option
                    Surface(
                        onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateSignatureMode(option)) },
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onIntent(SettingsIntent.UpdateSignatureMode(option)) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = getSignatureModeLabel(option),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                                Text(
                                    text = getSignatureModeDescription(option),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                    }
                }

                AnimatedVisibility(visible = state.signatureMode != SignatureMode.NONE) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Amount Threshold", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Require signature only when final amount exceeds the threshold",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.signatureThresholdEnabled,
                                onCheckedChange = {
                                    onIntent(SettingsIntent.UpdateSignatureThresholdEnabled(it))
                                }
                            )
                        }

                        AnimatedVisibility(visible = state.signatureThresholdEnabled) {
                            OutlinedTextField(
                                value = state.signatureThreshold,
                                onValueChange = {
                                    onIntent(SettingsIntent.UpdateSignatureThreshold(it))
                                },
                                label = { Text("Threshold (smallest currency unit)") },
                                supportingText = {
                                    Text("Example: 5000 means USD 50.00")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.medium
                            )
                        }
                    }
                }
                }
            }
        }
    }
}

/**
 * Tip Configuration section: enable/disable tipConfig for SALE and POST_AUTH transactions.
 * Allows configuring on-screen tip, tip mode, tip with tax, and suggestion values.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TipConfigSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Text(
        text = "Tip Configuration",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Configure on-screen tip for SALE and POST_AUTH transactions. Only applies when tipAmount is not set.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(10.dp))

    // Enable/Disable tipConfig
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Enable Tip Config", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Send tipConfig in SALE and POST_AUTH requests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = state.tipConfigEnabled,
            onCheckedChange = { onIntent(SettingsIntent.UpdateTipConfigEnabled(it)) }
        )
    }

    AnimatedVisibility(
        visible = state.tipConfigEnabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Terminal Configuration", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "On = terminal decides the tip prompt; Off = use the options below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.tipUseHostConfig,
                    onCheckedChange = { onIntent(SettingsIntent.UpdateTipUseHostConfig(it)) }
                )
            }

            AnimatedVisibility(
                visible = !state.tipUseHostConfig,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                Spacer(modifier = Modifier.height(12.dp))

                // On-Screen Tip toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("On-Screen Tip", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "On: tip is captured on the terminal screen. Off: with suggestions enabled the amounts are printed on the receipt instead",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.tipOnScreenTip,
                        onCheckedChange = { onIntent(SettingsIntent.UpdateTipOnScreenTip(it)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Include tipMode Field", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "When off, tipConfig will omit tipMode in Cloud request body",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.tipIncludeModeField,
                        onCheckedChange = { onIntent(SettingsIntent.UpdateTipIncludeModeField(it)) }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tip Mode selection
                Text(
                    text = "Tip Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val selectedContainer = MaterialTheme.colorScheme.primary
                    val selectedContent = MaterialTheme.colorScheme.onPrimary
                    val normalContainer = MaterialTheme.colorScheme.surfaceVariant
                    val normalContent = MaterialTheme.colorScheme.onSurfaceVariant
                    Button(
                        onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateTipMode(TipConfigPreferences.TipMode.ON_SALE)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.tipMode == TipConfigPreferences.TipMode.ON_SALE) selectedContainer else normalContainer,
                            contentColor = if (state.tipMode == TipConfigPreferences.TipMode.ON_SALE) selectedContent else normalContent
                        )
                    ) { Text("ON_SALE", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    Button(
                        onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateTipMode(TipConfigPreferences.TipMode.AFTER_SALE)) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.tipMode == TipConfigPreferences.TipMode.AFTER_SALE) selectedContainer else normalContainer,
                            contentColor = if (state.tipMode == TipConfigPreferences.TipMode.AFTER_SALE) selectedContent else normalContent
                        )
                    ) { Text("AFTER_SALE", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tip With Tax toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tip With Tax", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Include tax in tip calculation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.tipWithTax,
                        onCheckedChange = { onIntent(SettingsIntent.UpdateTipWithTax(it)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Suggestions section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tip Suggestions", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Preset tip amounts. Shown on the tip screen, or printed on the receipt when On-Screen Tip is off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.tipSuggestionsEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.UpdateTipSuggestionsEnabled(it)) }
                    )
                }

                AnimatedVisibility(
                    visible = state.tipSuggestionsEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))

                        // Fee Mode selection
                        Text(
                            text = "Fee Mode",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val selectedContainer = MaterialTheme.colorScheme.primary
                            val selectedContent = MaterialTheme.colorScheme.onPrimary
                            val normalContainer = MaterialTheme.colorScheme.surfaceVariant
                            val normalContent = MaterialTheme.colorScheme.onSurfaceVariant
                            Button(
                                onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateTipFeeMode(TipConfigPreferences.FeeMode.RATE)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.tipFeeMode == TipConfigPreferences.FeeMode.RATE) selectedContainer else normalContainer,
                                    contentColor = if (state.tipFeeMode == TipConfigPreferences.FeeMode.RATE) selectedContent else normalContent
                                )
                            ) { Text("RATE (%)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                            Button(
                                onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateTipFeeMode(TipConfigPreferences.FeeMode.AMOUNT)) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.tipFeeMode == TipConfigPreferences.FeeMode.AMOUNT) selectedContainer else normalContainer,
                                    contentColor = if (state.tipFeeMode == TipConfigPreferences.FeeMode.AMOUNT) selectedContent else normalContent
                                )
                            ) { Text("AMOUNT ($)", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Three suggestion value fields
                        val suffixText = if (state.tipFeeMode == TipConfigPreferences.FeeMode.RATE) "%" else "¢"
                        val labels = listOf("Suggestion 1", "Suggestion 2", "Suggestion 3")
                        val values = listOf(state.tipSuggestionValue1, state.tipSuggestionValue2, state.tipSuggestionValue3)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            values.forEachIndexed { index, value ->
                                OutlinedTextField(
                                    value = if (value == 0) "" else value.toString(),
                                    onValueChange = { text ->
                                        val parsed = text.filter { it.isDigit() }.toIntOrNull() ?: 0
                                        onIntent(SettingsIntent.UpdateTipSuggestionValue(index, parsed))
                                    },
                                    label = { Text(labels[index], fontSize = 10.sp) },
                                    suffix = { Text(suffixText) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = MaterialTheme.shapes.medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Three suggestion name fields
                        val nameLabels = listOf("Name 1", "Name 2", "Name 3")
                        val names = listOf(state.tipSuggestionName1, state.tipSuggestionName2, state.tipSuggestionName3)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            names.forEachIndexed { index, name ->
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { text ->
                                        onIntent(SettingsIntent.UpdateTipSuggestionName(index, text.take(50)))
                                    },
                                    label = { Text(nameLabels[index], fontSize = 10.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium
                                )
                            }
                        }
                    }
                }

                }
            }
        }
    }
}

/**
 * SDK section: App ID / Merchant ID / Secret Key fields + apply button, all inline.
 */
@Composable
private fun SdkSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    var secretKeyVisible by remember { mutableStateOf(false) }

    Text(
        text = "SDK Initialization Parameters",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Used to initialize TaplinkSDK. Changes take effect after tapping \"Save & Apply\".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Environment",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val selectedContainer = MaterialTheme.colorScheme.primary
        val selectedContent = MaterialTheme.colorScheme.onPrimary
        val normalContainer = MaterialTheme.colorScheme.surfaceVariant
        val normalContent = MaterialTheme.colorScheme.onSurfaceVariant
        Button(
            onClick = rememberDebouncedClick { onIntent(SettingsIntent.SwitchSdkEnvironment(SdkEnvironment.UAT)) },
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.sdkEnvironment == SdkEnvironment.UAT) selectedContainer else normalContainer,
                contentColor = if (state.sdkEnvironment == SdkEnvironment.UAT) selectedContent else normalContent
            )
        ) { Text("UAT", fontWeight = FontWeight.Bold) }
        Button(
            onClick = rememberDebouncedClick { onIntent(SettingsIntent.SwitchSdkEnvironment(SdkEnvironment.PROD)) },
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.sdkEnvironment == SdkEnvironment.PROD) selectedContainer else normalContainer,
                contentColor = if (state.sdkEnvironment == SdkEnvironment.PROD) selectedContent else normalContent
            )
        ) { Text("PROD", fontWeight = FontWeight.Bold) }
    }
    Spacer(modifier = Modifier.height(12.dp))

    OutlinedTextField(
        value = state.sdkAppId,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkAppId(it)) },
        label = { Text("App ID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Required") },
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sdkMerchantId,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkMerchantId(it)) },
        label = { Text("Merchant ID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Optional — validated only when provided") },
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sdkSecretKey,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkSecretKey(it)) },
        label = { Text("Secret Key") },
        placeholder = {
            if (state.hasExistingSecretKey && state.sdkSecretKey.isEmpty()) {
                Text("••••••••")
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (secretKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { secretKeyVisible = !secretKeyVisible }) {
                Icon(
                    imageVector = if (secretKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = if (secretKeyVisible) "Hide" else "Show"
                )
            }
        },
        supportingText = {
            Text(
                if (state.hasExistingSecretKey && state.sdkSecretKey.isEmpty()) {
                    "Existing key hidden. Enter new key to update."
                } else {
                    "Can be empty for some environments"
                }
            )
        },
        shape = MaterialTheme.shapes.medium
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
        onClick = rememberDebouncedClick { onIntent(SettingsIntent.ApplySdkConfig) },
        modifier = Modifier.fillMaxWidth(),
        enabled = state.isSdkConfigValid() && !state.isApplyingSdkConfig
    ) {
        if (state.isApplyingSdkConfig) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text("Save & Apply")
    }
}

@Composable
fun PrintReceiptConfigDialog(
    selectedReceipt: PrintReceipt,
    onReceiptChange: (PrintReceipt) -> Unit,
    onDismiss: () -> Unit
) {
    PosDialog(
        onDismissRequest = onDismiss,
        title = "Receipt Configuration",
        subtitle = "Select which receipt copies to print",
        icon = Icons.Default.Receipt,
        iconTint = MaterialTheme.colorScheme.primary,
        iconBackgroundColor = MaterialTheme.colorScheme.primaryContainer,
        confirmText = "Done",
        onConfirm = onDismiss,
        dismissText = null
    ) {
        PrintReceipt.values().forEach { option ->
            Surface(
                onClick = rememberDebouncedClick { onReceiptChange(option) },
                shape = MaterialTheme.shapes.medium,
                color = if (selectedReceipt == option) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                } else {
                    Color.Transparent
                }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedReceipt == option,
                        onClick = { onReceiptChange(option) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = getPrintReceiptLabel(option),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = getPrintReceiptDescription(option),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SdkParamsConfigDialog(
    appId: String,
    merchantId: String,
    secretKey: String,
    hasExistingSecretKey: Boolean,
    isApplying: Boolean,
    isValid: Boolean,
    onAppIdChange: (String) -> Unit,
    onMerchantIdChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    PosDialog(
        onDismissRequest = onDismiss,
        title = "SDK Parameters",
        subtitle = "Used to initialize TaplinkSDK. Takes effect after save.",
        icon = Icons.Default.Settings,
        iconTint = MaterialTheme.colorScheme.secondary,
        iconBackgroundColor = MaterialTheme.colorScheme.surfaceVariant,
        confirmText = "Save & Apply",
        onConfirm = onApply,
        confirmEnabled = isValid,
        confirmLoading = isApplying,
        dismissText = "Cancel",
        onDismiss = onDismiss
    ) {
        OutlinedTextField(
            value = appId,
            onValueChange = onAppIdChange,
            label = { Text("App ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Required") },
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = merchantId,
            onValueChange = onMerchantIdChange,
            label = { Text("Merchant ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            supportingText = { Text("Required") },
            shape = MaterialTheme.shapes.medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = secretKey,
            onValueChange = onSecretKeyChange,
            label = { Text("Secret Key") },
            placeholder = {
                if (hasExistingSecretKey && secretKey.isEmpty()) Text("••••••••")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            supportingText = {
                Text(
                    if (hasExistingSecretKey && secretKey.isEmpty()) {
                        "Existing key hidden. Enter new key to update."
                    } else {
                        "Can be empty for some environments"
                    }
                )
            },
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
fun PrintReceiptConfigurationCard(
    isExpanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    selectedReceipt: PrintReceipt,
    onReceiptChange: (PrintReceipt) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = rememberDebouncedClick { onExpandChange(!isExpanded) }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Print Receipt Configuration",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current: ${getPrintReceiptLabel(selectedReceipt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    PrintReceipt.values().forEach { option ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable(onClick = rememberDebouncedClick { onReceiptChange(option) }).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = selectedReceipt == option, onClick = { onReceiptChange(option) })
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(getPrintReceiptLabel(option), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(getPrintReceiptDescription(option), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
