package com.sunmi.tapro.taplink.demo.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

private const val DEBOUNCE_INTERVAL_MS = 500L

/**
 * Returns a debounced wrapper around [onClick] that ignores rapid consecutive taps
 * within [debounceMs] milliseconds. Use this instead of raw lambdas for all clickable
 * UI elements to prevent double-tap and navigation race conditions.
 */
@Composable
fun rememberDebouncedClick(
    debounceMs: Long = DEBOUNCE_INTERVAL_MS,
    onClick: () -> Unit,
): () -> Unit {
    // Anchor to the view so the lambda is stable across recompositions.
    val view = LocalView.current
    return remember(view, onClick) {
        var lastClick = 0L
        {
            val now = System.currentTimeMillis()
            if (now - lastClick >= debounceMs) {
                lastClick = now
                onClick()
            }
        }
    }
}
