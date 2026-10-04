package com.fgmachines.mikrotikmanager.voucher

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import java.util.EnumMap

object VoucherQrSvgFactory {
    fun create(payload: String, modules: Int = 33): String {
        val target = modules.coerceIn(21, 81)
        val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
            put(EncodeHintType.MARGIN, 1)
            put(EncodeHintType.CHARACTER_SET, "UTF-8")
        }
        val matrix = MultiFormatWriter().encode(
            payload,
            BarcodeFormat.QR_CODE,
            target,
            target,
            hints
        )

        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
            append(matrix.width)
            append(' ')
            append(matrix.height)
            append("\" shape-rendering=\"crispEdges\">")
            append("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>")
            append("<path fill=\"black\" d=\"")
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    if (matrix[x, y]) {
                        append('M').append(x).append(' ').append(y)
                        append("h1v1h-1z")
                    }
                }
            }
            append("\"/></svg>")
        }
    }
}
