package com.fgmachines.mikrotikmanager.business

/** Bounded RFC4180-style parser. No expression execution; spreadsheet exports neutralize formula prefixes. */
object BusinessCsv {
    const val MAX_CHARS=2_000_000
    fun parse(text: String): List<List<String>> {
        require(text.length<=MAX_CHARS) { "FILE_TOO_LARGE" }
        val input=text.removePrefix("\uFEFF");val rows=mutableListOf<List<String>>();val row=mutableListOf<String>();val cell=StringBuilder()
        var quoted=false;var closed=false;var i=0
        fun field() { require(cell.length<=2000) { "INVALID_CSV" };row+=cell.toString();cell.setLength(0);closed=false }
        fun record() { field();rows+=row.toList();row.clear();require(rows.size<=1001) { "IMPORT_LIMIT" } }
        while(i<input.length) {
            val c=input[i]
            if(quoted) {
                if(c=='"') { if(i+1<input.length && input[i+1]=='"') { cell.append('"');i++ } else { quoted=false;closed=true } }
                else cell.append(c)
            } else when(c) {
                '"' -> { require(cell.isEmpty() && !closed) { "INVALID_CSV" };quoted=true }
                ',' -> field()
                '\r','\n' -> { record();if(c=='\r' && i+1<input.length && input[i+1]=='\n') i++ }
                else -> { require(!closed) { "INVALID_CSV" };cell.append(c) }
            }
            require(cell.length<=2000 && row.size<=20) { "INVALID_CSV" };i++
        }
        require(!quoted) { "INVALID_CSV" }
        if(cell.isNotEmpty() || row.isNotEmpty() || closed) record()
        return rows
    }
    fun line(cells: List<String>): String=cells.joinToString(",") { raw ->
        val safe=if(raw.trimStart().firstOrNull() in listOf('=','+','-','@','\t','\r')) "'"+raw else raw
        "\""+safe.replace("\"","\"\"")+"\""
    }+"\r\n"
}
