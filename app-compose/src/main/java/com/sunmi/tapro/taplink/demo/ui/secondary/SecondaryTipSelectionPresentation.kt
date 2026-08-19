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
import com.sunmi.tapro.taplink.demo.di.DependencyProvider
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme
import kotlinx.coroutines.delay
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One line item rendered on the secondary-screen order list.
 *
 * @property name 商品名称
 * @property quantity 数量
 * @property unitPrice 单价
 * @property subtotal 该商品小计（单价 × 数量）
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
 * - 当 lines 非空时，副屏左侧 30% 显示订单清单，右侧 70% 继续广告轮播。
 * - 当 lines 为空（无商品）时，副屏全屏显示广告轮播。
 *
 * @property lines 订单行项目
 * @property totalAmount 订单合计金额
 * @property totalItemCount 商品总件数
 * @property currency 货币符号/代码
 */
data class SecondaryOrderState(
    val lines: List<SecondaryOrderLine>,
    val totalAmount: BigDecimal,
    val totalItemCount: Int,
    val currency: String
) {
    /** 是否包含有效订单内容。 */
    fun hasItems(): Boolean = lines.isNotEmpty()
}

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
     * - 当传入的 [orderState] 含有商品时，副屏左侧 30% 展示订单清单，右侧 70% 继续广告轮播。
     * - 若副屏已在展示广告，则仅增量刷新订单内容，避免重建 Presentation 导致轮播闪烁。
     * - Automatically replaced by tip selection or dismissed when entering transaction.
     *
     * @param orderState 当前订单快照；为 null 或无商品时副屏全屏播放广告。
     */
    fun showAdCarousel(orderState: SecondaryOrderState? = null): Boolean {
        // 副屏已在播放广告：直接增量更新订单内容即可，无需重建。
        adPresentation?.let { existing ->
            if (existing.isShowing) {
                existing.updateOrder(orderState)
                return true
            }
        }

        // 副屏播放器可能在交易期间被 tapro 接管，重新拉起后再展示。
        ensureUsbScreenPlayerReady()

        dismiss()

        val secondaryDisplay = findSecondaryDisplay()
        if (secondaryDisplay == null) {
            Log.d(TAG, "No secondary display found, skip ad carousel")
            return false
        }

        val newPresentation = SecondaryAdCarouselPresentation(
            outerContext = context,
            display = secondaryDisplay,
            images = listOf(R.mipmap.g1, R.mipmap.g2),
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
 *
 * 当存在订单内容时，左侧 30% 渲染订单清单，右侧 70% 渲染广告轮播；
 * 否则广告轮播占满整个副屏。
 */
private class SecondaryAdCarouselPresentation(
    outerContext: Context,
    display: Display,
    @DrawableRes private val images: List<Int>,
    initialOrder: SecondaryOrderState?
) : Presentation(outerContext, display) {
    private val composeViewTreeOwner = PresentationComposeViewTreeOwner()

    // 订单状态以 Compose State 持有，便于在不重建 Presentation 的情况下增量刷新。
    private val orderState: MutableState<SecondaryOrderState?> = mutableStateOf(initialOrder)

    /**
     * Update the order snapshot shown alongside the ad carousel.
     *
     * 仅更新 State，触发 Compose 局部重组，不会重启广告轮播计时器。
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
 *
 * 布局规则：
 * - [order] 含有商品时：左侧 30% 显示订单清单，右侧 70% 显示广告轮播。
 * - 否则：广告轮播占满整个副屏。
 */
@Composable
private fun SecondaryAdCarouselContent(
    @DrawableRes images: List<Int>,
    order: SecondaryOrderState?
) {
    if (order != null && order.hasItems()) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 左侧 30%：订单清单
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.3f)
            ) {
                SecondaryOrderListContent(order = order)
            }
            // 右侧 70%：广告轮播
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

    // 固定间隔切换广告图，保持副屏处于循环播放状态。
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
 * 展示每个商品的名称、数量、单价、小计，并在底部汇总件数与合计金额。
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

            // 商品清单可滚动，避免商品过多时溢出副屏。
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

            // 汇总：件数 + 合计金额
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
 * Single order line row: 名称、数量 × 单价、小计。
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
