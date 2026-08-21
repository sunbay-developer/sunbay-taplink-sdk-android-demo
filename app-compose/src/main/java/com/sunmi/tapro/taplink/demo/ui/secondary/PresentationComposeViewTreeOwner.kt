package com.sunmi.tapro.taplink.demo.ui.secondary

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * Provides [LifecycleOwner], [ViewModelStoreOwner], and [SavedStateRegistryOwner]
 * for a [ComposeView][androidx.compose.ui.platform.ComposeView] hosted inside an
 * Android [Presentation][android.app.Presentation].
 *
 * ## Why this is needed
 *
 * In Activity/Fragment scenarios, `ViewTreeLifecycleOwner` etc. are automatically
 * injected by the framework. `Presentation` is *not* a standard Activity content
 * view container, so Compose's [Recomposer] cannot locate the required owners
 * and throws `ViewTreeLifecycleOwner not found`.
 *
 * ## Usage
 *
 * ```kotlin
 * class MyPresentation(context: Context, display: Display) : Presentation(context, display) {
 *     private val composeOwner = PresentationComposeViewTreeOwner()
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         composeOwner.onCreate()
 *         val composeView = ComposeView(context).apply {
 *             setViewTreeLifecycleOwner(composeOwner)
 *             setViewTreeViewModelStoreOwner(composeOwner)
 *             setViewTreeSavedStateRegistryOwner(composeOwner)
 *             setContent { /* ... */ }
 *         }
 *         setContentView(composeView)
 *     }
 *
 *     override fun onStart() { super.onStart(); composeOwner.onStart() }
 *     override fun onStop()  { composeOwner.onStop(); super.onStop() }
 *     override fun onDetachedFromWindow() { composeOwner.onDestroy(); super.onDetachedFromWindow() }
 * }
 * ```
 */
class PresentationComposeViewTreeOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val viewModelStoreInternal = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)
    private var created = false
    private var started = false
    private var destroyed = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = viewModelStoreInternal
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    /**
     * Initialize saved-state and lifecycle once before ComposeView attaches.
     * Call in [Presentation.onCreate].
     */
    fun onCreate() {
        if (created || destroyed) return
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        created = true
    }

    /**
     * Enter visible state for Compose runtime.
     * Call in [Presentation.onStart].
     */
    fun onStart() {
        if (!created || destroyed || started) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        started = true
    }

    /**
     * Leave visible state while allowing potential re-show.
     * Call in [Presentation.onStop].
     */
    fun onStop() {
        if (!created || destroyed || !started) return
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        started = false
    }

    /**
     * Final cleanup to avoid leaking ViewModels from secondary screen.
     * Call in [Presentation.onDetachedFromWindow].
     */
    fun onDestroy() {
        if (destroyed) return
        if (started) onStop()
        if (created) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        viewModelStoreInternal.clear()
        destroyed = true
    }
}
