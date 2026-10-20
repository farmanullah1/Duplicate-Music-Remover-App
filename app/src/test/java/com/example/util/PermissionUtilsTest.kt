package com.example.util

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionUtilsTest {

    @Test
    fun getRequiredStoragePermissions_returnsNonEmptyArray() {
        val permissions = PermissionUtils.getRequiredStoragePermissions()
        assertNotNull(permissions)
        assertTrue(permissions.isNotEmpty())
    }
}
