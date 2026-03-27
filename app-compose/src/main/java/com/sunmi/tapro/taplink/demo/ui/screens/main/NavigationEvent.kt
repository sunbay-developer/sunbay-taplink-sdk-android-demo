package com.sunmi.tapro.taplink.demo.ui.screens.main

/**
 * Navigation Event sealed class
 * 
 * Represents one-time navigation events from the Main Screen.
 * These are consumed by the UI and then cleared to prevent re-navigation.
 * 
 * Following the single-event pattern for navigation in MVI architecture:
 * 1. ViewModel emits navigation event in state
 * 2. UI observes event and performs navigation
 * 3. UI sends ClearNavigationEvent intent
 * 4. ViewModel clears the event from state
 */
sealed class NavigationEvent {
    /**
     * Navigate to Transaction List screen
     */
    object ToTransactionList : NavigationEvent()
    
    /**
     * Navigate to Settings screen
     */
    object ToSettings : NavigationEvent()
    
    /**
     * Navigate to Transaction Progress screen
     * 
     * @param transactionId ID of the transaction to monitor
     */
    data class ToProgress(val transactionId: String) : NavigationEvent()
}
