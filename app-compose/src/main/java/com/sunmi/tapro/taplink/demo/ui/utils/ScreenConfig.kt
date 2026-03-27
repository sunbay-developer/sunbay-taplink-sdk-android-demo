package com.sunmi.tapro.taplink.demo.ui.utils

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

/**
 * Screen orientation: portrait or landscape.
 */
enum class ScreenOrientation {
    Portrait,
    Landscape
}

/**
 * Screen size breakpoint for adaptive layout.
 */
enum class ScreenSize {
    Compact,
    Medium,
    Expanded
}

/**
 * Combined screen configuration for adaptive layouts.
 * Used to choose portrait vs landscape layout and size-based tweaks.
 */
data class ScreenConfig(
    val orientation: ScreenOrientation,
    val widthSize: ScreenSize,
    val heightSize: ScreenSize
) {
    /** True when the device is in landscape orientation. */
    val isLandscape: Boolean
        get() = orientation == ScreenOrientation.Landscape
}

/**
 * Maps [WindowWidthSizeClass] / [WindowHeightSizeClass] to [ScreenSize].
 */
private fun mapToScreenSize(widthSizeClass: WindowWidthSizeClass): ScreenSize = when (widthSizeClass) {
    WindowWidthSizeClass.Compact -> ScreenSize.Compact
    WindowWidthSizeClass.Medium -> ScreenSize.Medium
    WindowWidthSizeClass.Expanded -> ScreenSize.Expanded
    else -> ScreenSize.Compact
}

private fun mapToScreenSize(heightSizeClass: WindowHeightSizeClass): ScreenSize = when (heightSizeClass) {
    WindowHeightSizeClass.Compact -> ScreenSize.Compact
    WindowHeightSizeClass.Medium -> ScreenSize.Medium
    WindowHeightSizeClass.Expanded -> ScreenSize.Expanded
    else -> ScreenSize.Compact
}

/**
 * Remembers the current [ScreenConfig] based on configuration and window size class.
 * Portrait devices get [ScreenOrientation.Portrait]; layout code can branch on [ScreenConfig.isLandscape]
 * to keep portrait behavior unchanged.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun rememberScreenConfig(): ScreenConfig {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val activity = context as? Activity
    val windowSizeClass: WindowSizeClass? = if (activity != null) {
        calculateWindowSizeClass(activity)
    } else {
        null
    }

    return remember(configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp, windowSizeClass) {
        val orientation = if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ScreenOrientation.Landscape
        } else {
            ScreenOrientation.Portrait
        }
        val widthSize = windowSizeClass?.let { mapToScreenSize(it.widthSizeClass) } ?: ScreenSize.Compact
        val heightSize = windowSizeClass?.let { mapToScreenSize(it.heightSizeClass) } ?: ScreenSize.Compact
        ScreenConfig(
            orientation = orientation,
            widthSize = widthSize,
            heightSize = heightSize
        )
    }
}
