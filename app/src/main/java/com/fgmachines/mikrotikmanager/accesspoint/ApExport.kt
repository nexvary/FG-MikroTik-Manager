package com.fgmachines.mikrotikmanager.accesspoint

object ApExport {
    fun xlsx(summary:List<ApRow>,observations:List<ApRow>,output:java.io.OutputStream) {
        fun xml(s:String)=s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
        fun note(reason:String)=listOf(mapOf("availability" to "N/A","reason" to reason))
        val sheets=linkedMapOf("Summary" to summary,"Access Points" to observations.filter{it["kind"]=="device"},"Sessions" to observations.filter{it["kind"]=="session"},"Cards" to note("No verified voucher usage correlation"),"Sales" to note("No verified sales attribution"),"Traffic" to observations.filter{it["kind"]=="session"},"Peak Hours" to note("Polling observations are not a complete usage history"))
        java.util.zip.ZipOutputStream(output).use{zip->
            fun entry(name:String,body:String){zip.putNextEntry(java.util.zip.ZipEntry(name));zip.write(body.toByteArray(Charsets.UTF_8));zip.closeEntry()}
            val root="http://schemas.openxmlformats.org/"
            entry("[Content_Types].xml","<Types xmlns=\"${root}package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"+sheets.keys.mapIndexed{i,_->"<Override PartName=\"/xl/worksheets/sheet${i+1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"}.joinToString("")+"</Types>")
            entry("_rels/.rels","<Relationships xmlns=\"${root}package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"${root}officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
            entry("xl/workbook.xml","<workbook xmlns=\"${root}spreadsheetml/2006/main\" xmlns:r=\"${root}officeDocument/2006/relationships\"><sheets>"+sheets.keys.mapIndexed{i,n->"<sheet name=\"${xml(n)}\" sheetId=\"${i+1}\" r:id=\"rId${i+1}\"/>"}.joinToString("")+"</sheets></workbook>")
            entry("xl/_rels/workbook.xml.rels","<Relationships xmlns=\"${root}package/2006/relationships\">"+sheets.keys.mapIndexed{i,_->"<Relationship Id=\"rId${i+1}\" Type=\"${root}officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${i+1}.xml\"/>"}.joinToString("")+"</Relationships>")
            sheets.entries.forEachIndexed{i,(_,rows)->
                val columns=rows.flatMap{it.keys}.distinct().sorted()
                fun column(index:Int):String{var n=index;var result="";do{result=('A'+n%26)+result;n=n/26-1}while(n>=0);return result}
                val cells=(listOf(columns)+rows.map{r->columns.map{r[it].orEmpty()}}).mapIndexed{r,values->"<row r=\"${r+1}\">"+values.mapIndexed{c,v->"<c r=\"${column(c)}${r+1}\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${xml(v)}</t></is></c>"}.joinToString("")+"</row>"}.joinToString("")
                entry("xl/worksheets/sheet${i+1}.xml","<worksheet xmlns=\"${root}spreadsheetml/2006/main\"><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" state=\"frozen\"/></sheetView></sheetViews><sheetData>$cells</sheetData>"+(if(columns.isEmpty())"" else "<autoFilter ref=\"A1:${column(columns.lastIndex)}${rows.size+1}\"/>")+"</worksheet>")
            }
        }
    }

    fun csv(rows:List<ApRow>):String {
        val columns=rows.flatMap{it.keys}.distinct().sorted()
        fun cell(raw:String):String {
            // Spreadsheet formula injection; quote alone does not neutralize formulas.
            val value=if(raw.trimStart().firstOrNull() in listOf('=','+','-','@') || raw.startsWith('\t') || raw.startsWith('\r')) "'$raw" else raw
            return "\""+value.replace("\"","\"\"")+"\""
        }
        return "\uFEFF"+(listOf(columns)+rows.map{r->columns.map{r[it].orEmpty()}}).joinToString("\r\n"){it.joinToString(",",transform=::cell)}+"\r\n"
    }
}
