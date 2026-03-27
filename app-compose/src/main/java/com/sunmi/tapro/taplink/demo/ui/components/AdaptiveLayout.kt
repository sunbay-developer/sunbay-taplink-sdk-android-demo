package com.sunmi.tapro.taplink.demo.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig

/**
 * Adaptive two-pane layout: side-by-side in landscape, stacked in portrait.
 * Portrait behavior is unchanged (first on top, second below).
 *
 * @param first Content for the first pane (left in landscape, top in portrait).
 * @param second Content for the second pane (right in landscape, bottom in portrait).
 * @param modifier Modifier for the root layout.
 */
@Composable
fun AdaptiveTwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenConfig = rememberScreenConfig()
    if (screenConfig.isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                first()
            }
            Box(modifier = Modifier.weight(1f)) {
                second()
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                first()
            }
            Box(modifier = Modifier.weight(1f)) {
                second()
            }
        }
    }
}

/**
 * Wraps content with optional max width constraint for landscape.
 * In portrait, content uses full width. In landscape, content is limited to [maxWidth] and centered.
 *
 * @param maxWidth Maximum width in landscape (default 1200.dp).
 * @param content The composable content to display.
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AdaptiveContent(
    maxWidth: Dp = 1200.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val screenConfig = rememberScreenConfig()
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val widthConstraint = if (screenConfig.isLandscape) {
            Modifier.widthIn(max = maxWidth.coerceAtMost(maxWidth))
        } else {
            Modifier
        }
        Box(modifier = widthConstraint.fillMaxWidth()) {
            content()
        }
    }
}
