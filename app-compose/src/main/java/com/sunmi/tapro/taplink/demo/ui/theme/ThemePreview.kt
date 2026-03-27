package com.sunmi.tapro.taplink.demo.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Preview composable to verify theme application
 * This demonstrates the Material3 theme with various components
 */
@Composable
fun ThemePreviewContent() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Display large text - for amounts
            Text(
                text = "$125.50",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            // Headline text
            Text(
                text = "TapLink POS",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            // Card with content
            Card(
                modifier = Modifier.padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Type: SALE",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Status: SUCCESS",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Primary button
            Button(onClick = { }) {
                Text("Process Payment")
            }
        }
    }
}

/**
 * Light theme preview
 */
@Preview(
    name = "Light Theme",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
fun ThemePreviewLight() {
    TaplinkTheme(darkTheme = false, dynamicColor = false) {
        ThemePreviewContent()
    }
}

/**
 * Dark theme preview
 */
@Preview(
    name = "Dark Theme",
    showBackground = true,
    widthDp = 360,
    heightDp = 640
)
@Composable
fun ThemePreviewDark() {
    TaplinkTheme(darkTheme = true, dynamicColor = false) {
        ThemePreviewContent()
    }
}
