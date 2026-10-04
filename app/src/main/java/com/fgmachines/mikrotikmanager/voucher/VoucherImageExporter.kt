package com.fgmachines.mikrotikmanager.voucher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VoucherImageExporter {
    suspend fun export(context: Context, voucher: VoucherDraft): File = withContext(Dispatchers.IO) {
        val bitmap = Bitmap.createBitmap(900, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.drawColor(Color.WHITE)
        canvas.scale(3f,3f)
        VoucherPdfExporter().drawVoucher(canvas,voucher,RectF(8f,8f,292f,232f))
        val dir = File(context.cacheDir,"exports").apply { mkdirs() }
        val file = File(dir,"voucher-" + java.util.UUID.randomUUID().toString() + ".png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle();file
    }
}
