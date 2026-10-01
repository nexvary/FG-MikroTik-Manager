package com.fgmachines.mikrotikmanager.voucher

import java.security.SecureRandom

class VoucherGenerator(
    private val random: RandomSource = SecureRandomSource()
) {
    fun generate(request: VoucherBatchRequest): VoucherBatch {
        val usernames = LinkedHashSet<String>(request.quantity)
        val vouchers = ArrayList<VoucherDraft>(request.quantity)

        var attempts = 0
        val maxAttempts = request.quantity * 50

        while (vouchers.size < request.quantity) {
            check(attempts++ < maxAttempts) {
                "Unable to generate enough unique vouchers with the selected format"
            }

            val rawUsername = randomToken(request.usernameLength, request.characterSet)
            val username = request.prefix + rawUsername + request.suffix

            if (!usernames.add(username)) continue

            val password = when (request.passwordMode) {
                VoucherPasswordMode.SAME_AS_USERNAME -> username
                VoucherPasswordMode.RANDOM ->
                    randomToken(request.passwordLength, request.characterSet)
            }

            vouchers += VoucherDraft(
                username = username,
                password = password,
                profile = request.profile,
                server = request.server,
                comment = request.comment,
                limitUptime = request.limitUptime,
                limitBytesTotal = request.limitBytesTotal
            )
        }

        return VoucherBatch(
            request = request,
            vouchers = vouchers
        )
    }

    private fun randomToken(
        length: Int,
        characterSet: VoucherCharacterSet
    ): String {
        val alphabet = when (characterSet) {
            VoucherCharacterSet.NUMERIC -> NUMERIC
            VoucherCharacterSet.ALPHANUMERIC -> ALPHANUMERIC
        }

        return buildString(length) {
            repeat(length) {
                append(alphabet[random.nextInt(alphabet.length)])
            }
        }
    }

    private companion object {
        const val NUMERIC = "0123456789"

        // Ambiguous characters 0/O and 1/I/L are intentionally excluded.
        const val ALPHANUMERIC =
            "23456789ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz"
    }
}

fun interface RandomSource {
    fun nextInt(bound: Int): Int
}

private class SecureRandomSource : RandomSource {
    private val secureRandom = SecureRandom()

    override fun nextInt(bound: Int): Int = secureRandom.nextInt(bound)
}
