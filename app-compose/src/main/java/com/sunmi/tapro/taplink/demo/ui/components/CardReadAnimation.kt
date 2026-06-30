package com.sunmi.tapro.taplink.demo.ui.components

import android.os.Build
import androidx.annotation.RawRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size

/**
 * Displays an animated WebP card reading animation from a raw resource.
 *
 * Uses Coil image loader with GIF/animated image decoder support.
 * The animation loops indefinitely to indicate the terminal is waiting for a card.
 * Sizing follows tapro-app convention: fixed height (370dp) with fillMaxWidth and ContentScale.Crop.
 *
 * @param animationResId Raw resource ID of the animated WebP file
 * @param modifier Modifier for layout customization
 * @param contentDescription Accessibility description
 */
@Composable
fun CardReadAnimation(
    @RawRes animationResId: Int,
    modifier: Modifier = Modifier,
    contentDescription: String = "Card reading animation"
) {
    val context = LocalContext.current

    // Reuse ImageLoader to avoid re-creating on each recomposition
    val imageLoader = remember(context) {
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val request = remember(animationResId) {
        ImageRequest.Builder(context)
            .data(animationResId)
            .size(Size.ORIGINAL)
            .memoryCacheKey("card_anim_$animationResId")
            .build()
    }

    AsyncImage(
        model = request,
        imageLoader = imageLoader,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .height(370.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
    )
}
