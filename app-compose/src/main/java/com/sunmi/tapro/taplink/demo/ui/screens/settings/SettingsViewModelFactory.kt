package com.sunmi.tapro.taplink.demo.ui.screens.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sunmi.tapro.taplink.demo.di.DependencyProvider

/**
 * Factory for creating SettingsViewModel instances
 * 
 * Provides dependencies to SettingsViewModel through constructor injection.
 * Required by ViewModelProvider to create ViewModel with custom constructor.
 * 
 * Usage:
 * ```
 * val viewModel: SettingsViewModel = viewModel(
 *     factory = SettingsViewModelFactory(application)
 * )
 * ```
 */
class SettingsViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(
                application = application,
                connectionManager = DependencyProvider.connectionManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
