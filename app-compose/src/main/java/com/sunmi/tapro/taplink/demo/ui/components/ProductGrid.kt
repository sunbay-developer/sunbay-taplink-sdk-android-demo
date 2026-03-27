package com.sunmi.tapro.taplink.demo.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.model.Product
import com.sunmi.tapro.taplink.demo.ui.utils.rememberScreenConfig

/**
 * ProductGrid component displays products in a clean grid layout.
 * - Portrait: 3 columns (unchanged).
 * - Landscape: 4/5/6 columns by available width (<960dp: 4, 960–1280dp: 5, >1280dp: 6).
 * When used inside a pane (e.g. MainScreen left 60%), column count uses the pane width.
 *
 * @param products List of products to display
 * @param onProductClick Callback when a product is clicked
 * @param onAddProductClick Callback when add product button is clicked
 * @param modifier Optional modifier for the grid
 */
@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun ProductGrid(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    onAddProductClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenConfig = rememberScreenConfig()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthDp = maxWidth
        val columns = remember(screenConfig.isLandscape, widthDp) {
            if (!screenConfig.isLandscape) {
                3
            } else {
                when {
                    widthDp < 960.dp -> 4
                    widthDp < 1280.dp -> 5
                    else -> 6
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = 12.dp,
                vertical = 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = products,
                key = { product -> product.id }
            ) { product ->
                ProductCard(
                    product = product,
                    onClick = { onProductClick(product) }
                )
            }
            
            // Add product button as last item
            item {
                AddProductCard(onClick = onAddProductClick)
            }
        }
    }
}
