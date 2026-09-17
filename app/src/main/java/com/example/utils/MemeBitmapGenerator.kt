package com.example.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max

data class MemeSticker(
    val emoji: String,
    val relX: Float, // 0f to 1f
    val relY: Float  // 0f to 1f
)

object MemeBitmapGenerator {

    suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val input: InputStream? = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun loadBitmapFromResource(context: Context, resId: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            BitmapFactory.decodeResource(context.resources, resId)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Renders a meme on top of a base bitmap with classic impact-style text, stroke outline,
     * and optional emoji stickers.
     */
    fun createMemeBitmap(
        baseBitmap: Bitmap,
        topText: String,
        bottomText: String,
        textColor: Int = Color.WHITE,
        hasOutline: Boolean = true,
        fontSizeRatio: Float = 0.08f,
        isUppercase: Boolean = true,
        stickers: List<MemeSticker> = emptyList()
    ): Bitmap {
        val width = baseBitmap.width
        val height = baseBitmap.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        // Draw original image
        canvas.drawBitmap(baseBitmap, 0f, 0f, null)

        val baseFontSize = max(24f, width * fontSizeRatio)

        // Text fill paint
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = baseFontSize
            typeface = Typeface.create("impact", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // Text stroke/outline paint (gives the classic meme look)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = baseFontSize
            typeface = Typeface.create("impact", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE
            strokeWidth = baseFontSize * 0.12f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }

        val formattedTop = if (isUppercase) topText.uppercase() else topText
        val formattedBottom = if (isUppercase) bottomText.uppercase() else bottomText

        // Draw Top Text
        if (formattedTop.isNotBlank()) {
            val topLines = splitIntoLines(formattedTop, width - 40, textPaint)
            var y = baseFontSize + 20f
            for (line in topLines) {
                if (hasOutline) {
                    canvas.drawText(line, width / 2f, y, strokePaint)
                }
                canvas.drawText(line, width / 2f, y, textPaint)
                y += baseFontSize * 1.15f
            }
        }

        // Draw Bottom Text
        if (formattedBottom.isNotBlank()) {
            val bottomLines = splitIntoLines(formattedBottom, width - 40, textPaint)
            val totalHeight = bottomLines.size * (baseFontSize * 1.15f)
            var y = height - totalHeight + (baseFontSize * 0.7f)
            for (line in bottomLines) {
                if (hasOutline) {
                    canvas.drawText(line, width / 2f, y, strokePaint)
                }
                canvas.drawText(line, width / 2f, y, textPaint)
                y += baseFontSize * 1.15f
            }
        }

        // Draw Stickers / Emojis
        if (stickers.isNotEmpty()) {
            val stickerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = baseFontSize * 1.4f
                textAlign = Paint.Align.CENTER
            }
            for (sticker in stickers) {
                val sx = sticker.relX * width
                val sy = sticker.relY * height
                canvas.drawText(sticker.emoji, sx, sy, stickerPaint)
            }
        }

        return output
    }

    private fun splitIntoLines(text: String, maxWidth: Int, paint: Paint): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            val bounds = Rect()
            paint.getTextBounds(testLine, 0, testLine.length, bounds)

            if (bounds.width() <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine)
                }
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }
}
