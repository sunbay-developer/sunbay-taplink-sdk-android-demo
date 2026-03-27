package com.sunmi.tapro.taplink.demo.model

/**
 * MessageType enum representing different types of messages displayed to users
 * Used to determine the visual styling and severity of MessageCard components
 */
enum class MessageType {
    /**
     * Error message - critical issues that prevent operation
     * Displayed with red color scheme
     */
    ERROR,

    /**
     * Warning message - potential issues that need attention
     * Displayed with orange/yellow color scheme
     */
    WARNING,

    /**
     * Informational message - general information for the user
     * Displayed with blue color scheme
     */
    INFO,

    /**
     * Success message - confirmation of successful operations
     * Displayed with green color scheme
     */
    SUCCESS
}
