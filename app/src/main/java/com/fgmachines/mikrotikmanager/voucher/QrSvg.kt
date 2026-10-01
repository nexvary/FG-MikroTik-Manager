package com.fgmachines.mikrotikmanager.voucher

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object QrSvg {

    fun encode(
        value: String,
        size: Int = 180,
        quietZone: Int = 1
    ): String {
        require(value.isNotBlank()) { "QR value cannot be blank" }
        require(size in 64..1024) { "QR size is out of range" }
        require(quietZone in 0..4) { "Quiet zone is out of range" }

        val matrix = QRCodeWriter().encode(
            value,
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to quietZone
            )
        )

        val path = buildString {
            for (y in 0 until matrix.height) {
                var runStart = -1
                for (x in 0 until matrix.width) {
                    val on = matrix[x, y]
                    if (on && runStart < 0) {
                        runStart = x
                    }

                    val runEnds = runStart >= 0 && (!on || x == matrix.width - 1)
                    if (runEnds) {
                        val endExclusive = if (on && x == matrix.width - 1) x + 1 else x
                        append("M")
                        append(runStart)
                        append(" ")
                        append(y)
                        append("h")
                        append(endExclusive - runStart)
                        append("v1h-")
                        append(endExclusive - runStart)
                        append("z")
                        runStart = -1
                    }
                }
            }
        }

        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" ")
            append("viewBox=\"0 0 ")
            append(matrix.width)
            append(" ")
            append(matrix.height)
            append("\" role=\"img\" aria-label=\"QR code\">")
            append("<rect width=\"100%\" height=\"100%\" fill=\"#fff\"/>")
            append("<path d=\"")
            append(path)
            append("\" fill=\"#000\"/>")
            append("</svg>")
        }
    }
}
