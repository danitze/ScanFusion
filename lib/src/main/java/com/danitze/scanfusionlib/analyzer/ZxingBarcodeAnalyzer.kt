package com.danitze.scanfusionlib.analyzer

import android.graphics.ImageFormat
import android.graphics.RectF
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class ZxingBarcodeAnalyzer(
    barcodeFormats: List<BarcodeFormat>?,
    private val previewViewWidth: Float,
    private val previewViewHeight: Float,
    private val previewViewRect: RectF,
    private val barcodeListener: (barcode: String) -> Unit
) : ImageAnalysis.Analyzer {

    private var scaleX = 1f
    private var scaleY = 1f

    private val reader: MultiFormatReader = MultiFormatReader()

    init {
        reader.setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to barcodeFormats
            )
        )
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            imageProxy.image?.let {
                scaleX = it.height.toFloat() / previewViewWidth
                scaleY = it.width.toFloat() / previewViewHeight
                if ((it.format == ImageFormat.YUV_420_888
                            || it.format == ImageFormat.YUV_422_888
                            || it.format == ImageFormat.YUV_444_888)
                    && it.planes.size == 3
                ) {
                    val buffer = it.planes[0].buffer // We get the luminance plane only, since we
                    // want to binarize it and we don't wanna take color into consideration.
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    // Create a LuminanceSource.
                    val barcodeRect = getBarcodeBoundingRect()
                    val rotatedImage = RotatedImage(bytes, imageProxy.width, imageProxy.height)
                    rotateImageArray(rotatedImage, imageProxy.imageInfo.rotationDegrees)

                    val source = PlanarYUVLuminanceSource(
                        rotatedImage.byteArray,
                        rotatedImage.width,
                        rotatedImage.height,
                        barcodeRect.left.toInt(),
                        barcodeRect.top.toInt(),
                        barcodeRect.width().toInt(),
                        barcodeRect.height().toInt(),
                        false
                    )

                    // Create a Binarizer
                    val binarizer = HybridBinarizer(source)
                    val binaryBitmap = BinaryBitmap(binarizer)
                    try {
                        val result = reader.decode(binaryBitmap)
                        barcodeListener(result.text ?: "")
                    } catch (e: NoSuchMethodError) {
                        Log.e("ScanFusion", "Barcode not found", e)
                    } catch (e: java.lang.IllegalArgumentException) {
                        Log.e("ScanFusion", "Barcode not found", e)
                    } catch (e: Throwable) {
                        Log.e("ScanFusion", "Barcode not found", e)
                    }
                } else {
                    Log.d("ScanFusion", "Wrong image format")
                }
            }
        } catch (ise: IllegalStateException) {
            Log.e("ScanFusion", "Illegal state when analyzing barcode", ise)
        }
        imageProxy.close()
    }

    // 90, 180. 270 rotation
    private fun rotateImageArray(imageToRotate: RotatedImage, rotationDegrees: Int) {
        if (rotationDegrees == 0) return // no rotation
        if (rotationDegrees % 90 != 0) return // only 90 degree times rotations

        val width = imageToRotate.width
        val height = imageToRotate.height

        val rotatedData = ByteArray(imageToRotate.byteArray.size)
        for (y in 0 until height) { // we scan the array by rows
            for (x in 0 until width) {
                when (rotationDegrees) {
                    90 -> rotatedData[x * height + height - y - 1] =
                        imageToRotate.byteArray[x + y * width] // Fill from top-right toward left (CW)
                    180 -> rotatedData[width * (height - y - 1) + width - x - 1] =
                        imageToRotate.byteArray[x + y * width] // Fill from bottom-right toward up (CW)
                    270 -> rotatedData[y + x * height] =
                        imageToRotate.byteArray[y * width + width - x - 1] // The opposite (CCW) of 90 degrees
                }
            }
        }

        imageToRotate.byteArray = rotatedData

        if (rotationDegrees != 180) {
            imageToRotate.height = width
            imageToRotate.width = height
        }
    }

    private fun translateX(x: Float) = x * scaleX
    private fun translateY(y: Float) = y * scaleY

    private fun getBarcodeBoundingRect() = RectF(
        translateX(previewViewRect.left),
        translateY(previewViewRect.top),
        translateX(previewViewRect.right),
        translateY(previewViewRect.bottom)
    )

    private class RotatedImage(
        var byteArray: ByteArray,
        var width: Int,
        var height: Int
    )
}