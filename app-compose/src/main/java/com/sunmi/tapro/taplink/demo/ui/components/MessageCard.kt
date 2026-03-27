package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme

/**
 * MessageCard - POS-style notification banner for displaying messages,
 * errors, warnings, and success notifications.
 *
 * Design features:
 * - Colored left accent strip indicating message type
 * - Circular icon with type-specific color background
 * - Compact layout with clear visual hierarchy
 * - Action buttons aligned to the right
 * - Elevated card with rounded corners
 * - Responsive width (max 560dp for landscape)
 *
 * @param message The message to display
 * @param onDismiss Callback when the dismiss button is clicked
 * @param onAction Callback when an action button is clicked
 * @param modifier Optional modifier for the card
 */
@Composable
fun MessageCard(
    message: Message,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val typeConfig = getMessageTypeConfig(message.type)

    Card(
        modifier = modifier
            .widthIn(min = 300.dp, max = 560.dp)
            .shadow(6.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Left accent strip (drawn behind content)
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .matchParentSize()
                    .background(
                        typeConfig.accentColor,
                        RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                    )
            )

            // Main content with left padding for accent strip
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 5.dp)
                    .padding(16.dp)
            ) {
                // Header row: icon + title + dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type icon with colored circle background
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(typeConfig.iconBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = typeConfig.icon,
                            contentDescription = message.type.name,
                            tint = typeConfig.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Title
                    Text(
                        text = message.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Dismiss button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Dismiss",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Content text
                if (message.content.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 48.dp)
                    )
                }

                // Action buttons
                if (message.actions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                    ) {
                        message.actions.forEach { action ->
                            MessageActionButton(
                                action = action,
                                typeConfig = typeConfig,
                                onDismiss = onDismiss,
                                onAction = onAction
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a single action button for the MessageCard.
 */
@Composable
private fun MessageActionButton(
    action: MessageAction,
    typeConfig: MessageTypeConfig,
    onDismiss: () -> Unit,
    onAction: (MessageAction) -> Unit
) {
    when (action) {
        MessageAction.DISMISS -> {
            TextButton(
                onClick = onDismiss,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("Dismiss", style = MaterialTheme.typography.labelMedium)
            }
        }
        MessageAction.RETRY -> {
            Button(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = typeConfig.accentColor
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Retry", style = MaterialTheme.typography.labelMedium)
            }
        }
        MessageAction.SETTINGS -> {
            OutlinedButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Settings", style = MaterialTheme.typography.labelMedium)
            }
        }
        MessageAction.DETAILS -> {
            OutlinedButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Details", style = MaterialTheme.typography.labelMedium)
            }
        }
        MessageAction.CANCEL -> {
            OutlinedButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelMedium)
            }
        }
        MessageAction.CONTACT_SUPPORT -> {
            OutlinedButton(
                onClick = { onAction(action) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Support", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/**
 * Configuration holder for message type visual properties.
 */
private data class MessageTypeConfig(
    val icon: ImageVector,
    val accentColor: Color,
    val iconBackground: Color
)

/**
 * Returns visual configuration for a given MessageType.
 */
@Composable
private fun getMessageTypeConfig(type: MessageType): MessageTypeConfig {
    return when (type) {
        MessageType.SUCCESS -> MessageTypeConfig(
            icon = Icons.Default.CheckCircle,
            accentColor = MaterialTheme.colorScheme.tertiary,
            iconBackground = MaterialTheme.colorScheme.tertiaryContainer
        )
        MessageType.ERROR -> MessageTypeConfig(
            icon = Icons.Default.Error,
            accentColor = MaterialTheme.colorScheme.error,
            iconBackground = MaterialTheme.colorScheme.errorContainer
        )
        MessageType.WARNING -> MessageTypeConfig(
            icon = Icons.Default.Warning,
            accentColor = MaterialTheme.colorScheme.secondary,
            iconBackground = MaterialTheme.colorScheme.secondaryContainer
        )
        MessageType.INFO -> MessageTypeConfig(
            icon = Icons.Default.Info,
            accentColor = MaterialTheme.colorScheme.primary,
            iconBackground = MaterialTheme.colorScheme.primaryContainer
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun MessageCardSuccessPreview() {
    TaplinkTheme {
        MessageCard(
            message = Message(
                type = MessageType.SUCCESS,
                title = "Connection Successful",
                content = "Successfully connected to payment terminal\nDevice: SUNMI-P2\nVersion: 1.2.3",
                actions = listOf(MessageAction.DISMISS)
            ),
            onDismiss = {},
            onAction = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun MessageCardErrorPreview() {
    TaplinkTheme {
        MessageCard(
            message = Message(
                type = MessageType.ERROR,
                title = "Connection Failed",
                content = "Failed to connect to payment terminal. Please check your configuration.",
                actions = listOf(MessageAction.RETRY, MessageAction.SETTINGS, MessageAction.DISMISS)
            ),
            onDismiss = {},
            onAction = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun MessageCardQuerySuccessPreview() {
    TaplinkTheme {
        MessageCard(
            message = Message(
                type = MessageType.SUCCESS,
                title = "Query Successful",
                content = "Transaction found: TXN123456\nStatus: SUCCESS",
                actions = listOf(MessageAction.DISMISS)
            ),
            onDismiss = {},
            onAction = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun MessageCardWarningPreview() {
    TaplinkTheme {
        MessageCard(
            message = Message(
                type = MessageType.WARNING,
                title = "Connection Timeout",
                content = "Connection attempt timed out. Please check your configuration and try again.",
                actions = listOf(MessageAction.DISMISS)
            ),
            onDismiss = {},
            onAction = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun MessageCardInfoPreview() {
    TaplinkTheme {
        MessageCard(
            message = Message(
                type = MessageType.INFO,
                title = "Processing Payment",
                content = "Please wait while we process your payment.",
                actions = listOf(MessageAction.CANCEL)
            ),
            onDismiss = {},
            onAction = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}
