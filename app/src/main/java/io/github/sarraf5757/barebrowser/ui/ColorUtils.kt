package io.github.sarraf5757.barebrowser.ui

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt

fun parseColorString(colorString: String?): Color? {
    if (colorString.isNullOrEmpty() || colorString == "null") return null
    val cleanColor = colorString.trim('"', '\'', ' ')

    if (cleanColor.startsWith("rgb")) {
        val regex = Regex("\\d+")
        val matchResults = regex.findAll(cleanColor).toList()
        if (matchResults.size >= 3) {
            val r = matchResults[0].value.toInt()
            val g = matchResults[1].value.toInt()
            val b = matchResults[2].value.toInt()
            val a = if (matchResults.size >= 4) (matchResults[3].value.toFloat() * 255).toInt() else 255
            return Color(red = r, green = g, blue = b, alpha = a)
        }
    } else if (cleanColor.startsWith("#")) {
        return Color(cleanColor.toColorInt())
    } else {
        // Let Android try to parse named colors
        return Color(cleanColor.toColorInt())
    }

    return null
}
