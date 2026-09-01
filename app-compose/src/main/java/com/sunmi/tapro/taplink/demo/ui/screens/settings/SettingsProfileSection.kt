package com.sunmi.tapro.taplink.demo.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.service.TerminalInfo
import com.sunmi.tapro.taplink.demo.ui.utils.rememberDebouncedClick

@Composable
internal fun ProfileSectionContent(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = "Query merchant and device information from the currently connected TaPro terminal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Button(
            onClick = rememberDebouncedClick { onIntent(SettingsIntent.RefreshProfile) },
            enabled = !state.isProfileLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isProfileLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp).width(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (state.isProfileLoading) "Refreshing Profile" else "Refresh Profile")
        }

        state.terminalInfo?.let { info ->
            MerchantProfileCard(info)
            DeviceProfileCard(info)
        } ?: Text(
            text = "No profile loaded. Connect to TaPro, then refresh to retrieve its merchant and device information.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MerchantProfileCard(info: TerminalInfo) {
    ProfileCard(title = "Merchant Information") {
        ProfileField("DBA Name", info.dbaName)
        ProfileField("Merchant ID", info.merchantId)
        ProfileField("MCC", info.mcc)
        ProfileField("Address", listOfNotNull(
            info.street,
            info.detailAddress,
            info.cityName,
            info.stateName,
            info.zipCode,
            info.country
        ).joinToString(", "))
        IdentifierList(
            title = "Merchant IDs",
            identifiers = info.midList,
            valueOf = { it.mid },
            labelOf = { it.channelName.ifBlank { it.channelCode } }
        )
    }
}

@Composable
private fun DeviceProfileCard(info: TerminalInfo) {
    ProfileCard(title = "Device Information") {
        ProfileField("Serial Number", info.terminal.sn)
        ProfileField("Vendor", info.terminal.vendor)
        ProfileField("Model", info.terminal.model)
        IdentifierList(
            title = "Terminal IDs",
            identifiers = info.terminal.tidList,
            valueOf = { it.tid },
            labelOf = { it.channelName.ifBlank { it.channelCode } }
        )
    }
}

@Composable
private fun ProfileCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ProfileField(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            modifier = Modifier.width(112.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: "Not available",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun <T> IdentifierList(
    title: String,
    identifiers: List<T>,
    valueOf: (T) -> String,
    labelOf: (T) -> String
) {
    Spacer(modifier = Modifier.height(6.dp))
    Text(title, style = MaterialTheme.typography.labelMedium)
    if (identifiers.isEmpty()) {
        Text(
            text = "Not available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    identifiers.forEachIndexed { index, identifier ->
        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        ProfileField(labelOf(identifier), valueOf(identifier))
    }
}
