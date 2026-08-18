package io.github.sarraf5757.barebrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.webkit.WebView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

suspend fun captureWebViewToThumbnail(webView: WebView?): String? {
    if (webView == null) return null
    
    // View measurements and drawing must happen on the Main thread
    val width = webView.width
    val height = webView.height
    if (width <= 0 || height <= 0) return null
    
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        
        // Offload heavy image processing (scaling, compression, base64) to IO thread
        withContext(Dispatchers.IO) {
            val thumbWidth = 300
            var thumbHeight = (height.toFloat() / width.toFloat() * thumbWidth).toInt()
            if (thumbHeight <= 0) {
                thumbHeight = 300
            }
            val scaledBitmap = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
            
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            
            // Clean up original bitmap to save memory since we have the scaled version
            bitmap.recycle()
            
            val base64 = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            scaledBitmap.recycle()
            
            base64
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun decodeBase64ToImageBitmap(base64Str: String?): ImageBitmap? {
    if (base64Str.isNullOrEmpty()) return null
    return withContext(Dispatchers.IO) {
        try {
            val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}
