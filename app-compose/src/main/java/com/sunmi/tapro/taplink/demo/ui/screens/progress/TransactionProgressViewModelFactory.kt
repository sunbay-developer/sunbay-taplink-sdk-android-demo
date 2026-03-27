package com.sunmi.tapro.taplink.demo.ui.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sunmi.tapro.taplink.demo.repository.TransactionRepository
import com.sunmi.tapro.taplink.demo.service.PaymentService

/**
 * Factory for creating TransactionProgressViewModel with dependencies
 * 
 * Required because ViewModel needs constructor parameters that cannot be
 * provided by the default ViewModelProvider.
 * 
 * @property transactionRepository Repository for transaction data access
 * @property paymentService Service for payment operations
 */
class TransactionProgressViewModelFactory(
    private val transactionRepository: TransactionRepository,
    private val paymentService: PaymentService
) : ViewModelProvider.Factory {
    
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionProgressViewModel::class.java)) {
            return TransactionProgressViewModel(
                transactionRepository = transactionRepository,
                paymentService = paymentService
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
