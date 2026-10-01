package com.fgmachines.mikrotikmanager.voucher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoucherGeneratorTest {

    @Test
    fun generatesRequestedNumberOfUniqueVouchers() {
        val seeded = java.util.Random(42L)
        val source = RandomSource { bound ->
            seeded.nextInt(bound)
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
        val seeded = java.util.Random(1337L)
        val source = RandomSource { bound ->
            seeded.nextInt(bound)
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
