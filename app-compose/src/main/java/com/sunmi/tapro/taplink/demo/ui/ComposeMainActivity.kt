package com.sunmi.tapro.taplink.demo.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.service.ConnectionState
import com.sunmi.tapro.taplink.demo.ui.navigation.NavGraph
import com.sunmi.tapro.taplink.demo.ui.navigation.Screen
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme

/**
 * Main Activity for Taplink Demo Application (Compose Version)
 *
 * This activity serves as the entry point for the Compose-based UI, providing:
 * - Material Design 3 theming
 * - Navigation graph setup
 * - "Initializing" overlay when app is connecting (hidden on Settings so Connect button shows loading instead)
 * - Lifecycle management for Compose
 *
 * The activity uses ComponentActivity as the base class to support Jetpack Compose.
 * All UI is rendered using Compose declarative UI framework.
 */
class ComposeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TaplinkTheme {
                val navController = rememberNavController()
                val connectionState by DependencyProvider.connectionManager.connectionState.collectAsState(initial = ConnectionState.Disconnected)
                val isInitializing = connectionState is ConnectionState.Connecting
                val currentRoute = navController.currentBackStackEntry?.destination?.route
                val isOnSettingsScreen = currentRoute == Screen.Settings.route
                val showInitializingOverlay = isInitializing && !isOnSettingsScreen

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavGraph(navController = navController)

                        if (showInitializingOverlay) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Initializing",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
