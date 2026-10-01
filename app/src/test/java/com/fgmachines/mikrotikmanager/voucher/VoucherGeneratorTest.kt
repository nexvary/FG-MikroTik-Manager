package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherGeneratorTest {

    @Test
    fun generatesRequestedNumberOfUniqueVouchers() {
        var calls = 0
        val source = RandomSource { bound ->
            val token = calls / 4
            val digit = calls % 4
            calls++
            val divisor = when (digit) {
                0 -> 1
                1 -> 10
                2 -> 100
                else -> 1000
            }
            (token / divisor) % bound
        }

        val batch = VoucherGenerator(source).generate(
            VoucherBatchRequest(
                quantity = 10,
                usernameLength = 4,
                prefix = "FG-",
                passwordMode = VoucherPasswordMode.SAME_AS_USERNAME,
                profile = "5M-1D"
            )
        )

        assertEquals(10, batch.vouchers.size)
        assertEquals(10, batch.vouchers.map { it.username }.toSet().size)
        assertTrue(batch.vouchers.all { it.username.startsWith("FG-") })
        assertTrue(batch.vouchers.all { it.password == it.username })
    }

    @Test
    fun generatesSeparateRandomPasswords() {
        var cursor = 3
        val source = RandomSource { bound ->
            val result = cursor % bound
            cursor += 7
            result
        }

        val batch = VoucherGenerator(source).generate(
            VoucherBatchRequest(
                quantity = 3,
                usernameLength = 6,
                passwordMode = VoucherPasswordMode.RANDOM,
                passwordLength = 8,
                characterSet = VoucherCharacterSet.ALPHANUMERIC,
                profile = "1H"
            )
        )

        assertTrue(batch.vouchers.all { it.password.length == 8 })
        assertTrue(batch.vouchers.all { it.password != it.username })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMoreThanFiveThousandVouchers() {
        VoucherBatchRequest(
            quantity = 5001,
            usernameLength = 6,
            profile = "default"
        )
    }
}
