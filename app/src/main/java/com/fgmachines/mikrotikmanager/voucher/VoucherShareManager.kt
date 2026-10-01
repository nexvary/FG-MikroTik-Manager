package com.fgmachines.mikrotikmanager.voucher

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

object VoucherShareManager {
    fun shareTextFile(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String
    ) {
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, safeName)
        file.writeText(content, Charsets.UTF_8)

        shareFile(context, file, mimeType)
    }

    fun shareFile(
        context: Context,
        file: File,
        mimeType: String
    ) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "Share voucher export")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
