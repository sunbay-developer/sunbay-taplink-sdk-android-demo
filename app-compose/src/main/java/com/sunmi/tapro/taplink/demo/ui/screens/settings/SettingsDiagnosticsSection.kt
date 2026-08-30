package com.sunmi.tapro.taplink.demo.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.sdk.TaplinkSDK

// ─── Diagnostics Section ──────────────────────────────────────────────────────

@Composable
internal fun CheckSectionContent() {
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
