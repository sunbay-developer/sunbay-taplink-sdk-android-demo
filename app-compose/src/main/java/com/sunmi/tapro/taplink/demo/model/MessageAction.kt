package com.sunmi.tapro.taplink.demo.model

/**
 * MessageAction enum representing actions that users can take in response to messages
 * Used in MessageCard component to provide interactive buttons
 */
enum class MessageAction {
    /**
     * Retry the failed operation
     * Typically shown for retryable errors
     */
    RETRY,

    /**
     * Navigate to settings screen
     * Shown when configuration changes might resolve the issue
     */
    SETTINGS,

    /**
     * Dismiss the message
     * Always available to close the message card
     */
    DISMISS,

    /**
     * View more details about the issue
     * Shown when additional information is available
     */
    DETAILS,

    /**
     * Contact support for assistance
     * Shown for critical errors that require help
     */
    CONTACT_SUPPORT,

    /**
     * Cancel the current operation
     * Shown during ongoing operations that can be cancelled
     */
    CANCEL
}
