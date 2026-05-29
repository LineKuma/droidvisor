package com.droidvisor

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTest {

    @Test
    fun navItem_creation_succeeds() {
        val navItem = NavItem("test", "Test", null as? androidx.compose.ui.graphics.vector.ImageVector)
        assertNotNull(navItem.route)
        assertTrue(navItem.route == "test")
    }
}