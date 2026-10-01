package com.fgmachines.mikrotikmanager.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import com.fgmachines.mikrotikmanager.voucher.VoucherExportBundle
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class VoucherSavedFiles(
    val directory: File,
    val rsc: File,
    val csv: File,
    val html: File
) {
    fun all(): List<File> = listOf(rsc, csv, html)
}

object VoucherAndroidExporter {

    fun saveBundle(
        context: Context,
        bundle: VoucherExportBundle,
        label: String
    ): VoucherSavedFiles {
        val documents = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val root = File(documents, "FG-MikroTik-Manager/Exports")
        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val safeLabel = sanitize(label).ifBlank { "cards" }
        val directory = File(root, safeLabel + "-" + timestamp)

        check(directory.mkdirs() || directory.isDirectory) {
            "Unable to create export directory"
        }

        val base = safeLabel + "-" + timestamp
        val rsc = File(directory, base + ".rsc").apply {
            writeText(bundle.rsc, Charsets.UTF_8)
        }
        val csv = File(directory, base + ".csv").apply {
            writeText(bundle.csv, Charsets.UTF_8)
        }
        val html = File(directory, base + ".html").apply {
            writeText(bundle.html, Charsets.UTF_8)
        }

        return VoucherSavedFiles(directory, rsc, csv, html)
    }

    fun shareFiles(
        context: Context,
        files: List<File>,
        chooserTitle: String = "مشاركة كروت MikroTik"
    ) {
        require(files.isNotEmpty()) { "No files to share" }

        val uris = ArrayList<Uri>(
            files.map { file ->
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )
            }
        )

        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    fun printHtml(
        context: Context,
        html: String,
        jobName: String
    ) {
        require(html.isNotBlank()) { "HTML output is empty" }

        val printManager =
            context.getSystemService(Context.PRINT_SERVICE) as PrintManager

        val webView = WebView(context)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = object : WebViewClient() {
            private var submitted = false

            override fun onPageFinished(view: WebView, url: String?) {
                if (submitted) return
                submitted = true

                printManager.print(
                    sanitize(jobName).ifBlank { "FG-MikroTik-Cards" },
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().build()
                )
            }
        }

        webView.loadDataWithBaseURL(
            null,
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun sanitize(value: String): String =
        value
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-')
            .take(48)
}
