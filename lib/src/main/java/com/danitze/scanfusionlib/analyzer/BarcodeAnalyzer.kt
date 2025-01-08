package com.danitze.scanfusionlib.analyzer

import android.annotation.SuppressLint
import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    barcodeFormats: List<Int>?,
    private val previewViewWidth: Float,
    private val previewViewHeight: Float,
    private val scanningBoxRect: RectF,
    private val barcodeListener: (barcode: String) -> Unit
) : ImageAnalysis.Analyzer {

    private var scaleX = 1f
    private var scaleY = 1f

    private val scanner: BarcodeScanner

    init {
        val options = BarcodeScannerOptions.Builder().apply {
            if (barcodeFormats != null) {
                setBarcodeFormats(barcodeFormats)
            }
        }.build()
        scanner = BarcodeScanning.getClient(options)
    }

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            scaleX = previewViewWidth / mediaImage.height.toFloat()
            scaleY = previewViewHeight / mediaImage.width.toFloat()

            val image: InputImage =
                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    barcodes.filter { barcode ->
                        barcode.boundingBox?.let {
                            scanningBoxRect.contains(getRealBoundingRect(it))
                        } ?: false
                    }.forEach { barcode ->
                        barcodeListener(barcode.rawValue ?: "")
                    }
                }
                .addOnFailureListener {
                    imageProxy.close()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun translateX(x: Float) = x * scaleX
    private fun translateY(y: Float) = y * scaleY

    private fun getRealBoundingRect(rect: Rect) = RectF(
        translateX(rect.left.toFloat()),
        translateY(rect.top.toFloat()),
        translateX(rect.right.toFloat()),
        translateY(rect.bottom.toFloat())
    )
}

private fun BarcodeScannerOptions.Builder.setBarcodeFormats(formats: List<Int>) = when {
    formats.isEmpty() -> this
    formats.size == 1 -> setBarcodeFormats(formats[0])
    else -> setBarcodeFormats(formats[0], *formats.drop(1).toIntArray())
}