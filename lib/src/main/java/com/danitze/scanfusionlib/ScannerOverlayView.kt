package com.danitze.scanfusionlib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.min
import android.graphics.PorterDuff.Mode.CLEAR
import com.danitze.scanfusionlib.util.dpToPx

class ScannerOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var overlayBitmap: Bitmap? = null
    private var overlayCanvas: Canvas? = null
    private val backgroundPaint: Paint = Paint().apply {
        alpha = BACKGROUND_ALPHA.toInt()
    }
    private val strokePaint: Paint = Paint().apply {
        color = context.resources.getColor(R.color.color_stroke)
    }
    private val transparentPaint: Paint = Paint().apply {
        alpha = 0
        xfermode = PorterDuffXfermode(CLEAR)
    }
    private var innerFrameRect: RectF = RectF()
    private var outerFrameRect: RectF = RectF()
    private val strokeWidthPx = STROKE_WIDTH_DP.dpToPx(context)
    private val innerRadiusPx = INNER_RADIUS.dpToPx(context)
    private val outerRadiusPx = OUTER_RADIUS.dpToPx(context)

    init {
        setWillNotDraw(false)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        if (overlayBitmap == null && width > 0 && height > 0) {
            overlayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                overlayCanvas = Canvas(this)
            }
            calculateFramePosition()
        }
    }

    override fun onDraw(canvas: Canvas) {
        overlayCanvas?.drawColor(context.resources.getColor(R.color.color_overlay_bg_tint))
        overlayCanvas?.drawRoundRect(outerFrameRect, outerRadiusPx, outerRadiusPx, strokePaint)
        overlayCanvas?.drawRoundRect(innerFrameRect, innerRadiusPx, innerRadiusPx, transparentPaint)
        canvas.drawBitmap(overlayBitmap!!, 0f, 0f, backgroundPaint)
        super.onDraw(canvas)
    }

    internal fun getScanningRect(): RectF = innerFrameRect

    private fun calculateFramePosition() {
        val centerX = width / 2
        val centerY = height / 2
        val size = min(centerX, centerY)
        innerFrameRect.set(
            centerX - size / 2F,
            centerY - size / 2F,
            centerX + size / 2F,
            centerY + size / 2F
        )
        outerFrameRect.set(
            innerFrameRect.left - strokeWidthPx,
            innerFrameRect.top - strokeWidthPx,
            innerFrameRect.right + strokeWidthPx,
            innerFrameRect.bottom + strokeWidthPx
        )
    }

    companion object {
        private const val BACKGROUND_ALPHA = 0.63 * 255
        private const val STROKE_WIDTH_DP = 4F
        private const val INNER_RADIUS = 12F
        private const val OUTER_RADIUS = 16F
    }

}