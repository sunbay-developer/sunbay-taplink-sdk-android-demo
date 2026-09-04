package com.sunmi.tapro.taplink.demo.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.ui.components.MessageCard
import com.sunmi.tapro.taplink.demo.ui.screens.settings.table.SettingsTableScreen
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import com.sunmi.tapro.taplink.demo.ui.utils.rememberDebouncedClick
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig

// Settings page sections
private enum class SettingsSection {
    CONNECTION,
    PROFILE,
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
    // Portrait starts with a category list and opens one configuration section at a time.
    var portraitSelectedSection by remember { mutableStateOf<SettingsSection?>(null) }
    val isPortraitDetail = !screenConfig.isLandscape && portraitSelectedSection != null

    val onIntent: (SettingsIntent) -> Unit = { viewModel.handleIntent(it) }
    val onNavigateUp = {
        if (isPortraitDetail) {
            portraitSelectedSection = null
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = isPortraitDetail) {
        portraitSelectedSection = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (isPortraitDetail) {
                                getSectionName(requireNotNull(portraitSelectedSection))
                            } else {
                                "Settings"
                            }
                        )
                        Text(
                            text = if (isPortraitDetail && portraitSelectedSection != SettingsSection.CONNECTION) {
                                "Settings"
                            } else {
                                getModeName(state.selectedMode) + " Mode"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = rememberDebouncedClick(onClick = onNavigateUp)) {
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
                            SettingsSection.PROFILE -> ProfileSectionContent(
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
                val section = portraitSelectedSection
                if (section == null) {
                    PortraitSettingsList(
                        onSectionSelect = { portraitSelectedSection = it },
                        onExit = { onIntent(SettingsIntent.ExitApplication) }
                    )
                } else {
                    PortraitSettingsDetail(
                        section = section,
                        state = state,
                        onIntent = onIntent
                    )
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
    SettingsSection.PROFILE -> "Profile"
    SettingsSection.TRANSACTION_AMOUNT -> "Transaction Amount"
    SettingsSection.PARAMETERS -> "Parameter Configuration"
    SettingsSection.DIAGNOSTICS -> "Diagnostics"
}

private fun getSectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.CONNECTION -> Icons.Default.Wifi
    SettingsSection.PROFILE -> Icons.Default.Person
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
                onClick = rememberDebouncedClick(onClick = onConnect),
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
                onClick = rememberDebouncedClick(onClick = onExit),
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
        onClick = rememberDebouncedClick(onClick = onClick),
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

// ─── Layout: Portrait list and detail ────────────────────────────────────────

private fun getSectionDescription(section: SettingsSection): String = when (section) {
    SettingsSection.CONNECTION -> "Choose a connection mode and configure its transport"
    SettingsSection.PROFILE -> "View the current terminal profile"
    SettingsSection.TRANSACTION_AMOUNT -> "Configure transaction amount and receipt options"
    SettingsSection.PARAMETERS -> "Configure SDK, tax, tip, and staff parameters"
    SettingsSection.DIAGNOSTICS -> "View Tapro, SDK, and hardware version information"
}

@Composable
private fun PortraitSettingsList(
    onSectionSelect: (SettingsSection) -> Unit,
    onExit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsSection.values().forEach { section ->
                PortraitSettingsListItem(
                    section = section,
                    onClick = { onSectionSelect(section) }
                )
            }
        }

        HorizontalDivider()
        OutlinedButton(
            onClick = rememberDebouncedClick(onClick = onExit),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Text("Exit Application")
        }
    }
}

@Composable
private fun PortraitSettingsListItem(
    section: SettingsSection,
    onClick: () -> Unit
) {
    Card(
        onClick = rememberDebouncedClick(onClick = onClick),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getSectionIcon(section),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = getSectionName(section),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = getSectionDescription(section),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open ${getSectionName(section)}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PortraitSettingsDetail(
    section: SettingsSection,
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when (section) {
                SettingsSection.CONNECTION -> ConnectionSectionContent(state, onIntent)
                SettingsSection.PROFILE -> ProfileSectionContent(state, onIntent)
                SettingsSection.TRANSACTION_AMOUNT -> TransactionAmountSectionContent(state, onIntent)
                SettingsSection.PARAMETERS -> ParameterConfigurationSectionContent(state, onIntent)
                SettingsSection.DIAGNOSTICS -> CheckSectionContent()
            }

            if (section == SettingsSection.CONNECTION && state.testResult != null) {
                Spacer(modifier = Modifier.height(16.dp))
                TestResultCard(result = state.testResult!!, isSuccess = state.testSuccess)
            }
        }

        if (section == SettingsSection.CONNECTION) {
            HorizontalDivider()
            Button(
                onClick = rememberDebouncedClick { onIntent(SettingsIntent.TestConnection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp, vertical = 0.dp),
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
                Text("Connect", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(modifier = Modifier.height(12.dp))
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
