package com.sunmi.tapro.taplink.demo.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Primary colors - Modern restaurant theme with warm tones
val PrimaryOrange = Color(0xFFFF6B35)
val PrimaryOrangeLight = Color(0xFFFF9B7A)
val PrimaryOrangeContainer = Color(0xFFFFE5DC)
val OnPrimaryOrange = Color.White
val OnPrimaryOrangeContainer = Color(0xFF3D1500)

// Secondary colors - Complementary warm tones
val SecondaryBrown = Color(0xFF6B4423)
val SecondaryBrownLight = Color(0xFFB8956A)
val SecondaryBrownContainer = Color(0xFFFFDDB3)
val OnSecondaryBrown = Color.White
val OnSecondaryBrownContainer = Color(0xFF251A00)

// Tertiary colors - Accent green for freshness
val TertiaryGreen = Color(0xFF4CAF50)
val TertiaryGreenLight = Color(0xFF80E27E)
val TertiaryGreenContainer = Color(0xFFC8E6C9)
val OnTertiaryGreen = Color.White
val OnTertiaryGreenContainer = Color(0xFF1B5E20)

// Error colors
val ErrorRed = Color(0xFFD32F2F)
val ErrorRedLight = Color(0xFFEF5350)
val ErrorRedContainer = Color(0xFFFFCDD2)
val OnErrorRed = Color.White
val OnErrorRedContainer = Color(0xFF5F2120)

// Background colors - Clean and modern
val BackgroundLight = Color(0xFFFFFBFF)
val OnBackgroundLight = Color(0xFF1F1B16)
val SurfaceLight = Color(0xFFFFFBFF)
val OnSurfaceLight = Color(0xFF1F1B16)
val SurfaceVariantLight = Color(0xFFF5F1EC)
val OnSurfaceVariantLight = Color(0xFF4F4539)

val BackgroundDark = Color(0xFF1F1B16)
val OnBackgroundDark = Color(0xFFEAE1D9)
val SurfaceDark = Color(0xFF1F1B16)
val OnSurfaceDark = Color(0xFFEAE1D9)
val SurfaceVariantDark = Color(0xFF4F4539)
val OnSurfaceVariantDark = Color(0xFFD3C4B4)

// Outline colors
val OutlineLight = Color(0xFF817567)
val OutlineVariantLight = Color(0xFFD3C4B4)
val OutlineDark = Color(0xFF9C8F80)
val OutlineVariantDark = Color(0xFF4F4539)

// Light color scheme for restaurant POS
val LightColorScheme = lightColorScheme(
    primary = PrimaryOrange,
    onPrimary = OnPrimaryOrange,
    primaryContainer = PrimaryOrangeContainer,
    onPrimaryContainer = OnPrimaryOrangeContainer,
    secondary = SecondaryBrown,
    onSecondary = OnSecondaryBrown,
    secondaryContainer = SecondaryBrownContainer,
    onSecondaryContainer = OnSecondaryBrownContainer,
    tertiary = TertiaryGreen,
    onTertiary = OnTertiaryGreen,
    tertiaryContainer = TertiaryGreenContainer,
    onTertiaryContainer = OnTertiaryGreenContainer,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorRedContainer,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight
)

// Dark color scheme for restaurant POS
val DarkColorScheme = darkColorScheme(
    primary = PrimaryOrangeLight,
    onPrimary = Color(0xFF5A1A00),
    primaryContainer = Color(0xFF7F2B00),
    onPrimaryContainer = PrimaryOrangeContainer,
    secondary = SecondaryBrownLight,
    onSecondary = Color(0xFF3E2D16),
    secondaryContainer = Color(0xFF56422A),
    onSecondaryContainer = SecondaryBrownContainer,
    tertiary = TertiaryGreenLight,
    onTertiary = Color(0xFF003A00),
    tertiaryContainer = Color(0xFF005200),
    onTertiaryContainer = TertiaryGreenContainer,
    error = ErrorRedLight,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = ErrorRedContainer,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark
)
