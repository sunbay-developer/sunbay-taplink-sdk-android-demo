package com.sunmi.tapro.taplink.demo.di

import android.app.Application
import com.google.gson.Gson
import android.content.Context
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.cloud.CloudPaymentService
import com.sunmi.tapro.taplink.demo.service.ConnectionManager
import com.sunmi.tapro.taplink.demo.service.PaymentService
import com.sunmi.tapro.taplink.demo.service.TaplinkPaymentService
import com.sunmi.tapro.taplink.demo.util.ConnectionPreferences
import com.sunmi.tapro.taplink.demo.util.PrintReceiptMapping

/**
 * Dependency Provider for manual dependency injection
 * 
 * This object provides centralized access to all application dependencies
 * using lazy initialization. It follows a simple manual DI pattern to keep
 * the architecture straightforward while maintaining testability.
 * 
 * Usage:
 * 1. Initialize in Application.onCreate(): DependencyProvider.init(this)
 * 2. Access dependencies: DependencyProvider.paymentService
 * 
 * All dependencies are lazily initialized on first access to optimize
 * application startup time.
 */
object DependencyProvider {
    
    private lateinit var application: Application
    
    /**
     * Initialize the dependency provider with application context
     * 
     * Must be called once in Application.onCreate() before accessing any dependencies.
     * 
     * @param app Application instance
     * @throws IllegalStateException if called more than once
     */
    fun init(app: Application) {
        if (::application.isInitialized) {
            throw IllegalStateException("DependencyProvider already initialized")
        }
        application = app
    }
    
    /**
     * Check if the provider has been initialized
     * 
     * @return true if init() has been called
     */
    fun isInitialized(): Boolean = ::application.isInitialized
    
    /**
     * Get the application instance
     * 
     * @throws IllegalStateException if not initialized
     */
    private fun requireApplication(): Application {
        if (!::application.isInitialized) {
            throw IllegalStateException("DependencyProvider not initialized. Call init() first.")
        }
        return application
    }
    
    /**
     * Get the application context
     * 
     * Provides public access to the application context for components
     * that need to read preferences or access Android resources.
     * 
     * @return Application context
     * @throws IllegalStateException if not initialized
     */
    fun requireContext(): Context = requireApplication().applicationContext
    
    // ========== Core Dependencies ==========
    
    /**
     * Gson instance for JSON serialization/deserialization
     * 
     * Used by TransactionRepository for persisting transactions to SharedPreferences
     */
    val gson: Gson by lazy {
        Gson()
    }
    
    // ========== Utility Dependencies ==========
    
    /**
     * Connection preferences for storing connection configuration
     * 
     * Note: ConnectionPreferences is an object (singleton), so we just return it directly
     */
    val connectionPreferences: ConnectionPreferences
        get() = ConnectionPreferences
    
    // ========== Repository Dependencies ==========
    
    /**
     * Transaction repository for managing transaction data
     * 
     * Uses SharedPreferences for persistent storage across app restarts.
     * Provides StateFlow for reactive UI updates.
     */
    val transactionRepository: TransactionRepository by lazy {
        TransactionRepository.getInstance(requireApplication())
    }
    
    // ========== Service Dependencies ==========
    
    /**
     * Taplink payment service for App-to-App, Cable, and LAN modes
     * 
     * Uses TaplinkPaymentService singleton instance which handles all payment operations
     * through the Taplink SDK
     */
    val taplinkPaymentService: PaymentService by lazy {
        TaplinkPaymentService.getInstance().apply {
            initialize(requireApplication())
            setPrintReceipt(PrintReceiptMapping.toSdk(ConnectionPreferences.getPrintReceipt(requireApplication())))
        }
    }
    
    /**
     * Cloud payment service for Cloud mode
     * 
     * Uses CloudPaymentService which communicates with Sunbay cloud API
     * via Nexus Java SDK. Must be initialized with cloud config before use.
     */
    val cloudPaymentService: CloudPaymentService by lazy {
        CloudPaymentService()
    }
    
    /**
     * Get the appropriate PaymentService based on current connection mode
     * 
     * Returns CloudPaymentService for CLOUD mode, TaplinkPaymentService for all others.
     * This replaces the previous lazy paymentService property to support dynamic mode switching.
     * 
     * @param context Android Context for reading preferences
     * @return PaymentService instance for the current connection mode
     */
    fun getPaymentService(context: Context): PaymentService {
        val mode = ConnectionPreferences.getConnectionMode(context)
        return when (mode) {
            ConnectionPreferences.ConnectionMode.CLOUD -> cloudPaymentService
            else -> taplinkPaymentService
        }
    }
    
    /**
     * Dynamic PaymentService property based on current connection mode
     * 
     * Provides backward compatibility for existing callers that reference
     * DependencyProvider.paymentService. Resolves to the correct service
     * instance on each access based on the current connection mode.
     */
    val paymentService: PaymentService
        get() = getPaymentService(requireApplication())
    
    /**
     * Connection manager for managing payment terminal connections
     * 
     * Provides reactive connection state updates through StateFlow.
     * Handles all connection modes: App-to-App, Cable, LAN, Cloud.
     * Uses dynamic paymentService resolution based on current connection mode.
     */
    val connectionManager: ConnectionManager by lazy {
        ConnectionManager(requireApplication(), ::getPaymentService)
    }
}
