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
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

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

suspend fun decodeBase64ToImageBitmap(base64Str: String): ImageBitmap {
    return withContext(Dispatchers.IO) {
        val imageBytes = Base64.decode(base64Str, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        bitmap.asImageBitmap()
    }
}
