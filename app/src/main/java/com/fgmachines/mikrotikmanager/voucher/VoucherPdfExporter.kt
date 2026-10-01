package com.fgmachines.mikrotikmanager.voucher

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VoucherPdfExporter {
    suspend fun export(
        context: Context,
        batch: VoucherBatch,
        fileName: String = "vouchers.pdf"
    ): File = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, fileName)
        val document = PdfDocument()

        try {
            val pageWidth = 595
            val pageHeight = 842
            val margin = 24f
            val gap = 10f
            val columns = 2
            val rows = 4
            val cardsPerPage = columns * rows
            val cardWidth = (pageWidth - (margin * 2) - gap) / columns
            val cardHeight = (pageHeight - (margin * 2) - (gap * (rows - 1))) / rows

            batch.vouchers.chunked(cardsPerPage).forEachIndexed { pageIndex, cards ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(
                        pageWidth,
                        pageHeight,
                        pageIndex + 1
                    ).create()
                )

                cards.forEachIndexed { index, voucher ->
                    val column = index % columns
                    val row = index / columns
                    val left = margin + column * (cardWidth + gap)
                    val top = margin + row * (cardHeight + gap)

                    drawVoucher(
                        canvas = page.canvas,
                        voucher = voucher,
                        bounds = RectF(
                            left,
                            top,
                            left + cardWidth,
                            top + cardHeight
                        )
                    )
                }

                document.finishPage(page)
            }

            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }

        file
    }

    private fun drawVoucher(
        canvas: android.graphics.Canvas,
        voucher: VoucherDraft,
        bounds: RectF
    ) {
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(70, 82, 94)
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 15f
            isFakeBoldText = true
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8.5f
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 12f
            isFakeBoldText = true
        }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 8.5f
        }

        canvas.drawRoundRect(bounds, 8f, 8f, border)

        val pad = 10f
        val left = bounds.left + pad
        val top = bounds.top + pad
        val network = voucher.branding.networkName.ifBlank { "WiFi" }.take(28)

        canvas.drawText(network, left, top + 14f, title)
        if (voucher.branding.priceText.isNotBlank()) {
            val price = voucher.branding.priceText.take(18)
            canvas.drawText(
                price,
                bounds.right - pad - value.measureText(price),
                top + 14f,
                value
            )
        }

        canvas.drawText("Username", left, top + 39f, label)
        canvas.drawText(voucher.username.take(24), left, top + 54f, value)
        canvas.drawText("Password", left, top + 74f, label)
        canvas.drawText(voucher.password.take(24), left, top + 89f, value)

        val qr = VoucherQrCodeFactory.create(
            VoucherQrPayloadBuilder.build(voucher),
            size = 220
        )
        val qrSize = 72f
        val qrRect = RectF(
            bounds.right - pad - qrSize,
            top + 28f,
            bounds.right - pad,
            top + 28f + qrSize
        )
        canvas.drawBitmap(qr, null, qrRect, null)

        val metaY = bounds.bottom - 28f
        val plan = voucher.profile.ifBlank { "—" }.take(22)
        val time = voucher.limitUptime.orEmpty().take(16)
        canvas.drawText("Plan: " + plan, left, metaY, small)
        if (time.isNotBlank()) {
            canvas.drawText("Time: " + time, left, metaY + 12f, small)
        }

        if (voucher.branding.supportPhone.isNotBlank()) {
            val support = voucher.branding.supportPhone.take(24)
            canvas.drawText(
                support,
                bounds.right - pad - small.measureText(support),
                bounds.bottom - 10f,
                small
            )
        }
    }
}
