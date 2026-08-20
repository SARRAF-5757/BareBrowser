package io.github.sarraf5757.barebrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.webkit.WebView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Parse a color string into a Compose [Color]
 */
fun parseColorString(colorString: String?): Color? {
    if (colorString.isNullOrEmpty() || colorString == "null") return null
    val cleanColor = colorString.trim('"', '\'', ' ')
    if (cleanColor == "transparent" || cleanColor == "rgba(0, 0, 0, 0)") return null

    try {
        if (cleanColor.startsWith("rgb")) {
            val regex = Regex("\\d+")
            val matchResults = regex.findAll(cleanColor).toList()
            if (matchResults.size >= 3) {
                val r = matchResults[0].value.toInt()
                val g = matchResults[1].value.toInt()
                val b = matchResults[2].value.toInt()
                val a = if (matchResults.size >= 4) {
                    val alphaStr = cleanColor.substringAfterLast(",").substringBefore(")").trim()
                    (alphaStr.toFloatOrNull()?.let { it * 255 }?.toInt() ?: 255)
                } else 255
                return Color(red = r, green = g, blue = b, alpha = a)
            }
        } else {
            return Color(cleanColor.toColorInt())
        }
    } catch (e: Exception) {
        // returning null
    }

    return null
}

/**
 * Capture the current content of a [WebView] and convert it into a Base64-encoded string
 * the capture is scaled down to a thumbnail size
 */
suspend fun captureWebViewToThumbnail(webView: WebView): String {
    val width = webView.width
    val height = webView.height
    
    val bitmap = createBitmap(width, height)
    val canvas = Canvas(bitmap)
    webView.draw(canvas)
    
    return withContext(Dispatchers.IO) {
        val thumbWidth = 300
        val thumbHeight = (height.toFloat() / width.toFloat() * thumbWidth).toInt()
        val scaledBitmap = bitmap.scale(thumbWidth, thumbHeight)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        bitmap.recycle()
        val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        scaledBitmap.recycle()
        base64
    }
}

/**
 * Decode a Base64-encoded string into an [ImageBitmap]
 */
suspend fun decodeBase64ToImageBitmap(base64Str: String): ImageBitmap {
    return withContext(Dispatchers.IO) {
        val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        bitmap.asImageBitmap()
    }
}
