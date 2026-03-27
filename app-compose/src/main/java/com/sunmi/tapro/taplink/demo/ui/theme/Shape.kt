package com.sunmi.tapro.taplink.demo.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Shape definitions for restaurant POS interface
// Using rounded corners for a modern, friendly appearance
val Shapes = Shapes(
    // Extra small shapes - for small components like chips
    extraSmall = RoundedCornerShape(4.dp),
    
    // Small shapes - for buttons and small cards
    small = RoundedCornerShape(8.dp),
    
    // Medium shapes - for cards and dialogs
    medium = RoundedCornerShape(12.dp),
    
    // Large shapes - for large cards and bottom sheets
    large = RoundedCornerShape(16.dp),
    
    // Extra large shapes - for full screen dialogs
    extraLarge = RoundedCornerShape(28.dp)
)
