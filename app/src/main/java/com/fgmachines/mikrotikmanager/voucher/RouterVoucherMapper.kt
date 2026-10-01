package com.fgmachines.mikrotikmanager.voucher

data class RouterCommand(
    val path: String,
    val attributes: Map<String, String>
)

data class VoucherWriteFailure(
    val commandIndex: Int,
    val path: String,
    val message: String
)

data class VoucherWriteResult(
    val totalCommands: Int,
    val succeededCommands: Int,
    val failures: List<VoucherWriteFailure>
) {
    val isSuccess: Boolean
        get() = failures.isEmpty()
}

object RouterVoucherMapper {

    fun commands(
        settings: WcgCardSettings,
        batch: VoucherBatch
    ): List<RouterCommand> =
        batch.vouchers.flatMap { voucher ->
            when (settings.accessMode) {
                VoucherAccessMode.HOTSPOT -> listOf(
                    RouterCommand(
                        path = "ip/hotspot/user",
                        attributes = buildMap {
                            put("name", voucher.username)
                            put("password", voucher.password)
                            put("profile", voucher.profile)
                            put("server", voucher.server)
                            voucher.limitUptime?.takeIf { it.isNotBlank() }?.let {
                                put("limit-uptime", it)
                            }
                            voucher.limitBytesTotal?.let {
                                put("limit-bytes-total", it.toString())
                            }
                            voucher.comment.takeIf { it.isNotBlank() }?.let {
                                put("comment", it)
                            }
                        }
                    )
                )

                VoucherAccessMode.USER_MANAGER -> buildList {
                    add(
                        RouterCommand(
                            path = "user-manager/user",
                            attributes = buildMap {
                                put("name", voucher.username)
                                put("password", voucher.password)
                                voucher.comment.takeIf { it.isNotBlank() }?.let {
                                    put("comment", it)
                                }
                            }
                        )
                    )
                    voucher.profile.takeIf { it.isNotBlank() }?.let { profile ->
                        add(
                            RouterCommand(
                                path = "user-manager/user-profile",
                                attributes = mapOf(
                                    "user" to voucher.username,
                                    "profile" to profile
                                )
                            )
                        )
                    }
                }

                VoucherAccessMode.PPPOE -> listOf(
                    RouterCommand(
                        path = "ppp/secret",
                        attributes = buildMap {
                            put("name", voucher.username)
                            put("password", voucher.password)
                            put("service", "pppoe")
                            put("profile", voucher.profile)
                            voucher.limitBytesTotal?.let {
                                put("limit-bytes-out", it.toString())
                            }
                            voucher.comment.takeIf { it.isNotBlank() }?.let {
                                put("comment", it)
                            }
                        }
                    )
                )
            }
        }
}
