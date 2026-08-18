package io.github.sarraf5757.barebrowser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import android.webkit.WebView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream

fun captureWebViewToThumbnail(webView: WebView?): String? {
    if (webView == null) return null
    try {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        webView.draw(canvas)
        
        val thumbWidth = 300
        val thumbHeight = (height.toFloat() / width.toFloat() * thumbWidth).toInt().takeIf { it > 0 } ?: 300
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, thumbWidth, thumbHeight, true)
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun decodeBase64ToImageBitmap(base64Str: String?): ImageBitmap? {
    if (base64Str == null) return null
    return try {
        val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
