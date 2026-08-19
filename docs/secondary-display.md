# Secondary Display Integration Guide

How the demo drives a USB customer-facing display (advertisement carousel, order
list, tip selection) and how to reproduce it in your own POS application.

Reference implementation:
`app-compose/src/main/java/com/sunmi/tapro/taplink/demo/ui/secondary/SecondaryTipSelectionPresentation.kt`

---

## 1. Ownership model

Secondary-display rendering is **entirely the integrator application's
responsibility**. It is not a TapLink protocol feature.

| Concern | Owner |
|---|---|
| Detecting the secondary `Display` | Integrator app (Android `DisplayManager`) |
| Rendering content on it | Integrator app (Android `Presentation`) |
| Launching the SUNMI USB screen player | Integrator app (local `Intent`) |
| Payment transaction | TapLink SDK ↔ TaPro |

The reason is a hard Android constraint: **`Presentation` can only render to a
`Display` that is registered in the *calling process's own* `DisplayManager`.**
A remote terminal cannot expose a display to your process. The USB customer
display must therefore be physically attached to the device running your app.

> The SDK exposes `TaplinkSDK.openUsbScreenPlayer(PaymentCallback)`, which asks
> TaPro to open *its* screen player. That is only relevant when TaPro runs on the
> same device as your app (App-to-App mode). **Never use its callback as the
> signal that your `Display` is ready** — it says nothing about your own
> `DisplayManager`.

---

## 2. Required sequence

```
1. Fast path      → DisplayManager.getDisplays() already contains a non-default,
                    STATE_ON display?  → use it
2. Launch player  → startActivity(Intent("sunmi.intent.action.SunmiUsbScreenPlayer"))
3. Wait           → DisplayManager.DisplayListener.onDisplayAdded / onDisplayChanged
                    (with timeout, e.g. 5s)
4. Render         → Presentation(context, display).show()
```

**The most common integration bug** is skipping step 3: launching the player and
immediately calling `getDisplays()`. The display is registered asynchronously
after the player app starts, so the query returns only the primary display and
`Presentation` is never shown.

### Waiting for the display

```kotlin
private suspend fun awaitSecondaryDisplayAdded(timeoutMs: Long): Display? {
    val manager = context.getSystemService(DisplayManager::class.java) ?: return null
    return withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            val listener = object : DisplayManager.DisplayListener {
                private fun resumeIfReady() {
                    if (!continuation.isActive) return
                    val display = findSecondaryDisplay() ?: return
                    manager.unregisterDisplayListener(this)
                    continuation.resume(display)
                }
                override fun onDisplayAdded(displayId: Int) = resumeIfReady()
                override fun onDisplayChanged(displayId: Int) = resumeIfReady()
                override fun onDisplayRemoved(displayId: Int) = Unit
            }
            manager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
            continuation.invokeOnCancellation { manager.unregisterDisplayListener(listener) }
            // Close the race between registration and an already-added display.
            findSecondaryDisplay()?.let { display ->
                if (continuation.isActive) {
                    manager.unregisterDisplayListener(listener)
                    continuation.resume(display)
                }
            }
        }
    }
}

private fun findSecondaryDisplay(): Display? =
    context.getSystemService(DisplayManager::class.java)?.displays?.firstOrNull {
        it.displayId != Display.DEFAULT_DISPLAY && it.state == Display.STATE_ON
    }
```

Because this is asynchronous, the public entry points (`showAdCarousel`,
`showTipSelection`) are **suspend functions**. Do not expose a synchronous
`hasSecondaryDisplay()` check as a precondition — it will report `false` before
the player has started. Call the suspend function and fall back to the primary
screen when it returns `false`.

---

## 3. Rendering with Compose inside `Presentation`

`Presentation` is not an `Activity`, so it does not install the
`ViewTree*Owner`s that Compose requires. Set them manually or Compose throws
`ViewTreeLifecycleOwner not found`:

```kotlin
val composeView = ComposeView(context).apply {
    setViewTreeLifecycleOwner(owner)
    setViewTreeViewModelStoreOwner(owner)
    setViewTreeSavedStateRegistryOwner(owner)
    setContent { /* ... */ }
}
setContentView(composeView)
```

See `PresentationComposeViewTreeOwner` in the reference file for a minimal
`LifecycleOwner` + `ViewModelStoreOwner` + `SavedStateRegistryOwner` bundle
driven from `onCreate` / `onStart` / `onStop` / `onDetachedFromWindow`.

---

## 4. Lifecycle rules

- **Update, don't rebuild.** When only the order content changes, push it into a
  Compose `MutableState` held by the running `Presentation`. Recreating the
  `Presentation` restarts the ad carousel timer and causes visible flicker.
- **Dismiss before the transaction starts**, and restore the idle carousel when
  the transaction reaches a terminal state.
- **Dismiss on `onDestroy`** (or when the composable leaves composition) to avoid
  leaking the window.
- **Guard against display loss.** `Presentation.show()` throws
  `WindowManager.InvalidDisplayException` if the display disappears between
  detection and display; catch it and fall back to the primary screen.

---

## 5. Customer-facing tip selection

The demo asks for the tip on the customer display before starting the payment:

1. User taps *Pay*.
2. App calls `showTipSelection(subtotal, currency, onTipSelected, onCancelled)`.
3. If it returns `false` (no secondary display), the app runs the normal
   on-terminal tip flow instead.
4. On selection, the tip is written into the transaction request as a top-level
   `tipAmount`, in the **smallest currency unit** (cents), and the payment starts.

Ensure `onTipSelected` / `onCancelled` fire exactly once — the demo guards this
with an `isCompleted` flag shared with the dismiss listener.

---

## 6. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `findSecondaryDisplay()` always returns `null` | Queried before the player registered the display | Wait via `DisplayListener` (§2) |
| Display never appears, even after waiting | Display is attached to the TaPro device, not to this device | `Presentation` cannot be used; render the content on TaPro instead |
| `sunmi.intent.action.SunmiUsbScreenPlayer` cannot be resolved | The SUNMI screen player app is not installed on this device | Skip silently; treat the secondary display as unavailable |
| `ViewTreeLifecycleOwner not found` | Compose used inside `Presentation` without owners | Set the three `ViewTree*Owner`s (§3) |
| Ad carousel restarts on every order change | `Presentation` rebuilt instead of updated | Push updates into a `MutableState` (§4) |
