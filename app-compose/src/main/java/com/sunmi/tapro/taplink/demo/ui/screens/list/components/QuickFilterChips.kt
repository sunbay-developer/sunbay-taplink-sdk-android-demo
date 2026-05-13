package com.sunmi.tapro.taplink.demo.ui.screens.list.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * FilterType enum
 * 
 * Defines the available filter types for transaction list
 */
enum class FilterType {
    ALL,       // All transactions
    TODAY,     // Today's transactions
    SUCCESS,   // Successful transactions
    FAILED,    // Failed transactions
    PENDING    // Pending transactions
}

/**
 * QuickFilterChips component
 * 
 * Displays a horizontal scrollable row of filter chips for quick filtering
 * Chip height: 40dp, Corner radius: 20dp (fully rounded)
 * Selected state: PrimaryOrange background, White text
 * Unselected state: SurfaceVariant background, OnSurfaceVariant text
 * Chip spacing: 8dp, Horizontal scroll layout
 */
@Composable
fun QuickFilterChips(
    selectedFilter: FilterType,
    onFilterSelected: (FilterType) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    
    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterType.values().forEach { filterType ->
            val isSelected = selectedFilter == filterType
            
            FilterChip(
                selected = isSelected,
                onClick = { onFilterSelected(filterType) },
                label = {
                    Text(
                        text = getFilterLabel(filterType),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                modifier = Modifier.height(40.dp),
                shape = MaterialTheme.shapes.extraLarge, // Fully rounded (20dp)
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFFFF6B35), // PrimaryOrange
                    selectedLabelColor = Color.White,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null
            )
        }
    }
}

/**
 * Get filter label text
 * 
 * @param filterType Filter type
 * @return Display label for the filter
 */
private fun getFilterLabel(filterType: FilterType): String {
    return when (filterType) {
        FilterType.ALL -> "All"
        FilterType.TODAY -> "Today"
        FilterType.SUCCESS -> "Success"
        FilterType.FAILED -> "Failed"
        FilterType.PENDING -> "Pending"
    }
}
