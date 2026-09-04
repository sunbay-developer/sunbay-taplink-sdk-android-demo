package com.sunmi.tapro.taplink.demo.ui.navigation

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private const val NAVIGATION_LOCK_DURATION_MS = 700L

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
    var navigationLockedUntil by remember { mutableStateOf(0L) }

    fun beginNavigation(): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now < navigationLockedUntil) return false
        navigationLockedUntil = now + NAVIGATION_LOCK_DURATION_MS
        return true
    }

    fun navigateOnce(route: String) {
        if (navController.currentDestination?.route != route && beginNavigation()) {
            navController.navigate(route) {
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    fun popBackStackOnce() {
        if (beginNavigation()) {
            if (!navController.popBackStack()) {
                navigationLockedUntil = 0L
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Main Screen - Product selection and order management
        composable(Screen.Main.route) {
            MainScreen(
                onNavigateToTransactionList = {
                    navigateOnce(Screen.TransactionList.route)
                },
                onNavigateToSettings = {
                    navigateOnce(Screen.Settings.route)
                },
                onNavigateToProgress = { transactionId ->
                    navigateOnce(Screen.TransactionProgress.createRoute(transactionId))
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
                    popBackStackOnce()
                },
                onNavigateToDetail = { id ->
                    // Replace progress so back from detail returns to the previous list/main screen.
                    if (beginNavigation()) {
                        navController.navigate(Screen.TransactionDetail.createRoute(id)) {
                            popUpTo(Screen.TransactionProgress.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
        
        // Transaction List Screen - Transaction history
        composable(Screen.TransactionList.route) {
            TransactionListScreen(
                onNavigateToDetail = { transactionId ->
                    navigateOnce(Screen.TransactionDetail.createRoute(transactionId))
                },
                onNavigateBack = { 
                    popBackStackOnce()
                },
                onNavigateToProgress = { transactionId ->
                    navigateOnce(Screen.TransactionProgress.createRoute(transactionId))
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
                    popBackStackOnce()
                },
                onNavigateToProgress = { id ->
                    navigateOnce(Screen.TransactionProgress.createRoute(id))
                }
            )
        }
        
        // Settings Screen - Connection configuration
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { 
                    popBackStackOnce()
                }
            )
        }
    }
}
