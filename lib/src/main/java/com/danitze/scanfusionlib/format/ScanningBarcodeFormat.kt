package com.danitze.scanfusionlib.format

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat

enum class ScanningBarcodeFormat {
    AZTEC,
    CODABAR,
    CODE_39,
    CODE_93,
    CODE_128,
    DATA_MATRIX,
    EAN_8,
    EAN_13,
    ITF,
    MAXICODE,
    PDF_417,
    QR_CODE,
    RSS_14,
    RSS_EXPANDED,
    UPC_A,
    UPC_E,
    UPC_EAN_EXTENSION;

    private fun toMlKitBarcode(): Int {
        return when (this) {
            AZTEC -> Barcode.FORMAT_AZTEC
            CODABAR -> Barcode.FORMAT_CODABAR
            CODE_39 -> Barcode.FORMAT_CODE_39
            CODE_93 -> Barcode.FORMAT_CODE_93
            CODE_128 -> Barcode.FORMAT_CODE_128
            DATA_MATRIX -> Barcode.FORMAT_DATA_MATRIX
            EAN_8 -> Barcode.FORMAT_EAN_8
            EAN_13 -> Barcode.FORMAT_EAN_13
            ITF -> Barcode.FORMAT_ITF
            PDF_417 -> Barcode.FORMAT_PDF417
            QR_CODE -> Barcode.FORMAT_QR_CODE
            UPC_A -> Barcode.FORMAT_UPC_A
            UPC_E -> Barcode.FORMAT_UPC_E
            MAXICODE, RSS_14, RSS_EXPANDED, UPC_EAN_EXTENSION -> Barcode.FORMAT_UNKNOWN
        }
    }

    fun toZxingBarcodeFormat(): BarcodeFormat {
        return when (this) {
            AZTEC -> BarcodeFormat.AZTEC
            CODABAR -> BarcodeFormat.CODABAR
            CODE_39 -> BarcodeFormat.CODE_39
            CODE_93 -> BarcodeFormat.CODE_93
            CODE_128 -> BarcodeFormat.CODE_128
            DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
            EAN_8 -> BarcodeFormat.EAN_8
            EAN_13 -> BarcodeFormat.EAN_13
            ITF -> BarcodeFormat.ITF
            MAXICODE -> BarcodeFormat.MAXICODE
            PDF_417 -> BarcodeFormat.PDF_417
            QR_CODE -> BarcodeFormat.QR_CODE
            RSS_14 -> BarcodeFormat.RSS_14
            RSS_EXPANDED -> BarcodeFormat.RSS_EXPANDED
            UPC_A -> BarcodeFormat.UPC_A
            UPC_E -> BarcodeFormat.UPC_E
            UPC_EAN_EXTENSION -> BarcodeFormat.UPC_EAN_EXTENSION
        }
    }


    companion object {
        fun all(): List<ScanningBarcodeFormat> = ScanningBarcodeFormat.entries

        internal fun List<ScanningBarcodeFormat>.toMlKitBarcodes(): List<Int> = map {
            it.toMlKitBarcode()
        }

        internal fun List<ScanningBarcodeFormat>.toZxingBarcodes(): List<BarcodeFormat> = map {
            it.toZxingBarcodeFormat()
        }
    }
}