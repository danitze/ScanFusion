package com.danitze.scanfusionlib

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
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
import com.danitze.scanfusionlib.analyzer.BarcodeAnalyzer
import com.danitze.scanfusionlib.analyzer.ZxingBarcodeAnalyzer
import com.danitze.scanfusionlib.databinding.ViewCodeScannerBinding
import com.danitze.scanfusionlib.format.ScanningBarcodeFormat
import com.danitze.scanfusionlib.format.ScanningBarcodeFormat.Companion.toMlKitBarcodes
import com.danitze.scanfusionlib.format.ScanningBarcodeFormat.Companion.toZxingBarcodes
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

    private var onCloseClick: (() -> Unit)? = null

    private val binding: ViewCodeScannerBinding = ViewCodeScannerBinding
        .inflate(LayoutInflater.from(context), this)

    private val lifecycleScope: CoroutineScope = CoroutineScope(Dispatchers.Main)

    private var camera: Camera? = null

    @DrawableRes
    private var closeIconRes: Int? = null
        set(value) {
            if (value == null) {
                return
            }
            field = value
            binding.buttonClose.setImageResource(value)
        }

    @DrawableRes
    private var torchEnabledIconRes: Int? = null

    @DrawableRes
    private var torchDisabledIconRes: Int? = null

    private var isTorchEnabled: Boolean = false
        set(value) {
            field = value
            refreshFlashImage()
        }
    private var isTorchSwitchable: Boolean = false
        set(value) {
            field = value
            binding.buttonFlash.isVisible = value
        }

    private var isCloseButtonVisible: Boolean = false
        set(value) {
            field = value
            binding.buttonClose.isVisible = value
        }

    private val barcodeProcessingState: BarcodeProcessingState = BarcodeProcessingState()

    init {
        context.withStyledAttributes(attrs, R.styleable.CodeScannerView) {
            isTorchSwitchable = getBoolean(
                R.styleable.CodeScannerView_is_torch_switchable,
                true
            )
            isCloseButtonVisible = getBoolean(
                R.styleable.CodeScannerView_is_close_button_visible,
                true
            )
            closeIconRes = getResourceId(
                R.styleable.CodeScannerView_close_icon,
                R.drawable.ic_close
            )
            torchEnabledIconRes = getResourceId(
                R.styleable.CodeScannerView_torch_enabled_icon,
                R.drawable.ic_flash_on
            )
            torchDisabledIconRes = getResourceId(
                R.styleable.CodeScannerView_torch_disabled_icon,
                R.drawable.ic_flash_off
            )
            isTorchEnabled = false
        }
        binding.buttonFlash.setOnClickListener {
            Log.d("MyTag", "Flash click")
            enableFlash(!isTorchEnabled)
        }
        binding.buttonClose.setOnClickListener {
            Log.d("MyTag", "Close click")
            onCloseClick?.invoke()
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        return bundleOf(
            "superState" to super.onSaveInstanceState(),
            EXTRA_KEY_IS_TORCH_ENABLED to isTorchEnabled,
            EXTRA_KEY_IS_TORCH_SWITCHABLE to isTorchSwitchable,
            EXTRA_KEY_IS_CLOSE_BUTTON_VISIBLE to isCloseButtonVisible,
            EXTRA_KEY_CLOSE_ICON_RES to closeIconRes,
            EXTRA_KEY_TORCH_ENABLED_ICON_RES to torchEnabledIconRes,
            EXTRA_KEY_TORCH_DISABLED_ICON_RES to torchDisabledIconRes
        )
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val superState: Parcelable? = if (state != null) {
            val bundle = state as Bundle
            isTorchEnabled = bundle.getBoolean(EXTRA_KEY_IS_TORCH_ENABLED)
            isTorchSwitchable = bundle.getBoolean(EXTRA_KEY_IS_TORCH_SWITCHABLE)
            isCloseButtonVisible = bundle.getBoolean(EXTRA_KEY_IS_CLOSE_BUTTON_VISIBLE, true)
            closeIconRes = bundle.getInt(EXTRA_KEY_CLOSE_ICON_RES, R.drawable.ic_close)
            torchEnabledIconRes = bundle.getInt(EXTRA_KEY_TORCH_ENABLED_ICON_RES, R.drawable.ic_flash_on)
            torchDisabledIconRes = bundle.getInt(EXTRA_KEY_TORCH_DISABLED_ICON_RES, R.drawable.ic_flash_off)
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

    fun setOnCloseClickListener(listener: () -> Unit) {
        onCloseClick = listener
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

    fun setCloseIconRes(
        @DrawableRes
        iconRes: Int
    ) {
        closeIconRes = iconRes
    }

    fun setTorchIcons(
        @DrawableRes
        disabledIconRes: Int,
        @DrawableRes
        enabledIconRes: Int
    ) {
        torchDisabledIconRes = disabledIconRes
        torchEnabledIconRes = enabledIconRes
        refreshFlashImage()
    }

    fun setIsFlashSwitchable(isSwitchable: Boolean) {
        isTorchSwitchable = isSwitchable
    }

    fun setIsCloseVisible(isVisible: Boolean) {
        isCloseButtonVisible = isVisible
    }

    private fun refreshFlashImage() {
        val torchEnabledIconRes = torchEnabledIconRes ?: return
        val torchDisabledIconRes = torchDisabledIconRes ?: return
        val buttonFlashImageRes = if (isTorchEnabled) {
            torchEnabledIconRes
        } else {
            torchDisabledIconRes
        }

        binding.buttonFlash.setImageResource(buttonFlashImageRes)
    }

    companion object {
        private const val EXTRA_KEY_IS_TORCH_ENABLED = "EXTRA_KEY_IS_TORCH_ENABLED"
        private const val EXTRA_KEY_IS_TORCH_SWITCHABLE = "EXTRA_KEY_IS_TORCH_SWITCHABLE"
        private const val EXTRA_KEY_IS_CLOSE_BUTTON_VISIBLE = "EXTRA_KEY_IS_CLOSE_BUTTON_VISIBLE"
        private const val EXTRA_KEY_CLOSE_ICON_RES = "EXTRA_KEY_CLOSE_ICON_RES"
        private const val EXTRA_KEY_TORCH_ENABLED_ICON_RES = "EXTRA_KEY_TORCH_ENABLED_ICON_RES"
        private const val EXTRA_KEY_TORCH_DISABLED_ICON_RES = "EXTRA_KEY_TORCH_DISABLED_ICON_RES"
    }
}