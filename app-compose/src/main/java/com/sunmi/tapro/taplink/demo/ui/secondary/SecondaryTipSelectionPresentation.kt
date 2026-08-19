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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.sunmi.tapro.taplink.demo.R
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

/**
 * One line item rendered on the secondary-screen order list.
 *
 * @property name Product name
 * @property quantity Quantity
 * @property unitPrice Unit price
 * @property subtotal Subtotal for this line item (unit price × quantity)
 */
data class SecondaryOrderLine(
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val subtotal: BigDecimal
)

/**
 * Order summary snapshot pushed to the secondary display.
 *
 * Business rule:
 * - When lines is non-empty, the left 30% of the secondary screen shows the order list and the right 70% continues the ad carousel.
 * - When lines is empty (no items), the ad carousel fills the entire secondary screen.
 *
 * @property lines Order line items
 * @property totalAmount Order total amount
 * @property totalItemCount Total item count
 * @property currency Currency symbol/code
 */
data class SecondaryOrderState(
    val lines: List<SecondaryOrderLine>,
    val totalAmount: BigDecimal,
    val totalItemCount: Int,
    val currency: String
) {
    /** Whether it contains valid order content. */
    fun hasItems(): Boolean = lines.isNotEmpty()
}

/**
 * Controller for displaying tip selection on a secondary display.
 */
class SecondaryTipSelectionController(private val context: Context) {
    companion object {
        private const val TAG = "SecondaryTipController"

        /** System Intent action for the SUNMI USB secondary screen player. */
        private const val ACTION_SUNMI_USB_SCREEN_PLAYER = "sunmi.intent.action.SunmiUsbScreenPlayer"

        /** Cooldown time for relaunching the secondary screen player. */
        private const val PLAYER_LAUNCH_COOLDOWN_MS = 10_000L

        /** Timeout waiting for the secondary display to appear in DisplayManager after launching the player. */
        private const val DISPLAY_READY_TIMEOUT_MS = 5_000L
    }

    private var presentation: SecondaryTipSelectionPresentation? = null
    private var adPresentation: SecondaryAdCarouselPresentation? = null
    private var lastPlayerLaunchAt = 0L

    /**
     * Returns true when at least one non-default display is available.
     */
    fun hasSecondaryDisplay(): Boolean {
        return findSecondaryDisplay() != null
    }

    /**
     * Show idle advertisement carousel on secondary display.
     *
     * Business rule:
     * - Used as default secondary-screen content when app is idle.
     * - When the passed [orderState] contains items, the left 30% of the secondary screen shows the order list and the right 70% continues the ad carousel.
     * - If the secondary screen is already showing ads, only refresh the order content incrementally to avoid carousel flicker caused by rebuilding the Presentation.
     * - Automatically replaced by tip selection or dismissed when entering transaction.
     *
     * @param orderState Current order snapshot; when null or empty, the secondary screen plays ads in full screen.
     */
    suspend fun showAdCarousel(orderState: SecondaryOrderState? = null): Boolean {
        // The secondary screen is already playing ads: update the order content incrementally without rebuilding.
        adPresentation?.let { existing ->
            if (existing.isShowing) {
                existing.updateOrder(orderState)
                return true
            }
        }

        // The secondary screen player may be taken over by tapro during a transaction; wait for it to become ready again before obtaining the Display.
        val secondaryDisplay = awaitSecondaryDisplayReady()
        if (secondaryDisplay == null) {
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
        newPresentation.setOnDismissListener {
            adPresentation = null
        }

        return try {
            newPresentation.show()
            adPresentation = newPresentation
            Log.d(TAG, "Secondary ad carousel shown on displayId=${secondaryDisplay.displayId}, hasOrder=${orderState?.hasItems() == true}")
            true
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.e(TAG, "Failed to show ad carousel due to invalid display", e)
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to show ad carousel due to illegal state", e)
            false
        }
    }

    /**
     * Show tip selection on the secondary display.
     *
     * @return true if the presentation is shown, false when no secondary display exists.
     */
    suspend fun showTipSelection(
        subtotalAmount: BigDecimal,
        currency: String,
        onTipSelected: (BigDecimal) -> Unit,
        onCancelled: () -> Unit
    ): Boolean {
        val secondaryDisplay = awaitSecondaryDisplayReady()
        if (secondaryDisplay == null) {
            Log.d(TAG, "No secondary display found, skip tip presentation")
            return false
        }

        dismiss()

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

        return try {
            newPresentation.show()
            presentation = newPresentation
            Log.d(TAG, "Secondary tip presentation shown on displayId=${secondaryDisplay.displayId}")
            true
        } catch (e: WindowManager.InvalidDisplayException) {
            Log.e(TAG, "Failed to show secondary tip presentation due to invalid display", e)
            onCancelled()
            false
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Failed to show secondary tip presentation due to illegal state", e)
            onCancelled()
            false
        }
    }

    /**
     * Dismiss current presentation safely.
     */
    fun dismiss() {
        presentation?.dismiss()
        presentation = null
        adPresentation?.dismiss()
        adPresentation = null
    }

    /**
     * Make sure the USB screen player is running and the secondary [Display] is
     * actually registered in [DisplayManager] before any Presentation is shown.
     *
     * Secondary screen rendering is fully handled by this app: Android [Presentation] can only render to the **local**
     * Display in [DisplayManager], so here we launch the SUNMI USB screen player locally
     * and wait for the Display to register via [DisplayManager.DisplayListener], instead of sending commands to tapro.
     *
     * @return Available secondary display, or null on timeout or unsupported device.
     */
    private suspend fun awaitSecondaryDisplayReady(): Display? {
        findSecondaryDisplay()?.let { return it }

        launchLocalUsbScreenPlayer()

        return awaitSecondaryDisplayAdded(DISPLAY_READY_TIMEOUT_MS)
    }

    /**
     * Best-effort launch of the local SUNMI USB screen player app.
     *
     * Silently ignored when the system app is not installed on the device; includes a cooldown to avoid repeated launches when the secondary screen is unavailable.
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
                            unregisterAndResume(display)
                        }
                    }

                    private fun unregisterAndResume(display: Display) {
                        runCatching { manager.unregisterDisplayListener(this) }
                        continuation.resume(display)
                    }

                    override fun onDisplayAdded(displayId: Int) = resumeIfReady()
                    override fun onDisplayChanged(displayId: Int) = resumeIfReady()
                    override fun onDisplayRemoved(displayId: Int) = Unit
                }
                manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
                continuation.invokeOnCancellation {
                    runCatching { manager.unregisterDisplayListener(listener) }
                }
                // A new display may have appeared between registration and query; perform an extra check to avoid missing events.
                findSecondaryDisplay()?.takeIf { continuation.isActive }?.let { display ->
                    runCatching { manager.unregisterDisplayListener(listener) }
                    continuation.resume(display)
                }
            }
        }
    }

    /**
     * Find the first available non-default display.
     */
    private fun findSecondaryDisplay(): Display? {
        val manager = context.getSystemService(DisplayManager::class.java) ?: return null
        return manager.displays.firstOrNull { display ->
            display.displayId != Display.DEFAULT_DISPLAY && display.state == Display.STATE_ON
        }
    }
}

/**
 * Presentation used to render the tip selection UI on a secondary screen.
 */
private class SecondaryTipSelectionPresentation(
    outerContext: Context,
    display: Display,
    private val subtotalAmount: BigDecimal,
    private val currency: String,
    private val onTipSelected: (BigDecimal) -> Unit,
    private val onCancelled: () -> Unit
) : Presentation(outerContext, display) {
    private val composeViewTreeOwner = PresentationComposeViewTreeOwner()

    /**
     * Build Compose content when the presentation is created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeViewTreeOwner.onCreate()

        val composeView = ComposeView(context).apply {
            // Presentation does not automatically inject ViewTree*Owner like Activity, so it must be set manually,
            // otherwise the Compose Recomposer will throw "ViewTreeLifecycleOwner not found" during initialization.
            setViewTreeLifecycleOwner(composeViewTreeOwner)
            setViewTreeViewModelStoreOwner(composeViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(composeViewTreeOwner)
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

    /**
     * Promote Compose lifecycle to RESUMED when presentation is visible.
     */
    override fun onStart() {
        super.onStart()
        composeViewTreeOwner.onStart()
    }

    /**
     * Move lifecycle down and release ViewModelStore when presentation is dismissed.
     */
    override fun onStop() {
        composeViewTreeOwner.onStop()
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        composeViewTreeOwner.onDestroy()
        super.onDetachedFromWindow()
    }
}

/**
 * Presentation used to render idle advertisement carousel on a secondary screen.
 *
 * When order content exists, the left 30% renders the order list and the right 70% renders the ad carousel;
 * otherwise the ad carousel fills the entire secondary screen.
 */
private class SecondaryAdCarouselPresentation(
    outerContext: Context,
    display: Display,
    @DrawableRes private val images: List<Int>,
    initialOrder: SecondaryOrderState?
) : Presentation(outerContext, display) {
    private val composeViewTreeOwner = PresentationComposeViewTreeOwner()

    // Order state is held as Compose State for incremental refresh without rebuilding the Presentation.
    private val orderState: MutableState<SecondaryOrderState?> = mutableStateOf(initialOrder)

    /**
     * Update the order snapshot shown alongside the ad carousel.
     *
     * Only updates the State, triggering a local Compose recomposition without restarting the ad carousel timer.
     */
    fun updateOrder(order: SecondaryOrderState?) {
        orderState.value = order
    }

    /**
     * Build ad carousel Compose content when the presentation is created.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        composeViewTreeOwner.onCreate()

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(composeViewTreeOwner)
            setViewTreeViewModelStoreOwner(composeViewTreeOwner)
            setViewTreeSavedStateRegistryOwner(composeViewTreeOwner)
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

    /**
     * Promote Compose lifecycle to RESUMED when presentation is visible.
     */
    override fun onStart() {
        super.onStart()
        composeViewTreeOwner.onStart()
    }

    /**
     * Move lifecycle down and release ViewModelStore when presentation is dismissed.
     */
    override fun onStop() {
        composeViewTreeOwner.onStop()
        super.onStop()
    }

    override fun onDetachedFromWindow() {
        composeViewTreeOwner.onDestroy()
        super.onDetachedFromWindow()
    }
}

/**
 * ViewTree owner used by ComposeView inside Presentation.
 *
 * Why it exists:
 * - In Activity/Fragment scenarios, ViewTreeLifecycleOwner etc. are automatically injected by the framework;
 * - Presentation is not a standard Activity content view container, so we need to explicitly provide the owner.
 */
private class PresentationComposeViewTreeOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInternal = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var created = false
    private var started = false
    private var destroyed = false

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = viewModelStoreInternal

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /**
     * Initialize saved-state and lifecycle once before ComposeView attaches.
     */
    fun onCreate() {
        if (created || destroyed) return
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        created = true
    }

    /**
     * Enter visible state for Compose runtime.
     */
    fun onStart() {
        if (!created || destroyed || started) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        started = true
    }

    /**
     * Leave visible state while allowing potential re-show.
     */
    fun onStop() {
        if (!created || destroyed || !started) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        started = false
    }

    /**
     * Final cleanup to avoid leaking ViewModels from secondary screen.
     */
    fun onDestroy() {
        if (destroyed) return
        if (started) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            started = false
        }
        if (created) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStoreInternal.clear()
        destroyed = true
    }
}

/**
 * Tip selection UI content for secondary display.
 */
@Composable
private fun SecondaryTipSelectionContent(
    subtotalAmount: BigDecimal,
    currency: String,
    onTipSelected: (BigDecimal) -> Unit,
    onCancelled: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Select Tip",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Subtotal: ${formatCurrency(subtotalAmount, currency)}",
            style = MaterialTheme.typography.titleMedium
        )

        TipActionButton(label = "No Tip") { onTipSelected(BigDecimal.ZERO) }
        TipActionButton(label = "10%") { onTipSelected(calculateTip(subtotalAmount, BigDecimal("0.10"))) }
        TipActionButton(label = "15%") { onTipSelected(calculateTip(subtotalAmount, BigDecimal("0.15"))) }
        TipActionButton(label = "20%") { onTipSelected(calculateTip(subtotalAmount, BigDecimal("0.20"))) }

        OutlinedButton(
            onClick = onCancelled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Text("Cancel")
        }
    }
}

/**
 * Reusable button for each tip option.
 */
@Composable
private fun TipActionButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label)
    }
}

/**
 * Calculate tip amount with half-up rounding to 2 decimals.
 */
private fun calculateTip(subtotalAmount: BigDecimal, rate: BigDecimal): BigDecimal {
    return subtotalAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP)
}

/**
 * Format currency for customer-facing secondary screen.
 */
private fun formatCurrency(amount: BigDecimal, currency: String): String {
    return "$currency ${amount.setScale(2, RoundingMode.HALF_UP)}"
}

/**
 * Idle advertisement carousel content for secondary display.
 *
 * Layout rules:
 * - When [order] contains items: left 30% shows the order list, right 70% shows the ad carousel.
 * - Otherwise: the ad carousel fills the entire secondary screen.
 */
@Composable
private fun SecondaryAdCarouselContent(
    @DrawableRes images: List<Int>,
    order: SecondaryOrderState?
) {
    if (order != null && order.hasItems()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left 30%: order list
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.3f)
            ) {
                SecondaryOrderListContent(order = order)
            }
            // Right 70%: ad carousel
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.7f)
            ) {
                AdCarousel(images = images)
            }
        }
    } else {
        AdCarousel(images = images)
    }
}

/**
 * Reusable advertisement carousel that loops through [images].
 */
@Composable
private fun AdCarousel(@DrawableRes images: List<Int>) {
    if (images.isEmpty()) {
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    // Switches ads at fixed intervals to keep the secondary screen looping.
    LaunchedEffect(images) {
        while (true) {
            delay(5000L)
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    Card(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = images[currentIndex]),
            contentDescription = "Advertisement ${currentIndex + 1}",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

/**
 * Customer-facing order list shown on the left portion of the secondary screen.
 *
 * Displays each product's name, quantity, unit price, and subtotal, and summarizes item count and total amount at the bottom.
 */
@Composable
private fun SecondaryOrderListContent(order: SecondaryOrderState) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Order",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(top = 8.dp))

            // The product list is scrollable to avoid overflowing the secondary screen when there are many items.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(order.lines) { line ->
                    SecondaryOrderLineRow(line = line, currency = order.currency)
                }
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.padding(top = 8.dp))

            // Summary: item count + total amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Items: ${order.totalItemCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.padding(top = 4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatCurrency(order.totalAmount, order.currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Single order line row: name, quantity × unit price, subtotal.
 */
@Composable
private fun SecondaryOrderLineRow(line: SecondaryOrderLine, currency: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${line.quantity} × ${formatCurrency(line.unitPrice, currency)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = formatCurrency(line.subtotal, currency),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}
