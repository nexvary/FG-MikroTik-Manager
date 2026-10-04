package com.fgmachines.mikrotikmanager.business

import org.junit.Assert.*
import org.junit.Test

class BusinessAccessTest {
    @Test fun financialAndRouterRolesAreDistinct() {
        assertTrue(BusinessAccess.permits("CASHIER",BusinessPermission.POST))
        assertFalse(BusinessAccess.permits("CASHIER",BusinessPermission.REVERSE))
        assertFalse(BusinessAccess.permits("CASHIER",BusinessPermission.ROUTER))
        assertTrue(BusinessAccess.permits("TECHNICIAN",BusinessPermission.ROUTER))
        assertFalse(BusinessAccess.permits("TECHNICIAN",BusinessPermission.POST))
        assertFalse(BusinessAccess.permits("MANAGER",BusinessPermission.AUTH))
        assertTrue(BusinessAccess.permits("OWNER",BusinessPermission.AUTH))
        assertFalse(BusinessAccess.permits("MANAGER",BusinessPermission.VOUCHERS))
        assertFalse(BusinessAccess.permits("TECHNICIAN",BusinessPermission.VOUCHERS))
        assertTrue(BusinessAccess.permits("ADMIN",BusinessPermission.VOUCHERS))
    }
    @Test fun branchAndOrganizationIsolation() {
        val assigned=BusinessScope("org","a")
        assertTrue(BusinessAccess.sameScope("MANAGER",assigned,assigned))
        assertFalse(BusinessAccess.sameScope("MANAGER",assigned,BusinessScope("org","b")))
        assertTrue(BusinessAccess.sameScope("ADMIN",assigned,BusinessScope("org","b")))
        assertFalse(BusinessAccess.sameScope("OWNER",assigned,BusinessScope("other","a")))
    }
    @Test fun unknownAndResellerCannotAcquireBusinessPrivileges() {
        assertTrue(BusinessAccess.permissions("unknown").isEmpty())
        assertTrue(BusinessAccess.permissions("RESELLER").isEmpty())
        assertEquals(setOf(BusinessPermission.READ),BusinessAccess.permissions("READ_ONLY"))
    }
}
