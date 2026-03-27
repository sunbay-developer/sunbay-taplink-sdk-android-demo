package com.sunmi.tapro.taplink.demo.util

import com.sunmi.tapro.taplink.demo.model.Message

/**
 * Error Handler
 * 
 * Centralized error handling and classification system that works with the Message model.
 * This class provides utilities for:
 * - Classifying errors by type and severity
 * - Determining if errors are retryable
 * - Converting errors to user-friendly Message objects
 * - Logging errors with appropriate context
 * 
 * All errors in the application should flow through this handler to ensure
 * consistent error handling and user experience.
 */
object ErrorHandler {
    
    /**
     * Error classification categories
     */
    enum class ErrorCategory {
        CONNECTION,      // Connection-related errors
        PAYMENT,         // Payment processing errors
        VALIDATION,      // Input validation errors
        NETWORK,         // Network communication errors
        SDK,             // SDK-related errors
        UNKNOWN          // Unclassified errors
    }
    
    /**
     * Error severity levels
     */
    enum class ErrorSeverity {
        CRITICAL,        // Critical errors requiring immediate attention
        HIGH,            // High priority errors
        MEDIUM,          // Medium priority errors
        LOW              // Low priority errors (warnings)
    }
    
    /**
     * Classify an error by its code and message
     * 
     * @param code Error code from SDK or service
     * @param message Error message
     * @return ErrorCategory classification
     */
    fun classifyError(code: String, message: String): ErrorCategory {
        return when {
            // Cloud-specific error codes
            code == "NETWORK_ERROR" -> ErrorCategory.NETWORK
            code == "CLOUD_NOT_INITIALIZED" -> ErrorCategory.SDK
            code == "CLOUD_ERROR" -> ErrorCategory.UNKNOWN
            
            // Connection errors (21x-25x range and legacy C codes)
            isConnectionError(code) -> ErrorCategory.CONNECTION
            
            // SDK initialization errors (20x range)
            code.startsWith("20") -> ErrorCategory.SDK
            
            // Payment processing errors (30x range)
            code.startsWith("30") -> ErrorCategory.PAYMENT
            
            // Network errors (check message content)
            message.contains("network", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) -> ErrorCategory.NETWORK
            
            // Validation errors (check message content)
            message.contains("invalid", ignoreCase = true) ||
            message.contains("required", ignoreCase = true) -> ErrorCategory.VALIDATION
            
            // Unknown category
            else -> ErrorCategory.UNKNOWN
        }
    }
    
    /**
     * Determine error severity based on code and category
     * 
     * @param code Error code
     * @param category Error category
     * @return ErrorSeverity level
     */
    fun determineErrorSeverity(code: String, category: ErrorCategory): ErrorSeverity {
        return when (category) {
            ErrorCategory.SDK -> ErrorSeverity.CRITICAL
            ErrorCategory.CONNECTION -> {
                when {
                    code == Constants.ConnectionErrorCodes.TARGET_APP_CRASHED -> ErrorSeverity.CRITICAL
                    code.startsWith("21") || code.startsWith("22") -> ErrorSeverity.HIGH
                    else -> ErrorSeverity.MEDIUM
                }
            }
            ErrorCategory.PAYMENT -> {
                when {
                    code == Constants.ErrorCodes.TRANSACTION_FAILED -> ErrorSeverity.HIGH
                    code == Constants.ErrorCodes.TRANSACTION_TERMINATED -> ErrorSeverity.MEDIUM
                    else -> ErrorSeverity.MEDIUM
                }
            }
            ErrorCategory.VALIDATION -> ErrorSeverity.LOW
            ErrorCategory.NETWORK -> ErrorSeverity.MEDIUM
            ErrorCategory.UNKNOWN -> ErrorSeverity.MEDIUM
        }
    }
    
    /**
     * Check if an error is retryable
     * 
     * @param code Error code
     * @param category Error category
     * @return true if the error can be retried
     */
    fun isRetryable(code: String, category: ErrorCategory): Boolean {
        return when (category) {
            ErrorCategory.CONNECTION -> {
                // Most connection errors are retryable except critical ones
                code != Constants.ConnectionErrorCodes.TARGET_APP_CRASHED
            }
            ErrorCategory.PAYMENT -> {
                // Check specific payment error codes
                when (code) {
                    Constants.ErrorCodes.RESPONSE_TIMEOUT,
                    Constants.ErrorCodes.REQUEST_SEND_FAILED,
                    Constants.ErrorCodes.TRANSACTION_PROCESSING -> true
                    Constants.ErrorCodes.TRANSACTION_TERMINATED,
                    Constants.ErrorCodes.UNSUPPORTED_TRANSACTION_TYPE,
                    Constants.ErrorCodes.MISSING_REQUIRED_PARAM -> false
                    else -> {
                        // Check if it's a user cancellation (not retryable)
                        code != "309"
                    }
                }
            }
            ErrorCategory.NETWORK -> true  // Network errors are usually retryable
            ErrorCategory.SDK -> false     // SDK errors usually require restart
            ErrorCategory.VALIDATION -> false  // Validation errors need user correction
            ErrorCategory.UNKNOWN -> false // Unknown errors should not be retried automatically
        }
    }
    
    /**
     * Handle a connection error and convert to Message
     * 
     * @param code Error code
     * @param message Error message
     * @return Message object for display
     */
    fun handleConnectionError(code: String, message: String): Message {
        val category = classifyError(code, message)
        val severity = determineErrorSeverity(code, category)
        
        // Log error for debugging
        println("Connection error - Code: $code, Message: $message, Category: $category, Severity: $severity")
        
        return ErrorMessageConverter.fromConnectionError(code, message)
    }
    
    /**
     * Handle a payment error and convert to Message
     * 
     * @param code Error code
     * @param message Error message
     * @return Message object for display
     */
    fun handlePaymentError(code: String, message: String): Message {
        val category = classifyError(code, message)
        val severity = determineErrorSeverity(code, category)
        val retryable = isRetryable(code, category)
        
        // Log error for debugging
        println("Payment error - Code: $code, Message: $message, Category: $category, Severity: $severity, Retryable: $retryable")
        
        return ErrorMessageConverter.fromPaymentError(code, message, retryable)
    }
    
    /**
     * Handle a validation error and convert to Message
     * 
     * @param field Field name that failed validation
     * @param reason Reason for validation failure
     * @return Message object for display
     */
    fun handleValidationError(field: String, reason: String): Message {
        // Log warning for debugging
        println("Validation error - Field: $field, Reason: $reason")
        
        return ErrorMessageConverter.fromValidationError(field, reason)
    }
    
    /**
     * Handle a generic exception and convert to Message
     * 
     * @param exception The exception that occurred
     * @param context Additional context about where the error occurred
     * @return Message object for display
     */
    fun handleException(exception: Exception, context: String = ""): Message {
        // Log exception for debugging
        println("Exception in $context: ${exception.message}")
        exception.printStackTrace()
        
        val message = exception.message ?: "An unexpected error occurred"
        return ErrorMessageConverter.fromPaymentError("EXCEPTION", message, isRetryable = false)
    }
    
    /**
     * Log an error with full context
     * 
     * @param code Error code
     * @param message Error message
     * @param context Additional context
     * @param exception Optional exception
     */
    fun logError(
        code: String,
        message: String,
        context: String = "",
        exception: Exception? = null
    ) {
        val category = classifyError(code, message)
        val severity = determineErrorSeverity(code, category)
        
        val logMessage = buildString {
            append("Error in $context - ")
            append("Code: $code, ")
            append("Message: $message, ")
            append("Category: $category, ")
            append("Severity: $severity")
        }
        
        // Log based on severity
        println("[$severity] $logMessage")
        exception?.printStackTrace()
    }
    
    /**
     * Check if an error code indicates a connection problem
     */
    private fun isConnectionError(code: String): Boolean {
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
