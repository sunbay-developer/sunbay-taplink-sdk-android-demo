package com.sunmi.tapro.taplink.demo.model

/**
 * Message data model for displaying user notifications, errors, and warnings
 * Used in MessageCard component for consistent error handling across the app
 *
 * @property type The type of message (ERROR, WARNING, INFO, SUCCESS)
 * @property title The title/heading of the message
 * @property content The detailed message content
 * @property actions List of available actions the user can take
 */
data class Message(
    val type: MessageType,
    val title: String,
    val content: String,
    val actions: List<MessageAction> = emptyList()
)
