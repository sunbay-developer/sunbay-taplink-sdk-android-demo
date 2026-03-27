package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sunmi.tapro.taplink.demo.model.TransactionStatus
import com.sunmi.tapro.taplink.demo.ui.theme.ErrorRed
import com.sunmi.tapro.taplink.demo.ui.theme.PrimaryOrange
import com.sunmi.tapro.taplink.demo.ui.theme.TertiaryGreen

/**
 * Badge size enum for StatusBadge component
 */
enum class BadgeSize(
    val height: Int,
    val fontSize: Int,
    val iconSize: Int,
    val horizontalPadding: Int,
    val verticalPadding: Int
) {
    Small(24, 12, 14, 10, 4),
    Medium(32, 14, 16, 12, 6),
    Large(40, 16, 18, 14, 8)
}

/**
 * Status badge component for displaying transaction status
 * 
 * @param status Transaction status to display
 * @param modifier Modifier for customization
 * @param size Badge size (Small, Medium, or Large)
 */
@Composable
fun StatusBadge(
    status: TransactionStatus,
    modifier: Modifier = Modifier,
    size: BadgeSize = BadgeSize.Medium
) {
    val (backgroundColor, textColor, icon, text) = when (status) {
        TransactionStatus.SUCCESS -> StatusConfig(
            backgroundColor = TertiaryGreen,
            textColor = Color.White,
            icon = Icons.Default.CheckCircle,
            text = "Success"
        )
        TransactionStatus.FAILED -> StatusConfig(
            backgroundColor = ErrorRed,
            textColor = Color.White,
            icon = Icons.Default.Close,
            text = "Failed"
        )
        TransactionStatus.PENDING -> StatusConfig(
            backgroundColor = PrimaryOrange,
            textColor = Color.White,
            icon = Icons.Default.Refresh,
            text = "Pending"
        )
        TransactionStatus.PROCESSING -> StatusConfig(
            backgroundColor = PrimaryOrange,
            textColor = Color.White,
            icon = Icons.Default.Refresh,
            text = "Processing"
        )
        TransactionStatus.CANCELLED -> StatusConfig(
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant,
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            icon = Icons.Default.Close,
            text = "Cancelled"
        )
    }

    Row(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(
                horizontal = size.horizontalPadding.dp,
                vertical = size.verticalPadding.dp
            ),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = textColor,
            modifier = Modifier.size(size.iconSize.dp)
        )
        Text(
            text = text,
            color = textColor,
            fontSize = size.fontSize.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Internal data class for status configuration
 */
private data class StatusConfig(
    val backgroundColor: Color,
    val textColor: Color,
    val icon: ImageVector,
    val text: String
)
