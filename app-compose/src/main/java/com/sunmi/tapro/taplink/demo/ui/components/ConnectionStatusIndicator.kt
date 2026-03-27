package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.ConnectionMode
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme

/**
 * ConnectionStatusIndicator - POS-style pill badge showing connection status.
 *
 * Design features:
 * - Pill-shaped badge with tinted background
 * - Status icon with matching color
 * - Pulsing dot animation for connecting state
 * - Spinning icon for connecting state
 * - Compact layout suitable for top bar placement
 *
 * @param connectionState Current connection state
 * @param modifier Optional modifier for the component
 */
@Composable
fun ConnectionStatusIndicator(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val config = getStatusConfig(connectionState)

    // Spin animation for connecting state
    val rotation by rememberInfiniteTransition(label = "spin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing)
        ),
        label = "rotation"
    )

    // Pulse animation for connecting dot
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val isConnecting = connectionState is ConnectionState.Connecting

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = config.backgroundColor,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status icon
            Icon(
                imageVector = config.icon,
                contentDescription = null,
                tint = config.contentColor,
                modifier = Modifier
                    .size(16.dp)
                    .then(if (isConnecting) Modifier.rotate(rotation) else Modifier)
            )

            // Status text
            Text(
                text = config.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = config.contentColor
            )

            // Pulsing dot for connecting state
            if (isConnecting) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(pulseAlpha)
                        .background(config.contentColor, CircleShape)
                )
            }
        }
    }
}

/**
 * Configuration holder for connection status visual properties.
 */
private data class ConnectionStatusConfig(
    val icon: ImageVector,
    val label: String,
    val contentColor: Color,
    val backgroundColor: Color
)

/**
 * Returns visual configuration for a given ConnectionState.
 */
@Composable
private fun getStatusConfig(connectionState: ConnectionState): ConnectionStatusConfig {
    return when (connectionState) {
        is ConnectionState.Disconnected -> ConnectionStatusConfig(
            icon = Icons.Default.LinkOff,
            label = "Disconnected",
            contentColor = MaterialTheme.colorScheme.error,
            backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
        is ConnectionState.Connecting -> ConnectionStatusConfig(
            icon = Icons.Default.Sync,
            label = "Connecting",
            contentColor = MaterialTheme.colorScheme.primary,
            backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        )
        is ConnectionState.Connected -> ConnectionStatusConfig(
            icon = Icons.Default.CheckCircle,
            label = when (connectionState.mode) {
                ConnectionMode.APP_TO_APP -> "App-to-App"
                ConnectionMode.CABLE -> "Cable"
                ConnectionMode.LAN -> "LAN"
                ConnectionMode.CLOUD -> "Cloud"

            },
            contentColor = MaterialTheme.colorScheme.tertiary,
            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
        )
        is ConnectionState.Error -> ConnectionStatusConfig(
            icon = Icons.Default.Error,
            label = "Error",
            contentColor = MaterialTheme.colorScheme.error,
            backgroundColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        )
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorDisconnectedPreview() {
    TaplinkTheme {
        ConnectionStatusIndicator(
            connectionState = ConnectionState.Disconnected,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorConnectingPreview() {
    TaplinkTheme {
        ConnectionStatusIndicator(
            connectionState = ConnectionState.Connecting,
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorConnectedPreview() {
    TaplinkTheme {
        ConnectionStatusIndicator(
            connectionState = ConnectionState.Connected(
                mode = ConnectionMode.APP_TO_APP,
                deviceId = "DEVICE123",
                version = "1.0.0"
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ConnectionStatusIndicatorErrorPreview() {
    TaplinkTheme {
        ConnectionStatusIndicator(
            connectionState = ConnectionState.Error(
                code = "TIMEOUT",
                message = "Connection timeout"
            ),
            modifier = Modifier.padding(8.dp)
        )
    }
}
