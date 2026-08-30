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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.screens.settings.table.SettingsTableScreen
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig

// Settings page sections
private enum class SettingsSection {
    CONNECTION,
    TRANSACTION_AMOUNT,
    PARAMETERS,
    DIAGNOSTICS
}

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
    var selectedSection by remember { mutableStateOf(SettingsSection.CONNECTION) }

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
                SettingsTableScreen(
                    sectionTitle = getSectionName(selectedSection),
                    sidebar = {
                        SettingsCategorySidebar(
                            selectedSection = selectedSection,
                            onSectionSelect = { selectedSection = it },
                            isTesting = state.isTesting,
                            isConfigValid = state.isConfigurationValid(),
                            onConnect = { onIntent(SettingsIntent.TestConnection) },
                            onExit = { onIntent(SettingsIntent.ExitApplication) }
                        )
                    },
                    detailContent = {
                        when (selectedSection) {
                            SettingsSection.CONNECTION -> ConnectionSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                            SettingsSection.TRANSACTION_AMOUNT -> TransactionAmountSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                            SettingsSection.PARAMETERS -> ParameterConfigurationSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                            SettingsSection.DIAGNOSTICS -> CheckSectionContent()
                        }
                        if (selectedSection != SettingsSection.DIAGNOSTICS && state.testResult != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            TestResultCard(
                                result = state.testResult!!,
                                isSuccess = state.testSuccess
                            )
                        }
                    }
                )
            } else {
                // Portrait: independent section cards stacked + fixed bottom action bar
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Connection settings remain independent from transaction settings.
                        PortraitSectionCard(
                            title = "Connection Mode",
                            icon = Icons.Default.Wifi
                        ) {
                            ConnectionSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                        }

                        PortraitSectionCard(
                            title = "Transaction Amount",
                            icon = Icons.Default.Add
                        ) {
                            TransactionAmountSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                        }

                        PortraitSectionCard(
                            title = "Parameter Configuration",
                            icon = Icons.Default.Tune
                        ) {
                            ParameterConfigurationSectionContent(
                                state = state,
                                onIntent = onIntent
                            )
                        }

                        // Diagnostics (collapsible, default collapsed; version info loaded when expanded)
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

// ─── Helper functions ──────────────────────────────────────────────────────────

private fun getSectionName(section: SettingsSection): String = when (section) {
    SettingsSection.CONNECTION -> "Connection Mode"
    SettingsSection.TRANSACTION_AMOUNT -> "Transaction Amount"
    SettingsSection.PARAMETERS -> "Parameter Configuration"
    SettingsSection.DIAGNOSTICS -> "Diagnostics"
}

private fun getSectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.CONNECTION -> Icons.Default.Wifi
    SettingsSection.TRANSACTION_AMOUNT -> Icons.Default.Add
    SettingsSection.PARAMETERS -> Icons.Default.Tune
    SettingsSection.DIAGNOSTICS -> Icons.Default.Info
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
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
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
                        color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
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

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    TaplinkTheme {
        SettingsScreen(onNavigateBack = {})
    }
}
