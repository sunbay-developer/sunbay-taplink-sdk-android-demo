package com.sunmi.tapro.taplink.demo.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import com.sunmi.tapro.taplink.sdk.TaplinkSDK
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.util.CloudPreferences
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.components.PosDialog
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig

// Settings page sections
private enum class SettingsSection { CONFIGURATION, DIAGNOSTICS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(
            context.applicationContext as android.app.Application
        )
    )
    val state by viewModel.state.collectAsState()
    val screenConfig = rememberScreenConfig()

    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            viewModel.handleIntent(SettingsIntent.NavigationConsumed)
            onNavigateBack()
        }
    }

    // Landscape: track which section is open in the right panel
    var selectedSection by remember { mutableStateOf(SettingsSection.CONFIGURATION) }

    val onIntent: (SettingsIntent) -> Unit = { viewModel.handleIntent(it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Settings")
                        Text(
                            text = getModeName(state.selectedMode) + " Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (screenConfig.isLandscape) {
                // Landscape: settings category sidebar + right detail panel
                Row(modifier = Modifier.fillMaxSize()) {
                    SettingsCategorySidebar(
                        selectedSection = selectedSection,
                        onSectionSelect = { selectedSection = it },
                        isTesting = state.isTesting,
                        isConfigValid = state.isConfigurationValid(),
                        onConnect = { onIntent(SettingsIntent.TestConnection) },
                        onExit = { onIntent(SettingsIntent.ExitApplication) }
                    )
                    VerticalDivider()
                    // Right panel: selected section content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 20.dp)
                    ) {
                        // Section title header
                        Text(
                            text = getSectionName(selectedSection).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = getSectionName(selectedSection),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        HorizontalDivider(modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
                        when (selectedSection) {
                            SettingsSection.CONFIGURATION -> {
                                UnifiedConfigurationSectionContent(
                                    state = state,
                                    onIntent = onIntent
                                )
                                if (state.testResult != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TestResultCard(
                                        result = state.testResult!!,
                                        isSuccess = state.testSuccess
                                    )
                                }
                            }
                            SettingsSection.DIAGNOSTICS -> CheckSectionContent()
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            } else {
                // Portrait: three section cards stacked + fixed bottom action bar
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Unified configuration
                        PortraitSectionCard(
                            title = "Configuration",
                            icon = Icons.Default.Wifi
                        ) {
                            UnifiedConfigurationSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                        }

                        // 2. Diagnostics (collapsible, default collapsed; version info loaded when expanded)
                        var diagnosticsExpanded by remember { mutableStateOf(false) }
                        ConfigGroupCard(
                            title = "Diagnostics",
                            subtitle = "Version info for Tapro, SDK, and Hardware",
                            expanded = diagnosticsExpanded,
                            onToggle = { diagnosticsExpanded = !diagnosticsExpanded }
                        ) {
                            CheckSectionContent()
                        }

                        if (state.testResult != null) {
                            TestResultCard(
                                result = state.testResult!!,
                                isSuccess = state.testSuccess
                            )
                        }
                    }

                    // Fixed bottom action bar
                    HorizontalDivider()
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Button(
                            onClick = { onIntent(SettingsIntent.TestConnection) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = !state.isTesting && state.isConfigurationValid()
                        ) {
                            if (state.isTesting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = "Connect",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { onIntent(SettingsIntent.ExitApplication) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Exit Application")
                        }
                    }
                }
            }

            // Message card overlay
            state.message?.let { message ->
                MessageCard(
                    message = message,
                    onDismiss = { onIntent(SettingsIntent.DismissMessage) },
                    onAction = { action ->
                        when (action) {
                            MessageAction.DISMISS -> onIntent(SettingsIntent.DismissMessage)
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
}

@Composable
private fun UnifiedConfigurationSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    var basicExpanded by remember { mutableStateOf(true) }
    var transactionExpanded by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(false) }

    ConfigGroupCard(
        title = "Basic",
        subtitle = "Connection mode and cloud parameters",
        expanded = basicExpanded,
        onToggle = { basicExpanded = !basicExpanded }
    ) {
        ConnectionSectionContent(state = state, onIntent = onIntent)
    }

    Spacer(modifier = Modifier.height(12.dp))

    ConfigGroupCard(
        title = "Transaction",
        subtitle = "Receipt and transaction output settings",
        expanded = transactionExpanded,
        onToggle = { transactionExpanded = !transactionExpanded }
    ) {
        PrintingSectionContent(state = state, onIntent = onIntent)
    }

    Spacer(modifier = Modifier.height(12.dp))

    ConfigGroupCard(
        title = "Advanced",
        subtitle = "SDK parameters and environment switch",
        expanded = advancedExpanded,
        onToggle = { advancedExpanded = !advancedExpanded }
    ) {
        SdkSectionContent(state = state, onIntent = onIntent)
    }
}

@Composable
private fun ConfigGroupCard(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    content = content
                )
            }
        }
    }
}

// ─── Layout: Landscape sidebar ────────────────────────────────────────────────

/**
 * Landscape left sidebar with settings category navigation and action buttons.
 */
@Composable
private fun SettingsCategorySidebar(
    selectedSection: SettingsSection,
    onSectionSelect: (SettingsSection) -> Unit,
    isTesting: Boolean,
    isConfigValid: Boolean,
    onConnect: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        SettingsSection.values().forEach { section ->
            SettingsCategoryItem(
                section = section,
                isSelected = selectedSection == section,
                onClick = { onSectionSelect(section) }
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

        Column(modifier = Modifier.padding(12.dp)) {
            Button(
                onClick = onConnect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isTesting && isConfigValid
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Connect")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onExit,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Exit")
            }
        }
    }
}

/**
 * Single category item in the landscape sidebar.
 */
@Composable
private fun SettingsCategoryItem(
    section: SettingsSection,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .background(
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = getSectionIcon(section),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = getSectionName(section),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor
            )
        }
    }
}

// ─── Layout: Portrait section card ────────────────────────────────────────────

/**
 * Card wrapper for a settings section in portrait mode.
 * Shows a colored header with icon + title, then content below.
 */
@Composable
private fun PortraitSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            // Section header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                content = content
            )
        }
    }
}

// ─── Section content composables ──────────────────────────────────────────────

/**
 * Connection section: mode dropdown + mode-specific configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectionSectionContent(
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
        ConnectionMode.APP_TO_APP -> AppToAppConfiguration()
        ConnectionMode.CABLE -> CableConfiguration(
            cableProtocol = state.cableProtocol,
            onCableProtocolChange = { onIntent(SettingsIntent.UpdateCableProtocol(it)) }
        )
        ConnectionMode.LAN -> LanConfiguration(
            ipAddress = state.ipAddress,
            port = state.port,
            onIpAddressChange = { onIntent(SettingsIntent.UpdateIpAddress(it)) },
            onPortChange = { onIntent(SettingsIntent.UpdatePort(it)) }
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
                    else -> null
                }
                intent?.let { onIntent(it) }
            },
            onAddOption = { field, value -> onIntent(SettingsIntent.AddCloudOption(field, value)) },
            onPushToTerminalChange = { onIntent(SettingsIntent.UpdateCloudPushToTerminal(it)) }
        )
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
            onClick = { onIntent(SettingsIntent.UpdatePrintReceipt(option)) },
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
            onClick = { onIntent(SettingsIntent.SwitchSdkEnvironment(SdkEnvironment.UAT)) },
            modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.sdkEnvironment == SdkEnvironment.UAT) selectedContainer else normalContainer,
                contentColor = if (state.sdkEnvironment == SdkEnvironment.UAT) selectedContent else normalContent
            )
        ) { Text("UAT", fontWeight = FontWeight.Bold) }
        Button(
            onClick = { onIntent(SettingsIntent.SwitchSdkEnvironment(SdkEnvironment.PROD)) },
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
        supportingText = { Text("Required") },
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
        onClick = { onIntent(SettingsIntent.ApplySdkConfig) },
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

// ─── Helper functions ──────────────────────────────────────────────────────────

private fun getSectionName(section: SettingsSection): String = when (section) {
    SettingsSection.CONFIGURATION -> "Configuration"
    SettingsSection.DIAGNOSTICS -> "Diagnostics"
}

private fun getSectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.CONFIGURATION -> Icons.Default.Tune
    SettingsSection.DIAGNOSTICS -> Icons.Default.Info
}

private fun getModeName(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.APP_TO_APP -> "App to App"
    ConnectionMode.CABLE -> "Cable"
    ConnectionMode.LAN -> "LAN"
    ConnectionMode.CLOUD -> "Cloud"
}

private fun getModeDescription(mode: ConnectionMode): String = when (mode) {
    ConnectionMode.APP_TO_APP -> "Direct connection via Tapro app"
    ConnectionMode.CABLE -> "Direct USB/serial connection"
    ConnectionMode.LAN -> "Network connection"
    ConnectionMode.CLOUD -> "Cloud API connection"
}

private fun getModeIcon(mode: ConnectionMode): ImageVector = when (mode) {
    ConnectionMode.APP_TO_APP -> Icons.Default.PhoneAndroid
    ConnectionMode.CABLE -> Icons.Default.Cable
    ConnectionMode.LAN -> Icons.Default.Wifi
    ConnectionMode.CLOUD -> Icons.Default.Cloud
}

// ─── Dialogs (kept for reference, not shown in main screen) ───────────────────

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
                onClick = { onReceiptChange(option) },
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
        iconBackgroundColor = MaterialTheme.colorScheme.secondaryContainer,
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

// ─── Existing sub-composables (kept intact) ────────────────────────────────────

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
                modifier = Modifier.fillMaxWidth().clickable { onExpandChange(!isExpanded) },
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
                            modifier = Modifier.fillMaxWidth().clickable { onReceiptChange(option) }.padding(vertical = 8.dp),
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

private fun getPrintReceiptLabel(option: PrintReceipt): String = when (option) {
    PrintReceipt.NONE -> "None"
    PrintReceipt.MERCHANT -> "Merchant Copy"
    PrintReceipt.CUSTOMER -> "Customer Copy"
    PrintReceipt.BOTH -> "Both Copies"
}

private fun getPrintReceiptDescription(option: PrintReceipt): String = when (option) {
    PrintReceipt.NONE -> "No receipt will be printed"
    PrintReceipt.MERCHANT -> "Print merchant copy only"
    PrintReceipt.CUSTOMER -> "Print customer copy only"
    PrintReceipt.BOTH -> "Print both merchant and customer copies"
}

@Composable
fun TestResultCard(
    result: String,
    isSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isSuccess) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
    val iconBg = if (isSuccess) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .matchParentSize()
                    .background(accentColor, RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 5.dp).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier.size(36.dp).background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    TaplinkTheme {
        SettingsScreen(onNavigateBack = {})
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
            OutlinedTextField(value = merchantId, onValueChange = onMerchantIdChange, label = { Text("Merchant ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true, supportingText = { Text("Required") })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = secretKey, onValueChange = onSecretKeyChange, label = { Text("Secret Key") }, placeholder = { if (hasExistingSecretKey && secretKey.isEmpty()) Text("••••••••") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), supportingText = { Text(if (hasExistingSecretKey && secretKey.isEmpty()) "Existing key hidden." else "Can be empty for some environments") })
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth(), enabled = isValid && !isApplying) {
                if (isApplying) { CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary); Spacer(modifier = Modifier.width(8.dp)) }
                Text("Save & Apply SDK Params")
            }
        }
    }
}

@Composable
fun AppToAppConfiguration() {
    Column {
        Text("App-to-App Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("✓ Direct Connection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("App-to-App mode provides direct communication with the Tapro payment app via Android IPC. No additional configuration is required.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
            Row(modifier = Modifier.fillMaxWidth().clickable { onCableProtocolChange(protocol) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
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

private fun getCableProtocolLabel(protocol: ConnectionPreferences.CableProtocol): String = when (protocol) {
    ConnectionPreferences.CableProtocol.AUTO -> "AUTO"
    ConnectionPreferences.CableProtocol.USB_AOA -> "USB_AOA (USB Android Open Accessory)"
    ConnectionPreferences.CableProtocol.USB_VSP -> "USB_VSP (USB Virtual Serial Port)"
    ConnectionPreferences.CableProtocol.RS232 -> "RS232 (Serial)"
}

private fun getCableProtocolDescription(protocol: ConnectionPreferences.CableProtocol): String = when (protocol) {
    ConnectionPreferences.CableProtocol.AUTO -> "Auto-detect protocol"
    ConnectionPreferences.CableProtocol.USB_AOA -> "USB Android Open Accessory 2.0"
    ConnectionPreferences.CableProtocol.USB_VSP -> "USB Virtual Serial Port"
    ConnectionPreferences.CableProtocol.RS232 -> "Standard RS232 serial"
}

@Composable
fun LanConfiguration(
    ipAddress: String,
    port: String,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit
) {
    Column {
        Text("LAN Configuration", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = ipAddress, onValueChange = onIpAddressChange, label = { Text("IP Address") }, placeholder = { Text("") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("IP address of the payment terminal") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = port, onValueChange = onPortChange, label = { Text("Port") }, placeholder = { Text("8080") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), supportingText = { Text("Port number (8443-8463)") })
        Spacer(modifier = Modifier.height(8.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("ℹ️ LAN Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("This mode connects to the payment terminal over a local area network. Ensure both devices are on the same network.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onClick = { onValueChange(fieldName, option.value); expanded = false },
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

private fun maskValue(value: String): String =
    if (value.length > 4) value.take(4) + "****" else "****"

// ─────────────────────────────────────────────────────────────────────────────
// Check Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CheckSectionContent() {
    val context = LocalContext.current
    // Version info is computed when this composable is first composed (i.e. when Diagnostics is expanded).
    val taproVersion = remember {
        try {
            val info = context.packageManager.getPackageInfo("com.sunmi.tapro", 0)
            info.versionName ?: "Unknown"
        } catch (_: Exception) {
            null
        }
    }

    val sdkVersion = remember {
        try {
            TaplinkSDK.getVersion().takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    val hardwareVersion = remember {
        try {
            val info = context.packageManager.getPackageInfo("com.sunmi.pay.hardware_v3", 0)
            info.versionName ?: "Unknown"
        } catch (_: Exception) {
            null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Check the versions of key components used by this application.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        VersionInfoRow(
            icon = Icons.Default.PhoneAndroid,
            label = "Tapro App",
            subtitle = "com.sunmi.tapro",
            version = taproVersion ?: "Not installed",
            isAvailable = taproVersion != null
        )

        VersionInfoRow(
            icon = Icons.Default.Tune,
            label = "Taplink SDK",
            subtitle = "Bundled in application",
            version = sdkVersion ?: "Unknown",
            isAvailable = sdkVersion != null
        )

        VersionInfoRow(
            icon = Icons.Default.Settings,
            label = "Hardware Service",
            subtitle = "com.sunmi.pay.hardware_v3",
            version = hardwareVersion ?: "Not installed",
            isAvailable = hardwareVersion != null
        )
    }
}

@Composable
private fun VersionInfoRow(
    icon: ImageVector,
    label: String,
    subtitle: String,
    version: String,
    isAvailable: Boolean
) {
    val okColor = MaterialTheme.colorScheme.primary
    val errColor = MaterialTheme.colorScheme.error
    val tint = if (isAvailable) okColor else errColor
    val containerColor = if (isAvailable)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.errorContainer

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(containerColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = version,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = tint
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(tint, CircleShape)
                    )
                    Text(
                        text = if (isAvailable) "Available" else "Not found",
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCloudOptionDialog(
    fieldLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputValue by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add $fieldLabel") },
        text = {
            OutlinedTextField(
                value = inputValue,
                onValueChange = { inputValue = it },
                label = { Text(fieldLabel) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(inputValue.trim()) }, enabled = inputValue.trim().isNotEmpty()) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
