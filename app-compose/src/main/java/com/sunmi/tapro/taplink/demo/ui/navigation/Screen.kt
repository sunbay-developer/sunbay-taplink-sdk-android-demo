package com.sunmi.tapro.taplink.demo.ui.navigation

import android.net.Uri

/**
 * Sealed class defining all navigation routes in the application.
 * Each screen has a route string and helper methods for creating routes with arguments.
 */
sealed class Screen(val route: String) {
    /**
     * Main screen - Product selection and order management
     */
    object Main : Screen("main")
    
    /**
     * Transaction progress screen - Shows real-time transaction status
     * Route includes transactionId as a path parameter
     */
    object TransactionProgress : Screen("progress/{transactionId}") {
        /**
         * Creates a route with the actual transaction ID
         * @param transactionId The ID of the transaction to display
         * @return The complete route string
         */
        fun createRoute(transactionId: String) = "progress/${Uri.encode(transactionId)}"
    }
    
    /**
     * Transaction list screen - Shows transaction history
     */
    object TransactionList : Screen("transaction_list")
    
    /**
     * Transaction detail screen - Shows detailed transaction information
     * Route includes transactionId as a path parameter
     */
    object TransactionDetail : Screen("detail/{transactionId}") {
        /**
         * Creates a route with the actual transaction ID
         * @param transactionId The ID of the transaction to display
         * @return The complete route string
         */
        fun createRoute(transactionId: String) = "detail/${Uri.encode(transactionId)}"
    }
    
    /**
     * Settings screen - Connection configuration
     */
    object Settings : Screen("settings")
}
