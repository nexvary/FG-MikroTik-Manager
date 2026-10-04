package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.*
import org.junit.Test

class VoucherHistoryIndexTest {
    @Test fun sameMillisecondIdsDoNotOverwriteAndRetainTheirTimestamp() {
        val ids = List(1000) { VoucherHistoryIndex.newId(1790911969376L) }
        assertEquals(1000, ids.toSet().size)
        assertTrue(ids.all { VoucherHistoryIndex.timestamp(it) == 1790911969376L })
        assertEquals(1790911969376L, VoucherHistoryIndex.timestamp("1790911969376"))
    }
    @Test fun pagingMixedLegacyAndNewIdsHasNoDuplicatesOrGaps() {
        val ids = setOf("1000", "1000-b", "1000-a", "999", "998-z")
        val first = VoucherHistoryIndex.page(ids, 2)
        val second = VoucherHistoryIndex.page(ids, 2, first.last())
        val third = VoucherHistoryIndex.page(ids, 2, second.last())
        assertEquals(listOf("1000-b", "1000-a", "1000", "999", "998-z"), first + second + third)
    }
    @Test fun eightyThousandIndexEntriesProduceOnlyRequestedPage() {
        val ids = (1..80000).map { (1790911969376L + it).toString() }.toSet()
        val page = VoucherHistoryIndex.page(ids, 20)
        assertEquals(20, page.size)
        assertEquals((1790911969376L + 80000).toString(), page.first())
        assertEquals(20, VoucherHistoryIndex.page(ids, 20, page.last()).size)
        assertTrue(VoucherHistoryIndex.page(ids, 20, page.last()).none { it in page })
    }
}
