package com.sunmi.tapro.taplink.demo.ui.screens.list

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/**
 * Factory for creating TransactionListViewModel instances
 * 
 * This factory is required because TransactionListViewModel takes constructor parameters
 * (Application) that need to be provided when the ViewModel is created.
 * 
 * Usage in Composable:
 * ```
 * val viewModel: TransactionListViewModel = viewModel(
 *     factory = TransactionListViewModelFactory(LocalContext.current.applicationContext as Application)
 * )
 * ```
 */
class TransactionListViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionListViewModel::class.java)) {
            return TransactionListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
