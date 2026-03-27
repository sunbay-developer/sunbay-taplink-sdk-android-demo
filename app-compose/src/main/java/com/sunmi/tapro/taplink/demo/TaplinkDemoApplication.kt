package com.sunmi.tapro.taplink.demo

import android.app.Application
import android.util.Log
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.util.TaplinkSdkInitializer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Main application class for Taplink Demo
 * 
 * Handles global SDK initialization and provides a centralized entry point
 * for SDK configuration. The SDK is initialized once at application startup
 * to ensure consistent configuration across all activities.
 */
class TaplinkDemoApplication : Application() {

    companion object {
        private const val TAG = "TaplinkDemoApp"
    }
    
    // Application-level coroutine scope for global operations
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "=== Application Started ===")

        // Setup global exception handler first to catch any initialization errors
        setupGlobalExceptionHandler()

        // Initialize dependency injection provider first
        // This must be done before any other initialization that might need dependencies
        initializeDependencyProvider()

        // Initialize SDK once at application level to ensure consistent configuration
        initializeTaplinkSDK()
        
        // Initialize connection manager and establish connection
        initializeConnectionManager()
        
        // Setup global connection state observer
        setupGlobalConnectionObserver()
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Cancel all coroutines when application terminates
        applicationScope.cancel()
    }

    /**
     * Setup global exception handler to catch uncaught exceptions
     * 
     * This handler catches exceptions that occur in SDK callbacks or other
     * asynchronous operations that are not caught by try-catch blocks.
     * Instead of crashing the app, it logs the error and shows a toast message.
     */
    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "=== Uncaught Exception Detected ===")
            Log.e(TAG, "Thread: ${thread.name}")
            Log.e(TAG, "Exception: ${throwable.javaClass.simpleName}")
            Log.e(TAG, "Message: ${throwable.message}")
            Log.e(TAG, "Stack trace:", throwable)
            
            // Check if this is a NoSuchMethodError from SDK
            if (throwable is NoSuchMethodError && throwable.message?.contains("PaymentRequest") == true) {
                Log.e(TAG, "SDK API incompatibility detected - NoSuchMethodError in PaymentRequest")
                
                // Show error message on main thread
                try {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        android.widget.Toast.makeText(
                            this,
                            "SDK version mismatch detected. Please contact support.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to show toast", e)
                }
                
                // Try to update connection state to show error to user
                try {
                    applicationScope.launch {
                        try {
                            val connectionManager = DependencyProvider.connectionManager
                            // Force disconnect to clean up state
                            connectionManager.disconnect()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to disconnect", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update connection state", e)
                }
                
                // Don't call default handler - prevent crash
                // Instead, just log the error and continue
                Log.e(TAG, "Exception handled - app will continue running")
                return@setDefaultUncaughtExceptionHandler
            }
            
            // For other exceptions, use default handler
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        Log.d(TAG, "Global exception handler installed")
    }

    /**
     * Initialize dependency injection provider
     * 
     * Sets up the DependencyProvider with the application context, making all
     * dependencies available throughout the application lifecycle. This must be
     * called before accessing any dependencies.
     */
    private fun initializeDependencyProvider() {
        try {
            Log.d(TAG, "=== Initializing DependencyProvider ===")
            DependencyProvider.init(this)
            Log.d(TAG, "DependencyProvider initialized successfully")
            
            // Verify all dependencies are accessible
            val verified = com.sunmi.tapro.taplink.demo.di.DependencyProviderTest.verifyDependencies()
            if (verified) {
                Log.d(TAG, "All dependencies verified and ready to use")
            } else {
                Log.e(TAG, "Dependency verification failed!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize DependencyProvider", e)
            throw e
        }
    }
    
    /**
     * Initialize ConnectionManager and establish connection
     * 
     * Gets the ConnectionManager instance from DependencyProvider and initiates
     * connection to the payment terminal. This ensures the app is ready to process
     * payments as soon as it starts.
     * 
     * The connection will be established based on saved preferences (connection mode,
     * IP address, etc.). If connection fails, the ConnectionManager will handle the
     * error and update its state accordingly.
     */
    private fun initializeConnectionManager() {
        try {
            Log.d(TAG, "=== Initializing ConnectionManager ===")
            
            // Get ConnectionManager instance from DependencyProvider
            val connectionManager = DependencyProvider.connectionManager
            
            Log.d(TAG, "ConnectionManager instance obtained")
            
            // Initiate connection to payment terminal
            // This will use saved preferences to determine connection mode and parameters
            connectionManager.connect()
            
            Log.d(TAG, "Connection initiated - state will be updated via StateFlow")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ConnectionManager", e)
            // Don't throw - connection errors should be handled gracefully
            // The ConnectionManager will update its state to Error, which will be
            // observed by UI components
        }
    }
    
    /**
     * Setup global connection state observer
     * 
     * Observes connection state changes at the application level and logs them.
     * This provides visibility into connection lifecycle events across the entire
     * application, which is useful for debugging and monitoring.
     * 
     * Individual screens will also observe connection state for UI updates, but
     * this global observer ensures we have application-wide visibility.
     */
    private fun setupGlobalConnectionObserver() {
        try {
            Log.d(TAG, "=== Setting up Global Connection Observer ===")
            
            val connectionManager = DependencyProvider.connectionManager
            
            // Observe connection state changes in application scope
            applicationScope.launch {
                connectionManager.connectionState.collect { state ->
                    when (state) {
                        is ConnectionState.Disconnected -> {
                            Log.d(TAG, "=== Global Connection State: DISCONNECTED ===")
                        }
                        is ConnectionState.Connecting -> {
                            Log.d(TAG, "=== Global Connection State: CONNECTING ===")
                        }
                        is ConnectionState.Connected -> {
                            Log.d(TAG, "=== Global Connection State: CONNECTED ===")
                            Log.d(TAG, "Mode: ${state.mode}")
                            Log.d(TAG, "Device ID: ${state.deviceId}")
                            Log.d(TAG, "Version: ${state.version}")
                        }
                        is ConnectionState.Error -> {
                            Log.e(TAG, "=== Global Connection State: ERROR ===")
                            Log.e(TAG, "Error Code: ${state.code}")
                            Log.e(TAG, "Error Message: ${state.message}")
                        }
                    }
                }
            }
            
            Log.d(TAG, "Global connection observer setup complete")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup global connection observer", e)
            // Don't throw - this is not critical for app functionality
        }
    }

    /**
     * Initialize Taplink SDK with basic configuration
     * 
     * Performs one-time SDK initialization using hardcoded credentials for UAT environment.
     * Connection mode is intentionally not set here because it needs to be
     * configurable at runtime based on user preferences.
     * 
     * @throws Exception if SDK initialization fails due to missing configuration
     */
    private fun initializeTaplinkSDK() {
        try {
            Log.d(TAG, "=== Taplink SDK Initialization Started ===")

            // Initialize from persisted preferences (falls back to demo defaults)
            TaplinkSdkInitializer.initFromPreferences(this)

        } catch (e: Exception) {
            Log.e(TAG, "=== Taplink SDK Initialization Response ===")
            Log.e(TAG, "Status: FAILURE")
            Log.e(TAG, "Error: ${e.message}")
            Log.e(TAG, "Exception: ", e)
        }
    }

}
