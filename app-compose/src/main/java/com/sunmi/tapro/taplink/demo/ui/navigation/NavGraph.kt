package com.sunmi.tapro.taplink.demo.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sunmi.tapro.taplink.demo.ui.screens.main.MainScreen
import com.sunmi.tapro.taplink.demo.ui.screens.progress.TransactionProgressScreen
import com.sunmi.tapro.taplink.demo.ui.screens.list.TransactionListScreen
import com.sunmi.tapro.taplink.demo.ui.screens.detail.TransactionDetailScreen
import com.sunmi.tapro.taplink.demo.ui.screens.settings.SettingsScreen

/**
 * Main navigation graph for the application.
 * Defines all screen destinations and their navigation relationships.
 * 
 * @param navController The navigation controller to use. Defaults to a new instance.
 * @param startDestination The initial screen to display. Defaults to Main screen.
 */
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Main.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Main Screen - Product selection and order management
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToTransactionList = {
                    navController.navigate(Screen.TransactionList.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onNavigateToProgress = { transactionId ->
                    navController.navigate(Screen.TransactionProgress.createRoute(transactionId))
                }
            )
        }
        
        // Transaction Progress Screen - Real-time transaction status
        composable(
            route = Screen.TransactionProgress.route,
            arguments = listOf(
                navArgument("transactionId") { 
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            TransactionProgressScreen(
                transactionId = transactionId,
                onNavigateBack = { 
                    navController.popBackStack() 
                },
                onNavigateToDetail = { id ->
                    // Pop progress so back from detail goes to Main (not progress/result)
                    navController.popBackStack()
                    navController.navigate(Screen.TransactionDetail.createRoute(id))
                }
            )
        }
        
        // Transaction List Screen - Transaction history
        composable(Screen.TransactionList.route) {
            TransactionListScreen(
                onNavigateToDetail = { transactionId ->
                    navController.navigate(Screen.TransactionDetail.createRoute(transactionId))
                },
                onNavigateBack = { 
                    navController.popBackStack() 
                },
                onNavigateToProgress = { transactionId ->
                    navController.navigate(Screen.TransactionProgress.createRoute(transactionId))
                }
            )
        }
        
        // Transaction Detail Screen - Detailed transaction information
        composable(
            route = Screen.TransactionDetail.route,
            arguments = listOf(
                navArgument("transactionId") { 
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            val transactionId = backStackEntry.arguments?.getString("transactionId") ?: ""
            TransactionDetailScreen(
                transactionId = transactionId,
                onNavigateBack = { 
                    navController.popBackStack() 
                },
                onNavigateToProgress = { id ->
                    navController.navigate(Screen.TransactionProgress.createRoute(id))
                }
            )
        }
        
        // Settings Screen - Connection configuration
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { 
                    navController.popBackStack() 
                }
            )
        }
    }
}
