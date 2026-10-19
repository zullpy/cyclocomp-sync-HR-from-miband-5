package com.ridecomputer.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

object MarkerUtils {

    /**
     * Creates a crisp, athletic badge marker for START or FINISH.
     */
    fun createRouteBadgeDrawable(
        context: Context,
        label: String,
        backgroundColor: Int,
        textColor: Int = 0xFF0F1115.toInt(),
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val width = (label.length * 10 * density + 28 * density).toInt()
        val height = (32 * density).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = backgroundColor
            style = Paint.Style.FILL
            setShadowLayer(4 * density, 0f, 2 * density, 0x80000000.toInt())
        }

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textSize = 11f * density
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val radius = 8f * density
        val rect = android.graphics.RectF(
            2 * density,
            2 * density,
            width - 2 * density,
            height - 2 * density
        )

        // Draw pill background & border
        canvas.drawRoundRect(rect, radius, radius, bgPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        // Draw text centered
        val bounds = Rect()
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val textY = rect.centerY() + (bounds.height() / 2f)
        canvas.drawText(label, rect.centerX(), textY, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    /**
     * Creates a pulse/dot icon for user's current GPS location.
     */
    fun createUserLocationDrawable(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val size = (32 * density).toInt()

        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Outer halo
        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x4D00E676.toInt() // Translucent AccentGreen
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 1, haloPaint)

        // Inner solid dot
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF00E676.toInt() // AccentGreen
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, dotPaint)

        // White border around dot
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 4f, borderPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
