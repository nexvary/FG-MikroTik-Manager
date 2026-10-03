package com.fgmachines.mikrotikmanager.ui

import android.content.Context
import android.print.PrintManager
import android.print.PrintAttributes
import android.webkit.WebView
import android.webkit.WebViewClient
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange

/** Android's print framework offers printers and Save as PDF; no web requests or JavaScript. */
fun printBusinessReceipt(context: Context,html: String) {
    val view=WebView(context)
    view.settings.javaScriptEnabled=false;view.settings.allowFileAccess=false;view.settings.allowContentAccess=false
    view.webViewClient=object: WebViewClient() {
        private var opened=false
        override fun onPageFinished(v: WebView,url: String) {
            if(opened)return;opened=true
            val delegate=v.createPrintDocumentAdapter("FG MTM receipt")
            val adapter=object: PrintDocumentAdapter() {
                override fun onStart()=delegate.onStart()
                override fun onLayout(oldAttributes: PrintAttributes?,newAttributes: PrintAttributes?,signal: CancellationSignal?,callback: LayoutResultCallback?,extras: Bundle?)=delegate.onLayout(oldAttributes,newAttributes,signal,callback,extras)
                override fun onWrite(pages: Array<out PageRange>?,destination: ParcelFileDescriptor?,signal: CancellationSignal?,callback: WriteResultCallback?)=delegate.onWrite(pages,destination,signal,callback)
                override fun onFinish() { delegate.onFinish();v.destroy() }
            }
            (context.getSystemService(Context.PRINT_SERVICE) as PrintManager).print("FG MTM receipt",adapter,PrintAttributes.Builder().build())
        }
    }
    view.loadDataWithBaseURL(null,html,"text/html","UTF-8",null)
}
