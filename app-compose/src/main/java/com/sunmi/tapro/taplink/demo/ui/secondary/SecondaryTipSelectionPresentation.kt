package com.sunmi.tapro.taplink.demo.ui.secondary

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * Controller for managing secondary (USB customer-facing) display content.
 *
 * Responsibilities:
 * 1. Detect secondary display via [DisplayManager]
 * 2. Launch the SUNMI USB screen player (local Intent) with cooldown
 * 3. Wait asynchronously for the Display to register ([DisplayManager.DisplayListener])
 * 4. Show/dismiss [Presentation] instances for ad carousel and tip selection
 *
 * This controller should be held as a singleton (e.g., in DependencyProvider) because
 * it maintains state across screen transitions (which Presentation is currently showing).
 */
class SecondaryTipSelectionController(private val context: Context) {
    companion object {
        private const val TAG = "SecondaryTipController"
        private const val ACTION_SUNMI_USB_SCREEN_PLAYER = "sunmi.intent.action.SunmiUsbScreenPlayer"
        private const val PLAYER_LAUNCH_COOLDOWN_MS = 10_000L
        private const val DISPLAY_READY_TIMEOUT_MS = 5_000L
    }

    private var presentation: Presentation? = null
    private var adPresentation: SecondaryAdCarouselPresentation? = null
    private var lastPlayerLaunchAt = 0L

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Quick synchronous check whether a non-default display exists.
     *
     * **Caution:** Do not use as a precondition for showing content. The display may
     * not be registered yet. Prefer [showAdCarousel] / [showTipSelection] which handle
     * the async wait internally and return `false` when unavailable.
     */
    fun hasSecondaryDisplay(): Boolean = findSecondaryDisplay() != null

    /**
     * Show idle advertisement carousel on the secondary display.
     *
     * - If the carousel is already showing, only the order snapshot is updated
     *   (no flicker, carousel timer continues).
     * - If no secondary display is available within [DISPLAY_READY_TIMEOUT_MS],
     *   returns `false`.
     *
     * @param orderState Current order snapshot; null or empty → full-screen ads.
     * @return `true` if content is displayed, `false` if no secondary display.
     */
    suspend fun showAdCarousel(orderState: SecondaryOrderState? = null): Boolean {
        // Incremental update path: avoid rebuilding Presentation when already showing.
        adPresentation?.let { existing ->
            if (existing.isShowing) {
                existing.updateOrder(orderState)
                return true
            }
        }

        val secondaryDisplay = awaitSecondaryDisplayReady() ?: run {
            Log.d(TAG, "No secondary display found, skip ad carousel")
            return false
        }

        dismiss()

        val newPresentation = SecondaryAdCarouselPresentation(
            outerContext = context,
            display = secondaryDisplay,
            images = listOf(R.mipmap.p3, R.mipmap.payments),
            initialOrder = orderState
        )
        newPresentation.setOnDismissListener { adPresentation = null }

        return showPresentationSafely(newPresentation).also { shown ->
            if (shown) {
                adPresentation = newPresentation
                Log.d(TAG, "Ad carousel shown on displayId=${secondaryDisplay.displayId}, hasOrder=${orderState?.hasItems() == true}")
            }
        }
    }

    /**
     * Show tip selection on the secondary display.
     *
     * The presentation fires exactly one of [onTipSelected] / [onCancelled], then
     * automatically dismisses itself. If the customer dismisses the presentation
     * externally (e.g., display disconnect), [onCancelled] fires.
     *
     * @return `true` if the presentation is shown, `false` if no secondary display.
     */
    suspend fun showTipSelection(
        subtotalAmount: BigDecimal,
        currency: String,
        onTipSelected: (BigDecimal) -> Unit,
        onCancelled: () -> Unit
    ): Boolean {
        val secondaryDisplay = awaitSecondaryDisplayReady() ?: run {
            Log.d(TAG, "No secondary display found, skip tip presentation")
            return false
        }

        dismiss()

        // Ensure exactly-once delivery of the result callback.
        var isCompleted = false
        fun finishWithTip(tip: BigDecimal) {
            if (isCompleted) return
            isCompleted = true
            onTipSelected(tip)
            dismiss()
        }
        fun finishWithCancel() {
            if (isCompleted) return
            isCompleted = true
            onCancelled()
            dismiss()
        }

        val newPresentation = SecondaryTipSelectionPresentation(
            outerContext = context,
            display = secondaryDisplay,
            subtotalAmount = subtotalAmount,
            currency = currency,
            onTipSelected = { finishWithTip(it) },
            onCancelled = { finishWithCancel() }
        )
        newPresentation.setOnDismissListener {
            if (!isCompleted) {
                isCompleted = true
                onCancelled()
            }
            presentation = null
        }

        return showPresentationSafely(newPresentation).also { shown ->
            if (shown) {
                presentation = newPresentation
                Log.d(TAG, "Tip selection shown on displayId=${secondaryDisplay.displayId}")
            } else {
                onCancelled()
            }
        }
    }

    /**
     * Dismiss all secondary presentations safely.
     */
    fun dismiss() {
        presentation?.dismiss()
        presentation = null
        adPresentation?.dismiss()
        adPresentation = null
    }

    // ─── Display Detection ────────────────────────────────────────────────────

    /**
     * Ensure the USB screen player is running and the secondary [Display] is
     * registered in [DisplayManager].
     *
     * Sequence: fast check → launch player (with cooldown) → await DisplayListener.
     */
    private suspend fun awaitSecondaryDisplayReady(): Display? {
        findSecondaryDisplay()?.let { return it }
        launchLocalUsbScreenPlayer()
        return awaitSecondaryDisplayAdded(DISPLAY_READY_TIMEOUT_MS)
    }

    /**
     * Best-effort launch of the local SUNMI USB screen player.
     * Silently ignored when not installed; cooldown prevents repeated launches.
     */
    private fun launchLocalUsbScreenPlayer() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlayerLaunchAt < PLAYER_LAUNCH_COOLDOWN_MS) return
        lastPlayerLaunchAt = now

        runCatching {
            val intent = Intent(ACTION_SUNMI_USB_SCREEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                Log.d(TAG, "Launched local SUNMI USB screen player")
            } else {
                Log.d(TAG, "SUNMI USB screen player not installed on this device")
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to launch local USB screen player: ${error.message}")
        }
    }

    /**
     * Suspend until a secondary display appears in [DisplayManager] or [timeoutMs] elapses.
     */
    private suspend fun awaitSecondaryDisplayAdded(timeoutMs: Long): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return withTimeoutOrNull(timeoutMs.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : DisplayManager.DisplayListener {
                    private fun resumeIfReady() {
                        findSecondaryDisplay()?.takeIf { continuation.isActive }?.let { display ->
                            runCatching { manager.unregisterDisplayListener(this) }
                            continuation.resume(display)
                        }
                    }

                    override fun onDisplayAdded(displayId: Int) = resumeIfReady()
                    override fun onDisplayChanged(displayId: Int) = resumeIfReady()
                    override fun onDisplayRemoved(displayId: Int) = Unit
                }
                manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
                continuation.invokeOnCancellation {
                    runCatching { manager.unregisterDisplayListener(listener) }
                }
                // Race guard: display may have appeared between registration and query.
                findSecondaryDisplay()?.takeIf { continuation.isActive }?.let { display ->
                    runCatching { manager.unregisterDisplayListener(listener) }
                    continuation.resume(display)
                }
            }
        }
    }

    private fun findSecondaryDisplay(): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return manager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY && display.state == Display.STATE_ON
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun showPresentationSafely(presentation: Presentation): Boolean {
        return try {
            presentation.show()
            true
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.e(TAG, "Failed to show presentation: display invalid", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to show presentation: illegal state", e)
            false
        }
    }
}

// ─── Presentation Implementations ─────────────────────────────────────────────

/**
 * Presentation that renders the tip selection UI on a secondary screen.
 */
private class SecondaryTipSelectionPresentation(
    outerContext: Context,
    display: Display,
    private val subtotalAmount: BigDecimal,
    private val currency: String,
    private val onTipSelected: (BigDecimal) -> Unit,
    private val onCancelled: () -> Unit
) : Presentation(outerContext, display) {

    private val composeOwner = PresentationComposeViewTreeOwner()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeOwner.onCreate()

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeViewModelStoreOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setContent {
                TaplinkTheme {
                    SecondaryTipSelectionContent(
                        subtotalAmount = subtotalAmount,
                        currency = currency,
                        onTipSelected = onTipSelected,
                        onCancelled = onCancelled
                    )
                }
            }
        }
        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        composeOwner.onStart()
    }

    override fun onStop() {
        composeOwner.onStop()
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        composeOwner.onDestroy()
        super.onDetachedFromWindow()
    }
}

/**
 * Presentation that renders idle advertisement carousel (with optional order list)
 * on a secondary screen.
 */
internal class SecondaryAdCarouselPresentation(
    outerContext: Context,
    display: Display,
    @DrawableRes private val images: List<Int>,
    initialOrder: SecondaryOrderState?
) : Presentation(outerContext, display) {

    private val composeOwner = PresentationComposeViewTreeOwner()
    private val orderState: MutableState<SecondaryOrderState?> = mutableStateOf(initialOrder)

    /**
     * Update the order snapshot shown alongside the ad carousel.
     * Triggers Compose recomposition without restarting the ad timer.
     */
    fun updateOrder(order: SecondaryOrderState?) {
        orderState.value = order
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeOwner.onCreate()

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeOwner)
            setViewTreeViewModelStoreOwner(composeOwner)
            setViewTreeSavedStateRegistryOwner(composeOwner)
            setContent {
                TaplinkTheme {
                    SecondaryAdCarouselContent(
                        images = images,
                        order = orderState.value
                    )
                }
            }
        }
        setContentView(composeView)
    }

    override fun onStart() {
        super.onStart()
        composeOwner.onStart()
    }

    override fun onStop() {
        composeOwner.onStop()
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        composeOwner.onDestroy()
        super.onDetachedFromWindow()
    }
}
