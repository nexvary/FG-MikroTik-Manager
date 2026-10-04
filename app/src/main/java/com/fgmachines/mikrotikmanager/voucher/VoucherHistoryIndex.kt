package com.fgmachines.mikrotikmanager.voucher

import java.util.UUID

/** Legacy timestamp IDs remain readable; new batches cannot overwrite one another within a millisecond. */
internal object VoucherHistoryIndex {
    fun newId(timestamp: Long): String = "$timestamp-${UUID.randomUUID()}"
    fun timestamp(id: String): Long = id.substringBefore('-').toLongOrNull() ?: 0L
    fun page(ids: Set<String>, limit: Int, beforeId: String? = null): List<String> {
        require(limit in 1..100) { "Page size must be between 1 and 100" }
        val beforeTime = beforeId?.let(::timestamp)
        return ids.asSequence().filter { id ->
            beforeId == null || timestamp(id) < beforeTime!! ||
                (timestamp(id) == beforeTime && id < beforeId)
        }.sortedWith(compareByDescending<String> { timestamp(it) }.thenByDescending { it })
            .take(limit).toList()
    }
}
