package com.sunmi.tapro.taplink.demo.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sunmi.tapro.taplink.demo.ui.theme.TaplinkTheme

/**
 * LoadingIndicator - POS-style loading component with card container.
 *
 * Design features:
 * - Elevated card with rounded corners
 * - Themed circular progress indicator
 * - Animated pulsing dots below message
 * - Clean centered layout
 *
 * @param message Optional message to display below the indicator
 * @param modifier Optional modifier for the component
 */
@Composable
fun LoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )

            if (message != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                PulsingDots()
            }
        }
    }
}

/**
 * FullScreenLoadingIndicator - Full-screen overlay loading state.
 *
 * Displays a centered loading card with semi-transparent scrim background.
 *
 * @param message Optional message to display below the indicator
 */
@Composable
fun FullScreenLoadingIndicator(
    message: String? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(8.dp, RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 5.dp,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )

                if (message != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    PulsingDots()
                }
            }
        }
    }
}

/**
 * InlineLoadingIndicator - Compact POS-style inline loading indicator.
 *
 * Pill-shaped container with small spinner and optional text.
 * Suitable for inline use within rows or buttons.
 *
 * @param message Optional message to display next to the indicator
 * @param modifier Optional modifier for the component
 */
@Composable
fun InlineLoadingIndicator(
    message: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 2.dp,
            trackColor = MaterialTheme.colorScheme.primaryContainer
        )

        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Animated pulsing dots to indicate ongoing activity.
 * Three dots that pulse sequentially with staggered timing.
 */
@Composable
private fun PulsingDots() {
    val transition = rememberInfiniteTransition(label = "dots")

    val delays = listOf(0, 150, 300)
    val alphas = delays.map { delay ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(600, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dot_$delay"
        )
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        alphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .alpha(alpha.value)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        CircleShape
                    )
            )
        }
    }
}

// Preview functions
@Preview(showBackground = true)
@Composable
fun LoadingIndicatorPreview() {
    TaplinkTheme {
        LoadingIndicator(
            message = "Processing payment...",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingIndicatorNoMessagePreview() {
    TaplinkTheme {
        LoadingIndicator(modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun FullScreenLoadingIndicatorPreview() {
    TaplinkTheme {
        FullScreenLoadingIndicator(message = "Connecting to payment terminal...")
    }
}

@Preview(showBackground = true)
@Composable
fun InlineLoadingIndicatorPreview() {
    TaplinkTheme {
        InlineLoadingIndicator(
            message = "Loading transactions...",
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun InlineLoadingIndicatorNoMessagePreview() {
    TaplinkTheme {
        InlineLoadingIndicator(modifier = Modifier.padding(16.dp))
    }
}
