package com.sunmi.tapro.taplink.demo.service

import com.sunmi.tapro.taplink.demo.model.Message
import com.sunmi.tapro.taplink.demo.util.ErrorHandler

/**
 * Extension functions for PaymentCallback to integrate with Message model
 * 
 * These extensions provide convenient methods to convert errors into Message objects
 * for consistent error handling across the application.
 */

/**
 * Create a PaymentCallback that converts errors to Message objects
 * 
 * @param onSuccess Callback for terminal-processed results (inspect PaymentResult status)
 * @param onFailure Callback for communication/technical errors with Message object
 * @param onProgress Optional callback for payment progress updates
 * @return PaymentCallback instance
 */
fun createMessageAwareCallback(
    onSuccess: (PaymentResult) -> Unit,
    onFailure: (Message) -> Unit,
    onProgress: ((String, String) -> Unit)? = null
): PaymentCallback {
    return object : PaymentCallback {
        override fun onSuccess(result: PaymentResult) {
            onSuccess(result)
        }

        override fun onFailure(code: String, message: String) {
            // onFailure = communication/technical error (connection lost, timeout, etc.)
            // NOT a transaction decline — declines arrive via onSuccess with isFailed().
            val errorMessage = ErrorHandler.handlePaymentError(code, message)
            onFailure(errorMessage)
        }

        override fun onProgress(status: String, message: String) {
            onProgress?.invoke(status, message)
        }
    }
}

/**
 * Create a ConnectionListener that converts errors to Message objects
 * 
 * @param onConnected Callback for successful connection
 * @param onDisconnected Callback for disconnection
 * @param onError Callback for connection error with Message object
 * @return ConnectionListener instance
 */
fun createMessageAwareConnectionListener(
    onConnected: (String, String) -> Unit,
    onDisconnected: (String) -> Unit,
    onError: (Message) -> Unit
): ConnectionListener {
    return object : ConnectionListener {
        override fun onConnected(deviceId: String, taproVersion: String) {
            onConnected(deviceId, taproVersion)
        }

        override fun onDisconnected(reason: String) {
            onDisconnected(reason)
        }

        override fun onError(code: String, message: String) {
            // Convert error to Message using ErrorHandler
            val messageObj = ErrorHandler.handleConnectionError(code, message)
            onError(messageObj)
        }
    }
}
