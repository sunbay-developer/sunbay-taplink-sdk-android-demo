package com.sunmi.tapro.taplink.demo.ui.secondary

import android.app.Presentation
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
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
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Controller for displaying tip selection on a secondary display.
 */
class SecondaryTipSelectionController(private val context: Context) {
    companion object {
        private const val TAG = "SecondaryTipController"
    }

    private var presentation: SecondaryTipSelectionPresentation? = null
    private var adPresentation: SecondaryAdCarouselPresentation? = null

    /**
     * Ask SDK/tapro to ensure USB screen player app is ready before showing Presentation.
     */
    private fun ensureUsbScreenPlayerReady() {
        runCatching {
            DependencyProvider.paymentService.ensureUsbScreenPlayerReady()
        }.onFailure { error ->
            Log.w(TAG, "Failed to request USB screen player readiness: ${error.message}")
        }
    }

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
     * - Automatically replaced by tip selection or dismissed when entering transaction.
     */
    fun showAdCarousel(): Boolean {
        if (adPresentation?.isShowing == true) {
            return true
        }

        dismiss()

        val secondaryDisplay = findSecondaryDisplay()
        if (secondaryDisplay == null) {
            Log.d(TAG, "No secondary display found, skip ad carousel")
            return false
        }

        val newPresentation = SecondaryAdCarouselPresentation(
            outerContext = context,
            display = secondaryDisplay,
            images = listOf(R.mipmap.g1, R.mipmap.g2)
        )
        newPresentation.setOnDismissListener {
            adPresentation = null
        }

        return try {
            newPresentation.show()
            adPresentation = newPresentation
            Log.d(TAG, "Secondary ad carousel shown on displayId=${secondaryDisplay.displayId}")
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
    fun showTipSelection(
        subtotalAmount: BigDecimal,
        currency: String,
        onTipSelected: (BigDecimal) -> Unit,
        onCancelled: () -> Unit
    ): Boolean {
        // 每次展示副屏 Presentation 前先请求 tapro 拉起副屏播放器。
        ensureUsbScreenPlayerReady()

        dismiss()

        val secondaryDisplay = findSecondaryDisplay()
        if (secondaryDisplay == null) {
            Log.d(TAG, "No secondary display found, skip tip presentation")
            return false
        }

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
            // Presentation 没有像 Activity 一样自动注入 ViewTree*Owner，需手动设置，
            // 否则 Compose Recomposer 在初始化时会抛 ViewTreeLifecycleOwner not found。
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
 */
private class SecondaryAdCarouselPresentation(
    outerContext: Context,
    display: Display,
    @DrawableRes private val images: List<Int>
) : Presentation(outerContext, display) {
    private val composeViewTreeOwner = PresentationComposeViewTreeOwner()

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
                    SecondaryAdCarouselContent(images = images)
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
 * - Activity/Fragment 场景下 ViewTreeLifecycleOwner 等由框架自动注入；
 * - Presentation 不是标准 Activity 内容视图容器，需我们显式提供 owner。
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
 */
@Composable
private fun SecondaryAdCarouselContent(@DrawableRes images: List<Int>) {
    if (images.isEmpty()) {
        return
    }

    var currentIndex by remember { mutableIntStateOf(0) }

    // 固定间隔切换广告图，保持副屏处于循环播放状态。
    LaunchedEffect(images) {
        while (true) {
            delay(3000L)
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
