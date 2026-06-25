package com.sunmi.tapro.taplink.demo.di

import android.app.Application
import android.util.Log

/**
 * Simple test class to verify DependencyProvider initialization
 * 
 * This class provides a manual verification method that can be called
 * from the Application class to ensure all dependencies are properly
 * initialized and accessible.
 */
object DependencyProviderTest {
    
    private const val TAG = "DependencyProviderTest"
    
    /**
     * Verify that all dependencies can be accessed without errors
     * 
     * This method attempts to access each dependency and logs the result.
     * It should be called after DependencyProvider.init() to ensure
     * everything is working correctly.
     * 
     * @return true if all dependencies are accessible, false otherwise
     */
    fun verifyDependencies(): Boolean {
        try {
            Log.d(TAG, "=== Verifying DependencyProvider ===")
            
            // Check if initialized
            if (!DependencyProvider.isInitialized()) {
                Log.e(TAG, "DependencyProvider not initialized!")
                return false
            }
            Log.d(TAG, "✓ DependencyProvider is initialized")
            
            // Verify ConnectionPreferences
            val connectionPrefs = DependencyProvider.connectionPreferences
            Log.d(TAG, "✓ ConnectionPreferences: ${connectionPrefs.javaClass.simpleName}")
            
            // Verify TransactionRepository
            val transactionRepo = DependencyProvider.transactionRepository
            Log.d(TAG, "✓ TransactionRepository: ${transactionRepo.javaClass.simpleName}")
            
            // Verify PaymentService
            val paymentService = DependencyProvider.paymentService
            Log.d(TAG, "✓ PaymentService: ${paymentService.javaClass.simpleName}")
            
            Log.d(TAG, "=== All dependencies verified successfully ===")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to verify dependencies", e)
            return false
        }
    }
}
