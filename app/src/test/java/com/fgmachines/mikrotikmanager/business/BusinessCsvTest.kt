package com.fgmachines.mikrotikmanager.business

import org.junit.Assert.*
import org.junit.Test

class BusinessCsvTest {
    @Test fun parsesQuotedArabicMultilineAndBom() {
        val rows=BusinessCsv.parse("\uFEFFname,phone\r\n\"أحمد, محمد\",\"line1\nline2\"\r\n\"a\"\"b\",\"\"\r\n")
        assertEquals(listOf("أحمد, محمد","line1\nline2"),rows[1]);assertEquals(listOf("a\"b",""),rows[2])
    }
    @Test fun formulaPrefixesAreNeutralizedAndQuotesEscaped() {
        val cells=BusinessCsv.parse(BusinessCsv.line(listOf("=SUM(A1)","  +cmd","-12","@x","hello,\"world\""))).single()
        assertEquals("'=SUM(A1)",cells[0]);assertEquals("'  +cmd",cells[1]);assertEquals("'-12",cells[2]);assertEquals("'@x",cells[3]);assertEquals("hello,\"world\"",cells[4])
    }
    @Test fun rejectsBrokenQuotesAndOverLimit() {
        for(text in listOf("\"unfinished","\"done\"bad","bad\"quote",(1..1002).joinToString("\n") { "x" })) {
            try { BusinessCsv.parse(text);fail("Expected invalid input") } catch(_: IllegalArgumentException) {}
        }
    }
    @Test fun handlesCrLfEmptyFieldsAndFinalRecord() { assertEquals(listOf(listOf("a","","c"),listOf("","","")),BusinessCsv.parse("a,,c\r\n,,")) }
}
