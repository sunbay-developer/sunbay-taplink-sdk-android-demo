package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Button size enum for PosButton component
 */
enum class ButtonSize(
    val height: Int,
    val minWidth: Int,
    val horizontalPadding: Int,
    val verticalPadding: Int
) {
    Medium(48, 120, 20, 12),
    Large(56, 120, 24, 16)
}

/**
 * POS-style button component with large touch-friendly design
 * 
 * @param text Button text
 * @param onClick Click handler
 * @param modifier Modifier for customization
 * @param icon Optional icon to display before text
 * @param enabled Whether button is enabled
 * @param loading Whether button is in loading state
 * @param colors Button colors
 * @param size Button size (Medium or Large)
 */
@Composable
fun PosButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    size: ButtonSize = ButtonSize.Large
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(
                minWidth = size.minWidth.dp,
                minHeight = size.height.dp
            ),
        enabled = enabled && !loading,
        colors = colors,
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(
            horizontal = size.horizontalPadding.dp,
            vertical = size.verticalPadding.dp
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                icon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
