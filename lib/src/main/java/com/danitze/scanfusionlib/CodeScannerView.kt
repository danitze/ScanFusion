package com.danitze.scanfusionlib

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import com.danitze.scanfusionlib.format.ScanningBarcodeFormat.Companion.toMlKitBarcodes
import com.danitze.scanfusionlib.format.ScanningBarcodeFormat.Companion.toZxingBarcodes
import com.danitze.scanfusionlib.analyzer.BarcodeAnalyzer
import com.danitze.scanfusionlib.analyzer.ZxingBarcodeAnalyzer
import com.danitze.scanfusionlib.databinding.ViewCodeScannerBinding
import com.danitze.scanfusionlib.format.ScanningBarcodeFormat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class CodeScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var onCodeScanned: ((String) -> Unit)? = null

    private var onTorchCannotSwitch: ((Throwable) -> Unit)? = null

    private val binding: ViewCodeScannerBinding = ViewCodeScannerBinding
        .inflate(LayoutInflater.from(context), this)

    private val lifecycleScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    private var camera: Camera? = null

    private var isTorchEnabled: Boolean = false
        set(value) {
            field = value
            val buttonFlashImageResource = if (value) {
                R.drawable.ic_flash_on
            } else {
                R.drawable.ic_flash_off
            }
            binding.buttonFlash.setImageResource(buttonFlashImageResource)
        }
    private var isTorchSwitchable: Boolean = false
        set(value) {
            field = value
            binding.buttonFlash.isVisible = value
        }

    private val barcodeProcessingState: BarcodeProcessingState = BarcodeProcessingState()

    init {
        context.withStyledAttributes(attrs, R.styleable.CodeScannerView) {
            isTorchSwitchable = getBoolean(
                R.styleable.CodeScannerView_is_torch_switchable,
                true
            )
        }
        binding.buttonFlash.setOnClickListener {
            enableFlash(!isTorchEnabled)
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return bundleOf(
            "superState" to super.onSaveInstanceState(),
            EXTRA_KEY_IS_TORCH_ENABLED to isTorchEnabled,
            EXTRA_KEY_IS_TORCH_SWITCHABLE to isTorchSwitchable
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val superState: Parcelable? = if (state != null) {
            val bundle = state as Bundle
            isTorchEnabled = bundle.getBoolean(EXTRA_KEY_IS_TORCH_ENABLED)
            isTorchSwitchable = bundle.getBoolean(EXTRA_KEY_IS_TORCH_SWITCHABLE)
            bundle.getParcelable("superState")
        } else {
            null
        }
        super.onRestoreInstanceState(superState)
    }

    fun setCodeScannedListener(listener: (String) -> Unit) {
        onCodeScanned = listener
    }

    fun onScanningEnabled(isEnabled: Boolean) {
        barcodeProcessingState.setIsScanningEnabled(isEnabled)
    }

    fun bindCameraUseCases(
        lifecycleOwner: LifecycleOwner,
        formats: List<ScanningBarcodeFormat> = ScanningBarcodeFormat.all(),
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val previewUseCase = Preview.Builder()
                .build()
                .apply {
                    surfaceProvider = binding.previewView.surfaceProvider
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val analysisUseCase = ImageAnalysis.Builder()
                .build()
                .apply {
                    val analyzerCallback: (String) -> Unit = { barcode ->
                        if (barcodeProcessingState.canBarcodeBeProcessed()) {
                            onCodeScanned?.invoke(barcode)
                            delayBarcodeProcessing()
                        }
                    }
                    setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        if (!isGooglePlayServicesAvailable(context)) {
                            BarcodeAnalyzer(
                                barcodeFormats = formats.toMlKitBarcodes(),
                                previewViewWidth = binding.previewView.width.toFloat(),
                                previewViewHeight = binding.previewView.height.toFloat(),
                                scanningBoxRect = binding.viewOverlay.getScanningRect(),
                                barcodeListener = analyzerCallback
                            )
                        } else {
                            ZxingBarcodeAnalyzer(
                                barcodeFormats = formats.toZxingBarcodes(),
                                previewViewWidth = binding.previewView.width.toFloat(),
                                previewViewHeight = binding.previewView.height.toFloat(),
                                previewViewRect = binding.viewOverlay.getScanningRect(),
                                barcodeListener = analyzerCallback
                            )
                        }
                    )
                }

            try {
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    previewUseCase,
                    analysisUseCase
                )
            } catch (illegalStateException: IllegalStateException) {
                // If the use case has already been bound to another lifecycle or method is not called on main thread.
                Log.e("ScanFusion", illegalStateException.message.orEmpty())
            } catch (illegalArgumentException: IllegalArgumentException) {
                // If the provided camera selector is unable to resolve a camera to be used for the given use cases.
                Log.e("ScanFusion", illegalArgumentException.message.orEmpty())
            }
        }, ContextCompat.getMainExecutor(context))
    }

    @SuppressLint("RestrictedApi")
    private fun enableFlash(enable: Boolean) {
        lifecycleScope.launch {
            kotlin.runCatching {
                suspendCoroutine { cont ->
                    camera!!.cameraControl.enableTorch(enable).addListener({
                        cont.resume(Unit)
                    }, Executors.newSingleThreadExecutor())
                }
            }.onFailure { onTorchCannotSwitch?.invoke(it) }
        }
    }

    private fun delayBarcodeProcessing() {
        lifecycleScope.launch {
            delay(3000)
            barcodeProcessingState.setIsBarcodeProcessing(false)
        }
    }

    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        return resultCode == ConnectionResult.SUCCESS
    }

    private class BarcodeProcessingState(
        private var isBarcodeProcessing: Boolean = false,
        private var isScanningEnabled: Boolean = true
    ) {

        fun canBarcodeBeProcessed(): Boolean = synchronized(this) {
            if (!isScanningEnabled) {
                return@synchronized false
            }
            val isBarcodeProcessingCached = isBarcodeProcessing
            if (!isBarcodeProcessing) {
                isBarcodeProcessing = true
            }
            !isBarcodeProcessingCached
        }

        fun setIsBarcodeProcessing(isProcessing: Boolean) {
            synchronized(this) {
                isBarcodeProcessing = isProcessing
            }
        }

        fun setIsScanningEnabled(isEnabled: Boolean) {
            synchronized(this) {
                isScanningEnabled = isEnabled
            }
        }
    }

    companion object {
        private const val EXTRA_KEY_IS_TORCH_ENABLED = "EXTRA_KEY_IS_TORCH_ENABLED"
        private const val EXTRA_KEY_IS_TORCH_SWITCHABLE = "EXTRA_KEY_IS_TORCH_SWITCHABLE"
    }
}