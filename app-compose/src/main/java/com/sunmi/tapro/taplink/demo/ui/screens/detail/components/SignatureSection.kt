package com.sunmi.tapro.taplink.demo.ui.screens.detail.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/**
 * Parses [receiptJson] and returns the decoded signature bitmap.
 *
 * receiptJson is the JSON string from SDK PaymentResult. The Tapro terminal
 * stores the Base64-encoded signature under the key "signature".
 *
 * Returns null when no signature is present or parsing fails.
 */
private fun extractSignatureBitmap(receiptJson: String?): android.graphics.Bitmap? {
    receiptJson ?: return null
    return try {
        val json = JSONObject(receiptJson)
        val base64 = json.optString("signature").takeIf { it.isNotEmpty() } ?: return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Exception) {
        null
    }
}

/**
 * Signature Section Component - Toast POS style
 *
 * Displays the cardholder's signature image extracted from [receiptJson].
 * Renders nothing when no signature is available.
 *
 * @param receiptJson JSON string from the SDK PaymentResult, may be null.
 * @param modifier    Layout modifier applied to the outer Card.
 */
@Composable
fun SignatureSection(
    receiptJson: String?,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(receiptJson) { extractSignatureBitmap(receiptJson) }
    bitmap ?: return

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Signature",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Cardholder signature",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .background(Color.White)
            )
        }
    }
}
