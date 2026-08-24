package com.sunmi.tapro.taplink.demo.service

import android.content.Context
import android.util.Log
import com.sunmi.tapro.taplink.demo.ui.secondary.SecondaryOrderState
import com.sunmi.tapro.taplink.demo.ui.secondary.SecondaryTipSelectionController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

/**
 * Secondary Display Manager
 *
 * High-level orchestrator for customer-facing secondary display lifecycle.
 * Wraps [SecondaryTipSelectionController] to provide a clean, integrator-friendly API
 * that manages display state transitions without exposing low-level Presentation details.
 *
 * ## Expected Lifecycle Flow
 *
 * ```
 * Connected → showIdle() → [order changes → updateOrder()]
 *           → showTipSelection() → onPaymentStarted()
 *           → ... (TaPro handles payment on display) ...
 *           → onPaymentCompleted() → showIdle()
 *           → release()
 * ```
 *
 * ## Integration Guide
 *
 * 1. Create an instance after establishing a connection to the payment terminal.
 * 2. Call [showIdle] to display the ad carousel (optionally with order data).
 * 3. As the customer builds their order, call [updateOrder] to refresh the display.
 * 4. Before payment, call [showTipSelection] to collect a tip on the secondary screen.
 * 5. Once the payment flow begins, call [onPaymentStarted] — TaPro takes over the display.
 * 6. After payment completes, call [onPaymentCompleted] to restore idle content.
 * 7. When the session ends (Activity destroyed, etc.), call [release] for cleanup.
 *
 * ## Thread Safety
 *
 * All public methods are safe to call from the main thread. Suspend functions should be
 * called from a coroutine scope. The [isDisplayAvailable] StateFlow can be collected
 * from any thread.
 *
 * @param context Application or Activity context (used for display detection)
 */
class SecondaryDisplayManager(context: Context) {

    companion object {
        private const val TAG = "SecondaryDisplayMgr"
    }

    // ─── Internal State ───────────────────────────────────────────────────────

    private val controller = SecondaryTipSelectionController(context)

    private val _isDisplayAvailable = MutableStateFlow(controller.hasSecondaryDisplay())

    /**
     * Whether a secondary display is currently detected and available.
     *
     * Collect this flow to reactively show/hide secondary-display-related UI controls.
     * Updated after each display interaction attempt.
     */
    val isDisplayAvailable: StateFlow<Boolean> = _isDisplayAvailable.asStateFlow()

    /** Tracks the last order state for restoration after payment completes. */
    private var lastOrderState: SecondaryOrderState? = null

    /** Current display phase for internal state machine tracking. */
    private var currentPhase: DisplayPhase = DisplayPhase.IDLE

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Show the idle ad carousel on the secondary display.
     *
     * If [order] is provided and contains items, the display shows a split layout
     * (order list + ad carousel). Otherwise, the full screen shows ads.
     *
     * This is the default resting state for the secondary display. Call this:
     * - After initial connection
     * - After payment completes ([onPaymentCompleted] calls this internally)
     * - When the order changes significantly (or use [updateOrder] for incremental updates)
     *
     * @param order Optional order snapshot to display alongside ads.
     * @return `true` if content was successfully shown, `false` if no display available.
     */
    suspend fun showIdle(order: SecondaryOrderState? = null): Boolean {
        Log.d(TAG, "showIdle(hasOrder=${order?.hasItems() == true})")
        lastOrderState = order

        val shown = controller.showAdCarousel(order)
        updateDisplayAvailability(shown)
        currentPhase = if (shown) DisplayPhase.IDLE else DisplayPhase.UNAVAILABLE

        return shown
    }

    /**
     * Update the order data displayed on the secondary screen without rebuilding
     * the Presentation.
     *
     * Use this for incremental order changes (item added/removed, quantity changed).
     * The ad carousel timer continues uninterrupted.
     *
     * If the display is not currently in idle phase (e.g., showing tip selection),
     * the order state is saved and will be applied when returning to idle.
     *
     * @param order Updated order snapshot, or null to clear.
     */
    suspend fun updateOrder(order: SecondaryOrderState?) {
        Log.d(TAG, "updateOrder(hasOrder=${order?.hasItems() == true})")
        lastOrderState = order

        if (currentPhase == DisplayPhase.IDLE) {
            // Delegate to controller which handles incremental update internally
            controller.showAdCarousel(order)
        }
        // If not in IDLE phase, the order will be applied when returning to idle
    }

    /**
     * Show the tip selection UI on the secondary display.
     *
     * Replaces the current content (idle/ads) with an interactive tip selection screen.
     * The customer selects a tip amount or skips; the result is delivered via callbacks.
     *
     * After the customer makes a selection (or cancels), the tip presentation dismisses
     * automatically. The caller should then proceed to [onPaymentStarted].
     *
     * @param subtotal Transaction subtotal in minor currency units (cents).
     * @param currency Currency code (e.g., "USD", "EUR").
     * @param onTipSelected Called with the selected tip amount when customer confirms.
     * @param onCancelled Called when customer skips tip or presentation is dismissed.
     * @return `true` if tip selection was shown, `false` if no display available.
     */
    suspend fun showTipSelection(
        subtotal: BigDecimal,
        currency: String,
        onTipSelected: (BigDecimal) -> Unit,
        onCancelled: () -> Unit
    ): Boolean {
        Log.d(TAG, "showTipSelection(subtotal=$subtotal, currency=$currency)")

        val shown = controller.showTipSelection(
            subtotalAmount = subtotal,
            currency = currency,
            onTipSelected = { tipAmount ->
                currentPhase = DisplayPhase.TIP_COMPLETED
                onTipSelected(tipAmount)
            },
            onCancelled = {
                currentPhase = DisplayPhase.TIP_COMPLETED
                onCancelled()
            }
        )

        updateDisplayAvailability(shown)
        if (shown) {
            currentPhase = DisplayPhase.TIP_SELECTION
        }

        return shown
    }

    /**
     * Dismiss secondary display content before payment processing begins.
     *
     * Call this after tip selection completes (or if no tip is needed) and before
     * initiating the payment transaction. TaPro takes over the secondary display
     * during payment processing to show payment status/animations.
     *
     * The display is cleared so TaPro can use it without conflicts.
     */
    fun onPaymentStarted() {
        Log.d(TAG, "onPaymentStarted() - dismissing content for TaPro")
        controller.dismiss()
        currentPhase = DisplayPhase.PAYMENT_IN_PROGRESS
    }

    /**
     * Restore idle content on the secondary display after payment completes.
     *
     * Call this after the payment result has been processed (whether approved,
     * declined, or failed). Restores the ad carousel with the last known order state.
     *
     * This is a suspend function because showing content on the secondary display
     * may require waiting for the display to become available again.
     */
    suspend fun onPaymentCompleted() {
        Log.d(TAG, "onPaymentCompleted() - restoring idle content")
        currentPhase = DisplayPhase.IDLE
        showIdle(lastOrderState)
    }

    /**
     * Release all resources and dismiss any active presentations.
     *
     * Call this in `onDestroy()` or when the secondary display is no longer needed.
     * After calling release, this instance should not be reused.
     */
    fun release() {
        Log.d(TAG, "release() - cleaning up secondary display resources")
        controller.dismiss()
        lastOrderState = null
        currentPhase = DisplayPhase.RELEASED
        _isDisplayAvailable.value = false
    }

    // ─── Internal Helpers ─────────────────────────────────────────────────────

    /**
     * Update the display availability state based on the result of a show operation.
     */
    private fun updateDisplayAvailability(shown: Boolean) {
        val available = shown || controller.hasSecondaryDisplay()
        if (_isDisplayAvailable.value != available) {
            _isDisplayAvailable.value = available
            Log.d(TAG, "Display availability changed: $available")
        }
    }

    /**
     * Internal state machine phases for tracking display content lifecycle.
     */
    private enum class DisplayPhase {
        /** Ad carousel / order display is showing (default resting state). */
        IDLE,

        /** Tip selection UI is active on the secondary display. */
        TIP_SELECTION,

        /** Tip selection completed, awaiting payment start. */
        TIP_COMPLETED,

        /** Payment in progress — TaPro controls the display. */
        PAYMENT_IN_PROGRESS,

        /** No secondary display detected. */
        UNAVAILABLE,

        /** Manager has been released — do not reuse. */
        RELEASED
    }
}
