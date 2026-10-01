package com.fgmachines.mikrotikmanager.voucher

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.EnumMap

object VoucherQrCodeFactory {
    fun create(payload: String, size: Int = 320): Bitmap {
        require(payload.isNotBlank()) { "QR payload cannot be blank" }
        val safeSize = size.coerceIn(160, 1024)
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            safeSize,
            safeSize,
            hints
        )
        val pixels = IntArray(safeSize * safeSize)
        for (y in 0 until safeSize) {
            val rowOffset = y * safeSize
            for (x in 0 until safeSize) {
                pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(
            pixels,
            safeSize,
            safeSize,
            Bitmap.Config.ARGB_8888
        )
    }
}
