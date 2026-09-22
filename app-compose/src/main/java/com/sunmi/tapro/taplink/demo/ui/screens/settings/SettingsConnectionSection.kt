package com.sunmi.tapro.taplink.demo.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.ui.utils.rememberDebouncedClick
import com.sunmi.tapro.taplink.demo.util.CloudPreferences
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences

// ─── Helper functions ──────────────────────────────────────────────────────────

internal fun getModeName(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.ON_DEVICE -> "On-Device"
    ConnectionMode.CROSS_DEVICE -> "Cross-Device"
    ConnectionMode.SUB_SCREEN -> "Sub-Screen"
    ConnectionMode.CLOUD -> "Cloud"
    ConnectionMode.APP_TO_APP -> "App to App (legacy)"
    ConnectionMode.CABLE -> "Cable (legacy)"
    ConnectionMode.LAN -> "LAN (legacy)"
}

internal fun getModeDescription(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.ON_DEVICE -> "Merchant app + Tapro run on the same terminal (recommended)"
    ConnectionMode.CROSS_DEVICE -> "Tapro acts as a standalone payment terminal over LAN or Cable"
    ConnectionMode.SUB_SCREEN -> "VSP connection with sub-screen"
    ConnectionMode.CLOUD -> "Cloud API connection"
    ConnectionMode.APP_TO_APP -> "Direct connection via Tapro app (use On-Device instead)"
    ConnectionMode.CABLE -> "Direct USB/serial connection (use Cross-Device instead)"
    ConnectionMode.LAN -> "Network connection (use Cross-Device instead)"
}

internal fun getModeIcon(mode: ConnectionMode): ImageVector = when (mode) {
    ConnectionMode.ON_DEVICE -> Icons.Default.PhoneAndroid
    ConnectionMode.CROSS_DEVICE -> Icons.Default.Cable
    ConnectionMode.CLOUD -> Icons.Default.Cloud
    ConnectionMode.SUB_SCREEN -> Icons.Default.Print
    ConnectionMode.APP_TO_APP -> Icons.Default.PhoneAndroid
    ConnectionMode.CABLE -> Icons.Default.Cable
    ConnectionMode.LAN -> Icons.Default.Wifi
}

private fun getCrossDeviceStrategyLabel(strategy: ConnectionPreferences.CrossDeviceStrategy): String = when (strategy) {
    ConnectionPreferences.CrossDeviceStrategy.LAN -> "LAN"
    ConnectionPreferences.CrossDeviceStrategy.CABLE -> "CABLE"
}

private fun getCrossDeviceStrategyDescription(strategy: ConnectionPreferences.CrossDeviceStrategy): String = when (strategy) {
    ConnectionPreferences.CrossDeviceStrategy.LAN -> "Connect over local area network"
    ConnectionPreferences.CrossDeviceStrategy.CABLE -> "Connect over USB/serial cable"
}


private fun getCableProtocolLabel(protocol: ConnectionPreferences.CableProtocol): String = when (protocol) {
    ConnectionPreferences.CableProtocol.USB_AOA -> "USB_AOA (USB Android Open Accessory)"
    ConnectionPreferences.CableProtocol.USB_VSP -> "USB_VSP (USB Virtual Serial Port)"
    ConnectionPreferences.CableProtocol.RS232 -> "RS232 (Serial)"
}

private fun getCableProtocolDescription(protocol: ConnectionPreferences.CableProtocol): String = when (protocol) {
    ConnectionPreferences.CableProtocol.USB_AOA -> "USB Android Open Accessory 2.0"
    ConnectionPreferences.CableProtocol.USB_VSP -> "USB Virtual Serial Port"
    ConnectionPreferences.CableProtocol.RS232 -> "Standard RS232 serial"
}

private fun maskValue(value: String): String =
    if (value.length > 4) value.take(4) + "****" else "****"

// ─── Section content composables ──────────────────────────────────────────────

/**
 * Connection section: mode dropdown + mode-specific configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    var modeDropdownExpanded by remember { mutableStateOf(false) }

    Text(
        text = "Connection Mode",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(8.dp))

    ExposedDropdownMenuBox(
        expanded = modeDropdownExpanded,
        onExpandedChange = { modeDropdownExpanded = it }
    ) {
        OutlinedTextField(
            value = getModeName(state.selectedMode),
            onValueChange = {},
            readOnly = true,
            label = { Text("Mode") },
            leadingIcon = {
                Icon(
                    imageVector = getModeIcon(state.selectedMode),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modeDropdownExpanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(
            expanded = modeDropdownExpanded,
            onDismissRequest = { modeDropdownExpanded = false }
        ) {
            ConnectionMode.values().forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = getModeIcon(mode),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (state.selectedMode == mode) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Column {
                                Text(
                                    text = getModeName(mode),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (state.selectedMode == mode) FontWeight.SemiBold else FontWeight.Normal
                                )
                                Text(
                                    text = getModeDescription(mode),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = {
                        onIntent(SettingsIntent.SelectMode(mode))
                        modeDropdownExpanded = false
                    },
                    trailingIcon = {
                        if (state.selectedMode == mode) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))

    // Mode-specific configuration
    when (state.selectedMode) {
        ConnectionMode.ON_DEVICE -> AppToAppConfiguration(
            appToAppMode = state.appToAppMode,
            onAppToAppModeChange = { onIntent(SettingsIntent.UpdateAppToAppMode(it)) }
        )
        ConnectionMode.CROSS_DEVICE -> CrossDeviceConfiguration(
            state = state,
            onIntent = onIntent
        )
        ConnectionMode.APP_TO_APP -> AppToAppConfiguration(
            appToAppMode = state.appToAppMode,
            onAppToAppModeChange = { onIntent(SettingsIntent.UpdateAppToAppMode(it)) },
            legacy = true
        )
        ConnectionMode.CABLE -> CableConfiguration(
            cableProtocol = state.cableProtocol,
            onCableProtocolChange = { onIntent(SettingsIntent.UpdateCableProtocol(it)) }
        )
        ConnectionMode.LAN -> LanConfiguration(
            ipAddress = state.ipAddress,
            port = state.port,
            isAutoDiscovering = state.isAutoDiscovering,
            isQrScanning = state.isQrScanning,
            onIpAddressChange = { onIntent(SettingsIntent.UpdateIpAddress(it)) },
            onPortChange = { onIntent(SettingsIntent.UpdatePort(it)) },
            onAutoDiscover = { onIntent(SettingsIntent.AutoDiscoverAndConnect) },
            onScanQr = { onIntent(SettingsIntent.ScanQrAndConnect) }
        )
        ConnectionMode.CLOUD -> CloudConfiguration(
            state = state,
            onValueChange = { field, value ->
                val intent = when (field) {
                    "apiKey" -> SettingsIntent.UpdateCloudApiKey(value)
                    "baseUrl" -> SettingsIntent.UpdateCloudBaseUrl(value)
                    "terminalSn" -> SettingsIntent.UpdateCloudTerminalSn(value)
                    "merchantId" -> SettingsIntent.UpdateCloudMerchantId(value)
                    "appId" -> SettingsIntent.UpdateCloudAppId(value)
                    "notifyUrl" -> SettingsIntent.UpdateCloudNotifyUrl(value)
                    "terminalEventNotifyUrl" -> SettingsIntent.UpdateCloudTerminalEventNotifyUrl(value)
                    else -> null
                }
                intent?.let { onIntent(it) }
            },
            onAddOption = { field, value -> onIntent(SettingsIntent.AddCloudOption(field, value)) },
            onPushToTerminalChange = { onIntent(SettingsIntent.UpdateCloudPushToTerminal(it)) }
        )
        ConnectionMode.SUB_SCREEN -> SubScreenModeConfiguration(
            state = state,
            onIntent = onIntent
        )
    }

    Spacer(modifier = Modifier.height(20.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(16.dp))
    SdkSectionContent(state = state, onIntent = onIntent)
}

@Composable
internal fun SecondaryDisplaySectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Text(
        text = "Secondary Display",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "When enabled, advertisement images and tip selection are pushed to the secondary display (USB screen player).",
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
            Text("Enable Secondary Display Ad", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Show ad carousel and order info on the secondary screen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = state.secondaryDisplayAdEnabled,
            onCheckedChange = { onIntent(SettingsIntent.UpdateSecondaryDisplayAdEnabled(it)) }
        )
    }
}

@Composable
fun CrossDeviceConfiguration(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Column {
        Text(
            "Cross-Device Configuration",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Connection Strategy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))

        ConnectionPreferences.CrossDeviceStrategy.values().forEach { strategy ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateCrossDeviceStrategy(strategy)) })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.crossDeviceStrategy == strategy,
                    onClick = { onIntent(SettingsIntent.UpdateCrossDeviceStrategy(strategy)) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(getCrossDeviceStrategyLabel(strategy), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        getCrossDeviceStrategyDescription(strategy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.crossDeviceStrategy == ConnectionPreferences.CrossDeviceStrategy.LAN) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.crossDeviceIpAddress,
                onValueChange = { onIntent(SettingsIntent.UpdateCrossDeviceIpAddress(it)) },
                label = { Text("IP Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Terminal IP address for LAN strategy") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.crossDevicePort,
                onValueChange = { onIntent(SettingsIntent.UpdateCrossDevicePort(it)) },
                label = { Text("Port") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Port number for LAN strategy") }
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Cable Protocol",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            ConnectionPreferences.CableProtocol.values().forEach { protocol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = rememberDebouncedClick { onIntent(SettingsIntent.UpdateCrossDeviceCableProtocol(protocol)) })
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.crossDeviceCableProtocol == protocol,
                        onClick = { onIntent(SettingsIntent.UpdateCrossDeviceCableProtocol(protocol)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(getCableProtocolLabel(protocol), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            getCableProtocolDescription(protocol),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "ℹ️ Cross-Device Mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Select how the payment terminal is reached. This mode is the modern replacement for legacy LAN/CABLE settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SubScreenModeConfiguration(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Column {
        Text(
            "Sub-Screen Configuration",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "✓ VSP + Sub-Screen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This mode connects via USB Virtual Serial Port (VSP) and automatically " +
                        "opens the sub-screen on the payment terminal. No additional " +
                        "configuration is required.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Enable Secondary Display Ad",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Show ad carousel and order info on the secondary screen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    Switch(
                        checked = state.secondaryDisplayAdEnabled,
                        onCheckedChange = { onIntent(SettingsIntent.UpdateSecondaryDisplayAdEnabled(it)) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Requirements:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "• Payment terminal must support secondary display\n" +
                        "• USB cable must be connected between devices\n" +
                        "• Terminal must have VSP driver enabled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun SdkCredentialsCard(
    appId: String,
    merchantId: String,
    secretKey: String,
    hasExistingSecretKey: Boolean,
    isApplying: Boolean,
    isValid: Boolean,
    onAppIdChange: (String) -> Unit,
    onMerchantIdChange: (String) -> Unit,
    onSecretKeyChange: (String) -> Unit,
    onApply: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Taplink SDK Parameters", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text("These parameters are used to initialize TaplinkSDK.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = appId, onValueChange = onAppIdChange, label = { Text("App ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true, supportingText = { Text("Required") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = merchantId, onValueChange = onMerchantIdChange, label = { Text("Merchant ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true, supportingText = { Text("Optional — validated only when provided") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = secretKey, onValueChange = onSecretKeyChange, label = { Text("Secret Key") }, placeholder = { if (hasExistingSecretKey && secretKey.isEmpty()) Text("••••••••") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), supportingText = { Text(if (hasExistingSecretKey && secretKey.isEmpty()) "Existing key hidden." else "Can be empty for some environments") })
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = rememberDebouncedClick(onClick = onApply), modifier = Modifier.fillMaxWidth(), enabled = isValid && !isApplying) {
                if (isApplying) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary); Spacer(modifier = Modifier.width(8.dp)) }
                Text("Save & Apply SDK Params")
            }
        }

    }
}

@Composable
fun AppToAppConfiguration(
    appToAppMode: ConnectionPreferences.AppToAppMode,
    onAppToAppModeChange: (ConnectionPreferences.AppToAppMode) -> Unit,
    legacy: Boolean = false
) {
    Column {
        Text(
            if (legacy) "App-to-App Configuration (legacy)" else "On-Device Configuration",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✓ Direct Connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (legacy) {
                        "App-to-App mode provides direct communication with the Tapro payment app via Android IPC. " +
                            "This is the classic name for the current On-Device integration mode; prefer the " +
                            "\"On-Device\" mode entry for new integrations. No additional configuration is required."
                    } else {
                        "On-Device mode connects directly to the Tapro payment app running on the SAME terminal " +
                            "via Android IPC (internally implemented as App-to-App). No additional configuration is required."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Transaction Mode:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = rememberDebouncedClick { onAppToAppModeChange(ConnectionPreferences.AppToAppMode.CUSTOM) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = appToAppMode == ConnectionPreferences.AppToAppMode.CUSTOM,
                        onClick = { onAppToAppModeChange(ConnectionPreferences.AppToAppMode.CUSTOM) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("CUSTOM (Default)", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                        Text("Tapro shows full foreground payment UI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Requirements:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text("• Tapro app must be installed on this device\n• Both apps must be running on the same device\n• No network or cable connection needed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
fun CableConfiguration(
    cableProtocol: ConnectionPreferences.CableProtocol,
    onCableProtocolChange: (ConnectionPreferences.CableProtocol) -> Unit
) {
    Column {
        Text("Cable Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connection Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        ConnectionPreferences.CableProtocol.values().forEach { protocol ->
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = rememberDebouncedClick { onCableProtocolChange(protocol) }).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = cableProtocol == protocol, onClick = { onCableProtocolChange(protocol) })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(getCableProtocolLabel(protocol), style = MaterialTheme.typography.bodyLarge)
                    Text(getCableProtocolDescription(protocol), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ℹ️ Cable Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Select the connection type provided by the SDK. No device path is required — ensure the cable is properly connected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LanConfiguration(
    ipAddress: String,
    port: String,
    isAutoDiscovering: Boolean,
    isQrScanning: Boolean,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onAutoDiscover: () -> Unit,
    onScanQr: () -> Unit
) {
    Column {
        Text("LAN Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = ipAddress, onValueChange = onIpAddressChange, label = { Text("IP Address") }, placeholder = { Text("") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("IP address of the payment terminal") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = onPortChange, label = { Text("Port") }, placeholder = { Text("8080") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Port number (8443-8463)") })
        Spacer(modifier = Modifier.height(12.dp))

        // ── New SDK features: auto-discover & QR scan connect ──────────────────
        val actionInProgress = isAutoDiscovering || isQrScanning
        Text("Quick Connect", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = rememberDebouncedClick(onClick = onAutoDiscover),
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f)
            ) {
                if (isAutoDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Auto Discover")
            }
            OutlinedButton(
                onClick = rememberDebouncedClick(onClick = onScanQr),
                enabled = !actionInProgress,
                modifier = Modifier.weight(1f)
            ) {
                if (isQrScanning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ℹ️ LAN Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("This mode connects to the payment terminal over a local area network. Ensure both devices are on the same network.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(6.dp))
                Text("• Auto Discover: finds Taplink services on the LAN and connects automatically.\n• Scan QR: scans the QR code shown on the terminal's LAN screen to connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun CrossDeviceConfiguration(
    strategy: ConnectionPreferences.CrossDeviceStrategy,
    ipAddress: String,
    port: String,
    cableProtocol: ConnectionPreferences.CableProtocol,
    autoPreferLan: Boolean,
    onStrategyChange: (ConnectionPreferences.CrossDeviceStrategy) -> Unit,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onCableProtocolChange: (ConnectionPreferences.CableProtocol) -> Unit
) {
    Column {
        Text("Cross-Device Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connection Strategy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(4.dp))
        ConnectionPreferences.CrossDeviceStrategy.values().forEach { candidate ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = rememberDebouncedClick { onStrategyChange(candidate) })
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = strategy == candidate, onClick = { onStrategyChange(candidate) })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(getCrossDeviceStrategyLabel(candidate), style = MaterialTheme.typography.bodyLarge)
                    Text(getCrossDeviceStrategyDescription(candidate), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        // LAN host/port required for LAN strategy.
        if (strategy == ConnectionPreferences.CrossDeviceStrategy.LAN) {
            Text("LAN Transport", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpAddressChange,
                label = { Text("IP Address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("IP address of the payment terminal") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Port") },
                placeholder = { Text("8443") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = { Text("Port number (8443-8463)") }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Cable protocol is required for CABLE strategy.
        if (strategy == ConnectionPreferences.CrossDeviceStrategy.CABLE) {
            Text("Cable Protocol", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            ConnectionPreferences.CableProtocol.values().forEach { protocol ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = rememberDebouncedClick { onCableProtocolChange(protocol) })
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = cableProtocol == protocol, onClick = { onCableProtocolChange(protocol) })
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(getCableProtocolLabel(protocol), style = MaterialTheme.typography.bodyLarge)
                        Text(getCableProtocolDescription(protocol), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ℹ️ Cross-Device Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Tapro acts as a standalone payment terminal driven by this app over the selected " +
                        "LAN or cable transport. Choose the cable protocol explicitly when using " +
                        "a cable connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudConfiguration(
    state: SettingsState,
    onValueChange: (String, String) -> Unit,
    onAddOption: (String, String) -> Unit,
    onPushToTerminalChange: (Boolean) -> Unit
) {
    Column {
        Text("Cloud Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "API Key", value = state.cloudApiKey, options = state.cloudApiKeyOptions, fieldName = "apiKey", onValueChange = onValueChange, onAddOption = onAddOption, isMasked = true, supportingText = "Required")
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "Base URL", value = state.cloudBaseUrl, options = state.cloudBaseUrlOptions, fieldName = "baseUrl", onValueChange = onValueChange, onAddOption = onAddOption, supportingText = "Required")
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "App ID", value = state.cloudAppId, options = state.cloudAppIdOptions, fieldName = "appId", onValueChange = onValueChange, onAddOption = onAddOption, supportingText = "Required — Cloud-specific App ID")
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "Terminal SN", value = state.cloudTerminalSn, options = state.cloudTerminalSnOptions, fieldName = "terminalSn", onValueChange = onValueChange, onAddOption = onAddOption, supportingText = "Optional — device serial number")
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "Merchant ID", value = state.cloudMerchantId, options = state.cloudMerchantIdOptions, fieldName = "merchantId", onValueChange = onValueChange, onAddOption = onAddOption, supportingText = "Optional — overrides SDK Merchant ID")
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "Notify URL", value = state.cloudNotifyUrl, options = state.cloudNotifyUrlOptions, fieldName = "notifyUrl", onValueChange = onValueChange, onAddOption = onAddOption, supportingText = "Notification callback URL")
        Spacer(modifier = Modifier.height(8.dp))
        CloudDropdownField(label = "Terminal Event Notify URL", value = state.cloudTerminalEventNotifyUrl, options = state.cloudTerminalEventNotifyUrlOptions, fieldName = "terminalEventNotifyUrl", onValueChange = onValueChange, onAddOption = onAddOption, supportingText = "Terminal event callback URL")
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Push to Terminal", style = MaterialTheme.typography.bodyLarge)
                Text("Send pushToTerminal field for supported transactions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.cloudPushToTerminal, onCheckedChange = onPushToTerminalChange)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("☁️ Cloud Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("This mode connects directly to the Sunbay cloud API via HTTP. No local Tapro app is required. Ensure you have a valid API Key.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudDropdownField(
    label: String,
    value: String,
    options: List<CloudPreferences.LabeledOption>,
    fieldName: String,
    onValueChange: (String, String) -> Unit,
    onAddOption: (String, String) -> Unit,
    isMasked: Boolean = false,
    supportingText: String = ""
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var fieldVisible by remember { mutableStateOf(!isMasked) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(fieldName, it) },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
            singleLine = true,
            readOnly = false,
            visualTransformation = if (isMasked && !fieldVisible) PasswordVisualTransformation() else VisualTransformation.None,
            trailingIcon = {
                Row {
                    if (isMasked) {
                        IconButton(onClick = { fieldVisible = !fieldVisible }) {
                            Icon(imageVector = if (fieldVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
                        }
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            supportingText = if (supportingText.isNotEmpty()) ({ Text(supportingText) }) else null
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(if (isMasked) maskValue(option.label) else option.label, maxLines = 1) },
                    onClick = rememberDebouncedClick { onValueChange(fieldName, option.value); expanded = false },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add new...", color = MaterialTheme.colorScheme.primary)
                    }
                },
                onClick = { expanded = false; showAddDialog = true },
                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
            )
        }
    }

    if (showAddDialog) {
        AddCloudOptionDialog(
            fieldLabel = label,
            onConfirm = { newValue -> onAddOption(fieldName, newValue); showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddCloudOptionDialog(
    fieldLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValue by remember { mutableStateOf("") }
    val dialogSurface = if (isSystemInDarkTheme()) Color(0xFF202124) else Color.White
    val dialogText = if (isSystemInDarkTheme()) Color(0xFFF1F3F4) else Color(0xFF202124)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Add $fieldLabel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text(fieldLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = dialogSurface,
        titleContentColor = dialogText,
        textContentColor = dialogText,
        confirmButton = {
            TextButton(
                onClick = rememberDebouncedClick { onConfirm(inputValue.trim()) },
                enabled = inputValue.trim().isNotEmpty()
            ) {
                Text(
                    "Add",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = rememberDebouncedClick(onClick = onDismiss)) {
                Text(
                    "Cancel",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun SdkSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    var secretKeyVisible by remember { mutableStateOf(false) }

    Text("SDK Initialization Parameters", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Used to initialize TaplinkSDK. Changes take effect after tapping \"Save & Apply\".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text("Environment", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        supportingText = { Text("Required") }
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sdkMerchantId,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkMerchantId(it)) },
        label = { Text("Merchant ID") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        supportingText = { Text("Optional — validated only when provided") }
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sdkSecretKey,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkSecretKey(it)) },
        label = { Text("Secret Key") },
        placeholder = { if (state.hasExistingSecretKey && state.sdkSecretKey.isEmpty()) Text("••••••••") },
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
        }
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sdkConfigTimeoutSeconds,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkConfigTimeoutSeconds(it)) },
        label = { Text("TaplinkConfig Timeout") },
        suffix = { Text("s") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            Text("Global fallback timeout. SDK default: 180 seconds.")
        }
    )
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = state.sdkRequestTimeoutSeconds,
        onValueChange = { onIntent(SettingsIntent.UpdateSdkRequestTimeoutSeconds(it)) },
        label = { Text("Per-request Timeout") },
        suffix = { Text("s") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        supportingText = {
            Text("Optional. When set, this value takes precedence over TaplinkConfig timeout.")
        }
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
