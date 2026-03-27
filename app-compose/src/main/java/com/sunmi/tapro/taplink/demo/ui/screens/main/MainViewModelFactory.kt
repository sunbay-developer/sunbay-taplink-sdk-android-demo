package com.sunmi.tapro.taplink.demo.ui.screens.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating MainViewModel instances
 * 
 * This factory is required because MainViewModel takes constructor parameters
 * (Application) that need to be provided when the ViewModel is created.
 * 
 * Usage in Composable:
 * ```
 * val viewModel: MainViewModel = viewModel(
 *     factory = MainViewModelFactory(LocalContext.current.applicationContext as Application)
 * )
 * ```
 */
class MainViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
