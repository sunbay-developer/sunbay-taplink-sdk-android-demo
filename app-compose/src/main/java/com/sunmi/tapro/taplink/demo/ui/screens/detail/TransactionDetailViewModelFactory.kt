package com.sunmi.tapro.taplink.demo.ui.screens.detail

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository

/**
 * Factory for creating TransactionDetailViewModel with dependencies
 * 
 * Provides proper dependency injection for the ViewModel, ensuring
 * TransactionRepository and PaymentService are correctly initialized.
 * Uses DependencyProvider.paymentService to dynamically resolve the correct
 * service based on current connection mode (Cloud vs App-to-App/Cable/LAN).
 */
class TransactionDetailViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionDetailViewModel::class.java)) {
            val transactionRepository = TransactionRepository.getInstance(context)
            return TransactionDetailViewModel(
                transactionRepository = transactionRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
