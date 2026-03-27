package com.sunmi.tapro.taplink.demo.util

import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.model.MessageAction
import com.sunmi.tapro.taplink.demo.model.MessageType

/**
 * Helper class for quadruple return values
 */
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

/**
 * Error Message Converter
 * 
 * Converts error codes and messages from the SDK and services into
 * user-friendly Message objects for display in MessageCard components.
 * 
 * This utility provides centralized error handling logic that:
 * - Classifies errors by type (connection, payment, validation, etc.)
 * - Determines appropriate message type (ERROR, WARNING, INFO)
 * - Suggests relevant actions (RETRY, SETTINGS, CONTACT_SUPPORT, etc.)
 * - Provides user-friendly error messages
 */
object ErrorMessageConverter {

    /**
     * Convert connection error to Message
     * 
     * @param code Error code from SDK
     * @param message Error message from SDK
     * @return Message object for display
     */
    fun fromConnectionError(code: String, message: String): Message {
        val (title, content, actions) = when {
            // SDK incompatibility errors
            code == "SDK_INCOMPATIBLE" -> {
                Triple(
                    "SDK Version Mismatch",
                    "The SDK version is incompatible with the current configuration. Please contact support for assistance.",
                    listOf(MessageAction.CONTACT_SUPPORT, MessageAction.DISMISS)
                )
            }
            
            // Connection exception errors
            code == "CONNECTION_EXCEPTION" -> {
                Triple(
                    "Connection Error",
                    message.ifEmpty { "An unexpected error occurred during connection." },
                    listOf(MessageAction.RETRY, MessageAction.SETTINGS, MessageAction.DISMISS)
                )
            }
            
            // Configuration errors
            code == "CONFIG_ERROR" -> {
                Triple(
                    "Configuration Error",
                    message.ifEmpty { "Connection configuration is invalid or incomplete." },
                    listOf(MessageAction.SETTINGS, MessageAction.DISMISS)
                )
            }
            
            // Connection timeout errors
            code.startsWith("22") || code == Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT -> {
                Triple(
                    "Connection Timeout",
                    "Unable to connect to payment terminal. Please check if the terminal is powered on and try again.",
                    listOf(MessageAction.RETRY, MessageAction.SETTINGS, MessageAction.DISMISS)
                )
            }
            
            // Connection failed errors
            code.startsWith("21") || code == Constants.ConnectionErrorCodes.CONNECTION_FAILED -> {
                Triple(
                    "Connection Failed",
                    "Failed to establish connection with payment terminal. Please verify your connection settings.",
                    listOf(MessageAction.SETTINGS, MessageAction.RETRY, MessageAction.DISMISS)
                )
            }
            
            // Connection lost errors
            code.startsWith("23") || code == Constants.ConnectionErrorCodes.CONNECTION_LOST -> {
                Triple(
                    "Connection Lost",
                    "Connection to payment terminal was lost. Please reconnect to continue.",
                    listOf(MessageAction.RETRY, MessageAction.SETTINGS, MessageAction.DISMISS)
                )
            }
            
            // Service disconnected errors
            code.startsWith("24") || code == Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED -> {
                Triple(
                    "Service Disconnected",
                    "Payment service disconnected unexpectedly. Please restart the connection.",
                    listOf(MessageAction.RETRY, MessageAction.DISMISS)
                )
            }
            
            // Target app crashed
            code == Constants.ConnectionErrorCodes.TARGET_APP_CRASHED -> {
                Triple(
                    "Terminal App Error",
                    "Payment terminal application encountered an error. Please restart the terminal app and try again.",
                    listOf(MessageAction.RETRY, MessageAction.CONTACT_SUPPORT, MessageAction.DISMISS)
                )
            }
            
            // Generic connection error
            else -> {
                Triple(
                    "Connection Error",
                    message.ifEmpty { "An error occurred while connecting to the payment terminal." },
                    listOf(MessageAction.RETRY, MessageAction.SETTINGS, MessageAction.DISMISS)
                )
            }
        }
        
        return Message(
            type = MessageType.ERROR,
            title = title,
            content = "$content\n\nError Code: $code",
            actions = actions
        )
    }

    /**
     * Convert payment error to Message
     * 
     * @param code Error code from SDK
     * @param message Error message from SDK
     * @param isRetryable Whether the error is retryable
     * @return Message object for display
     */
    fun fromPaymentError(code: String, message: String, isRetryable: Boolean = true): Message {
        val (title, content, messageType, actions) = when {
            // User cancelled transaction
            code == "309" || message.contains("cancel", ignoreCase = true) || 
            message.contains("abort", ignoreCase = true) -> {
                Quadruple(
                    "Transaction Cancelled",
                    "The transaction was cancelled by the user.",
                    MessageType.WARNING,
                    listOf(MessageAction.DISMISS)
                )
            }
            
            // Card declined
            code.startsWith("4") || message.contains("decline", ignoreCase = true) -> {
                Quadruple(
                    "Card Declined",
                    "The payment card was declined. Please try a different payment method.",
                    MessageType.ERROR,
                    listOf(MessageAction.RETRY, MessageAction.DISMISS)
                )
            }
            
            // Insufficient funds
            message.contains("insufficient", ignoreCase = true) -> {
                Quadruple(
                    "Insufficient Funds",
                    "The payment card has insufficient funds. Please use a different payment method.",
                    MessageType.ERROR,
                    listOf(MessageAction.RETRY, MessageAction.DISMISS)
                )
            }
            
            // Invalid card
            message.contains("invalid card", ignoreCase = true) -> {
                Quadruple(
                    "Invalid Card",
                    "The payment card is invalid or not supported. Please try a different card.",
                    MessageType.ERROR,
                    listOf(MessageAction.RETRY, MessageAction.DISMISS)
                )
            }
            
            // Timeout errors
            code.startsWith("3") && message.contains("timeout", ignoreCase = true) -> {
                Quadruple(
                    "Transaction Timeout",
                    "The transaction timed out. Please try again.",
                    MessageType.WARNING,
                    listOf(MessageAction.RETRY, MessageAction.DISMISS)
                )
            }
            
            // Connection-related payment errors
            isConnectionRelatedError(code) -> {
                Quadruple(
                    "Connection Error",
                    "Lost connection to payment terminal during transaction. Please reconnect and try again.",
                    MessageType.ERROR,
                    listOf(MessageAction.RETRY, MessageAction.SETTINGS, MessageAction.DISMISS)
                )
            }
            
            // Generic payment error
            else -> {
                val defaultActions = if (isRetryable) {
                    listOf(MessageAction.RETRY, MessageAction.CONTACT_SUPPORT, MessageAction.DISMISS)
                } else {
                    listOf(MessageAction.CONTACT_SUPPORT, MessageAction.DISMISS)
                }
                
                Quadruple(
                    "Payment Error",
                    message.ifEmpty { "An error occurred while processing the payment." },
                    MessageType.ERROR,
                    defaultActions
                )
            }
        }
        
        return Message(
            type = messageType,
            title = title,
            content = "$content\n\nError Code: $code",
            actions = actions
        )
    }

    /**
     * Convert validation error to Message
     * 
     * @param field Field name that failed validation
     * @param reason Reason for validation failure
     * @return Message object for display
     */
    fun fromValidationError(field: String, reason: String): Message {
        return Message(
            type = MessageType.WARNING,
            title = "Invalid Input",
            content = "$field: $reason",
            actions = listOf(MessageAction.DISMISS)
        )
    }

    /**
     * Create info message
     * 
     * @param title Message title
     * @param content Message content
     * @param actions Available actions
     * @return Message object for display
     */
    fun createInfoMessage(
        title: String,
        content: String,
        actions: List<MessageAction> = listOf(MessageAction.DISMISS)
    ): Message {
        return Message(
            type = MessageType.INFO,
            title = title,
            content = content,
            actions = actions
        )
    }

    /**
     * Create success message
     * 
     * @param title Message title
     * @param content Message content
     * @param actions Available actions
     * @return Message object for display
     */
    fun createSuccessMessage(
        title: String,
        content: String,
        actions: List<MessageAction> = listOf(MessageAction.DISMISS)
    ): Message {
        return Message(
            type = MessageType.SUCCESS,
            title = title,
            content = content,
            actions = actions
        )
    }

    /**
     * Create warning message
     * 
     * @param title Message title
     * @param content Message content
     * @param actions Available actions
     * @return Message object for display
     */
    fun createWarningMessage(
        title: String,
        content: String,
        actions: List<MessageAction> = listOf(MessageAction.DISMISS)
    ): Message {
        return Message(
            type = MessageType.WARNING,
            title = title,
            content = content,
            actions = actions
        )
    }

    /**
     * Check if error code indicates a connection problem
     * Supports both new error code format (21x-25x) and legacy format (Cxx)
     */
    private fun isConnectionRelatedError(code: String): Boolean {
        // New error code format (21x-25x range)
        if (code.startsWith("21") || code.startsWith("22") || 
            code.startsWith("23") || code.startsWith("24") || 
            code.startsWith("25")) {
            return true
        }
        
        // Legacy error code format
        return when (code) {
            Constants.ConnectionErrorCodes.TARGET_APP_CRASHED,
            Constants.ConnectionErrorCodes.CONNECTION_TIMEOUT,
            Constants.ConnectionErrorCodes.CONNECTION_FAILED,
            Constants.ConnectionErrorCodes.CONNECTION_LOST,
            Constants.ConnectionErrorCodes.SERVICE_DISCONNECTED,
            Constants.ConnectionErrorCodes.SERVICE_BINDING_FAILED -> true
            else -> code.startsWith("C")  // Most C-codes are connection related
        }
    }
}
